# POC Agent-to-Agent — Ana → Agent Investimentos → MCP (tracking-money, cdb)

- **Data:** 2026-09-22
- **Status:** aprovado em brainstorming
- **Objetivo:** validar, em Java 25 + Spring Boot 4, a comunicação Supervisor → Especialista via **A2A** e Especialista → ferramentas via **MCP**, conforme a guideline HAIFA (§4.6: "Times em Spring Boot devem validar integração A2A em POC antes de rollout").

## 1. Decisões

| Tema | Decisão |
|---|---|
| Stack de agente | LangChain4j 1.20 (AI Services) sobre Spring Boot 4.1.1 |
| Cliente A2A (Ana) | SDK a2a-java (`Client` + `JSONRPCTransport`) encapsulado em `@Tool delegar_investimentos`. **Não** usamos `@A2AClientAgent`: o spike mostrou que ele descarta o DataPart (schema §9) e busca o Agent Card no startup |
| Servidor A2A | SDK oficial a2a-java `org.a2aproject.sdk:*:1.3.1.Final` (protocolo A2A 1.0: método `SendMessage`, header `A2A-Version: 1.0`), JSON-RPC 2.0 síncrono, HTTP/1.1, Agent Card em `/.well-known/agent-card.json`, ponte manual (sem CDI) para Spring MVC. Timeout interno do SDK (5s hardcoded no builder) elevado para 60s |
| MCP servers | SDK oficial MCP Java `io.modelcontextprotocol.sdk:mcp:2.0.1` (servlet Streamable HTTP em `/mcp`); cliente `langchain4j-mcp` |
| LLM | Cliente OpenAI-compatible configurável por env (`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`) — aponta para LLM Gateway (LiteLLM), OpenAI ou Ollama sem mudar código |
| Dados dos MCP servers | Mock em memória por `customerId` |
| Entrada | REST `POST /chat` na Ana |
| Memória | Postgres nos dois agentes (um container, schemas `ana` e `investimentos`) via `langchain4j-community-sql` (`SQLChatMemoryStore`) |
| Execução | Dockerfile por projeto + `docker-compose.yml` (mesma rede = "intra-cluster") + `Makefile` + `smoke-test.sh` |
| Estrutura | 4 projetos Maven independentes (cada um "de um time"), sem parent comum |

Aplicação da regra 5.1 da guideline: Agent Investimentos decide etapas internas → **A2A**. tracking-money e cdb são stateless, recebem argumentos e devolvem resultado → **MCP**.

## 2. Arquitetura

```
cliente ──POST /chat──▶ ana-agent :8080 ──A2A JSON-RPC──▶ investimentos-agent :8081
                          │ (LLM supervisor)                 │ (LLM especialista)
                          │                                  ├─MCP Streamable HTTP─▶ tracking-money-mcp :8082
                          │                                  └─MCP Streamable HTTP─▶ cdb-mcp :8083
                          ▼                                  ▼
                   postgres schema ana            postgres schema investimentos
                                  LLM: endpoint OpenAI-compatible (env)
```

### Componentes

- **ana-agent** — supervisor. `POST /chat {sessionId, customerId, message}` → `{sessionId, reply, debug?}`. AI Service com system prompt de triagem e **uma** capacidade de delegação: o Agent Investimentos, conhecido apenas pela URL do servidor A2A (env `INVESTIMENTOS_A2A_URL`). Não importa schema algum do especialista. Memória de chat em Postgres por `sessionId`.
- **investimentos-agent** — especialista. Servidor A2A com Agent Card (skill `localizar-dinheiro-investimentos`). `AgentExecutor` executa um AI Service cujas tools vêm dos dois MCP servers. Memória em Postgres por `contextId` do A2A. Responde Task `COMPLETED` com **DataPart** no schema §9 e **TextPart** com o `answerDraft`.
- **tracking-money-mcp** — tools `listar_movimentacoes(customerId)` e `consultar_status_transferencia(transferenciaId)`.
- **cdb-mcp** — tools `listar_posicoes_cdb(customerId)` e `listar_resgates_cdb(customerId)` (status `SOLICITADO` | `EM_LIQUIDACAO` | `LIQUIDADO`).

### Schema de resposta do especialista (guideline §9)

```json
{ "facts": ["..."], "answerDraft": "...", "confidence": 0.0, "risks": ["..."], "sources": ["..."] }
```
Todos os campos obrigatórios; `confidence` ∈ [0,1].

## 3. Jornada

1. **Turno 1 (só Ana):** "meu dinheiro sumiu" → Ana pergunta onde estava aplicado (conta, investimentos, outros). Sem A2A.
2. **Turno 2 (delegação):** "estava em investimentos e não consigo encontrar" → o LLM da Ana chama `delegar_investimentos(pedido)`; a tool envia A2A `SendMessage` com TextPart (intenção em linguagem natural) + DataPart `{"customerId": ...}`, `contextId = sessionId`. O `customerId` vem do request (via `InvocationParameters`), nunca do LLM.
3. **Dentro do Investimentos (invisível à Ana):** consulta resgates e posições de CDB; se houver resgate, consulta movimentações para ver se o crédito caiu; cruza e monta o schema §9.
4. **Resposta:** Ana reescreve o `answerDraft` no tom dela. `debug` (facts/confidence/sources) opcional na resposta do `/chat` (`?debug=true`).

### Cenários mock

| customerId | Mock | Resposta esperada (palavra-chave) |
|---|---|---|
| `cli-001` | Resgate CDB `EM_LIQUIDACAO` há 5 min, sem crédito na conta | "liquidação" |
| `cli-002` | Resgate `LIQUIDADO` + crédito na conta ontem | "conta" |
| `cli-003` | CDB ativo, nenhum resgate | "aplicado" |
| `cli-004` | Nenhuma posição ou movimentação | `confidence` baixa, `risks` sugere atendimento humano |

## 4. Erros

- Investimentos indisponível / timeout A2A (90s no cliente da Ana; 60s de execução no servidor) / Task `FAILED` → Ana responde "não consegui consultar seus investimentos agora, tente em instantes" e loga; `/chat` não retorna 5xx.
- MCP server fora → tool retorna erro ao LLM; Investimentos responde com `risks` preenchido, `confidence` baixa e fonte ausente em `sources`.
- Config de LLM ausente → fail-fast no startup.

## 5. Observabilidade

- Log estruturado por hop com `sessionId`/`contextId`, `taskId`, tool chamada e duração.
- `/actuator/health` em todos os serviços (healthcheck no compose).
- Fora do escopo: OpenTelemetry, LangWatch.

## 6. Testes

- MCP servers: integração com cliente MCP real (`tools/list`, `tools/call`) cobrindo os cenários mock.
- Investimentos: Agent Card + `SendMessage` com `ChatModel` fake roteirizado (sem chave de LLM).
- Ana: Investimentos stubado (servidor A2A fake em teste) — triagem, delegação e fallback de erro, com `ChatModel` fake.
- Integração (`@Tag("integration")`, `make test-integration`): Ana (`ChatControllerIT`) e Investimentos (`A2aJsonRpcControllerIT`) com a aplicação real, Postgres e LLM real (Ollama) via Testcontainers; saídas para outros sistemas dubladas (A2A do especialista na Ana; clients/tools MCP no especialista). Regra ArchUnit exige um `<Entrypoint>IT` por `@RestController`. `make test` segue só com unitários.
- E2E: `smoke-test.sh` contra o compose com LLM real (`make smoke`), asserções por palavra-chave.

## 7. Fora do escopo

Autenticação entre agentes, MCP Gateway, streaming (`SendStreamingMessage`), push notifications, UI, Kubernetes, OpenTelemetry.

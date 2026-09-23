# POC Agent-to-Agent — Ana → Investimentos → MCP

Valida, em Java 25 + Spring Boot 4, a delegação **Supervisor → Especialista via A2A 1.0** e **Especialista → tools via MCP**.
Design: `docs/superpowers/specs/2026-09-22-a2a-poc-design.md`.

```
cliente ─POST /chat─▶ ana-agent:8080 ─A2A JSON-RPC─▶ investimentos-agent:8081 ─MCP─▶ cdb-mcp:8083
                                                                               └─MCP─▶ tracking-money-mcp:8082
```

## Rodando

Pré-requisitos: JDK 25 (`$HOME/.sdkman/candidates/java/25.0.2-tem`, ou `make JAVA_HOME=...`), Maven, Docker, `jq`.

```bash
cp .env.example .env    # preencha LLM_BASE_URL / LLM_API_KEY / LLM_MODEL
make test               # só unitários (rápido, sem Docker nem LLM real)
make test-integration   # testes de integração: Postgres + LLM real (Ollama) via Testcontainers — ver abaixo
make up                 # build + compose, espera todos healthy — o chat fica em http://localhost:3000
make smoke              # jornada completa para cli-001..cli-004
make smoke-falha        # derruba o especialista e confere o fallback da Ana
make logs               # hops: ana.chat, ana.tool.delegar_investimentos, a2a.task.*, mcp.tool.call
make down
make test-web          # frontend: typecheck + lint + vitest
make run-web           # chat web em http://localhost:3000 (next dev), Ana em localhost:8080
```

### Rodar e explorar localmente (fora do Docker)

Para depurar um serviço na IDE/terminal sem subir o compose inteiro — Postgres do compose + `mvn spring-boot:run`
(não usa Testcontainers):

```bash
make db-up              # só o Postgres do compose (localhost:55432, schemas ana/investimentos)
make run-mcps           # cdb-mcp :8083 + tracking-money-mcp :8082, saída prefixada, Ctrl+C encerra
make run-investimentos  # :8081 (db-up + carrega .env); precisa dos MCPs no ar
make run-ana            # :8080 (db-up + carrega .env); delega para localhost:8081
make db-down            # para o Postgres (mantém os dados)
make db-reset           # remove o container e o volume anônimo do Postgres (init.sql roda de novo)
```

Fora do Docker, `LLM_BASE_URL` precisa ser alcançável **a partir do host**: para Ollama nativo use
`http://localhost:11434/v1`, não `http://host.docker.internal:11434/v1` (esse nome só existe dentro dos containers).

## Testes de integração (Testcontainers)

Mesmo padrão do projeto de referência `analizza-auction`, adaptado a projetos Maven de módulo único:

- **Separação por tag JUnit** no mesmo `src/test/java`: o surefire exclui `@Tag("integration")` por padrão
  (`make test` / `mvn test`); o profile Maven `integration-test` roda só essa tag
  (`make test-integration` = `mvn -P integration-test test` em `investimentos-agent` e `ana-agent`).
- **`BaseIntegrationTest`** por agente (pacote base): `@SpringBootTest(RANDOM_PORT)` com a aplicação real, **sem**
  profile `test` (os beans de `AnaConfig` / `EspecialistaConfig` carregam de verdade), containers singleton estáticos
  iniciados de forma eager (sem `@Container`/`@Testcontainers`/`@ServiceConnection`/`withReuse`), ligação por
  `@DynamicPropertySource` (`memoria.*`, `llm.*`) e `RestTestClient` na porta aleatória. Um `@BeforeEach` limpa `chat_memory`.
- **Reais**: Postgres (`postgres:17-alpine`, a mesma imagem do compose; schema `public`) e o LLM, via Ollama
  (`ollama/ollama:0.34.3` + modelo `qwen2.5:3b`, troque com `IT_OLLAMA_MODEL=...`).
- **Dublados** (outros sistemas): na Ana, `InvestimentosClient` com `@MockitoBean`; no especialista, os dois
  `McpClient` (conectam no construtor) com `@MockitoBean` por nome e o `mcpToolProvider` trocado por um fake com os 4
  nomes de tool reais e respostas copiadas dos mocks dos MCP servers (cli-001), ainda decorado pelo `ToolProviderComLog`.
- **Nomenclatura**: `<Entrypoint>IT extends BaseIntegrationTest` (`ChatControllerIT`, `A2aJsonRpcControllerIT`).
  A regra ArchUnit `architecture/EntrypointHasIntegrationTestRuleIT` (`FreezingArchRule`, store em
  `<projeto>/archunit_store`, versionar após a primeira execução) falha se um `@RestController`/`@Scheduled` novo não tiver IT.
- As asserções toleram um modelo pequeno: status, schema §9, `confidence` em [0,1], memória persistida no Postgres e
  o `customerId` vindo do request (nunca do LLM) — não comparam o texto gerado.

**Primeira execução** (precisa de Docker rodando) baixa: `postgres:17-alpine` (~100MB, se ainda não estiver local),
`ollama/ollama:0.34.3` (~3,7GB comprimido em amd64, ~2,8GB em arm64), o modelo `qwen2.5:3b` (~2GB, via `ollama pull`
dentro do container) e `testcontainers/ryuk:0.12.0` (se ainda não estiver local). Depois do pull, o container é gravado como a imagem local
`tc-ollama-qwen2.5-3b` (`OllamaContainer.commitToImage`) e as execuções seguintes sobem direto dela, sem baixar o
modelo de novo. Para refazer o cache: `docker rmi tc-ollama-qwen2.5-3b`. Inferência em CPU é lenta (minutos por
teste); por isso o timeout do `ChatModel` é configurável (`llm.timeout`, default 60s; 300s nos ITs).

## Fluxo ponta a ponta

Turno 2 da jornada ("estava em investimentos e não encontro"), do navegador até os MCP servers e de volta. No turno 1 ("meu dinheiro sumiu") o LLM da Ana responde direto, sem chamar a tool: os passos da delegação não acontecem e `debug` volta `null`.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    box chat-web (Next.js :3000)
        participant CH as Chat.tsx
        participant BFF as api/chat/route.ts
    end
    box ana-agent (:8080)
        participant CC as ChatController
        participant AA as AnaAssistant
        participant DT as DelegacaoInvestimentosTool
        participant AC as InvestimentosA2aClient
    end
    box investimentos-agent (:8081)
        participant JR as A2aJsonRpcController
        participant RH as JSONRPCHandler / DefaultRequestHandler
        participant EX as InvestimentosAgentExecutor
        participant ES as EspecialistaInvestimentos
        participant TP as ToolExecutorComLog → DefaultMcpClient
    end
    participant CDB as cdb-mcp (:8083)<br/>CdbTools / CdbRepository
    participant TM as tracking-money-mcp (:8082)<br/>TrackingMoneyTools / TrackingMoneyRepository
    participant LLM as LLM (OpenAI-compatible)
    participant PG as Postgres (chat_memory)

    U->>CH: digita a mensagem
    CH->>BFF: POST /api/chat {sessionId, customerId, message}
    BFF->>CC: POST /chat?debug=true
    CC->>AA: conversar(sessionId, message, InvocationParameters)
    Note right of CC: InvocationParameters leva sessionId,<br/>customerId e requestId (fora do LLM)
    AA->>PG: SQLChatMemoryStore.getMessages(sessionId) [schema ana]
    AA->>LLM: prompt de triagem + histórico + tool delegar_investimentos
    LLM-->>AA: tool call delegar_investimentos(pedido)
    AA->>DT: delegarInvestimentos(pedido, InvocationParameters)
    DT->>AC: delegar(sessionId, customerId, pedido)
    AC->>JR: GET /.well-known/agent-card.json (A2ACardResolver)
    AC->>JR: POST / JSON-RPC SendMessage (A2A-Version 1.0)<br/>TextPart pedido + DataPart customerId, contextId = sessionId
    JR->>RH: onMessageSend(SendMessageRequest)
    RH->>EX: execute(RequestContext, AgentEmitter)
    EX->>ES: investigar(contextId, customerId + pedido)
    ES->>PG: getMessages(contextId) [schema investimentos]
    loop enquanto o LLM pedir tools
        ES->>LLM: pedido + tools MCP disponíveis
        LLM-->>ES: tool call
        ES->>TP: executa a tool (loga tool, contextId, durationMs)
        alt tools de CDB
            TP->>CDB: MCP tools/call listar_resgates_cdb / listar_posicoes_cdb
            CDB-->>TP: JSON com resgates e posições
        else tools de conta
            TP->>TM: MCP tools/call listar_movimentacoes / consultar_status_transferencia
            TM-->>TP: JSON com movimentações e status
        end
        TP-->>ES: resultado da tool
    end
    LLM-->>ES: JSON no schema §9 (facts, answerDraft, confidence, risks, sources)
    ES-->>EX: RespostaEspecialista
    EX->>RH: addArtifact(TextPart answerDraft + DataPart §9) e complete()
    RH-->>JR: Task TASK_STATE_COMPLETED
    JR-->>AC: resultado JSON-RPC
    AC-->>DT: RespostaInvestimentos (lida do DataPart)
    DT->>DT: UltimasRespostasInvestimentos.registrar(requestId)
    DT-->>AA: paraTextoLlm()
    AA->>LLM: resultado da tool
    LLM-->>AA: resposta final no tom da Ana
    AA->>PG: updateMessages(sessionId)
    AA-->>CC: reply
    CC-->>BFF: {sessionId, reply, debug}
    BFF-->>CH: JSON
    CH-->>U: bolha da Ana + PainelDebug (facts, confidence, risks, sources)
```

Se o especialista estiver fora, der timeout (90s) ou a Task terminar diferente de `COMPLETED`, o `InvestimentosA2aClient` lança `InvestimentosIndisponivelException`. A `DelegacaoInvestimentosTool` a converte em `INDISPONIVEL: ...`, e a Ana responde "não consegui consultar seus investimentos agora", sem 5xx. Se a Ana estiver fora, o BFF responde 502 e o chat mostra um aviso.

## Objetos do fluxo

| Objeto | App | Como funciona |
|---|---|---|
| `Chat` (`components/Chat.tsx`) | chat-web | Client component. Guarda a conversa em memória, gera o `sessionId` com `crypto.randomUUID()`, permite trocar o cliente (nova sessão) e chama `POST /api/chat`. Mostra "Ana está digitando…" e avisos de erro. |
| `PainelDebug` (`components/PainelDebug.tsx`) | chat-web | Um item por turno. Mostra o retorno do especialista (barra de `confidence`, `facts`, `risks`, `sources`) ou "sem delegação" quando `debug` é `null`. |
| `POST /api/chat` (`app/api/chat/route.ts`) | chat-web | BFF: repassa o corpo para `${ANA_URL}/chat?debug=true` com timeout de 120s. Erro de rede ou 5xx vira 502 com mensagem amiga; outros erros viram 400. O navegador nunca fala com a Ana. |
| `GET /api/health` (`app/api/health/route.ts`) | chat-web | Healthcheck do container (`{"status":"UP"}`). |
| `ChatController` | ana-agent | `POST /chat`. Valida os campos, monta os `InvocationParameters` (`sessionId`, `customerId`, `requestId`), chama o `AnaAssistant` e, com `?debug=true`, anexa o §9 do turno. |
| `AnaAssistant` (criado por `AnaFactory`) | ana-agent | AI Service do LangChain4j: prompt de triagem (`prompts/ana-system.txt`), memória por `sessionId` e uma única tool, `delegar_investimentos`. |
| `DelegacaoInvestimentosTool` | ana-agent | `@Tool delegar_investimentos`. Lê `customerId` e `sessionId` dos `InvocationParameters` (nunca do LLM), chama o cliente A2A, registra o §9 para o debug e devolve texto ao LLM, ou `INDISPONIVEL:` em caso de falha. |
| `UltimasRespostasInvestimentos` | ana-agent | Guarda o §9 por `requestId` durante a requisição, para o `ChatController` devolver no `debug`. |
| `InvestimentosA2aClient` | ana-agent | Cliente a2a-java. Resolve o Agent Card a cada chamada, envia `SendMessage` (TextPart + DataPart `customerId`, `contextId = sessionId`), impõe timeout de 90s com cancelamento e extrai o DataPart §9 da Task. |
| `SQLChatMemoryStore` (em `AnaConfig`) | ana-agent | Memória de chat da Ana no Postgres (schema `ana`, tabela `chat_memory`), por `sessionId`. |
| `A2aJsonRpcController` | investimentos-agent | Ponte Spring MVC do servidor A2A: `GET /.well-known/agent-card.json` e `POST /` (JSON-RPC 2.0). Os corpos trafegam como String e o SDK serializa. |
| `A2aServerConfig` | investimentos-agent | Monta o servidor a2a-java sem CDI (AgentCard, TaskStore e QueueManager em memória, MainEventBusProcessor, executores). Eleva o timeout do SDK de 5s para 60s via `RequestHandlerTimeouts`. |
| `JSONRPCHandler` / `DefaultRequestHandler` | investimentos-agent (SDK a2a-java) | Ciclo de vida da Task: cria, enfileira, executa o `AgentExecutor` e agrega os eventos até o estado final. |
| `InvestimentosAgentExecutor` | investimentos-agent | `AgentExecutor`. Extrai o `customerId` do DataPart, chama o especialista com a memória do `contextId`, publica o artifact `[TextPart, DataPart §9]` e dá `complete()`, ou `fail()` em caso de erro. |
| `EspecialistaInvestimentos` (criado por `EspecialistaFactory`) | investimentos-agent | AI Service com as tools MCP. Decide sozinho quais tools chamar (autonomia do especialista) e devolve `RespostaEspecialista` no schema §9. |
| `ToolProviderComLog` / `McpToolProvider` | investimentos-agent | Na montagem do especialista, descobre as tools dos dois MCP servers (Streamable HTTP em `/mcp`) e envolve o executor de cada uma com `ToolExecutorComLog`. |
| `ToolExecutorComLog` / `DefaultMcpClient` | investimentos-agent | Em cada chamada de tool feita pelo LLM: loga tool, `contextId` (memoryId) e duração, e executa a chamada MCP `tools/call` via `DefaultMcpClient`; erros sobem inalterados para o LangChain4j devolver ao LLM. |
| `SQLChatMemoryStore` (em `EspecialistaConfig`) | investimentos-agent | Memória do especialista no Postgres (schema `investimentos`), por `contextId` A2A. |
| `CdbTools` / `CdbRepository` | cdb-mcp | Tools `listar_posicoes_cdb` e `listar_resgates_cdb` (entrada `customerId`, JSON Schema estrito), com dados mock em memória. |
| `TrackingMoneyTools` / `TrackingMoneyRepository` | tracking-money-mcp | Tools `listar_movimentacoes` (`customerId`) e `consultar_status_transferencia` (`transferenciaId`), com dados mock em memória. |
| `McpServerConfig` | cdb-mcp e tracking-money-mcp | Registra o servlet `HttpServletStreamableServerTransportProvider` em `/mcp` e o `McpSyncServer` com as tools. |
| LLM | externo | Endpoint compatível com OpenAI configurado por `LLM_BASE_URL`, `LLM_API_KEY` e `LLM_MODEL`, usado pela Ana e pelo especialista. |
| Postgres | infra (compose) | Uma instância com os schemas `ana` e `investimentos`, cada um com sua tabela `chat_memory`. |

## Conversa manual

```bash
curl -s -X POST 'localhost:8080/chat?debug=true' -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","customerId":"cli-001","message":"meu dinheiro sumiu"}' | jq
curl -s -X POST 'localhost:8080/chat?debug=true' -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","customerId":"cli-001","message":"estava em investimentos e agora nao consigo encontrar"}' | jq
curl -s localhost:8081/.well-known/agent-card.json | jq
```

## Cenários mock

| customerId | Situação | Esperado |
|---|---|---|
| cli-001 | Resgate CDB em liquidação, crédito processando | "em liquidação, aguarde alguns minutos" |
| cli-002 | Resgate liquidado, crédito na conta | "já está na sua conta" |
| cli-003 | CDB ativo, sem resgate | "continua aplicado" |
| cli-004 | Nada encontrado | confidence < 0.5, encaminha para humano |

## Achados técnicos (Spring Boot + a2a-java)

- O servidor a2a-java é CDI/Quarkus; em Spring foi preciso um controller-ponte (`A2aJsonRpcController`) e wiring manual (`A2aServerConfig`).
- O builder do `DefaultRequestHandler` fixa timeout de 5s para o agente — elevado para 60s via `RequestHandlerTimeouts`.
- `@A2AClientAgent` (LangChain4j) descarta DataParts e resolve o Agent Card no startup; a Ana usa o client do a2a-java como `@Tool`.
- Protocolo A2A 1.0: método `SendMessage`, header `A2A-Version: 1.0` obrigatório.
- Dentro do especialista, o LLM copia o customerId para os argumentos das tools MCP; em produção, vincular o customerId no servidor (ex.: decorator do ToolProvider por contextId) para evitar consulta a outro cliente via prompt injection.
- `GetTask`/`ListTasks` do servidor A2A ficam expostos sem autenticação (`authorizationRequired(false)` em `A2aServerConfig`, porta 8081 publicada no compose); em produção, habilitar um `TaskAuthorizationProvider`/autenticação antes de expor a porta.

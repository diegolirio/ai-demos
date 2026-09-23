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
make up                 # build + compose, espera todos healthy
make smoke              # jornada completa para cli-001..cli-004
make smoke-falha        # derruba o especialista e confere o fallback da Ana
make logs               # hops: ana.chat, ana.tool.delegar_investimentos, a2a.task.*, mcp.tool.call
make down
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

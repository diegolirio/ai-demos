# Relatório agentic em /diagnostics/report — plano de implementação

> **Execução:** o Diego implementa cada task à mão; o Claude descreve, dá a conferência e revisa o que foi escrito. Este plano não traz corpo de classe: só arquivo, assinatura, anotação, propriedade, comando e resultado esperado.

**Goal:** servir em `GET /diagnostics/report` o HTML do `HtmlReportGenerator`, com cada chat como uma sessão (pergunta, resposta, tokens, duração).

**Architecture:** o `Assistant` deixa de ser `@AiService` e passa a ser uma sequência do `langchain4j-agentic` com um único passo (`AnswerAgent`), montada numa `@Configuration` com um `AgentMonitor` como listener. Um controller novo lê o monitor e devolve o HTML.

**Tech Stack:** Spring Boot 4.1.1, Kotlin, Java 25, Gradle Kotlin DSL, LangChain4j 1.20.0-beta30 (`langchain4j-agentic`, starter open-ai).

**Spec:** `docs/superpowers/specs/2026-10-19-relatorio-agentic-design.md`

## Global Constraints

- Versão: `dev.langchain4j:langchain4j-agentic:$langchain4jVersion`, com `langchain4jVersion=1.20.0-beta30` lido de `gradle.properties`.
- `outputKey = "resposta"` no passo, na sequência e no `sequenceBuilder`.
- Retorno `String` (nunca `Result<String>`).
- Sem `chatMemory` / `chatMemoryProvider`.
- `@MemoryId` só em `Assistant.chat`, nunca no `AnswerAgent`.
- `ChatController` não importa nada de `dev.langchain4j`.
- Pacote base: `ai.analizza.agent.langchain4j`.

## Review Focus

1. `POST /chat` sem `conversationId` no JSON → 400, não 500. Conferido à mão na Task 3.
2. Mesmo `conversationId` em duas chamadas → uma sessão com duas execuções na página, e o modelo **não** lembra da primeira (sem memória nesta fatia). Conferido na Task 6.
3. Acentos da página mínima ("execução") aparecem certos no navegador (charset UTF-8 no `text/html`). Conferido na Task 5.
4. Erro do modelo (ex.: `LLM_API_TOKEN` inválido) → `/chat` dá 500 e a página continua abrindo, sem quebrar o relatório. Conferido na Task 6.
5. Reinício da aplicação → página volta à página mínima (monitor em memória). Conferido na Task 6.

---

### Task 1: Dependência do agentic

**Files:**
- Modify: `build.gradle.kts` (bloco `dependencies`, logo abaixo das duas linhas langchain4j)

- [ ] **Step 1:** acrescentar `implementation("dev.langchain4j:langchain4j-agentic:${langchain4jVersion}")`. Por quê: traz `AgenticServices`, `@Agent`, `AgentMonitor` e `HtmlReportGenerator`.
- [ ] **Step 2: Conferir**
  Run: `./gradlew dependencies --configuration runtimeClasspath | grep agentic`
  Expected: `dev.langchain4j:langchain4j-agentic:1.20.0-beta30`
- [ ] **Step 3: Conferir que nada quebrou**
  Run: `./gradlew compileKotlin`
  Expected: `BUILD SUCCESSFUL`
- [ ] **Step 4: Commit** — `build: adiciona langchain4j-agentic`

### Task 2: Interface do passo

**Files:**
- Create: `src/main/kotlin/ai/analizza/agent/langchain4j/infrastructure/llm/AnswerAgent.kt`

**Interfaces:**
- Produces: `interface AnswerAgent` com, no método (o `@Agent` só aceita função, não interface):
  `@Agent(name = "answer", outputKey = "resposta")`
  `@UserMessage("{{userInput}}")`
  `fun answer(@V("userInput") userInput: String): String`
  Imports: `dev.langchain4j.agentic.Agent`, `dev.langchain4j.service.UserMessage`, `dev.langchain4j.service.V`.

- [ ] **Step 1:** criar a interface. Por quê: é o agente que de fato chama o modelo; o `{{userInput}}` é preenchido pela variável `userInput` do escopo agêntico, que a sequência grava a partir do seu próprio `@V("userInput")`. Sem `@MemoryId` aqui: sem memória configurada, o `AiServices` recusa esse parâmetro na montagem.
- [ ] **Step 2: Conferir**
  Run: `./gradlew compileKotlin`
  Expected: `BUILD SUCCESSFUL`
- [ ] **Step 3: Commit** — `feat: interface AnswerAgent do passo agentic`

### Task 3: Assistant vira sequência; conversationId no contrato

**Files:**
- Modify: `src/main/kotlin/ai/analizza/agent/langchain4j/infrastructure/llm/Assistant.kt`
- Modify: `src/main/kotlin/ai/analizza/agent/langchain4j/controller/ChatController.kt`
- Modify: `build.gradle.kts` (remover `langchain4j-spring-boot4-starter`)

**Interfaces:**
- Produces: `interface Assistant` com, no método (o `@Agent` só aceita função, não interface):
  `@Agent(name = "assistant", outputKey = "resposta")`
  `fun chat(@MemoryId conversationId: String, @V("userInput") userMessage: String): String`
  Imports: `dev.langchain4j.agentic.Agent`, `dev.langchain4j.service.MemoryId`, `dev.langchain4j.service.V`.
- Produces: `class ChatRequest(val conversationId: String, val message: String)`; `ChatResponse` igual.

- [ ] **Step 1:** em `Assistant.kt`, tirar `@AiService` e `@SystemMessage` (a mensagem de sistema vai para a Task 4) e aplicar a assinatura acima. Por quê: o `@MemoryId` da sequência é o rótulo da sessão no relatório.
- [ ] **Step 2:** em `ChatController.kt`, `ChatRequest` ganha `conversationId` e o controller repassa `chatRequest.conversationId` e `chatRequest.message` para `assistant.chat`.
- [ ] **Step 3:** em `build.gradle.kts`, remover `implementation("dev.langchain4j:langchain4j-spring-boot4-starter:${langchain4jVersion}")`. Por quê: só servia para escanear `@AiService`.
- [ ] **Step 4: Conferir compilação**
  Run: `./gradlew compileKotlin`
  Expected: `BUILD SUCCESSFUL` (se reclamar de `AiService`/`SystemMessage` não resolvidos, sobrou import no `Assistant.kt`)
- [ ] **Step 5: Conferir a falha esperada**
  Run: `./gradlew test`
  Expected: FAIL em `contextLoads`, com `NoSuchBeanDefinitionException` para `Assistant`. Ninguém monta o bean ainda; a Task 4 resolve.
- [ ] **Step 6: Commit** — só depois da Task 4, junto (o contexto não sobe entre as duas).

### Task 4: Montagem por código

**Files:**
- Create: `src/main/kotlin/ai/analizza/agent/langchain4j/config/AgentConfig.kt`

**Interfaces:**
- Consumes: `AnswerAgent` (Task 2), `Assistant` (Task 3), `dev.langchain4j.model.chat.ChatModel` (bean do starter open-ai).
- Produces:
  `@Configuration class AgentConfig`
  `@Bean fun agentMonitor(): AgentMonitor`
  `@Bean fun assistant(chatModel: ChatModel, agentMonitor: AgentMonitor): Assistant`
  Imports: `dev.langchain4j.agentic.AgenticServices`, `dev.langchain4j.agentic.observability.AgentMonitor`.

- [ ] **Step 1:** `agentMonitor()` devolve `AgentMonitor()` (construtor público, padrões da lib).
- [ ] **Step 2:** `assistant(...)`, em duas etapas:
  - passo: `AgenticServices.agentBuilder(AnswerAgent::class.java)` com `.chatModel(chatModel)`, `.systemMessageProvider { "Você é um assistente conciso que responde em português" }` e `.build()`;
  - sequência: `AgenticServices.sequenceBuilder(Assistant::class.java)` com `.subAgents(passo)`, `.outputKey("resposta")`, `.listener(agentMonitor)` e `.build()`.
  Por quê: o relatório só enxerga agente montado dentro de um sistema agêntico; o listener na sequência herda para o passo.
- [ ] **Step 3: Conferir contexto**
  Run: `./gradlew test`
  Expected: `BUILD SUCCESSFUL`. Se falhar com "In order to use @MemoryId, please configure the ChatMemoryProvider", parar e trazer o stack trace (é a hipótese da spec que não foi vista rodando).
- [ ] **Step 4: Conferir o chat de verdade**
  Run: `set -a && source local.env && set +a && ./gradlew bootRun` e, em outro terminal,
  `curl -s -X POST localhost:8080/chat -H 'Content-Type: application/json' -d '{"conversationId":"a","message":"Qual a capital do Brasil?"}'`
  Expected: `{"answer":"...Brasília..."}`; no log do bootRun, nenhum WARN `Improper invocation of a standalone agent`.
- [ ] **Step 5: Conferir Review Focus 1**
  Run: `curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/chat -H 'Content-Type: application/json' -d '{"message":"oi"}'`
  Expected: `400`
- [ ] **Step 6: Commit** (Tasks 3 e 4) — `feat: Assistant montado por AgenticServices com AgentMonitor`

### Task 5: Rota do relatório

**Files:**
- Create: `src/test/kotlin/ai/analizza/agent/langchain4j/controller/DiagnosticsControllerTest.kt`
- Create: `src/main/kotlin/ai/analizza/agent/langchain4j/controller/DiagnosticsController.kt`

**Interfaces:**
- Consumes: bean `AgentMonitor` (Task 4).
- Produces:
  `@RestController class DiagnosticsController(private val agentMonitor: AgentMonitor)`
  `@GetMapping("/diagnostics/report", produces = ["text/html;charset=UTF-8"]) fun report(): String`

- [ ] **Step 1: Escrever o teste que falha**
  `@WebMvcTest(DiagnosticsController::class)` com `@Import` de uma `@TestConfiguration` que expõe `@Bean fun agentMonitor() = AgentMonitor()` (monitor real, vazio).
  Teste `deve devolver pagina minima quando monitor vazio`, via `MockMvc` em `GET /diagnostics/report`:
  - status `200`;
  - content type compatível com `text/html`;
  - corpo contém `Nenhuma execução registrada ainda.`
- [ ] **Step 2: Ver falhar**
  Run: `./gradlew test --tests '*DiagnosticsControllerTest'`
  Expected: FAIL (classe `DiagnosticsController` não existe → erro de compilação do teste).
- [ ] **Step 3: Implementar `report()`**
  Se `agentMonitor.allMemoryIds()` estiver vazio, devolver uma página HTML mínima com `<title>Relatório de agentes</title>` e o parágrafo `Nenhuma execução registrada ainda.`; senão `HtmlReportGenerator.generateReport(agentMonitor)` (import `dev.langchain4j.agentic.observability.HtmlReportGenerator`). Por quê do desvio: o gerador quebra com NPE sem sessões.
- [ ] **Step 4: Ver passar**
  Run: `./gradlew test`
  Expected: `BUILD SUCCESSFUL` (os dois testes).
- [ ] **Step 5: Conferir Review Focus 3** — com a app recém-subida, abrir `http://localhost:8080/diagnostics/report` no navegador: "execução" com acento certo.
- [ ] **Step 6: Commit** — `feat: rota GET /diagnostics/report com relatorio do AgentMonitor`

### Task 6: Conferência de aceite

Sem arquivos novos.

- [ ] **Step 1:** subir com `set -a && source local.env && set +a && ./gradlew bootRun`.
- [ ] **Step 2:** dois chats:
  `curl -s -X POST localhost:8080/chat -H 'Content-Type: application/json' -d '{"conversationId":"a","message":"Qual a capital do Brasil?"}'`
  `curl -s -X POST localhost:8080/chat -H 'Content-Type: application/json' -d '{"conversationId":"b","message":"Quanto é 2+2?"}'`
  Expected: duas respostas JSON.
- [ ] **Step 3:** abrir `http://localhost:8080/diagnostics/report`.
  Expected: duas sessões, `a` e `b`; cada uma com o agente `assistant` contendo `answer`, pergunta, resposta, tokens e duração.
- [ ] **Step 4: Review Focus 2** — `{"conversationId":"a","message":"Qual foi minha pergunta anterior?"}`.
  Expected: o modelo não sabe (sem memória); a sessão `a` passa a ter duas execuções.
- [ ] **Step 5: Review Focus 4** — reiniciar com `LLM_API_TOKEN=invalido` e mandar um chat.
  Expected: `/chat` responde 500; `/diagnostics/report` abre e mostra a execução com falha (ou a página mínima, se a lib não registrar a falha).
- [ ] **Step 6: Review Focus 5** — reiniciar normal e abrir a página.
  Expected: página mínima.

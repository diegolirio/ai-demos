# spring-ai-llm, fatias 0 e 1 — plano de implementação

> **Execução:** o Diego implementa cada task à mão; o Claude descreve, dá a conferência e revisa. Este plano traz arquivo, assinatura, anotação, propriedade, comando e resultado esperado; não traz corpo de classe. Quando o Diego pedir, o Claude entrega o bloco.

**Goal:** criar a POC `spring-ai-llm` com `POST /chat` via `ChatClient` (fatia 0) e observar tokens e duração pelo Actuator e pelo Micrometer (fatia 1).

**Architecture:** o mesmo desenho da `agent-langchain4j`. O `ChatController` depende de `infrastructure/llm/Assistant`, e o `Assistant` embrulha o `ChatClient` que o starter do Spring AI configura. A observação usa as métricas `gen_ai.*` que o Spring AI publica no Micrometer, expostas pelo Actuator.

**Tech Stack:** Spring Boot 4.1.2, Kotlin 2.3.21, Java 25, Gradle Kotlin DSL, Spring AI 2.0.1 (`spring-ai-starter-model-openai`), OpenRouter.

**Spec:** `step-by-step/docs/superpowers/specs/2026-10-23-poc-spring-ai-llm-design.md`

## Global Constraints

- Pasta: `ai-demos/step-by-step/spring-ai-llm`.
- Pacote base: `ai.analizza.springai.llm`.
- Contrato: `POST /chat` recebe `{"conversationId","message"}` e devolve `{"answer"}`; sem `conversationId`, responde 400.
- Mensagem de sistema: "Você é um assistente conciso que responde em português".
- Variáveis: `LLM_BASE_URL`, `LLM_API_TOKEN`, `LLM_MODEL`, num `local.env` ignorado pelo Git.

## Review Focus

1. URL base com ou sem `/v1`: um 404 na primeira chamada é o sinal (Task 4).
2. `contextLoads` sem chave: o teste não pode depender do `local.env` (Task 2).
3. Pedido sem `conversationId` → 400, não 500 (Task 4).
4. As métricas `gen_ai.*` só aparecem **depois** do primeiro chat (Task 5).
5. Prompt e resposta aparecendo no log, para equivaler ao `log-requests` (Task 5).

---

## Fatia 0

### Task 1: Projeto no Initializr

- [ ] **Step 1:** em https://start.spring.io escolher:
  - Project **Gradle - Kotlin**, Language **Kotlin**, Spring Boot **4.1.2**;
  - Group `ai.analizza`, Artifact `spring-ai-llm`, Package name `ai.analizza.springai.llm`;
  - Packaging Jar, Java **25**;
  - Dependencies: **Spring Web**, **Spring Boot Actuator**, **OpenAI**.
- [ ] **Step 2:** gerar o zip e descompactar em `ai-demos/step-by-step/spring-ai-llm`.
- [ ] **Step 3: Conferir**
  Run: `grep -E "springAiVersion|spring-ai-starter-model-openai" build.gradle.kts`
  Expected: `springAiVersion` = `2.0.1` e o starter presente.

### Task 2: Configuração, local.env e Makefile

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `local.env` (copiado da `agent-langchain4j`), `Makefile`
- Modify: `.gitignore` (acrescentar `local.env`)

- [ ] **Step 1:** em `application.properties`, as propriedades (o padrão depois de `:` permite subir sem `local.env`, o que o `contextLoads` precisa):
  - `spring.ai.openai.base-url=${LLM_BASE_URL:https://openrouter.ai/api/v1}`
  - `spring.ai.openai.api-key=${LLM_API_TOKEN:change-it}`
  - `spring.ai.openai.chat.model=${LLM_MODEL:nvidia/nemotron-3-ultra-550b-a55b:free}`
- [ ] **Step 2:** `Makefile` com o alvo `run`, igual ao da outra POC, com TAB antes do comando.
- [ ] **Step 3: Conferir o teste**
  Run: `./gradlew test`
  Expected: `BUILD SUCCESSFUL`. Se falhar pedindo API key, a propriedade da chave não foi lida.
- [ ] **Step 4: Conferir a subida**
  Run: `make run`
  Expected: `Started ...` na porta 8081.
- [ ] **Step 5: Commit** — `chore: projeto spring-ai-llm no initializr com OpenAI e Actuator` (sem o `local.env`).

### Task 3: Assistant com ChatClient

**Files:**
- Create: `src/main/kotlin/ai/analizza/springai/llm/infrastructure/llm/Assistant.kt`

**Interfaces:**
- Consumes: `org.springframework.ai.chat.client.ChatClient.Builder`, bean criado pelo starter.
- Produces: `@Component class Assistant(chatClientBuilder: ChatClient.Builder)` com `fun chat(conversationId: String, userMessage: String): String`.

- [ ] **Step 1:** no construtor (ou num `val` inicializado), montar o `ChatClient` com `chatClientBuilder.defaultSystem("<mensagem de sistema>").build()`. Por quê: o `defaultSystem` é o equivalente ao `systemMessageProvider` da outra POC.
- [ ] **Step 2:** `chat(...)` faz `chatClient.prompt().user(userMessage).call().content()`. O `content()` pode devolver `null`, então trate com `orEmpty()` ou com um erro claro. O `conversationId` ainda não é usado; ele entra na fatia 2.
- [ ] **Step 3: Conferir**
  Run: `./gradlew compileKotlin`
  Expected: `BUILD SUCCESSFUL`

### Task 4: ChatController

**Files:**
- Create: `src/main/kotlin/ai/analizza/springai/llm/controller/ChatController.kt`

**Interfaces:**
- Consumes: `Assistant.chat(conversationId: String, userMessage: String): String`.
- Produces: o mesmo arquivo da `agent-langchain4j` (`@RestController`, `@RequestMapping("/chat")`, `ChatRequest(val conversationId: String, val message: String)`, `ChatResponse(val answer: String)`), mudando só o pacote.

- [ ] **Step 1:** criar o controller. Por quê: fica idêntico ao da outra POC, então a diferença entre as libs aparece só no `Assistant`.
- [ ] **Step 2: Conferir o chat**
  Run: `make run`; em outro terminal, `curl -s -X POST localhost:8081/chat -H 'Content-Type: application/json' -d '{"conversationId":"a","message":"Qual a capital do Brasil?"}'`
  Expected: `{"answer":"...Brasília..."}`. Um 404 com `/v1/v1/` no log quer dizer que a base não deve ter o `/v1`, e um 404 sem `/v1` quer dizer que deve ter.
- [ ] **Step 3: Conferir o 400**
  Run: `curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8081/chat -H 'Content-Type: application/json' -d '{"message":"oi"}'`
  Expected: `400`
- [ ] **Step 4: Commit** — `feat: POST /chat com ChatClient do Spring AI`

## Fatia 1

### Task 5: Métricas e logs

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1:** expor as métricas com `management.endpoints.web.exposure.include=health,metrics`.
- [ ] **Step 2:** ligar o log de prompt e resposta das observações do Spring AI (`spring.ai.chat.observations.*`). Confirme os nomes exatos na documentação da 2.0.1 ou nas sugestões da IDE.
- [ ] **Step 3: Conferir antes de qualquer chat**
  Run: `curl -s localhost:8081/actuator/metrics | grep -o 'gen_ai[^"]*'`
  Expected: nada. As métricas só nascem na primeira chamada.
- [ ] **Step 4: Conferir depois de dois chats** (os mesmos curls da outra POC, ids `a` e `b`)
  Run: `curl -s localhost:8081/actuator/metrics | grep -o 'gen_ai[^"]*'`
  Expected: `gen_ai.client.operation` e `gen_ai.client.token.usage`.
- [ ] **Step 5: Ler os números**
  Run: `curl -s localhost:8081/actuator/metrics/gen_ai.client.token.usage` e `curl -s localhost:8081/actuator/metrics/gen_ai.client.operation`
  Expected: o total de tokens e o tempo somados das duas chamadas, com tags de modelo e de tipo de token (`availableTags`). Não há tag de conversa.
- [ ] **Step 6:** no log do `make run`, prompt e resposta aparecem a cada chat.
- [ ] **Step 7: Commit** — `feat: observacao do Spring AI via Actuator e Micrometer`

### Task 6: Registro da comparação

**Files:**
- Create: `step-by-step/docs/comparacao-langchain4j-spring-ai.md`

- [ ] **Step 1:** o Claude rascunha um parágrafo por fatia (0 e 1), com o que foi visto rodando nas duas POCs. O Diego revisa.
- [ ] **Step 2: Commit** — `docs: comparacao LangChain4j x Spring AI, fatias 0 e 1`

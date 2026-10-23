# LangChain4j × Spring AI — comparação por fatia

Duas POCs com o mesmo contrato HTTP (`POST /chat` com `conversationId` e `message`), o mesmo modelo no OpenRouter (`nvidia/nemotron-3-ultra-550b-a55b:free`) e a mesma mensagem de sistema.

| | `agent-langchain4j` | `spring-ai-llm` |
|---|---|---|
| Boot / lib | Spring Boot 4.1.1 · LangChain4j 1.20.0-beta30 | Spring Boot 4.1.2 · Spring AI 2.0.1 |
| Porta | 8080 | 8081 |

## Fatia 0 — projeto e chat

**Montagem**
- **LangChain4j:** o chat é uma interface anotada, e a lib gera a implementação. Primeiro foi o `@AiService`; depois, `AgenticServices.agentBuilder` + `sequenceBuilder` numa `@Configuration`. Cada mudança de forma (avulso → sequência) mexe em anotações, numa interface nova (`AnswerAgent`) e numa configuração.
- **Spring AI:** o chat é uma classe sua (`Assistant`) que recebe o `ChatClient.Builder` do starter e escreve a chamada à mão: `prompt().user(...).call().content()`. Não há interface gerada nem configuração extra.

**Mensagem de sistema**
- LangChain4j: `@SystemMessage` ou `systemMessageProvider`.
- Spring AI: `defaultSystem(...)` no builder.

**Configuração**
- **Os dois** leem as mesmas variáveis com propriedades próprias: `langchain4j.open-ai.chat-model.*` × `spring.ai.openai.*`.
- **No Spring AI 2.0**, `chat.options.model` foi depreciado em favor de `chat.model`.
- **URL base:** o Spring AI 2.0 usa o SDK oficial da OpenAI, que espera a base **com** `/v1`, igual ao LangChain4j.

**Tropeços**
- **LangChain4j:**
  - `@Agent` só aceita método, não interface;
  - `@MemoryId` sem memória é recusado num agente montado por `AiServices`, por isso ficou só na sequência;
  - sem o `@AiService`, o starter `langchain4j-spring-boot4-starter` ficou sem uso.
- **Spring AI:** nenhum na biblioteca; os tropeços foram de ambiente (pacote gerado pelo Initializr e porta ocupada).

**Leitura:** para um chat simples, o Spring AI pede menos peças e menos conceitos. O LangChain4j pede mais estrutura já de saída, e essa estrutura (agentes, sequência, escopo) é o que habilita a fatia 1 dele.

## Fatia 1 — observação

| | LangChain4j (`AgentMonitor` + `HtmlReportGenerator`) | Spring AI (Micrometer + Actuator) |
|---|---|---|
| O que entrega | página HTML pronta em `/diagnostics/report`, escrita por nós (~25 linhas) | métricas em `/actuator/metrics`, com 3 propriedades |
| Unidade | **sessão** (`@MemoryId`) → runs → agentes | **acumulado** da aplicação |
| Por conversa | sim: sessão `a`, sessão `b` | não: não há tag de conversa |
| Por chamada | pergunta, resposta, tokens e duração de cada run | só `COUNT`, `TOTAL_TIME` e `MAX` |
| Tokens | da última ida ao modelo de cada run | soma por tipo (`input`, `output`, `total`); sem filtro de `gen_ai.token.type` o valor soma os três e conta em dobro |
| Topologia | diagrama da sequência e dos agentes | não há |
| Log de prompt/resposta | `log-requests`/`log-responses`: HTTP completo, com headers e `usage` | `spring.ai.chat.observations.log-prompt`/`log-completion`: só o texto |
| Onde vive | memória do processo; some ao reiniciar | memória do processo; some ao reiniciar (até um exportador mandar para fora) |
| Caminho para produção | não há: é uma ferramenta de desenvolvimento | padrão Micrometer, pronto para Prometheus ou OpenTelemetry |

**Leitura:** o LangChain4j responde à pergunta "o que aconteceu nesta conversa?". O Spring AI responde a "quanto a aplicação está gastando e quão rápido?". Um é ferramenta de depuração de agente; o outro, de operação. Para ter a visão por conversa no Spring AI, seria preciso um advisor próprio ou traces num coletor. Essa decisão ficou para depois.

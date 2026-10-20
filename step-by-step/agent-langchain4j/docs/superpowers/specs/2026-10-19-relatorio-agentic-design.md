# Relatório visual de execuções (langchain4j-agentic) — design

## Intenção

Ver no navegador cada chat do `agent-langchain4j` como uma sessão: pergunta, resposta, tokens e duração. A página vem pronta do `HtmlReportGenerator` do `langchain4j-agentic`. Quando a fatia de ferramentas chegar, as linhas de tool aparecem na mesma página; hoje não há tools.

Projeto de estudo: quem implementa é o Diego, passo a passo. A spec e o plano descrevem arquivo, conteúdo e motivo; não trazem corpo de classe.

## Tamanho

Mudança pequena (bounded). O fluxo do chat já existe; muda a forma de montar o agente e entra uma rota de leitura.

## Objetivos

- `GET /diagnostics/report` devolve `text/html` com o relatório do `AgentMonitor`.
- Cada `conversationId` vira uma sessão na página.
- Coluna Tokens preenchida (última ida ao modelo da execução).

## Fora do escopo

- Memória de conversa (próxima fatia). Nesta fatia cada chamada é independente: o modelo não vê mensagens anteriores, mesmo com o mesmo `conversationId`.
- Chave de ambiente para ligar/desligar a rota: sempre ligada.
- Persistência do monitor: fica em memória, por processo, some ao reiniciar.
- Tools, cadastro de sistemas, persona.

## Anti-objetivos

- Agente avulso chamado direto pelo `agentBuilder` (WARN "Improper invocation of a standalone agent outside of an agentic system", sem tokens, sessão por UUID).
- Retorno `Result<String>` (aparece como `Result@hash`).
- `ChatController` passar a conhecer classes do langchain4j: ele continua dependendo só da interface `Assistant`.

## Restrições

- Spring Boot 4.1.1, Kotlin, Java 25, Gradle Kotlin DSL.
- `dev.langchain4j:langchain4j-agentic` na mesma versão das outras libs langchain4j: `langchain4jVersion=1.20.0-beta30` (via `providers.gradleProperty`).
- `ChatModel` vem do `langchain4j-open-ai-spring-boot4-starter` (configuração atual em `application.properties`).

## Decisões do Diego

1. Dependência só `implementation("dev.langchain4j:langchain4j-agentic:$langchain4jVersion")`.
2. Sai o `@AiService`; entra uma `@Configuration` com bean `AgentMonitor` e bean `Assistant` montado por `AgenticServices.agentBuilder(passo)` + `AgenticServices.sequenceBuilder(Assistant)`, com `.listener(monitor)` na sequência.
3. `Assistant.chat` recebe o identificador da conversa como `@MemoryId`; `ChatRequest` ganha `conversationId`.
4. Rota `GET /diagnostics/report`, `text/html`: página mínima própria enquanto `monitor.allMemoryIds()` estiver vazio (o gerador quebra com NPE nesse estado), senão `HtmlReportGenerator.generateReport(monitor)`.
5. Sem `chatMemory` nem `chatMemoryProvider` nesta fatia.

## Decisões do Claude (podem ser trocadas)

- **`@MemoryId` só na sequência.** Conferido no bytecode do langchain4j 1.20.0: `AiServiceValidation` recusa parâmetro `@MemoryId` quando não há memória configurada ("In order to use @MemoryId, please configure the ChatMemoryProvider on the '...'"). O passo é montado sobre `AiServices`, então leva só `@V("userInput")`. A sequência não passa por essa validação e é ela que define a sessão no monitor. **Não verificado rodando**: se a subida falhar com essa mensagem citando a sequência, voltar à conversa.
- Nomes: passo `AnswerAgent` (`@Agent(name = "answer")`), sequência `Assistant` (`@Agent(name = "assistant")`), configuração `AgentConfig`, rota `DiagnosticsController`. `outputKey = "resposta"` nos dois e no `sequenceBuilder`.
- Mensagem de sistema sai do `@SystemMessage` e vai para `systemMessageProvider` do passo, com o mesmo texto: "Você é um assistente conciso que responde em português".
- `langchain4j-spring-boot4-starter` sai do build: sem `@AiService` ele fica sem uso.
- `conversationId` obrigatório (`String` não nula).
- `setMaxRetainedSessions` no padrão da lib.
- Um teste `@WebMvcTest` só para a página mínima do monitor vazio; o caminho com sessões é conferido à mão (precisa do modelo de verdade).

## Riscos conhecidos

- Um `AgentMonitor` acompanha um agente raiz (`setRootAgent`). Com uma sequência só, um bean basta; ao surgir outro agente raiz, rever.
- O escopo agêntico com `@MemoryId` fica guardado em memória por id, sem expiração. Aceitável para estudo.
- Exceção do modelo chega embrulhada (`AgentInvocationException` → `InvocationTargetException` → `UndeclaredThrowableException` → `InvocationTargetException` → original). Nesta fatia não há tratamento; o erro sobe como 500.

## Conferência de aceite

Dois `curl` em `POST /chat` com `conversationId` `a` e `b`; abrir `http://localhost:8080/diagnostics/report`; ver duas sessões, `a` e `b`, cada uma com pergunta, resposta, tokens e duração. Nenhum WARN "Improper invocation" no log.

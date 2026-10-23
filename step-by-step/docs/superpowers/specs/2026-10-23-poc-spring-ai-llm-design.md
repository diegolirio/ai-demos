# POC spring-ai-llm — design

## Intenção

Aprender o Spring AI fazendo, e comparar com a POC `agent-langchain4j`, para saber quando usar cada uma. O que for aprendido aqui vira, depois, a opção "Spring AI" da skill `analizza-new-agent`, que hoje só gera agentes com LangChain4j. A mudança na skill é um projeto separado, que só começa depois desta POC.

O Diego faz tudo à mão, do Spring Initializr ao código. O Claude descreve cada passo (arquivo, conteúdo, motivo), dá o comando de conferência com o resultado esperado e confere o que foi escrito. Quando o Diego pedir, o Claude entrega o bloco de código.

## Objetivos

- A mesma POC da `agent-langchain4j`, com as mesmas fatias, na mesma ordem e com o mesmo contrato HTTP.
- Ao fim de cada fatia, um registro curto do que foi mais fácil, mais difícil ou diferente em cada lib, em `step-by-step/docs/comparacao-langchain4j-spring-ai.md`.

## Fatias

| # | Fatia | Correspondente na `agent-langchain4j` |
|---|-------|---------------------------------------|
| 0 | Projeto no Initializr e `POST /chat` com `ChatClient` | estado antes do relatório |
| 1 | Observação com Actuator e Micrometer | relatório `/diagnostics/report` |
| 2 | Memória de conversa | idem |
| 3 | Cadastro de sistemas com persona "Especialista em Produtos" | idem |
| 4 | Tools que leem o GitHub | idem |

As fatias 2 a 4 ganham o próprio plano quando chegar a vez delas.

## Fora do escopo

- Página HTML própria de relatório. Na fatia 1 vale só o que o Spring AI entrega pronto; a página fica para decidir depois.
- Alterar a skill `analizza-new-agent`.
- Mexer na `agent-langchain4j`.

## Anti-objetivos

- Uma POC que "funciona", mas com outra forma: contrato HTTP, nomes ou fatias diferentes, o que estraga a comparação.
- Código gerado pelo Claude sem o Diego pedir.

## Restrições

- Pasta `ai-demos/step-by-step/spring-ai-llm`.
- Spring Boot **4.1.2**: o Initializr não oferece mais a 4.1.1 usada na outra POC, e a diferença é só de patch.
- Kotlin, Java 25, Gradle Kotlin DSL.
- Spring AI **2.0.1**, a versão que o Initializr traz para o Boot 4.1.2, com o starter `spring-ai-starter-model-openai`.
- O mesmo OpenRouter, com as variáveis `LLM_BASE_URL`, `LLM_API_TOKEN` e `LLM_MODEL` num `local.env` ignorado pelo Git.
- O mesmo contrato da outra POC:
  - `POST /chat` recebe `{"conversationId": "...", "message": "..."}` e devolve `{"answer": "..."}`;
  - sem `conversationId`, a resposta é 400.
- A mesma mensagem de sistema: "Você é um assistente conciso que responde em português".
- Um `Makefile` com o alvo `run`, igual ao da outra POC.

## Decisões do Diego

- Mesmas fatias, mesma ordem.
- Observação: Actuator e Micrometer, sem página própria por enquanto.
- Primeiro a POC, depois a skill.

## Decisões do Claude (podem ser trocadas)

- **Group e pacote:** group `ai.analizza.spring-ai-llm` e pacote `ai.analizza.springai.llm`, seguindo o padrão da outra POC.
- **Camadas:** a mesma estrutura de pacotes da outra POC.
  - `controller/ChatController.kt` depende de uma classe `infrastructure/llm/Assistant`, que embrulha o `ChatClient` e expõe `fun chat(conversationId: String, userMessage: String): String`.
  - Assim, o controller fica idêntico nas duas POCs e a diferença aparece só no `Assistant`.
- **`conversationId` na fatia 0:** ele entra no contrato desde já, mas só é usado na fatia 2, como o id da conversa na memória.
- **Documentos:** spec e planos ficam em `step-by-step/docs/superpowers/`, porque a pasta `spring-ai-llm` só existe depois do zip do Initializr.

## Pontos a conferir rodando

- **URL base:** se o Spring AI 2.0.1 espera a URL com ou sem `/v1`. Se a primeira chamada der 404 com `/v1/v1/` na URL, tire o `/v1` da base.
- **Logs de prompt e resposta:** os nomes das propriedades que ligam esses logs nas observações (`spring.ai.chat.observations.*`). Conferir na documentação da 2.0.1 ou nas sugestões da IDE.
- **Métricas:** os nomes vistos no jar `spring-ai-model` 2.0.1 são `gen_ai.client.operation` e `gen_ai.client.token.usage`. Confirmar em `/actuator/metrics`.

## Diferença já esperada

As métricas do Micrometer somam por modelo e por tipo de operação, não por conversa. O `conversationId` não vira dimensão (seria cardinalidade alta), então não haverá a visão "sessão b, três runs" que o relatório do LangChain4j mostra. Isso entra no registro de comparação da fatia 1.

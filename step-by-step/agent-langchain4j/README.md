# Agent Langchain4j

## Creating my agent application

<img width="1710" height="904" alt="image" src="https://github.com/user-attachments/assets/eb0b40c6-5c72-4a2b-b0dd-7cddbd36471e" />


And... add the both deps:

```kotlin
	implementation("dev.langchain4j:langchain4j-open-ai-spring-boot4-starter:${langchain4jVersion}")
	implementation("dev.langchain4j:langchain4j-spring-boot4-starter:${langchain4jVersion}")
```  

add props...:

```properties
langchain4j.open-ai.chat-model.base-url=${LLM_BASE_URL:https://openrouter.ai/api/v1}
langchain4j.open-ai.chat-model.api-key=${LLM_API_TOKEN:change-it}
langchain4j.open-ai.chat-model.model-name=${LLM_MODEL:nvidia/nemotron-3-ultra-550b-a55b:free}
langchain4j.open-ai.chat-model.timeout=60s
langchain4j.open-ai.chat-model.log-requests=true
langchain4j.open-ai.chat-model.log-responses=true
logging.level.dev.langchain4j=DEBUG
```

Creating my AI Assistant (LLM):

```kotlin
package ai.analizza.agent.langchain4j.infrastructure.llm

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.spring.AiService

@AiService
interface Assistant {

    @SystemMessage("Você é um assistente conciso que responde em português")
    fun chat(userMessage: String): String
}
```

Now, my controller:   

```kotlin
@RestController
@RequestMapping("/chat")
class ChatController(
    private val assistant: Assistant,
) {

    @PostMapping
    fun chat(@RequestBody @Validated chatRequest: ChatRequest): ChatResponse {
        return ChatResponse(answer = assistant.chat(chatRequest.message))
    }

}

class ChatRequest(val message: String)
class ChatResponse(val answer: String)
```

Upping and requesting...:

```sh
curl -s -X POST http://localhost:8080/chat \
    -H "Content-Type: application/json" -d '{"message":"Em uma frase: o que é o Spring Boot?"}'
```

## Agentic Monitor

Add dependencies on build.gradle.kts   
   
```
implementation("dev.langchain4j:langchain4j-agentic:${langchain4jVersion}")
```

Create AnswerAgent interface:   
   
```kotlin
import dev.langchain4j.agentic.Agent
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

interface AnswerAgent {

    @Agent(name = "answer", outputKey = "resposta")
    @UserMessage("{{userInput}}")
    fun answer(@V("userInput") userInput: String): String
}
```

```kotlin
interface Assistant {

    @Agent(name = "assistant", outputKey = "resposta")
    fun chat(@MemoryId conversationId: String, @V("userInput") userMessage: String): String
}
```

```kotlin
@RestController
@RequestMapping("/chat")
class ChatController(
    private val assistant: Assistant,
) {

    @PostMapping
    fun chat(@RequestBody @Validated chatRequest: ChatRequest): ChatResponse {
        return ChatResponse(
            answer = assistant.chat(chatRequest.conversationId, chatRequest.message)
        )
    }

}

class ChatRequest(val conversationId: String, val message: String)
class ChatResponse(val answer: String)
```

```kotlin
package ai.analizza.agent.langchain4j.config

import ai.analizza.agent.langchain4j.infrastructure.llm.AnswerAgent
import ai.analizza.agent.langchain4j.infrastructure.llm.Assistant
import dev.langchain4j.agentic.AgenticServices
import dev.langchain4j.agentic.observability.AgentMonitor
import dev.langchain4j.model.chat.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgentConfig {

    @Bean
    fun agentMonitor(): AgentMonitor = AgentMonitor()

    @Bean
    fun assistant(chatModel: ChatModel, agentMonitor: AgentMonitor): Assistant {
        val answerAgent = AgenticServices.agentBuilder(AnswerAgent::class.java)
            .chatModel(chatModel)
            .systemMessageProvider { "Você é um assistente conciso que responde em português" }
            .build()

        return AgenticServices.sequenceBuilder(Assistant::class.java)
            .subAgents(answerAgent)
            .outputKey("resposta")
            .listener(agentMonitor)
            .build()
    }
}
```


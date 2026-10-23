# Spring AI LLM 

<img width="891" height="454" alt="image" src="https://github.com/user-attachments/assets/29b35d0a-645a-4e18-9a4c-d4771f52e188" />


Props.  

```props
spring.ai.openai.base-url=${LLM_BASE_URL:https://openrouter.ai/api/v1}
spring.ai.openai.api-key=${LLM_API_TOKEN:change-it}
spring.ai.openai.chat.model=${LLM_MODEL:nvidia/nemotron-3-ultra-550b-a55b:free}
```

```local.env
LLM_API_TOKEN=sk-*****
```
      
Crie uma saída para infraestrutura para o LLM    
   
```kotlin
@Component
class Assistant(chatClientBuilder: ChatClient.Builder) {

    private val chatClient: ChatClient = chatClientBuilder
        .defaultSystem("Você é um assistente conciso que responde em português")
        .build()

    fun chat(conversationId: String, userMessage: String): String {
        return chatClient.prompt()
            .user(userMessage)
            .call()
            .content()
            .orEmpty()
    }
}
```

Controller

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

Request: 

```sh
curl -s -X POST localhost:8081/chat -H 'Content-Type: application/json' -d '{"conversationId":"a","message":"Qual a capital do Brasil?"}'
```
> O esperado é {"answer":"...Brasília..."}.
   
##### Telemetry 

```props
management.endpoints.web.exposure.include=health,metrics
spring.ai.chat.observations.log-prompt=true
spring.ai.chat.observations.log-completion=true
```



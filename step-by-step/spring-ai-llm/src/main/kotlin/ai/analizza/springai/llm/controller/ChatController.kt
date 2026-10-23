package ai.analizza.springai.llm.controller

import ai.analizza.springai.llm.infrastructure.llm.Assistant
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
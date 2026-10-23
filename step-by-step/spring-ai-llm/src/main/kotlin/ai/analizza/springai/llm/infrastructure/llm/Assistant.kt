package ai.analizza.springai.llm.infrastructure.llm

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

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
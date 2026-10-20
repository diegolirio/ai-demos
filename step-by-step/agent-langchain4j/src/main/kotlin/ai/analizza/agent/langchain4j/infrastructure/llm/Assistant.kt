package ai.analizza.agent.langchain4j.infrastructure.llm

import dev.langchain4j.agentic.Agent
import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.V

interface Assistant {

    @Agent(name = "assistant", outputKey = "resposta")
    fun chat(@MemoryId conversationId: String, @V("userInput") userMessage: String): String
}
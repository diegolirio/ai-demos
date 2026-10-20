package ai.analizza.agent.langchain4j.infrastructure.llm

import dev.langchain4j.agentic.Agent
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

interface AnswerAgent {

    @Agent(name = "answer", outputKey = "resposta")
    @UserMessage("{{userInput}}")
    fun answer(@V("userInput") userInput: String): String
}
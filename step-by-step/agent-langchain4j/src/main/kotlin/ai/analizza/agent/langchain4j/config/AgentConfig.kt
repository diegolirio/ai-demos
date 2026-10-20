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
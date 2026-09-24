package poc.a2a.ana.assistente;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

public final class AnaFactory {

    private AnaFactory() {
    }

    /** tools: DelegacaoInvestimentosTool (A2A) e ConsultaCreditoTool (MCP direto). */
    public static AnaAssistant criar(ChatModel chatModel, ChatMemoryProvider memoria, Object... tools) {
        return AiServices.builder(AnaAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoria)
                .tools(tools)
                .build();
    }
}

package poc.a2a.investimentos.especialista;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;

public final class EspecialistaFactory {

    private EspecialistaFactory() {
    }

    /** Erros de execução de tool (ex.: MCP fora do ar) voltam ao LLM como texto (default do LangChain4j). */
    public static EspecialistaInvestimentos criar(ChatModel chatModel, ToolProvider toolProvider,
                                                  ChatMemoryProvider memoria) {
        return AiServices.builder(EspecialistaInvestimentos.class)
                .chatModel(chatModel)
                .toolProvider(toolProvider)
                .chatMemoryProvider(memoria)
                .build();
    }
}

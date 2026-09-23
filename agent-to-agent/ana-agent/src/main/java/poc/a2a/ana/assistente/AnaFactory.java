package poc.a2a.ana.assistente;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

public final class AnaFactory {

    private AnaFactory() {
    }

    public static AnaAssistant criar(ChatModel chatModel, ChatMemoryProvider memoria,
                                     DelegacaoInvestimentosTool delegacao) {
        return AiServices.builder(AnaAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoria)
                .tools(delegacao)
                .build();
    }
}

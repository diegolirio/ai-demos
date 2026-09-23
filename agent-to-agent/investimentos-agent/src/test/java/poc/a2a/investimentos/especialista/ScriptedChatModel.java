package poc.a2a.investimentos.especialista;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** LLM falso e determinístico: cada chamada ao modelo consome o próximo passo roteirizado. */
public class ScriptedChatModel implements ChatModel {

    private final List<Function<ChatRequest, AiMessage>> passos;
    private final List<ChatRequest> requisicoes = new ArrayList<>();

    @SafeVarargs
    public ScriptedChatModel(Function<ChatRequest, AiMessage>... passos) {
        this.passos = List.of(passos);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        int chamada = requisicoes.size();
        requisicoes.add(request);
        if (chamada >= passos.size()) {
            throw new IllegalStateException("Chamada inesperada ao modelo #" + (chamada + 1));
        }
        return ChatResponse.builder().aiMessage(passos.get(chamada).apply(request)).build();
    }

    public List<ChatRequest> requisicoes() {
        return requisicoes;
    }

    public static Function<ChatRequest, AiMessage> chamarTool(String nome, String argumentosJson) {
        return request -> AiMessage.from(ToolExecutionRequest.builder()
                .id("call-" + nome).name(nome).arguments(argumentosJson).build());
    }

    public static Function<ChatRequest, AiMessage> responder(Function<List<ToolExecutionResultMessage>, String> texto) {
        return request -> AiMessage.from(texto.apply(resultadosDeTools(request.messages())));
    }

    public static List<ToolExecutionResultMessage> resultadosDeTools(List<ChatMessage> mensagens) {
        return mensagens.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
    }
}

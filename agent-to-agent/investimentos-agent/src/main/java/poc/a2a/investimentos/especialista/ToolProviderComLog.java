package poc.a2a.investimentos.especialista;

import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;

/**
 * Decora um {@link ToolProvider} para logar cada chamada de tool feita pelo LLM do especialista
 * (spec &sect;5: "log por hop com contextId, taskId, tool chamada e duracao"). Preserva o
 * {@link dev.langchain4j.agent.tool.ReturnBehavior} original de cada tool (ex.: immediate return).
 */
public class ToolProviderComLog implements ToolProvider {

    private final ToolProvider delegate;

    public ToolProviderComLog(ToolProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult resultado = delegate.provideTools(request);
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (AiServiceTool tool : resultado.aiServiceTools()) {
            builder.add(tool.toBuilder()
                    .toolExecutor(new ToolExecutorComLog(tool.name(), tool.toolExecutor()))
                    .build());
        }
        return builder.build();
    }

    @Override
    public boolean isDynamic() {
        return delegate.isDynamic();
    }
}

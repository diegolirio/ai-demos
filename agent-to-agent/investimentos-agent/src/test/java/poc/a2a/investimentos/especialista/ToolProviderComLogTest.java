package poc.a2a.investimentos.especialista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;

class ToolProviderComLogTest {

    private static final ToolSpecification TOOL_SPEC = ToolSpecification.builder()
            .name("minha_tool")
            .description("tool de teste")
            .parameters(JsonObjectSchema.builder().addStringProperty("x").build())
            .build();

    private static ToolProviderRequest requisicao() {
        return new ToolProviderRequest("ctx-1", UserMessage.from("pedido qualquer"));
    }

    @Test
    void delegaResultadoDoToolProviderMantendoNomeEEspecificacao() {
        ToolExecutor executorFake = (request, memoryId) -> "resultado-ok";
        ToolProvider delegate = request -> new ToolProviderResult(Map.of(TOOL_SPEC, executorFake));

        ToolProviderResult resultado = new ToolProviderComLog(delegate).provideTools(requisicao());

        assertThat(resultado.aiServiceTools()).hasSize(1);
        AiServiceTool tool = resultado.aiServiceTools().getFirst();
        assertThat(tool.name()).isEqualTo("minha_tool");
        assertThat(tool.toolSpecification()).isEqualTo(TOOL_SPEC);

        String saida = tool.toolExecutor().execute(
                ToolExecutionRequest.builder().name("minha_tool").arguments("{}").build(), "mem-1");
        assertThat(saida).isEqualTo("resultado-ok");
    }

    @Test
    void relancaExcecaoDaToolSemAlteracao() {
        RuntimeException falhaOriginal = new IllegalStateException("mcp fora do ar");
        ToolExecutor executorQueFalha = (request, memoryId) -> {
            throw falhaOriginal;
        };
        ToolProvider delegate = request -> new ToolProviderResult(Map.of(TOOL_SPEC, executorQueFalha));

        ToolProviderResult resultado = new ToolProviderComLog(delegate).provideTools(requisicao());
        ToolExecutor executorComLog = resultado.aiServiceTools().getFirst().toolExecutor();
        ToolExecutionRequest request = ToolExecutionRequest.builder().name("minha_tool").arguments("{}").build();

        assertThatThrownBy(() -> executorComLog.execute(request, "mem-1"))
                .isSameAs(falhaOriginal);
    }

    @Test
    void isDynamicDelegaParaOToolProviderOriginal() {
        ToolProvider dinamico = new ToolProvider() {
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                return new ToolProviderResult(Map.of());
            }

            @Override
            public boolean isDynamic() {
                return true;
            }
        };

        assertThat(new ToolProviderComLog(dinamico).isDynamic()).isTrue();
    }
}

package poc.a2a.investimentos.especialista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProviderRequest;
import org.junit.jupiter.api.Test;

/** O especialista so enxerga as tools da jornada de investimentos; credito e com a Ana. */
class EspecialistaConfigTest {

    static McpClient client(String chave, String... tools) {
        McpClient client = mock(McpClient.class);
        when(client.key()).thenReturn(chave);
        when(client.listTools()).thenReturn(Arrays.stream(tools).map(nome -> ToolSpecification.builder()
                .name(nome).description(nome)
                .parameters(JsonObjectSchema.builder().addStringProperty("customerId").build())
                .build()).toList());
        return client;
    }

    @Test
    void naoExpoeASolicitacaoDeCreditoAoEspecialista() {
        McpClient cdb = client("cdb-mcp", "listar_posicoes_cdb", "listar_resgates_cdb");
        McpClient tracking = client("tracking-money-mcp", "listar_movimentacoes", "consultar_status_transferencia");
        McpClient cred = client("cred-mcp", "consultar_conta_garantia", "consultar_solicitacoes_credito");

        List<String> nomes = EspecialistaConfig.provedorMcp(cdb, tracking, cred)
                .provideTools(new ToolProviderRequest("ctx-1", UserMessage.from("pedido")))
                .aiServiceTools().stream().map(AiServiceTool::name).toList();

        assertThat(nomes).containsExactlyInAnyOrderElementsOf(EspecialistaConfig.TOOLS_DO_ESPECIALISTA)
                .doesNotContain("consultar_solicitacoes_credito");
    }
}

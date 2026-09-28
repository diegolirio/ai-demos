package poc.a2a.investimentos.especialista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProviderRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    /** Pelo LiteLLM as tools chegam como "{servidor}-{tool}"; o especialista ve os nomes originais. */
    @Test
    void pelosGatewayVeOsNomesSemPrefixoENaoVeOCredito() {
        McpClient cdb = client("cdb-mcp", "cdb_mcp-listar_posicoes_cdb", "cdb_mcp-listar_resgates_cdb");
        McpClient tracking = client("tracking-money-mcp", "tracking_money_mcp-listar_movimentacoes",
                "tracking_money_mcp-consultar_status_transferencia");
        McpClient cred = client("cred-mcp", "cred_mcp-consultar_conta_garantia",
                "cred_mcp-consultar_solicitacoes_credito");

        List<String> nomes = EspecialistaConfig.provedorMcp(cdb, tracking, cred)
                .provideTools(new ToolProviderRequest("ctx-1", UserMessage.from("pedido")))
                .aiServiceTools().stream().map(AiServiceTool::name).toList();

        assertThat(nomes).containsExactlyInAnyOrderElementsOf(EspecialistaConfig.TOOLS_DO_ESPECIALISTA);
    }

    /** O LLM chama o nome limpo; o McpClient recebe o nome real do gateway (com prefixo). */
    @Test
    void executaComONomeOriginalDoGateway() {
        McpClient cdb = client("cdb-mcp", "cdb_mcp-listar_posicoes_cdb");
        when(cdb.executeTool(any(ToolExecutionRequest.class), any(InvocationContext.class)))
                .thenReturn(ToolExecutionResult.builder().resultText("[]").build());

        AiServiceTool tool = EspecialistaConfig.provedorMcp(cdb)
                .provideTools(new ToolProviderRequest("ctx-1", UserMessage.from("pedido")))
                .aiServiceTools().getFirst();
        tool.toolExecutor().execute(ToolExecutionRequest.builder()
                .id("1").name("listar_posicoes_cdb").arguments("{\"customerId\":\"cli-001\"}").build(), "ctx-1");

        ArgumentCaptor<ToolExecutionRequest> requisicao = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(cdb).executeTool(requisicao.capture(), any(InvocationContext.class));
        assertThat(tool.name()).isEqualTo("listar_posicoes_cdb");
        assertThat(requisicao.getValue().name()).isEqualTo("cdb_mcp-listar_posicoes_cdb");
    }

    @Test
    void cabecalhoDoGatewaySoComChave() {
        assertThat(EspecialistaConfig.cabecalhosGateway("sk-x")).containsExactly(
                java.util.Map.entry("x-litellm-api-key", "Bearer sk-x"));
        assertThat(EspecialistaConfig.cabecalhosGateway("")).isEmpty();
        assertThat(EspecialistaConfig.cabecalhosGateway("  ")).isEmpty();
        assertThat(EspecialistaConfig.cabecalhosGateway(null)).isEmpty();
    }
}

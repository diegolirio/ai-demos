package poc.a2a.trackingmoney;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackingMoneyMcpServerTest {

    @LocalServerPort
    int port;

    McpClient client;

    @BeforeEach
    void conectar() {
        client = DefaultMcpClient.builder()
                .key("tracking-money-mcp")
                .transport(StreamableHttpMcpTransport.builder()
                        .url("http://localhost:" + port + "/mcp")
                        .timeout(Duration.ofSeconds(10))
                        .build())
                .build();
    }

    @AfterEach
    void fechar() throws Exception {
        client.close();
    }

    private String chamar(String tool, String argumentosJson) {
        return client.executeTool(ToolExecutionRequest.builder()
                .id("1").name(tool).arguments(argumentosJson).build()).resultText();
    }

    @Test
    void listaAsDuasTools() {
        List<ToolSpecification> tools = client.listTools();

        assertThat(tools).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("listar_movimentacoes", "consultar_status_transferencia");
    }

    @Test
    void cli001TemCreditoEmProcessamento() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-001\"}"))
                .contains("\"status\":\"PROCESSANDO\"").contains("\"transferenciaId\":\"trf-001\"");
        assertThat(chamar("consultar_status_transferencia", "{\"transferenciaId\":\"trf-001\"}"))
                .contains("\"status\":\"EM_PROCESSAMENTO\"").contains("30 minutos");
    }

    @Test
    void cli002TemCreditoConcluido() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-002\"}"))
                .contains("\"status\":\"CONCLUIDA\"").contains("3000.00");
    }

    @Test
    void cli003ECli004NaoTemMovimentacoes() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-003\"}")).isEqualTo("[]");
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-004\"}")).isEqualTo("[]");
    }

    @Test
    void cli005RecebeuOResgateIntegral() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-005\"}"))
                .contains("\"status\":\"CONCLUIDA\"").contains("10000.00").contains("\"transferenciaId\":\"trf-005\"");
        assertThat(chamar("consultar_status_transferencia", "{\"transferenciaId\":\"trf-005\"}"))
                .contains("\"status\":\"CONCLUIDA\"");
    }

    @Test
    void cli008RecebeuSoAParteLiberada() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-008\"}"))
                .contains("\"status\":\"CONCLUIDA\"").contains("6500.00").contains("\"transferenciaId\":\"trf-008\"");
        assertThat(chamar("consultar_status_transferencia", "{\"transferenciaId\":\"trf-008\"}"))
                .contains("\"status\":\"CONCLUIDA\"");
    }

    @Test
    void cli006ECli007NaoRecebemCredito() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-006\"}")).isEqualTo("[]");
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-007\"}")).isEqualTo("[]");
    }

    @Test
    void transferenciaInexistenteEErro() {
        assertThatThrownBy(() -> chamar("consultar_status_transferencia", "{\"transferenciaId\":\"trf-999\"}"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("trf-999");
    }
}

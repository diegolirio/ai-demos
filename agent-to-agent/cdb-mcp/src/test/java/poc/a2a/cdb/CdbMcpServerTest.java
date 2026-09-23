package poc.a2a.cdb;

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
class CdbMcpServerTest {

    @LocalServerPort
    int port;

    McpClient client;

    @BeforeEach
    void conectar() {
        client = DefaultMcpClient.builder()
                .key("cdb-mcp")
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
                .containsExactlyInAnyOrder("listar_posicoes_cdb", "listar_resgates_cdb");
        assertThat(tools).allSatisfy(t -> assertThat(t.parameters().required()).containsExactly("customerId"));
    }

    @Test
    void cli001TemResgateEmLiquidacao() {
        assertThat(chamar("listar_resgates_cdb", "{\"customerId\":\"cli-001\"}"))
                .contains("\"resgateId\":\"res-001\"").contains("\"status\":\"EM_LIQUIDACAO\"").contains("5000.00");
    }

    @Test
    void cli002TemResgateLiquidado() {
        assertThat(chamar("listar_resgates_cdb", "{\"customerId\":\"cli-002\"}"))
                .contains("\"status\":\"LIQUIDADO\"").contains("3000.00");
    }

    @Test
    void cli003TemPosicaoAtivaESemResgates() {
        assertThat(chamar("listar_posicoes_cdb", "{\"customerId\":\"cli-003\"}"))
                .contains("\"posicaoId\":\"pos-003\"").contains("8420.10");
        assertThat(chamar("listar_resgates_cdb", "{\"customerId\":\"cli-003\"}")).isEqualTo("[]");
    }

    @Test
    void cli004NaoTemNada() {
        assertThat(chamar("listar_posicoes_cdb", "{\"customerId\":\"cli-004\"}")).isEqualTo("[]");
        assertThat(chamar("listar_resgates_cdb", "{\"customerId\":\"cli-004\"}")).isEqualTo("[]");
    }

    @Test
    void rejeitaEntradaForaDoSchema() {
        assertThatThrownBy(() -> chamar("listar_resgates_cdb", "{\"foo\":\"bar\"}"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("input validation failed");
    }
}

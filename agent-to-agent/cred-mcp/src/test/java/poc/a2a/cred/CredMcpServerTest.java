package poc.a2a.cred;

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
class CredMcpServerTest {

    @LocalServerPort
    int port;

    McpClient client;

    @BeforeEach
    void conectar() {
        client = DefaultMcpClient.builder()
                .key("cred-mcp")
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

    private String consultar(String customerId) {
        return client.executeTool(ToolExecutionRequest.builder()
                .id("1").name("consultar_conta_garantia")
                .arguments("{\"customerId\":\"" + customerId + "\"}").build()).resultText();
    }

    @Test
    void listaAsToolsComCustomerIdObrigatorio() {
        List<ToolSpecification> tools = client.listTools();

        assertThat(tools).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("consultar_conta_garantia", "consultar_solicitacoes_credito");
        assertThat(tools).allSatisfy(t -> assertThat(t.parameters().required()).containsExactly("customerId"));
    }

    private String solicitacoes(String customerId) {
        return client.executeTool(ToolExecutionRequest.builder()
                .id("1").name("consultar_solicitacoes_credito")
                .arguments("{\"customerId\":\"" + customerId + "\"}").build()).resultText();
    }

    @Test
    void cli009EmprestimoRecusadoPorRenda() {
        assertThat(solicitacoes("cli-009"))
                .contains("\"solicitacaoId\":\"sol-009\"").contains("\"status\":\"RECUSADA\"")
                .contains("\"motivoCodigo\":\"RENDA_INSUFICIENTE\"").contains("\"valorSolicitado\":30000.00")
                .contains("\"dataSolicitacao\":\"2026-09-20T10:30:00\"");
    }

    @Test
    void cli011TemUmaAprovadaEUmaRecusada() {
        assertThat(solicitacoes("cli-011"))
                .contains("\"status\":\"APROVADA\"").contains("\"status\":\"RECUSADA\"")
                .contains("\"reavaliacaoApos\":\"2027-01-15\"");
    }

    @Test
    void semSolicitacoesDevolveListaVazia() {
        assertThat(solicitacoes("cli-001")).isEqualTo("[]");
    }

    @Test
    void solicitacoesRejeitaEntradaForaDoSchema() {
        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                .id("1").name("consultar_solicitacoes_credito").arguments("{\"foo\":\"bar\"}").build()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("input validation failed");
    }

    @Test
    void cli005FoiLiberadoParaAConta() {
        assertThat(consultar("cli-005"))
                .contains("\"resgateId\":\"res-005\"").contains("\"status\":\"LIBERADO_CONTA\"")
                .contains("\"valorLiberado\":10000.00");
    }

    @Test
    void cli006EstaEmAnalise() {
        assertThat(consultar("cli-006")).contains("\"status\":\"EM_ANALISE\"").contains("4200.00");
    }

    @Test
    void cli007RetidoAtePagarAFatura() {
        assertThat(consultar("cli-007"))
                .contains("\"status\":\"RETIDO_ATE_PAGAMENTO_FATURA\"")
                .contains("\"gastoCartao\":12000.00").contains("\"vencimentoFatura\":\"2026-10-05\"");
    }

    @Test
    void cli008RetidoParcialmente() {
        assertThat(consultar("cli-008"))
                .contains("\"status\":\"RETIDO_PARCIAL\"")
                .contains("\"valorRetido\":3500.00").contains("\"valorLiberado\":6500.00");
    }

    @Test
    void semRetencaoDevolveListaVazia() {
        assertThat(consultar("cli-001")).isEqualTo("[]");
    }

    @Test
    void rejeitaEntradaForaDoSchema() {
        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                .id("1").name("consultar_conta_garantia").arguments("{\"foo\":\"bar\"}").build()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("input validation failed");
    }
}

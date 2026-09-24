package poc.a2a.ana.credito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CredMcpSolicitacoesCreditoTest {

    static final String JSON_CLI_011 = """
            [{"solicitacaoId":"sol-011a","tipo":"CARTAO_CREDITO","dataSolicitacao":"2026-09-20T10:30:00",
              "status":"APROVADA","valorSolicitado":3000.00,"motivoCodigo":null,"motivoCliente":null,
              "proximoPasso":"Cartao aprovado","reavaliacaoApos":null},
             {"solicitacaoId":"sol-011b","tipo":"EMPRESTIMO_PESSOAL","dataSolicitacao":"2026-09-20T10:30:00",
              "status":"RECUSADA","valorSolicitado":20000.00,"motivoCodigo":"RELACIONAMENTO_RECENTE",
              "motivoCliente":"Conta recente","proximoPasso":"Aguarde","reavaliacaoApos":"2027-01-15"}]""";

    static ToolExecutionResult resultado(String texto, boolean erro) {
        return ToolExecutionResult.builder().resultText(texto).isError(erro).build();
    }

    @Test
    void chamaAToolComOCustomerIdELeOJson() {
        McpClient mcp = mock(McpClient.class);
        when(mcp.executeTool(any(ToolExecutionRequest.class))).thenReturn(resultado(JSON_CLI_011, false));
        CredMcpSolicitacoesCredito adaptador = new CredMcpSolicitacoesCredito(() -> mcp);

        List<SolicitacaoCredito> solicitacoes = adaptador.consultar("cli-011");

        ArgumentCaptor<ToolExecutionRequest> requisicao = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(mcp).executeTool(requisicao.capture());
        assertThat(requisicao.getValue().name()).isEqualTo("consultar_solicitacoes_credito");
        assertThat(requisicao.getValue().arguments()).isEqualTo("{\"customerId\":\"cli-011\"}");
        assertThat(solicitacoes).hasSize(2);
        assertThat(solicitacoes.get(1)).isEqualTo(new SolicitacaoCredito("sol-011b", "EMPRESTIMO_PESSOAL",
                LocalDateTime.of(2026, 9, 20, 10, 30), "RECUSADA", new BigDecimal("20000.00"),
                "RELACIONAMENTO_RECENTE", "Conta recente", "Aguarde", LocalDate.of(2027, 1, 15)));
    }

    @Test
    void listaVazia() {
        McpClient mcp = mock(McpClient.class);
        when(mcp.executeTool(any(ToolExecutionRequest.class))).thenReturn(resultado("[]", false));

        assertThat(new CredMcpSolicitacoesCredito(() -> mcp).consultar("cli-001")).isEmpty();
    }

    @Test
    void naoConectaNoConstrutorEReusaOClient() {
        AtomicInteger conexoes = new AtomicInteger();
        McpClient mcp = mock(McpClient.class);
        when(mcp.executeTool(any(ToolExecutionRequest.class))).thenReturn(resultado("[]", false));
        CredMcpSolicitacoesCredito adaptador = new CredMcpSolicitacoesCredito(() -> {
            conexoes.incrementAndGet();
            return mcp;
        });

        assertThat(conexoes).hasValue(0);
        adaptador.consultar("cli-001");
        adaptador.consultar("cli-002");
        assertThat(conexoes).hasValue(1);
    }

    @Test
    void falhaNaConexaoViraIndisponivel() {
        CredMcpSolicitacoesCredito adaptador = new CredMcpSolicitacoesCredito(() -> {
            throw new IllegalStateException("Connection refused");
        });

        assertThatThrownBy(() -> adaptador.consultar("cli-009"))
                .isInstanceOf(CreditoIndisponivelException.class).hasMessageContaining("Connection refused");
    }

    @Test
    void falhaNaChamadaDescartaOClientEReconectaNaProxima() throws Exception {
        McpClient quebrado = mock(McpClient.class);
        when(quebrado.executeTool(any(ToolExecutionRequest.class))).thenThrow(new RuntimeException("session expired"));
        McpClient novo = mock(McpClient.class);
        when(novo.executeTool(any(ToolExecutionRequest.class))).thenReturn(resultado("[]", false));
        var fila = new java.util.ArrayDeque<>(List.of(quebrado, novo));
        CredMcpSolicitacoesCredito adaptador = new CredMcpSolicitacoesCredito(fila::poll);

        assertThatThrownBy(() -> adaptador.consultar("cli-009")).isInstanceOf(CreditoIndisponivelException.class);
        verify(quebrado).close();
        assertThat(adaptador.consultar("cli-009")).isEmpty();
    }

    @Test
    void erroDaToolViraIndisponivel() {
        McpClient mcp = mock(McpClient.class);
        when(mcp.executeTool(any(ToolExecutionRequest.class))).thenReturn(resultado("boom", true));

        assertThatThrownBy(() -> new CredMcpSolicitacoesCredito(() -> mcp).consultar("cli-009"))
                .isInstanceOf(CreditoIndisponivelException.class).hasMessageContaining("boom");
    }

    @Test
    void jsonInvalidoViraIndisponivel() {
        McpClient mcp = mock(McpClient.class);
        when(mcp.executeTool(any(ToolExecutionRequest.class))).thenReturn(resultado("nao e json", false));

        assertThatThrownBy(() -> new CredMcpSolicitacoesCredito(() -> mcp).consultar("cli-009"))
                .isInstanceOf(CreditoIndisponivelException.class);
    }

    @Test
    void closeFechaOClientAberto() throws Exception {
        McpClient mcp = mock(McpClient.class);
        when(mcp.executeTool(any(ToolExecutionRequest.class))).thenReturn(resultado("[]", false));
        CredMcpSolicitacoesCredito adaptador = new CredMcpSolicitacoesCredito(() -> mcp);
        adaptador.consultar("cli-001");

        adaptador.close();

        verify(mcp).close();
    }
}

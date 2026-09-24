package poc.a2a.ana.credito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador MCP: a Ana chama a tool do cred-mcp pelo McpClient (sem McpToolProvider), montando os argumentos
 * em Java. Conexao preguicosa: DefaultMcpClient conecta no construtor, entao o client so e criado na primeira
 * consulta (a Ana sobe mesmo com o cred-mcp fora) e e descartado em qualquer falha (a proxima reconecta, o que
 * cobre o restart do cred-mcp, que invalida a sessao MCP).
 */
public class CredMcpSolicitacoesCredito implements SolicitacoesCredito, AutoCloseable {

    static final String TOOL = "consultar_solicitacoes_credito";

    private static final Logger log = LoggerFactory.getLogger(CredMcpSolicitacoesCredito.class);

    private final Supplier<McpClient> fabrica;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Object trava = new Object();
    private McpClient client;

    public CredMcpSolicitacoesCredito(Supplier<McpClient> fabrica) {
        this.fabrica = fabrica;
    }

    public static CredMcpSolicitacoesCredito conectandoEm(String url, Duration timeout) {
        return new CredMcpSolicitacoesCredito(() -> DefaultMcpClient.builder()
                .key("cred-mcp")
                .clientName("ana-agent")
                .transport(StreamableHttpMcpTransport.builder().url(url).timeout(timeout).build())
                .toolExecutionTimeout(timeout)
                .build());
    }

    @Override
    public List<SolicitacaoCredito> consultar(String customerId) {
        McpClient atual = client();
        ToolExecutionResult resultado;
        try {
            resultado = atual.executeTool(ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(TOOL)
                    .arguments(json.writeValueAsString(Map.of("customerId", customerId)))
                    .build());
        } catch (RuntimeException e) {
            descartar(atual);
            throw new CreditoIndisponivelException("falha ao chamar " + TOOL + ": " + e.getMessage(), e);
        }
        if (resultado.isError()) {
            throw new CreditoIndisponivelException(TOOL + " devolveu erro: " + resultado.resultText());
        }
        try {
            SolicitacaoCredito[] itens = json.readValue(resultado.resultText(), SolicitacaoCredito[].class);
            if (itens == null) {
                throw new CreditoIndisponivelException("resposta vazia de " + TOOL);
            }
            return List.of(itens);
        } catch (JacksonException e) {
            throw new CreditoIndisponivelException("resposta invalida de " + TOOL, e);
        }
    }

    private McpClient client() {
        synchronized (trava) {
            if (client == null) {
                try {
                    client = fabrica.get();
                } catch (RuntimeException e) {
                    throw new CreditoIndisponivelException("nao foi possivel conectar ao cred-mcp: " + e.getMessage(), e);
                }
            }
            return client;
        }
    }

    private void descartar(McpClient falhou) {
        synchronized (trava) {
            if (client == falhou) {
                client = null;
            }
        }
        fecharSemFalhar(falhou);
    }

    @Override
    public void close() {
        synchronized (trava) {
            if (client != null) {
                fecharSemFalhar(client);
                client = null;
            }
        }
    }

    private static void fecharSemFalhar(McpClient mcp) {
        try {
            mcp.close();
        } catch (Exception e) {
            log.debug("cred-mcp.client.close.falhou erro={}", e.toString());
        }
    }
}

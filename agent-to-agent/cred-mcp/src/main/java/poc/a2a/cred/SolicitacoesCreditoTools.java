package poc.a2a.cred;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SolicitacoesCreditoTools {

    private static final Logger log = LoggerFactory.getLogger(SolicitacoesCreditoTools.class);

    private final SolicitacoesCreditoRepository repository;
    private final JsonMapper jsonMapper;

    public SolicitacoesCreditoTools(SolicitacoesCreditoRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(consultarSolicitacoesCredito());
    }

    private SyncToolSpecification consultarSolicitacoesCredito() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("consultar_solicitacoes_credito", ContaGarantiaTools.CUSTOMER_ID_SCHEMA)
                        .description("Lista as solicitacoes de credito do cliente (EMPRESTIMO_PESSOAL | "
                                + "CARTAO_CREDITO): solicitacaoId, dataSolicitacao, status (APROVADA | RECUSADA | "
                                + "EM_ANALISE), valorSolicitado, motivoCodigo (interno) e motivoCliente (texto para o "
                                + "cliente) quando RECUSADA, proximoPasso e reavaliacaoApos. "
                                + "Retorna um array JSON (vazio se nao houver solicitacoes).")
                        .build())
                .callHandler((exchange, request) -> {
                    String customerId = (String) request.arguments().get("customerId");
                    var resultado = repository.solicitacoes(customerId);
                    log.info("mcp.tool.call tool=consultar_solicitacoes_credito customerId={} itens={}",
                            customerId, resultado.size());
                    return CallToolResult.builder()
                            .addTextContent(jsonMapper.writeValueAsString(resultado))
                            .isError(false)
                            .build();
                })
                .build();
    }
}

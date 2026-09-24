package poc.a2a.cred;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ContaGarantiaTools {

    private static final Logger log = LoggerFactory.getLogger(ContaGarantiaTools.class);

    static final Map<String, Object> CUSTOMER_ID_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("customerId", Map.of(
                    "type", "string",
                    "minLength", 1,
                    "description", "Identificador do cliente, ex.: cli-001")),
            "required", List.of("customerId"),
            "additionalProperties", false);

    private final ContaGarantiaRepository repository;
    private final JsonMapper jsonMapper;

    public ContaGarantiaTools(ContaGarantiaRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(consultarContaGarantia());
    }

    private SyncToolSpecification consultarContaGarantia() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("consultar_conta_garantia", CUSTOMER_ID_SCHEMA)
                        .description("Lista as retencoes em conta garantia de resgates de investimento do cliente, "
                                + "motivadas por gastos no cartao de credito: resgateId, status (LIBERADO_CONTA | "
                                + "EM_ANALISE | RETIDO_ATE_PAGAMENTO_FATURA | RETIDO_PARCIAL), valorResgatado, "
                                + "valorRetido, valorLiberado, gastoCartao, vencimentoFatura e detalhe. "
                                + "Retorna um array JSON (vazio se o resgate nao passou pela conta garantia).")
                        .build())
                .callHandler((exchange, request) -> {
                    String customerId = (String) request.arguments().get("customerId");
                    var resultado = repository.retencoes(customerId);
                    log.info("mcp.tool.call tool=consultar_conta_garantia customerId={} itens={}",
                            customerId, resultado.size());
                    return CallToolResult.builder()
                            .addTextContent(jsonMapper.writeValueAsString(resultado))
                            .isError(false)
                            .build();
                })
                .build();
    }
}

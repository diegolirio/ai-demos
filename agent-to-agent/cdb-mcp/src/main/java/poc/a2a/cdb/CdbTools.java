package poc.a2a.cdb;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CdbTools {

    private static final Logger log = LoggerFactory.getLogger(CdbTools.class);

    private static final Map<String, Object> CUSTOMER_ID_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("customerId", Map.of(
                    "type", "string",
                    "minLength", 1,
                    "description", "Identificador do cliente, ex.: cli-001")),
            "required", List.of("customerId"),
            "additionalProperties", false);

    private final CdbRepository repository;
    private final JsonMapper jsonMapper;

    public CdbTools(CdbRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(
                tool("listar_posicoes_cdb",
                        "Lista as posicoes de CDB (aplicacoes ativas) de um cliente: emissor, valor aplicado, "
                                + "valor atual, taxa em % do CDI e vencimento. Retorna um array JSON (vazio se nao houver).",
                        repository::posicoes),
                tool("listar_resgates_cdb",
                        "Lista os resgates de CDB de um cliente com valor, data de solicitacao e status "
                                + "(SOLICITADO | EM_LIQUIDACAO | LIQUIDADO). Use para saber se um resgate ja foi pago "
                                + "ou ainda esta em liquidacao. Retorna um array JSON (vazio se nao houver).",
                        repository::resgates));
    }

    private SyncToolSpecification tool(String name, String description, Function<String, List<?>> consulta) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(name, CUSTOMER_ID_SCHEMA).description(description).build())
                .callHandler((exchange, request) -> {
                    String customerId = (String) request.arguments().get("customerId");
                    List<?> resultado = consulta.apply(customerId);
                    log.info("mcp.tool.call tool={} customerId={} itens={}", name, customerId, resultado.size());
                    return CallToolResult.builder()
                            .addTextContent(jsonMapper.writeValueAsString(resultado))
                            .isError(false)
                            .build();
                })
                .build();
    }
}

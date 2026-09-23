package poc.a2a.trackingmoney;

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
public class TrackingMoneyTools {

    private static final Logger log = LoggerFactory.getLogger(TrackingMoneyTools.class);

    private final TrackingMoneyRepository repository;
    private final JsonMapper jsonMapper;

    public TrackingMoneyTools(TrackingMoneyRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    private static Map<String, Object> schemaComUmCampo(String campo, String descricao) {
        return Map.of(
                "type", "object",
                "properties", Map.of(campo, Map.of("type", "string", "minLength", 1, "description", descricao)),
                "required", List.of(campo),
                "additionalProperties", false);
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(listarMovimentacoes(), consultarStatusTransferencia());
    }

    private SyncToolSpecification listarMovimentacoes() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("listar_movimentacoes",
                                schemaComUmCampo("customerId", "Identificador do cliente, ex.: cli-001"))
                        .description("Lista as movimentacoes da conta corrente do cliente (creditos e debitos) com "
                                + "valor, descricao, data/hora, status (PROCESSANDO | CONCLUIDA) e transferenciaId. "
                                + "Use para verificar se o dinheiro de um resgate ja caiu na conta. Retorna um array JSON.")
                        .build())
                .callHandler((exchange, request) -> {
                    String customerId = (String) request.arguments().get("customerId");
                    var resultado = repository.movimentacoes(customerId);
                    log.info("mcp.tool.call tool=listar_movimentacoes customerId={} itens={}", customerId, resultado.size());
                    return CallToolResult.builder()
                            .addTextContent(jsonMapper.writeValueAsString(resultado)).isError(false).build();
                })
                .build();
    }

    private SyncToolSpecification consultarStatusTransferencia() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("consultar_status_transferencia",
                                schemaComUmCampo("transferenciaId", "Identificador da transferencia, ex.: trf-001"))
                        .description("Consulta o status de uma transferencia entre produtos (ex.: resgate de "
                                + "investimento para a conta corrente): status, previsao de conclusao e detalhe. "
                                + "Retorna um objeto JSON.")
                        .build())
                .callHandler((exchange, request) -> {
                    String transferenciaId = (String) request.arguments().get("transferenciaId");
                    log.info("mcp.tool.call tool=consultar_status_transferencia transferenciaId={}", transferenciaId);
                    return repository.transferencia(transferenciaId)
                            .map(status -> CallToolResult.builder()
                                    .addTextContent(jsonMapper.writeValueAsString(status)).isError(false).build())
                            .orElseGet(() -> CallToolResult.builder()
                                    .addTextContent("Transferencia nao encontrada: " + transferenciaId)
                                    .isError(true).build());
                })
                .build();
    }
}

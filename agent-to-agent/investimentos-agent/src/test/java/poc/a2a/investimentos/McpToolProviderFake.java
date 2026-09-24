package poc.a2a.investimentos;

import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dublê das tools MCP (cdb-mcp e tracking-money-mcp sao OUTROS sistemas): os mesmos 5 nomes, descricoes e
 * parametros das tools reais, com respostas canned copiadas dos repositorios mock dos MCP servers
 * (CdbRepository / TrackingMoneyRepository) para cli-001, no mesmo JSON que o servidor serializa.
 * Cliente desconhecido -> array vazio, como os servidores reais.
 */
final class McpToolProviderFake {

    static final String CUSTOMER_ID = "cli-001";

    static final String POSICOES_CLI_001 = """
            [{"posicaoId":"pos-001","emissor":"Banco Alfa S.A.","valorAplicado":10000.00,"valorAtual":5850.42,\
            "taxaPercentualCdi":110.0,"vencimento":"2028-01-15"}]""";

    static final String RESGATES_CLI_001 = """
            [{"resgateId":"res-001","posicaoId":"pos-001","valor":5000.00,"status":"EM_LIQUIDACAO",\
            "dataSolicitacao":"2026-09-22T10:00:00"}]""";

    static final String MOVIMENTACOES_CLI_001 = """
            [{"movimentacaoId":"mov-001","tipo":"CREDITO","valor":5000.00,"descricao":"Resgate CDB res-001",\
            "dataHora":"2026-09-22T10:01:00","status":"PROCESSANDO","transferenciaId":"trf-001"}]""";

    static final String TRANSFERENCIA_TRF_001 = """
            {"transferenciaId":"trf-001","status":"EM_PROCESSAMENTO","previsao":"ate 30 minutos",\
            "detalhe":"Liquidacao do resgate em andamento; o credito sera efetuado na conta corrente"}""";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private McpToolProviderFake() {
    }

    static ToolProvider criar() {
        ToolProviderResult resultado = ToolProviderResult.builder()
                .add(porCliente("listar_posicoes_cdb",
                        "Lista as posicoes de CDB (aplicacoes ativas) de um cliente: emissor, valor aplicado, "
                                + "valor atual, taxa em % do CDI e vencimento. Retorna um array JSON (vazio se nao houver)."),
                        executor("customerId", id -> CUSTOMER_ID.equals(id) ? POSICOES_CLI_001 : "[]"))
                .add(porCliente("listar_resgates_cdb",
                        "Lista os resgates de CDB de um cliente com valor, data de solicitacao e status "
                                + "(SOLICITADO | EM_LIQUIDACAO | LIQUIDADO). Use para saber se um resgate ja foi pago "
                                + "ou ainda esta em liquidacao. Retorna um array JSON (vazio se nao houver)."),
                        executor("customerId", id -> CUSTOMER_ID.equals(id) ? RESGATES_CLI_001 : "[]"))
                .add(porCliente("listar_movimentacoes",
                        "Lista as movimentacoes da conta corrente do cliente (creditos e debitos) com "
                                + "valor, descricao, data/hora, status (PROCESSANDO | CONCLUIDA) e transferenciaId. "
                                + "Use para verificar se o dinheiro de um resgate ja caiu na conta. Retorna um array JSON."),
                        executor("customerId", id -> CUSTOMER_ID.equals(id) ? MOVIMENTACOES_CLI_001 : "[]"))
                .add(tool("consultar_status_transferencia",
                        "Consulta o status de uma transferencia entre produtos (ex.: resgate de "
                                + "investimento para a conta corrente): status, previsao de conclusao e detalhe. "
                                + "Retorna um objeto JSON.",
                        "transferenciaId", "Identificador da transferencia, ex.: trf-001"),
                        executor("transferenciaId", id -> "trf-001".equals(id)
                                ? TRANSFERENCIA_TRF_001 : "Transferencia nao encontrada: " + id))
                .add(porCliente("consultar_conta_garantia",
                        "Lista as retencoes em conta garantia de resgates de investimento do cliente, "
                                + "motivadas por gastos no cartao de credito: resgateId, status (LIBERADO_CONTA | "
                                + "EM_ANALISE | RETIDO_ATE_PAGAMENTO_FATURA | RETIDO_PARCIAL), valorResgatado, "
                                + "valorRetido, valorLiberado, gastoCartao, vencimentoFatura e detalhe. "
                                + "Retorna um array JSON (vazio se o resgate nao passou pela conta garantia)."),
                        executor("customerId", id -> "[]"))
                .build();
        return request -> resultado;
    }

    private static ToolSpecification porCliente(String nome, String descricao) {
        return tool(nome, descricao, "customerId", "Identificador do cliente, ex.: cli-001");
    }

    private static ToolSpecification tool(String nome, String descricao, String campo, String descricaoCampo) {
        return ToolSpecification.builder()
                .name(nome)
                .description(descricao)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(campo, descricaoCampo)
                        .required(campo)
                        .build())
                .build();
    }

    private static ToolExecutor executor(String campo, Function<String, String> resposta) {
        return (request, memoryId) -> {
            Map<?, ?> argumentos = JSON.readValue(request.arguments(), Map.class);
            Object valor = argumentos.get(campo);
            return resposta.apply(valor == null ? "" : valor.toString().trim());
        };
    }
}

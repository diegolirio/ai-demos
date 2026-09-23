package poc.a2a.ana.assistente;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poc.a2a.ana.investimentos.InvestimentosClient;
import poc.a2a.ana.investimentos.InvestimentosIndisponivelException;
import poc.a2a.ana.investimentos.RespostaInvestimentos;

/** Única capacidade de delegação da Ana: passa a intenção ao Especialista de Investimentos via A2A. */
public class DelegacaoInvestimentosTool {

    public static final String SESSION_ID = "sessionId";
    public static final String CUSTOMER_ID = "customerId";
    public static final String REQUEST_ID = "requestId";

    private static final Logger log = LoggerFactory.getLogger(DelegacaoInvestimentosTool.class);

    private final InvestimentosClient investimentos;
    private final UltimasRespostasInvestimentos ultimas;

    public DelegacaoInvestimentosTool(InvestimentosClient investimentos, UltimasRespostasInvestimentos ultimas) {
        this.investimentos = investimentos;
        this.ultimas = ultimas;
    }

    @Tool(name = "delegar_investimentos", value = "Delega ao Especialista de Investimentos a tarefa de descobrir onde "
            + "esta o dinheiro que o cliente tinha em investimentos (aplicado, em liquidacao de resgate ou ja na conta). "
            + "Use quando o cliente disser que o dinheiro estava em investimentos.")
    public String delegarInvestimentos(
            @P("Intencao do cliente em linguagem natural, ex.: cliente nao encontra dinheiro que estava em investimentos")
            String pedido,
            InvocationParameters parametros) {
        String sessionId = parametros.get(SESSION_ID);
        String customerId = parametros.get(CUSTOMER_ID);
        String requestId = parametros.get(REQUEST_ID);
        log.info("ana.tool.delegar_investimentos sessionId={} customerId={} pedido='{}'", sessionId, customerId, pedido);
        long inicio = System.nanoTime();
        try {
            RespostaInvestimentos resposta = investimentos.delegar(sessionId, customerId, pedido);
            ultimas.registrar(requestId, resposta);
            log.info("ana.tool.delegar_investimentos.ok sessionId={} customerId={} durationMs={}", sessionId,
                    customerId, (System.nanoTime() - inicio) / 1_000_000);
            return resposta.paraTextoLlm();
        } catch (InvestimentosIndisponivelException e) {
            log.warn("ana.tool.delegar_investimentos.indisponivel sessionId={} motivo={} durationMs={}", sessionId,
                    e.getMessage(), (System.nanoTime() - inicio) / 1_000_000);
            return "INDISPONIVEL: nao foi possivel consultar o especialista de investimentos agora.";
        }
    }
}

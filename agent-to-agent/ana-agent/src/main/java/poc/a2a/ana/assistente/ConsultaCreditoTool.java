package poc.a2a.ana.assistente;

import java.util.List;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poc.a2a.ana.atendimento.HistoricoAtendimentos;
import poc.a2a.ana.credito.CreditoIndisponivelException;
import poc.a2a.ana.credito.SolicitacaoCredito;
import poc.a2a.ana.credito.SolicitacoesCredito;

/**
 * Credito e fluxo da propria Ana: consulta o cred-mcp direto (McpClient), sem especialista.
 * Ao contrario do McpToolProvider do especialista, o LLM nao preenche nada: customerId vem de
 * InvocationParameters e o motivoCodigo (interno) e filtrado antes de o texto voltar ao LLM.
 */
public class ConsultaCreditoTool {

    public static final String INDISPONIVEL =
            "INDISPONIVEL: nao foi possivel consultar as solicitacoes de credito agora.";

    private static final Logger log = LoggerFactory.getLogger(ConsultaCreditoTool.class);

    private final SolicitacoesCredito credito;
    private final UltimasConsultasCredito ultimas;
    private final HistoricoAtendimentos historico;

    public ConsultaCreditoTool(SolicitacoesCredito credito, UltimasConsultasCredito ultimas,
                               HistoricoAtendimentos historico) {
        this.credito = credito;
        this.ultimas = ultimas;
        this.historico = historico;
    }

    @Tool(name = "consultar_solicitacoes_credito", value = "Consulta as solicitacoes de credito do cliente "
            + "(emprestimo pessoal e cartao de credito): status (APROVADA, RECUSADA, EM_ANALISE), motivo da recusa "
            + "em texto para o cliente, proximo passo e data de reavaliacao. Use quando o cliente perguntar sobre "
            + "um pedido de emprestimo ou de cartao, inclusive por que foi recusado. Nao tem parametros: a "
            + "ferramenta ja sabe quem e o cliente.")
    public String consultarSolicitacoesCredito(InvocationParameters parametros) {
        String sessionId = parametros.get(DelegacaoInvestimentosTool.SESSION_ID);
        String customerId = parametros.get(DelegacaoInvestimentosTool.CUSTOMER_ID);
        String requestId = parametros.get(DelegacaoInvestimentosTool.REQUEST_ID);
        log.info("ana.tool.consultar_solicitacoes_credito sessionId={} customerId={}", sessionId, customerId);
        long inicio = System.nanoTime();
        try {
            List<SolicitacaoCredito> solicitacoes = credito.consultar(customerId);
            ultimas.registrar(requestId, solicitacoes);
            if (!solicitacoes.isEmpty()) {
                registrarAtendimento(customerId, sessionId, solicitacoes);
            }
            log.info("ana.tool.consultar_solicitacoes_credito.ok sessionId={} customerId={} itens={} durationMs={}",
                    sessionId, customerId, solicitacoes.size(), (System.nanoTime() - inicio) / 1_000_000);
            return SolicitacaoCredito.paraTextoLlm(solicitacoes);
        } catch (CreditoIndisponivelException e) {
            log.warn("ana.tool.consultar_solicitacoes_credito.indisponivel sessionId={} motivo={} durationMs={}",
                    sessionId, e.getMessage(), (System.nanoTime() - inicio) / 1_000_000);
            return INDISPONIVEL;
        }
    }

    /** O historico e acessorio: falha ao gravar nao pode derrubar o atendimento. */
    private void registrarAtendimento(String customerId, String sessionId, List<SolicitacaoCredito> solicitacoes) {
        try {
            historico.registrarCredito(customerId, sessionId, SolicitacaoCredito.resumo(solicitacoes));
        } catch (RuntimeException e) {
            log.warn("ana.atendimento.registro.falhou sessionId={} customerId={} erro={}", sessionId, customerId,
                    e.toString());
        }
    }
}

package poc.a2a.ana.atendimento;

import java.util.List;

import poc.a2a.ana.investimentos.RespostaInvestimentos;

/** Atendimentos por cliente (customerId), para a Ana lembrar do que ja foi visto em outras sessoes. */
public interface HistoricoAtendimentos {

    void registrar(String customerId, String sessionId, RespostaInvestimentos resposta);

    /** Consulta de solicitacoes de credito (origem CREDITO); resumo montado em Java, sem codigos internos. */
    void registrarCredito(String customerId, String sessionId, String resumo);

    /** Mais recentes primeiro, excluindo a sessao atual. */
    List<Atendimento> recentesDeOutrasSessoes(String customerId, String sessionIdAtual, int limite);
}

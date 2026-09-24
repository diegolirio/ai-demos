package poc.a2a.ana.atendimento;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import poc.a2a.ana.investimentos.RespostaInvestimentos;

/** Dublê em memória do histórico (testes sem Postgres). */
public class HistoricoAtendimentosEmMemoria implements HistoricoAtendimentos {

    private record Registro(String customerId, String sessionId, Atendimento atendimento) {
    }

    private final List<Registro> registros = new ArrayList<>();

    @Override
    public synchronized void registrar(String customerId, String sessionId, RespostaInvestimentos resposta) {
        registros.add(new Registro(customerId, sessionId, new Atendimento(OffsetDateTime.now(),
                resposta.answerDraft(), resposta.confidence(), resposta.situacaoGarantia())));
    }

    @Override
    public synchronized List<Atendimento> recentesDeOutrasSessoes(String customerId, String sessionIdAtual, int limite) {
        return registros.reversed().stream()
                .filter(r -> r.customerId().equals(customerId) && !r.sessionId().equals(sessionIdAtual))
                .limit(limite)
                .map(Registro::atendimento)
                .toList();
    }
}

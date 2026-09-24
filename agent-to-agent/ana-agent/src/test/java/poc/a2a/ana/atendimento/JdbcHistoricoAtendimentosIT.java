package poc.a2a.ana.atendimento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import poc.a2a.ana.BaseIntegrationTest;
import poc.a2a.ana.investimentos.RespostaInvestimentos;
import poc.a2a.ana.investimentos.SituacaoGarantia;

/** Histórico de atendimentos no Postgres real (tabela criada pelo JdbcHistoricoAtendimentos no startup). */
class JdbcHistoricoAtendimentosIT extends BaseIntegrationTest {

    @Autowired
    HistoricoAtendimentos historico;

    static RespostaInvestimentos resposta(String resumo, SituacaoGarantia situacao) {
        return new RespostaInvestimentos(List.of("f"), resumo, 0.9, List.of(), List.of("cred-mcp"), situacao);
    }

    @Test
    void gravaELeDeOutrasSessoesMaisRecentesPrimeiro() throws Exception {
        SituacaoGarantia parcial = new SituacaoGarantia("RETIDO_PARCIAL", new BigDecimal("10000.00"),
                new BigDecimal("3500.00"), new BigDecimal("6500.00"), "Pagar a fatura");
        historico.registrar("cli-008", "sess-1", resposta("primeiro", parcial));
        historico.registrar("cli-008", "sess-2", resposta("segundo", null));
        historico.registrar("cli-008", "sess-atual", resposta("da sessao atual", null));
        historico.registrar("cli-001", "sess-9", resposta("outro cliente", null));

        List<Atendimento> anteriores = historico.recentesDeOutrasSessoes("cli-008", "sess-atual", 3);

        assertThat(anteriores).extracting(Atendimento::resumo).containsExactly("segundo", "primeiro");
        assertThat(anteriores.get(1).situacaoGarantia()).isEqualTo(parcial);
        assertThat(anteriores.get(0).situacaoGarantia()).isNull();
        assertThat(atendimentoRows("cli-008")).isEqualTo(3);
    }

    @Test
    void respeitaOLimite() {
        for (int i = 1; i <= 5; i++) {
            historico.registrar("cli-005", "sess-" + i, resposta("atendimento " + i, null));
        }

        assertThat(historico.recentesDeOutrasSessoes("cli-005", "nova", 3))
                .extracting(Atendimento::resumo).containsExactly("atendimento 5", "atendimento 4", "atendimento 3");
    }

    @Test
    void registraCreditoComOrigem() {
        historico.registrar("cli-011", "sess-1", resposta("investimentos", null));
        historico.registrarCredito("cli-011", "sess-2", "EMPRESTIMO_PESSOAL RECUSADA (conta recente)");

        List<Atendimento> anteriores = historico.recentesDeOutrasSessoes("cli-011", "nova", 3);

        assertThat(anteriores).extracting(Atendimento::origem).containsExactly(Origem.CREDITO, Origem.INVESTIMENTOS);
        assertThat(anteriores.getFirst().resumo()).isEqualTo("EMPRESTIMO_PESSOAL RECUSADA (conta recente)");
        assertThat(anteriores.getFirst().confidence()).isEqualTo(1.0);
        assertThat(anteriores.getFirst().situacaoGarantia()).isNull();
    }
}

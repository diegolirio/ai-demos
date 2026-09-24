package poc.a2a.ana.atendimento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import poc.a2a.ana.investimentos.SituacaoGarantia;

class FormatadorAtendimentosTest {

    @Test
    void semAtendimentos() {
        assertThat(FormatadorAtendimentos.formatar(List.of())).isEqualTo("nenhum");
    }

    @Test
    void umaLinhaPorAtendimentoNoHorarioDeBrasilia() {
        Atendimento comGarantia = new Atendimento(OffsetDateTime.of(2026, 9, 22, 17, 3, 0, 0, ZoneOffset.UTC),
                "Parte do resgate foi liberada.", 0.9,
                new SituacaoGarantia("RETIDO_PARCIAL", new BigDecimal("10000.00"), new BigDecimal("3500.00"),
                        new BigDecimal("6500.00"), "Pagar a fatura"));
        Atendimento semGarantia = new Atendimento(OffsetDateTime.of(2026, 9, 21, 12, 0, 0, 0, ZoneOffset.UTC),
                "Seu resgate esta em liquidacao.", 0.8, null);

        assertThat(FormatadorAtendimentos.formatar(List.of(comGarantia, semGarantia))).isEqualTo("""
                22/09 14:03 — Parte do resgate foi liberada. [garantia: RETIDO_PARCIAL, liberado 6500.00, retido 3500.00]
                21/09 09:00 — Seu resgate esta em liquidacao.""");
    }
}

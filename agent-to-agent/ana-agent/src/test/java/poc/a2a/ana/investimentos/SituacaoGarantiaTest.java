package poc.a2a.ana.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SituacaoGarantiaTest {

    @Test
    void mapaSemStatusVoltaNulo() {
        assertThat(SituacaoGarantia.deMapa(Map.of("valorLiberado", "1.00"))).isNull();
    }

    @Test
    void naoEhMapaVoltaNulo() {
        assertThat(SituacaoGarantia.deMapa("qualquer coisa")).isNull();
    }

    @Test
    void statusComValorFaltandoVoltaNulo() {
        assertThat(SituacaoGarantia.deMapa(Map.of("status", "RETIDO_PARCIAL",
                "valorResgatado", "10000.00", "valorRetido", "3500.00"))).isNull();
    }

    @Test
    void statusComValorNaoNumericoVoltaNulo() {
        assertThat(SituacaoGarantia.deMapa(Map.of("status", "RETIDO_PARCIAL",
                "valorResgatado", "abc", "valorRetido", "3500.00", "valorLiberado", "6500.00"))).isNull();
    }

    @Test
    void statusComValorNanVoltaNulo() {
        assertThat(SituacaoGarantia.deMapa(Map.of("status", "RETIDO_PARCIAL",
                "valorResgatado", Double.NaN, "valorRetido", "3500.00", "valorLiberado", "6500.00"))).isNull();
    }

    @Test
    void valoresComEscalaMaiorSaoArredondados() {
        SituacaoGarantia g = SituacaoGarantia.deMapa(Map.of("status", "RETIDO_PARCIAL",
                "valorResgatado", "10000.005", "valorRetido", "3500.004", "valorLiberado", "6500.006"));

        assertThat(g).isNotNull();
        assertThat(g.valorResgatado()).isEqualTo(new BigDecimal("10000.01"));
        assertThat(g.valorRetido()).isEqualTo(new BigDecimal("3500.00"));
        assertThat(g.valorLiberado()).isEqualTo(new BigDecimal("6500.01"));
    }

    @Test
    void restoDaRespostaContinuaParseadoQuandoGarantiaEhInvalida() {
        RespostaInvestimentos resposta = RespostaInvestimentos.deMapa(Map.of(
                "facts", List.of("f"), "answerDraft", "r", "confidence", 0.9, "risks", List.of(),
                "sources", List.of("cred-mcp"),
                "situacaoGarantia", Map.of("status", "RETIDO_PARCIAL", "valorResgatado", "nao-numerico")));

        assertThat(resposta.situacaoGarantia()).isNull();
        assertThat(resposta.answerDraft()).isEqualTo("r");
        assertThat(resposta.facts()).containsExactly("f");
    }
}

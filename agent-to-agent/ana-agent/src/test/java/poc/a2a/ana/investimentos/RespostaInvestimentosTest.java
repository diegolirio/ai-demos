package poc.a2a.ana.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RespostaInvestimentosTest {

    @Test
    void leSituacaoGarantiaDoDataPart() {
        RespostaInvestimentos resposta = RespostaInvestimentos.deMapa(Map.of(
                "facts", List.of("f"), "answerDraft", "r", "confidence", 0.9, "risks", List.of(),
                "sources", List.of("cred-mcp"),
                "situacaoGarantia", Map.of("status", "RETIDO_PARCIAL", "valorResgatado", 10000.0,
                        "valorRetido", 3500, "valorLiberado", "6500.00", "proximoPasso", "Pagar a fatura")));

        SituacaoGarantia situacao = resposta.situacaoGarantia();
        assertThat(situacao.status()).isEqualTo("RETIDO_PARCIAL");
        assertThat(situacao.valorResgatado()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(situacao.valorRetido()).isEqualTo(new BigDecimal("3500.00"));
        assertThat(situacao.valorLiberado()).isEqualTo(new BigDecimal("6500.00"));
        assertThat(resposta.paraTextoLlm()).contains("situacaoGarantia").contains("RETIDO_PARCIAL");
    }

    @Test
    void semSituacaoGarantiaFicaNula() {
        RespostaInvestimentos resposta = RespostaInvestimentos.deMapa(Map.of("answerDraft", "r", "confidence", 0.5));

        assertThat(resposta.situacaoGarantia()).isNull();
        assertThat(resposta.paraTextoLlm()).doesNotContain("situacaoGarantia");
    }
}

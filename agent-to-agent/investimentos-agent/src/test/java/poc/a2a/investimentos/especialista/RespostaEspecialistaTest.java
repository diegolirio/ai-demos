package poc.a2a.investimentos.especialista;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RespostaEspecialistaTest {

    static SituacaoGarantia parcial(String status, String retido, String liberado) {
        return new SituacaoGarantia(status, new BigDecimal("10000.00"), new BigDecimal(retido),
                new BigDecimal(liberado), "Pagar a fatura do cartao para liberar o restante");
    }

    static RespostaEspecialista com(SituacaoGarantia situacao) {
        return new RespostaEspecialista(List.of("fato"), "rascunho", 0.9, List.of(), List.of("cred-mcp"), situacao);
    }

    @Test
    void mantemSituacaoConsistente() {
        RespostaEspecialista resposta = com(parcial("RETIDO_PARCIAL", "3500.00", "6500.00")).validada();

        assertThat(resposta.situacaoGarantia().status()).isEqualTo("RETIDO_PARCIAL");
        assertThat(resposta.risks()).isEmpty();
    }

    @Test
    void descartaStatusDesconhecido() {
        RespostaEspecialista resposta = com(parcial("BLOQUEADO", "3500.00", "6500.00")).validada();

        assertThat(resposta.situacaoGarantia()).isNull();
        assertThat(resposta.risks()).containsExactly(RespostaEspecialista.RISCO_GARANTIA_DESCARTADA);
    }

    @Test
    void descartaSomaQueNaoFecha() {
        RespostaEspecialista resposta = com(parcial("RETIDO_PARCIAL", "3500.00", "7000.00")).validada();

        assertThat(resposta.situacaoGarantia()).isNull();
        assertThat(resposta.risks()).containsExactly(RespostaEspecialista.RISCO_GARANTIA_DESCARTADA);
    }

    @Test
    void semSituacaoNaoMudaNada() {
        RespostaEspecialista original = new RespostaEspecialista(List.of("f"), "r", 0.5, List.of("x"), List.of("cdb-mcp"));

        assertThat(original.validada()).isEqualTo(original);
        assertThat(original.comoMapa()).doesNotContainKey("situacaoGarantia");
    }

    @Test
    void mapaDoDataPartIncluiASituacao() {
        Map<String, Object> mapa = com(parcial("RETIDO_PARCIAL", "3500.00", "6500.00")).comoMapa();

        assertThat(mapa.get("situacaoGarantia")).isInstanceOfSatisfying(Map.class, s -> {
            assertThat(s.get("status")).isEqualTo("RETIDO_PARCIAL");
            assertThat(s.get("valorRetido")).isEqualTo(new BigDecimal("3500.00"));
        });
    }
}

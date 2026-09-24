package poc.a2a.ana.credito;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class SolicitacaoCreditoTest {

    static final LocalDateTime DATA = LocalDateTime.of(2026, 9, 20, 10, 30);

    static final SolicitacaoCredito APROVADA = new SolicitacaoCredito("sol-011a", "CARTAO_CREDITO", DATA,
            "APROVADA", new BigDecimal("3000.00"), null, null, "Cartao aprovado; chega em ate 10 dias uteis", null);

    static final SolicitacaoCredito RECUSADA = new SolicitacaoCredito("sol-011b", "EMPRESTIMO_PESSOAL", DATA,
            "RECUSADA", new BigDecimal("20000.00"), "RELACIONAMENTO_RECENTE",
            "Sua conta tem menos de 6 meses de relacionamento com o banco",
            "Uma nova analise pode ser feita depois da data de reavaliacao", LocalDate.of(2027, 1, 15));

    @Test
    void textoParaOLlmTrazMotivoDoClienteSemOCodigoInterno() {
        String texto = SolicitacaoCredito.paraTextoLlm(List.of(APROVADA, RECUSADA));

        assertThat(texto).isEqualTo("""
                solicitacoes de credito do cliente:
                - CARTAO_CREDITO APROVADA, solicitada em 20/09/2026, valor 3000.00; proximoPasso: Cartao aprovado; chega em ate 10 dias uteis
                - EMPRESTIMO_PESSOAL RECUSADA, solicitada em 20/09/2026, valor 20000.00; motivo: Sua conta tem menos de 6 meses de relacionamento com o banco; proximoPasso: Uma nova analise pode ser feita depois da data de reavaliacao; reavaliacaoApos: 15/01/2027""");
        assertThat(texto).doesNotContain("RELACIONAMENTO_RECENTE");
    }

    @Test
    void listaVaziaViraNenhuma() {
        assertThat(SolicitacaoCredito.paraTextoLlm(List.of())).isEqualTo(SolicitacaoCredito.NENHUMA)
                .startsWith("NENHUMA:");
    }

    @Test
    void resumoDoHistoricoSemCodigoInterno() {
        assertThat(SolicitacaoCredito.resumo(List.of(APROVADA, RECUSADA))).isEqualTo(
                "CARTAO_CREDITO APROVADA; EMPRESTIMO_PESSOAL RECUSADA "
                        + "(Sua conta tem menos de 6 meses de relacionamento com o banco)");
    }
}

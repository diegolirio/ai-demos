package poc.a2a.ana.atendimento;

import java.time.OffsetDateTime;

import poc.a2a.ana.investimentos.SituacaoGarantia;

/** Um atendimento anterior (delegacao ao especialista ou consulta de credito bem-sucedida). */
public record Atendimento(OffsetDateTime criadoEm, String resumo, double confidence,
                          SituacaoGarantia situacaoGarantia, Origem origem) {

    public Atendimento(OffsetDateTime criadoEm, String resumo, double confidence, SituacaoGarantia situacaoGarantia) {
        this(criadoEm, resumo, confidence, situacaoGarantia, Origem.INVESTIMENTOS);
    }
}

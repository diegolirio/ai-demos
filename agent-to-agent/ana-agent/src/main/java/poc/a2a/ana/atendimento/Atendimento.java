package poc.a2a.ana.atendimento;

import java.time.OffsetDateTime;

import poc.a2a.ana.investimentos.SituacaoGarantia;

/** Um atendimento anterior (uma delegacao bem-sucedida ao especialista). */
public record Atendimento(OffsetDateTime criadoEm, String resumo, double confidence,
                          SituacaoGarantia situacaoGarantia) {
}

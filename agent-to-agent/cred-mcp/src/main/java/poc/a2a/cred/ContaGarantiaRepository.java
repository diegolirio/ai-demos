package poc.a2a.cred;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Mock em memoria: resgates de investimento retidos em conta garantia por gastos no cartao de credito. */
@Component
public class ContaGarantiaRepository {

    public enum StatusGarantia { LIBERADO_CONTA, EM_ANALISE, RETIDO_ATE_PAGAMENTO_FATURA, RETIDO_PARCIAL }

    public record RetencaoGarantia(String resgateId, LocalDateTime dataEntrada, StatusGarantia status,
                                   BigDecimal valorResgatado, BigDecimal valorRetido, BigDecimal valorLiberado,
                                   BigDecimal gastoCartao, LocalDate vencimentoFatura, String detalhe) {
    }

    private static final BigDecimal DEZ_MIL = new BigDecimal("10000.00");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final LocalDateTime ENTRADA = LocalDateTime.of(2026, 9, 22, 9, 5);
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 10, 5);

    private final Map<String, List<RetencaoGarantia>> retencoes = Map.of(
            "cli-005", List.of(new RetencaoGarantia("res-005", ENTRADA, StatusGarantia.LIBERADO_CONTA,
                    DEZ_MIL, ZERO, DEZ_MIL, ZERO, null,
                    "Analise concluida sem pendencias no cartao; valor encaminhado para a conta corrente")),
            "cli-006", List.of(new RetencaoGarantia("res-006", ENTRADA, StatusGarantia.EM_ANALISE,
                    DEZ_MIL, DEZ_MIL, ZERO, new BigDecimal("4200.00"), null,
                    "Resgate em analise na conta garantia por gastos no cartao de credito; sem prazo definido")),
            "cli-007", List.of(new RetencaoGarantia("res-007", ENTRADA, StatusGarantia.RETIDO_ATE_PAGAMENTO_FATURA,
                    DEZ_MIL, DEZ_MIL, ZERO, new BigDecimal("12000.00"), VENCIMENTO,
                    "Valor retido como garantia da fatura do cartao; liberado apos o pagamento da fatura")),
            "cli-008", List.of(new RetencaoGarantia("res-008", ENTRADA, StatusGarantia.RETIDO_PARCIAL,
                    DEZ_MIL, new BigDecimal("3500.00"), new BigDecimal("6500.00"), new BigDecimal("3500.00"),
                    VENCIMENTO,
                    "R$ 3500.00 retidos como garantia da fatura do cartao; R$ 6500.00 liberados para a conta corrente")));

    public List<RetencaoGarantia> retencoes(String customerId) {
        return retencoes.getOrDefault(customerId, List.of());
    }
}

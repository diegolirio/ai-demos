package poc.a2a.trackingmoney;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Mock em memória: movimentações da conta corrente e status de transferências entre produtos. */
@Component
public class TrackingMoneyRepository {

    public enum TipoMovimentacao { CREDITO, DEBITO }

    public enum StatusMovimentacao { PROCESSANDO, CONCLUIDA }

    public record Movimentacao(String movimentacaoId, TipoMovimentacao tipo, BigDecimal valor, String descricao,
                               LocalDateTime dataHora, StatusMovimentacao status, String transferenciaId) {
    }

    public record StatusTransferencia(String transferenciaId, String status, String previsao, String detalhe) {
    }

    private final Map<String, List<Movimentacao>> movimentacoes = Map.of(
            "cli-001", List.of(new Movimentacao("mov-001", TipoMovimentacao.CREDITO, new BigDecimal("5000.00"),
                    "Resgate CDB res-001", LocalDateTime.of(2026, 9, 22, 10, 1),
                    StatusMovimentacao.PROCESSANDO, "trf-001")),
            "cli-002", List.of(new Movimentacao("mov-002", TipoMovimentacao.CREDITO, new BigDecimal("3000.00"),
                    "Resgate CDB res-002", LocalDateTime.of(2026, 9, 21, 9, 45),
                    StatusMovimentacao.CONCLUIDA, "trf-002")));

    private final Map<String, StatusTransferencia> transferencias = Map.of(
            "trf-001", new StatusTransferencia("trf-001", "EM_PROCESSAMENTO", "ate 30 minutos",
                    "Liquidacao do resgate em andamento; o credito sera efetuado na conta corrente"),
            "trf-002", new StatusTransferencia("trf-002", "CONCLUIDA", "ja efetuado",
                    "Credito efetuado na conta corrente"));

    public List<Movimentacao> movimentacoes(String customerId) {
        return movimentacoes.getOrDefault(customerId, List.of());
    }

    public Optional<StatusTransferencia> transferencia(String transferenciaId) {
        return Optional.ofNullable(transferencias.get(transferenciaId));
    }
}

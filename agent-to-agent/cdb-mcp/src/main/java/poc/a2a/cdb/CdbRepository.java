package poc.a2a.cdb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Mock em memória dos cenários da jornada "meu dinheiro sumiu". */
@Component
public class CdbRepository {

    public enum StatusResgate { SOLICITADO, EM_LIQUIDACAO, LIQUIDADO }

    public record PosicaoCdb(String posicaoId, String emissor, BigDecimal valorAplicado,
                             BigDecimal valorAtual, BigDecimal taxaPercentualCdi, LocalDate vencimento) {
    }

    public record ResgateCdb(String resgateId, String posicaoId, BigDecimal valor,
                             StatusResgate status, LocalDateTime dataSolicitacao) {
    }

    private static ResgateCdb liquidadoDezMil(int n) {
        return new ResgateCdb("res-00" + n, "pos-00" + n, new BigDecimal("10000.00"),
                StatusResgate.LIQUIDADO, LocalDateTime.of(2026, 9, 22, 9, 0));
    }

    private final Map<String, List<PosicaoCdb>> posicoes = Map.of(
            "cli-001", List.of(new PosicaoCdb("pos-001", "Banco Alfa S.A.", new BigDecimal("10000.00"),
                    new BigDecimal("5850.42"), new BigDecimal("110.0"), LocalDate.of(2028, 1, 15))),
            "cli-003", List.of(new PosicaoCdb("pos-003", "Banco Gama S.A.", new BigDecimal("8000.00"),
                    new BigDecimal("8420.10"), new BigDecimal("105.0"), LocalDate.of(2027, 6, 30))));

    private final Map<String, List<ResgateCdb>> resgates = Map.of(
            "cli-001", List.of(new ResgateCdb("res-001", "pos-001", new BigDecimal("5000.00"),
                    StatusResgate.EM_LIQUIDACAO, LocalDateTime.of(2026, 9, 22, 10, 0))),
            "cli-002", List.of(new ResgateCdb("res-002", "pos-002", new BigDecimal("3000.00"),
                    StatusResgate.LIQUIDADO, LocalDateTime.of(2026, 9, 21, 9, 30))),
            // cli-005..cli-008: resgate liquidado que passou pela conta garantia (ver cred-mcp)
            "cli-005", List.of(liquidadoDezMil(5)),
            "cli-006", List.of(liquidadoDezMil(6)),
            "cli-007", List.of(liquidadoDezMil(7)),
            "cli-008", List.of(liquidadoDezMil(8)));

    public List<PosicaoCdb> posicoes(String customerId) {
        return posicoes.getOrDefault(customerId, List.of());
    }

    public List<ResgateCdb> resgates(String customerId) {
        return resgates.getOrDefault(customerId, List.of());
    }
}

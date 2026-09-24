package poc.a2a.cred;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Mock em memoria: solicitacoes de emprestimo pessoal e de cartao de credito.
 * motivoCodigo e interno (politica de credito); motivoCliente e o texto aprovado para o cliente.
 */
@Component
public class SolicitacoesCreditoRepository {

    public enum TipoSolicitacao { EMPRESTIMO_PESSOAL, CARTAO_CREDITO }

    public enum StatusSolicitacao { APROVADA, RECUSADA, EM_ANALISE }

    public record SolicitacaoCredito(String solicitacaoId, TipoSolicitacao tipo, LocalDateTime dataSolicitacao,
                                     StatusSolicitacao status, BigDecimal valorSolicitado, String motivoCodigo,
                                     String motivoCliente, String proximoPasso, LocalDate reavaliacaoApos) {
    }

    private static final LocalDateTime DATA = LocalDateTime.of(2026, 9, 20, 10, 30);

    private final Map<String, List<SolicitacaoCredito>> solicitacoes = Map.of(
            "cli-009", List.of(new SolicitacaoCredito("sol-009", TipoSolicitacao.EMPRESTIMO_PESSOAL, DATA,
                    StatusSolicitacao.RECUSADA, new BigDecimal("30000.00"), "RENDA_INSUFICIENTE",
                    "A parcela do valor pedido compromete mais do que o permitido da renda informada",
                    "Simule um valor menor ou atualize sua renda no app", null)),
            "cli-010", List.of(new SolicitacaoCredito("sol-010", TipoSolicitacao.CARTAO_CREDITO, DATA,
                    StatusSolicitacao.RECUSADA, new BigDecimal("5000.00"), "RESTRICAO_CADASTRAL",
                    "Encontramos uma pendencia no seu CPF em orgaos de protecao ao credito",
                    "Regularize a pendencia e faca uma nova solicitacao", LocalDate.of(2026, 10, 23))),
            "cli-011", List.of(
                    new SolicitacaoCredito("sol-011a", TipoSolicitacao.CARTAO_CREDITO, DATA,
                            StatusSolicitacao.APROVADA, new BigDecimal("3000.00"), null, null,
                            "Cartao aprovado; chega em ate 10 dias uteis", null),
                    new SolicitacaoCredito("sol-011b", TipoSolicitacao.EMPRESTIMO_PESSOAL, DATA,
                            StatusSolicitacao.RECUSADA, new BigDecimal("20000.00"), "RELACIONAMENTO_RECENTE",
                            "Sua conta tem menos de 6 meses de relacionamento com o banco",
                            "Uma nova analise pode ser feita depois da data de reavaliacao",
                            LocalDate.of(2027, 1, 15))),
            "cli-012", List.of(new SolicitacaoCredito("sol-012", TipoSolicitacao.EMPRESTIMO_PESSOAL, DATA,
                    StatusSolicitacao.EM_ANALISE, new BigDecimal("15000.00"), null, null,
                    "Resposta em ate 2 dias uteis", null)));

    public List<SolicitacaoCredito> solicitacoes(String customerId) {
        return solicitacoes.getOrDefault(customerId, List.of());
    }
}

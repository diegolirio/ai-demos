package poc.a2a.ana.credito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Solicitacao de credito como o cred-mcp devolve (tipo/status como String: a Ana so repassa).
 * motivoCodigo e interno: nunca vai para o LLM nem para o historico, so para o debug.
 */
public record SolicitacaoCredito(String solicitacaoId, String tipo, LocalDateTime dataSolicitacao, String status,
                                 BigDecimal valorSolicitado, String motivoCodigo, String motivoCliente,
                                 String proximoPasso, LocalDate reavaliacaoApos) {

    public static final String NENHUMA =
            "NENHUMA: nenhuma solicitacao de emprestimo ou cartao encontrada para o cliente.";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Texto devolvido ao LLM da Ana como resultado da tool. */
    public static String paraTextoLlm(List<SolicitacaoCredito> solicitacoes) {
        if (solicitacoes.isEmpty()) {
            return NENHUMA;
        }
        return "solicitacoes de credito do cliente:\n" + solicitacoes.stream()
                .map(SolicitacaoCredito::linhaLlm).collect(Collectors.joining("\n"));
    }

    /** Resumo deterministico para ana.atendimento (lido nas proximas sessoes). */
    public static String resumo(List<SolicitacaoCredito> solicitacoes) {
        return solicitacoes.stream()
                .map(s -> s.motivoCliente() == null ? s.tipo() + " " + s.status()
                        : s.tipo() + " " + s.status() + " (" + s.motivoCliente() + ")")
                .collect(Collectors.joining("; "));
    }

    private String linhaLlm() {
        StringBuilder linha = new StringBuilder("- ").append(tipo).append(' ').append(status);
        if (dataSolicitacao != null) {
            linha.append(", solicitada em ").append(DATA.format(dataSolicitacao));
        }
        if (valorSolicitado != null) {
            linha.append(", valor ").append(valorSolicitado.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        if (motivoCliente != null) {
            linha.append("; motivo: ").append(motivoCliente);
        }
        if (proximoPasso != null) {
            linha.append("; proximoPasso: ").append(proximoPasso);
        }
        if (reavaliacaoApos != null) {
            linha.append("; reavaliacaoApos: ").append(DATA.format(reavaliacaoApos));
        }
        return linha.toString();
    }
}

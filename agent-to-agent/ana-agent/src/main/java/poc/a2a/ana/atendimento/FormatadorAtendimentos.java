package poc.a2a.ana.atendimento;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Texto curto dos atendimentos anteriores para o system prompt da Ana. */
public final class FormatadorAtendimentos {

    /** Usado quando nao ha atendimentos anteriores (ChatController tambem usa este valor). */
    public static final String NENHUM = "nenhum";

    /** resumo vem do especialista (influenciado por texto do usuario): nunca deve virar instrucao no prompt. */
    private static final int LIMITE_RESUMO = 240;

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private FormatadorAtendimentos() {
    }

    public static String formatar(List<Atendimento> atendimentos) {
        if (atendimentos.isEmpty()) {
            return NENHUM;
        }
        return atendimentos.stream().map(FormatadorAtendimentos::linha).collect(Collectors.joining("\n"));
    }

    private static String linha(Atendimento a) {
        String prefixo = a.origem() == Origem.CREDITO ? "[credito] " : "";
        String linha = DATA_HORA.format(a.criadoEm().atZoneSameInstant(BRASILIA)) + " — " + prefixo
                + resumoDeUmaLinha(a.resumo());
        var g = a.situacaoGarantia();
        return g == null ? linha : linha + " [garantia: %s, liberado %s, retido %s]".formatted(
                g.status(), valorOuInterrogacao(g.valorLiberado()), valorOuInterrogacao(g.valorRetido()));
    }

    /** Colapsa quebras de linha/espacos em um so espaco e limita o tamanho: o texto vira uma unica linha do historico. */
    private static String resumoDeUmaLinha(String resumo) {
        String colapsado = resumo.replaceAll("\\s+", " ").trim();
        return colapsado.length() > LIMITE_RESUMO ? colapsado.substring(0, LIMITE_RESUMO) + "..." : colapsado;
    }

    /** Linhas do JDBC podem trazer colunas de garantia parciais (partial garantia columns). */
    private static String valorOuInterrogacao(BigDecimal valor) {
        return valor == null ? "?" : valor.toPlainString();
    }
}

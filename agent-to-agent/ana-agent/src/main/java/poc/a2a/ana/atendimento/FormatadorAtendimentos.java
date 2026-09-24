package poc.a2a.ana.atendimento;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Texto curto dos atendimentos anteriores para o system prompt da Ana. */
public final class FormatadorAtendimentos {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private FormatadorAtendimentos() {
    }

    public static String formatar(List<Atendimento> atendimentos) {
        if (atendimentos.isEmpty()) {
            return "nenhum";
        }
        return atendimentos.stream().map(FormatadorAtendimentos::linha).collect(Collectors.joining("\n"));
    }

    private static String linha(Atendimento a) {
        String linha = DATA_HORA.format(a.criadoEm().atZoneSameInstant(BRASILIA)) + " — " + a.resumo();
        var g = a.situacaoGarantia();
        return g == null ? linha : linha + " [garantia: %s, liberado %s, retido %s]".formatted(
                g.status(), g.valorLiberado().toPlainString(), g.valorRetido().toPlainString());
    }
}

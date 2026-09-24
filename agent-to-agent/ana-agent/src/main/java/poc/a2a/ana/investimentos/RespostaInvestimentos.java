package poc.a2a.ana.investimentos;

import java.util.List;
import java.util.Map;

/** Schema padrão de resposta de especialista (guideline §9), como recebido no DataPart A2A. */
public record RespostaInvestimentos(List<String> facts, String answerDraft, double confidence,
                                    List<String> risks, List<String> sources, SituacaoGarantia situacaoGarantia) {

    public RespostaInvestimentos(List<String> facts, String answerDraft, double confidence,
                                 List<String> risks, List<String> sources) {
        this(facts, answerDraft, confidence, risks, sources, null);
    }

    public static RespostaInvestimentos deMapa(Map<?, ?> mapa) {
        return new RespostaInvestimentos(
                lista(mapa.get("facts")),
                mapa.get("answerDraft") == null ? "" : mapa.get("answerDraft").toString(),
                mapa.get("confidence") instanceof Number n ? n.doubleValue() : 0.0,
                lista(mapa.get("risks")),
                lista(mapa.get("sources")),
                SituacaoGarantia.deMapa(mapa.get("situacaoGarantia")));
    }

    private static List<String> lista(Object valor) {
        return valor instanceof List<?> itens ? itens.stream().map(String::valueOf).toList() : List.of();
    }

    /** Texto devolvido ao LLM da Ana como resultado da tool. */
    public String paraTextoLlm() {
        String texto = "answerDraft: " + answerDraft
                + "\nfacts: " + facts
                + "\nconfidence: " + confidence
                + "\nrisks: " + risks
                + "\nsources: " + sources;
        return situacaoGarantia == null ? texto : texto + "\nsituacaoGarantia: " + situacaoGarantia;
    }
}

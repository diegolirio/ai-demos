package poc.a2a.investimentos.especialista;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema padrão de resposta de especialista (guideline §9) + situacaoGarantia opcional. */
public record RespostaEspecialista(List<String> facts, String answerDraft, double confidence,
                                   List<String> risks, List<String> sources, SituacaoGarantia situacaoGarantia) {

    public static final String RISCO_GARANTIA_DESCARTADA = "situacaoGarantia inconsistente descartada";

    public RespostaEspecialista(List<String> facts, String answerDraft, double confidence,
                                List<String> risks, List<String> sources) {
        this(facts, answerDraft, confidence, risks, sources, null);
    }

    /** Descarta situacaoGarantia inconsistente (status fora do enum ou soma que nao fecha) e registra em risks. */
    public RespostaEspecialista validada() {
        if (situacaoGarantia == null || situacaoGarantia.consistente()) {
            return this;
        }
        List<String> novosRisks = new ArrayList<>(risks == null ? List.of() : risks);
        novosRisks.add(RISCO_GARANTIA_DESCARTADA);
        return new RespostaEspecialista(facts, answerDraft, confidence, List.copyOf(novosRisks), sources, null);
    }

    /** Formato do DataPart A2A. */
    public Map<String, Object> comoMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("facts", facts == null ? List.of() : facts);
        mapa.put("answerDraft", answerDraft);
        mapa.put("confidence", confidence);
        mapa.put("risks", risks == null ? List.of() : risks);
        mapa.put("sources", sources == null ? List.of() : sources);
        if (situacaoGarantia != null) {
            mapa.put("situacaoGarantia", situacaoGarantia.comoMapa());
        }
        return mapa;
    }
}

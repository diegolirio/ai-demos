package poc.a2a.investimentos.especialista;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema padrão de resposta de especialista (guideline §9). */
public record RespostaEspecialista(List<String> facts, String answerDraft, double confidence,
                                   List<String> risks, List<String> sources) {

    /** Formato do DataPart A2A. */
    public Map<String, Object> comoMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("facts", facts == null ? List.of() : facts);
        mapa.put("answerDraft", answerDraft);
        mapa.put("confidence", confidence);
        mapa.put("risks", risks == null ? List.of() : risks);
        mapa.put("sources", sources == null ? List.of() : sources);
        return mapa;
    }
}

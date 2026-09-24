package poc.a2a.investimentos.especialista;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Onde esta o dinheiro de um resgate retido em conta garantia (preenchido pelo LLM a partir do cred-mcp). */
public record SituacaoGarantia(String status, BigDecimal valorResgatado, BigDecimal valorRetido,
                               BigDecimal valorLiberado, String proximoPasso) {

    static final Set<String> STATUS_VALIDOS =
            Set.of("LIBERADO_CONTA", "EM_ANALISE", "RETIDO_ATE_PAGAMENTO_FATURA", "RETIDO_PARCIAL");

    /** Status conhecido e retido + liberado = resgatado. O LLM pode errar valores; o executor descarta se falhar. */
    public boolean consistente() {
        return status != null && STATUS_VALIDOS.contains(status)
                && valorResgatado != null && valorRetido != null && valorLiberado != null
                && valorRetido.add(valorLiberado).compareTo(valorResgatado) == 0;
    }

    public Map<String, Object> comoMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("status", status);
        mapa.put("valorResgatado", valorResgatado);
        mapa.put("valorRetido", valorRetido);
        mapa.put("valorLiberado", valorLiberado);
        mapa.put("proximoPasso", proximoPasso == null ? "" : proximoPasso);
        return mapa;
    }
}

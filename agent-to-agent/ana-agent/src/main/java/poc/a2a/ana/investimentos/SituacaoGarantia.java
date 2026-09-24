package poc.a2a.ana.investimentos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Retencao em conta garantia informada pelo especialista (DataPart §9, campo opcional). */
public record SituacaoGarantia(String status, BigDecimal valorResgatado, BigDecimal valorRetido,
                               BigDecimal valorLiberado, String proximoPasso) {

    static SituacaoGarantia deMapa(Object valor) {
        if (!(valor instanceof Map<?, ?> mapa) || mapa.get("status") == null) {
            return null;
        }
        return new SituacaoGarantia(mapa.get("status").toString(), decimal(mapa.get("valorResgatado")),
                decimal(mapa.get("valorRetido")), decimal(mapa.get("valorLiberado")),
                mapa.get("proximoPasso") == null ? "" : mapa.get("proximoPasso").toString());
    }

    /** O DataPart pode trazer Double, Integer, BigDecimal ou String: normaliza para 2 casas. */
    private static BigDecimal decimal(Object valor) {
        if (valor == null) {
            return null;
        }
        return new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}

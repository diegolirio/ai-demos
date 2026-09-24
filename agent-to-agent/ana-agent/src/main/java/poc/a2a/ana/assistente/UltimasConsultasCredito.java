package poc.a2a.ana.assistente;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import poc.a2a.ana.credito.SolicitacaoCredito;

/** Ultima consulta de credito por requisicao (requestId), para o campo "credito" do modo debug do /chat. */
@Component
public class UltimasConsultasCredito {

    private final Map<String, List<SolicitacaoCredito>> porRequisicao = new ConcurrentHashMap<>();

    public void registrar(String requestId, List<SolicitacaoCredito> solicitacoes) {
        porRequisicao.put(requestId, solicitacoes);
    }

    public List<SolicitacaoCredito> remover(String requestId) {
        return porRequisicao.remove(requestId);
    }
}

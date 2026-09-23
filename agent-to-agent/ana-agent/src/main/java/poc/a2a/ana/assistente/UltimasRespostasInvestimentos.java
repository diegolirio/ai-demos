package poc.a2a.ana.assistente;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import poc.a2a.ana.investimentos.RespostaInvestimentos;

/**
 * Guarda a última resposta do especialista por requisição (não por sessão), para o modo debug do /chat.
 * Chaveado por requestId para que duas requisições /chat concorrentes na mesma sessão não
 * roubem/limpem o payload de debug uma da outra.
 */
@Component
public class UltimasRespostasInvestimentos {

    private final Map<String, RespostaInvestimentos> porRequisicao = new ConcurrentHashMap<>();

    public void registrar(String requestId, RespostaInvestimentos resposta) {
        porRequisicao.put(requestId, resposta);
    }

    public RespostaInvestimentos remover(String requestId) {
        return porRequisicao.remove(requestId);
    }
}

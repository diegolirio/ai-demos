package poc.a2a.ana.chat;

import java.util.UUID;

import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import poc.a2a.ana.assistente.AnaAssistant;
import poc.a2a.ana.assistente.DelegacaoInvestimentosTool;
import poc.a2a.ana.assistente.UltimasRespostasInvestimentos;
import poc.a2a.ana.investimentos.RespostaInvestimentos;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AnaAssistant ana;
    private final UltimasRespostasInvestimentos ultimas;

    public ChatController(AnaAssistant ana, UltimasRespostasInvestimentos ultimas) {
        this.ana = ana;
        this.ultimas = ultimas;
    }

    @PostMapping("/chat")
    public ChatResposta chat(@RequestBody ChatRequisicao requisicao,
                             @RequestParam(defaultValue = "false") boolean debug) {
        if (vazio(requisicao.sessionId()) || vazio(requisicao.customerId()) || vazio(requisicao.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId, customerId e message sao obrigatorios");
        }
        long inicio = System.nanoTime();
        String requestId = UUID.randomUUID().toString();

        InvocationParameters parametros = new InvocationParameters();
        parametros.put(DelegacaoInvestimentosTool.SESSION_ID, requisicao.sessionId());
        parametros.put(DelegacaoInvestimentosTool.CUSTOMER_ID, requisicao.customerId());
        parametros.put(DelegacaoInvestimentosTool.REQUEST_ID, requestId);
        String reply;
        RespostaInvestimentos respostaEspecialista;
        try {
            reply = ana.conversar(requisicao.sessionId(), requisicao.message(), parametros);
        } finally {
            respostaEspecialista = ultimas.remover(requestId);
        }
        log.info("ana.chat sessionId={} customerId={} delegou={} durationMs={}", requisicao.sessionId(),
                requisicao.customerId(), respostaEspecialista != null, (System.nanoTime() - inicio) / 1_000_000);
        return new ChatResposta(requisicao.sessionId(), reply, debug ? respostaEspecialista : null);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}

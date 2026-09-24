package poc.a2a.ana.chat;

import java.util.Map;
import java.util.UUID;

import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import poc.a2a.ana.assistente.AnaAssistant;
import poc.a2a.ana.assistente.DelegacaoInvestimentosTool;
import poc.a2a.ana.assistente.UltimasRespostasInvestimentos;
import poc.a2a.ana.atendimento.FormatadorAtendimentos;
import poc.a2a.ana.atendimento.HistoricoAtendimentos;
import poc.a2a.ana.cliente.CadastroClientes;
import poc.a2a.ana.cliente.Cpf;
import poc.a2a.ana.investimentos.RespostaInvestimentos;

@RestController
public class ChatController {

    static final int LIMITE_ATENDIMENTOS = 3;

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AnaAssistant ana;
    private final UltimasRespostasInvestimentos ultimas;
    private final CadastroClientes cadastro;
    private final HistoricoAtendimentos historico;

    public ChatController(AnaAssistant ana, UltimasRespostasInvestimentos ultimas, CadastroClientes cadastro,
                          HistoricoAtendimentos historico) {
        this.ana = ana;
        this.ultimas = ultimas;
        this.cadastro = cadastro;
        this.historico = historico;
    }

    @PostMapping("/chat")
    public ChatResposta chat(@RequestBody ChatRequisicao requisicao,
                             @RequestParam(defaultValue = "false") boolean debug) {
        if (vazio(requisicao.sessionId()) || vazio(requisicao.cpf()) || vazio(requisicao.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId, cpf e message sao obrigatorios");
        }
        Cpf cpf = Cpf.de(requisicao.cpf())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF invalido"));
        String customerId = cadastro.customerId(cpf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "cliente nao encontrado"));
        long inicio = System.nanoTime();
        String requestId = UUID.randomUUID().toString();

        InvocationParameters parametros = new InvocationParameters();
        parametros.put(DelegacaoInvestimentosTool.SESSION_ID, requisicao.sessionId());
        parametros.put(DelegacaoInvestimentosTool.CUSTOMER_ID, customerId);
        parametros.put(DelegacaoInvestimentosTool.REQUEST_ID, requestId);
        String anteriores = atendimentosAnteriores(customerId, requisicao.sessionId());
        String reply;
        RespostaInvestimentos respostaEspecialista;
        try {
            reply = ana.conversar(requisicao.sessionId(), requisicao.message(), anteriores, parametros);
        } finally {
            respostaEspecialista = ultimas.remover(requestId);
        }
        log.info("ana.chat sessionId={} cpf={} customerId={} comHistorico={} delegou={} durationMs={}",
                requisicao.sessionId(), cpf.mascarado(), customerId, !"nenhum".equals(anteriores),
                respostaEspecialista != null, (System.nanoTime() - inicio) / 1_000_000);
        return new ChatResposta(requisicao.sessionId(), reply, debug ? respostaEspecialista : null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> erro(ResponseStatusException e) {
        String motivo = e.getReason() == null ? "requisicao invalida" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", motivo));
    }

    /** Historico e acessorio: se o banco falhar, a Ana atende sem ele. */
    private String atendimentosAnteriores(String customerId, String sessionId) {
        try {
            return FormatadorAtendimentos.formatar(
                    historico.recentesDeOutrasSessoes(customerId, sessionId, LIMITE_ATENDIMENTOS));
        } catch (RuntimeException e) {
            log.warn("ana.atendimento.leitura.falhou sessionId={} customerId={} erro={}", sessionId, customerId,
                    e.toString());
            return "nenhum";
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}

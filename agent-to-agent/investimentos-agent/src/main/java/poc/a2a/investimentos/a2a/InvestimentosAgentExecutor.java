package poc.a2a.investimentos.a2a;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import poc.a2a.investimentos.especialista.EspecialistaInvestimentos;
import poc.a2a.investimentos.especialista.RespostaEspecialista;

/** Recebe a Task A2A e delega ao especialista (LLM + tools MCP). Etapas internas são invisíveis ao supervisor. */
@Component
public class InvestimentosAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(InvestimentosAgentExecutor.class);

    private final EspecialistaInvestimentos especialista;

    public InvestimentosAgentExecutor(EspecialistaInvestimentos especialista) {
        this.especialista = especialista;
    }

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        long inicio = System.nanoTime();
        String customerId = extrairCustomerId(context.getMessage());
        String pedido = "customerId: %s%nPedido: %s".formatted(customerId, context.getUserInput());
        log.info("a2a.task.start contextId={} taskId={} customerId={}",
                context.getContextId(), context.getTaskId(), customerId);
        try {
            RespostaEspecialista resposta = especialista.investigar(context.getContextId(), pedido);
            List<Part<?>> parts = List.of(new TextPart(resposta.answerDraft()), new DataPart(resposta.comoMapa()));
            emitter.addArtifact(parts, null, "resposta-investimentos", null);
            emitter.complete();
            log.info("a2a.task.completed contextId={} taskId={} confidence={} sources={} durationMs={}",
                    context.getContextId(), context.getTaskId(), resposta.confidence(), resposta.sources(),
                    (System.nanoTime() - inicio) / 1_000_000);
        } catch (RuntimeException e) {
            log.error("a2a.task.failed contextId={} taskId={} durationMs={}",
                    context.getContextId(), context.getTaskId(), (System.nanoTime() - inicio) / 1_000_000, e);
            emitter.fail();
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        throw new UnsupportedOperationError();
    }

    private static String extrairCustomerId(Message message) {
        if (message != null) {
            for (Part<?> part : message.parts()) {
                if (part instanceof DataPart dataPart && dataPart.data() instanceof Map<?, ?> dados
                        && dados.get("customerId") != null) {
                    return dados.get("customerId").toString();
                }
            }
        }
        return "nao-informado";
    }
}

package poc.a2a.ana.assistente;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Supervisor. InvocationParameters leva sessionId/customerId até a tool sem passar pelo LLM.
 * atendimentosAnteriores (outras sessões do mesmo cliente) entra no system prompt de forma determinística.
 */
public interface AnaAssistant {

    @SystemMessage(fromResource = "/prompts/ana-system.txt")
    String conversar(@MemoryId String sessionId, @UserMessage String mensagem,
                     @V("atendimentosAnteriores") String atendimentosAnteriores, InvocationParameters parametros);
}

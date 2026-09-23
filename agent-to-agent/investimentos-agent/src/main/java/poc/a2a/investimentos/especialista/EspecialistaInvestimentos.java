package poc.a2a.investimentos.especialista;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** AI Service do especialista (implementado pelo LangChain4j na Task 4). A memória é por contextId A2A. */
public interface EspecialistaInvestimentos {

    @SystemMessage(fromResource = "/prompts/especialista-investimentos.txt")
    RespostaEspecialista investigar(@MemoryId String contextId, @UserMessage String pedido);
}

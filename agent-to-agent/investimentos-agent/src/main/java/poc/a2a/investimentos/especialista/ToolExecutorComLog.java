package poc.a2a.investimentos.especialista;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decora o {@link ToolExecutor} de uma tool para logar cada chamada (spec &sect;5: "log por hop com
 * contextId, taskId, tool chamada e duracao"). O memoryId do AiService é o contextId A2A (ver
 * {@link EspecialistaInvestimentos}). Erros de execução são relançados sem alteração: o tratamento
 * padrão do LangChain4j (devolver o erro ao LLM como texto) continua se aplicando.
 */
final class ToolExecutorComLog implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorComLog.class);

    private final String nomeTool;
    private final ToolExecutor delegate;

    ToolExecutorComLog(String nomeTool, ToolExecutor delegate) {
        this.nomeTool = nomeTool;
        this.delegate = delegate;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        long inicio = System.nanoTime();
        boolean erro = false;
        try {
            return delegate.execute(request, memoryId);
        } catch (RuntimeException e) {
            erro = true;
            throw e;
        } finally {
            registrar(memoryId, inicio, erro);
        }
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        Object memoryId = context == null ? null : context.chatMemoryId();
        long inicio = System.nanoTime();
        boolean erro = false;
        try {
            return delegate.executeWithContext(request, context);
        } catch (RuntimeException e) {
            erro = true;
            throw e;
        } finally {
            registrar(memoryId, inicio, erro);
        }
    }

    private void registrar(Object memoryId, long inicioNanos, boolean erro) {
        long duracaoMs = (System.nanoTime() - inicioNanos) / 1_000_000;
        log.info("especialista.tool.call tool={} memoryId={} durationMs={} erro={}",
                nomeTool, memoryId, duracaoMs, erro);
    }
}

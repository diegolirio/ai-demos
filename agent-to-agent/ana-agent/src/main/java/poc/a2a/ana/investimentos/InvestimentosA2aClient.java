package poc.a2a.ana.investimentos;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cliente A2A (a2a-java) do Especialista de Investimentos.
 * O Agent Card é resolvido a cada chamada: a Ana sobe mesmo com o especialista fora e sempre usa o card atual.
 */
public class InvestimentosA2aClient implements InvestimentosClient, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InvestimentosA2aClient.class);

    private final String serverUrl;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final A2AHttpClient httpClient = A2AHttpClientFactory.create();

    public InvestimentosA2aClient(String serverUrl, Duration timeout) {
        this.serverUrl = serverUrl;
        this.timeout = timeout;
    }

    @Override
    public RespostaInvestimentos delegar(String contextId, String customerId, String pedido) {
        long inicio = System.nanoTime();
        Future<Task> futuro = executor.submit(() -> enviar(contextId, customerId, pedido));
        try {
            Task task = futuro.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            TaskState estado = task.status().state();
            log.info("a2a.delegacao contextId={} customerId={} taskId={} state={} durationMs={}",
                    contextId, customerId, task.id(), estado, (System.nanoTime() - inicio) / 1_000_000);
            if (estado != TaskState.TASK_STATE_COMPLETED) {
                throw new InvestimentosIndisponivelException("Task A2A terminou em " + estado);
            }
            return extrairResposta(task);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            log.warn("a2a.delegacao.timeout contextId={} timeout={}", contextId, timeout);
            throw new InvestimentosIndisponivelException("Timeout de " + timeout + " na delegacao A2A", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            futuro.cancel(true);
            throw new InvestimentosIndisponivelException("Delegacao A2A interrompida", e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() instanceof CompletionException ce && ce.getCause() != null
                    ? ce.getCause() : e.getCause();
            log.warn("a2a.delegacao.erro contextId={} erro={}", contextId, causa.toString());
            throw new InvestimentosIndisponivelException("Falha na delegacao A2A: " + causa.getMessage(), causa);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private Task enviar(String contextId, String customerId, String pedido) {
        try {
            AgentCard card = A2ACardResolver.builder()
                    .httpClient(httpClient)
                    .baseUrl(serverUrl)
                    .build()
                    .getAgentCard();

            CompletableFuture<Object> resultado = new CompletableFuture<>();
            try (Client client = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setStreaming(false).build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder().httpClient(httpClient))
                    .addConsumer((evento, agentCard) -> {
                        if (evento instanceof TaskEvent taskEvent) {
                            resultado.complete(taskEvent.getTask());
                        } else if (evento instanceof MessageEvent messageEvent) {
                            resultado.complete(messageEvent.getMessage());
                        }
                    })
                    .streamingErrorHandler(resultado::completeExceptionally)
                    .build()) {

                Message mensagem = Message.builder()
                        .role(Message.Role.ROLE_USER)
                        .messageId(UUID.randomUUID().toString())
                        .contextId(contextId)
                        .parts(List.<Part<?>>of(new TextPart(pedido), new DataPart(Map.of("customerId", customerId))))
                        .build();

                client.sendMessage(mensagem); // não-streaming: os consumers rodam antes do retorno
                // sem limite próprio: o timeout do cliente (delegar) + cancel(true) do Future já limitam a espera
                Object saida = resultado.get();
                if (saida instanceof Task task) {
                    return task;
                }
                throw new IllegalStateException("Resposta A2A inesperada: " + saida.getClass().getSimpleName());
            }
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private static RespostaInvestimentos extrairResposta(Task task) {
        for (Artifact artifact : task.artifacts()) {
            for (Part<?> part : artifact.parts()) {
                if (part instanceof DataPart dataPart && dataPart.data() instanceof Map<?, ?> dados) {
                    return RespostaInvestimentos.deMapa(dados);
                }
            }
        }
        throw new InvestimentosIndisponivelException("Task " + task.id() + " sem DataPart no schema padrao");
    }
}

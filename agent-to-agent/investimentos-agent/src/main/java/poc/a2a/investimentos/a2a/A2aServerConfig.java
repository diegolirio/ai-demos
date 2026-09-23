package poc.a2a.investimentos.a2a;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandlerTimeouts;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.DefaultPushNotificationUrlValidator;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring manual (sem CDI) do servidor a2a-java.
 *
 * Só objetos sem campos @Inject viram beans Spring: o Spring honra @Inject/@PostConstruct do jakarta.
 * DefaultRequestHandler e InMemoryPushNotificationConfigStore têm campos @Inject e são criados como
 * objetos comuns dentro dos métodos de fábrica.
 */
@Configuration(proxyBeanMethods = false)
public class A2aServerConfig {

    @Bean
    AgentCard agentCard(@Value("${a2a.public-url}") String publicUrl) {
        return AgentCard.builder()
                .name("investimentos-agent")
                .description("Especialista de investimentos: localiza dinheiro do cliente em investimentos (CDB), "
                        + "verifica resgates, liquidacao e credito em conta. Responde no schema padrao "
                        + "(facts, answerDraft, confidence, risks, sources) em um DataPart.")
                .version("1.0.0")
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), publicUrl)))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text", "application/json"))
                .defaultOutputModes(List.of("text", "application/json"))
                .skills(List.of(AgentSkill.builder()
                        .id("localizar-dinheiro-investimentos")
                        .name("Localizar dinheiro em investimentos")
                        .description("Descobre onde esta o dinheiro que o cliente tinha em investimentos: "
                                + "aplicado, em liquidacao de resgate ou ja creditado na conta. "
                                + "Envie a intencao em texto e um DataPart {\"customerId\": \"...\"}.")
                        .tags(List.of("investimentos", "cdb", "resgate", "liquidacao"))
                        .examples(List.of("Cliente nao encontra o dinheiro que estava em investimentos"))
                        .build()))
                .build();
    }

    @Bean
    MainEventBus mainEventBus() {
        return new MainEventBus();
    }

    @Bean
    InMemoryTaskStore taskStore() {
        return new InMemoryTaskStore();
    }

    @Bean
    InMemoryQueueManager queueManager(InMemoryTaskStore taskStore, MainEventBus mainEventBus) {
        return new InMemoryQueueManager(taskStore, mainEventBus);
    }

    /**
     * Bean Spring de propósito: o Spring chama o @PostConstruct start() (thread "MainEventBusProcessor")
     * e o @PreDestroy stop(), substituindo o MainEventBusProcessorInitializer do CDI.
     * Sem essa thread o SendMessage fica pendurado até o timeout.
     */
    @Bean
    MainEventBusProcessor mainEventBusProcessor(MainEventBus bus, InMemoryTaskStore taskStore,
                                                InMemoryQueueManager queueManager) {
        var pushConfigStore = new InMemoryPushNotificationConfigStore();
        var pushSender = new BasePushNotificationSender(pushConfigStore, new DefaultPushNotificationUrlValidator());
        return new MainEventBusProcessor(bus, taskStore, pushSender, queueManager);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService a2aInternalExecutor() {
        // core = max = 50: com ArrayBlockingQueue o ThreadPoolExecutor só cria threads além do core quando a fila
        // enche, entao core menor que max deixava a concorrencia real presa em 5 ate 100 tarefas enfileiradas.
        ThreadPoolExecutor executor = new ThreadPoolExecutor(50, 50, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), Thread.ofPlatform().name("a2a-agent-", 0).daemon().factory());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService a2aEventConsumerExecutor() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 10, TimeUnit.SECONDS,
                new SynchronousQueue<>(), Thread.ofPlatform().name("a2a-event-consumer-", 0).daemon().factory());
    }

    @Bean
    JSONRPCHandler jsonRpcHandler(AgentCard agentCard, AgentExecutor agentExecutor, InMemoryTaskStore taskStore,
                                  InMemoryQueueManager queueManager, MainEventBusProcessor mainEventBusProcessor,
                                  ExecutorService a2aInternalExecutor, ExecutorService a2aEventConsumerExecutor,
                                  @Value("${a2a.agent-timeout-seconds}") int agentTimeoutSeconds) {
        DefaultRequestHandler requestHandler = DefaultRequestHandler.builder()
                .agentExecutor(agentExecutor)
                .taskStore(taskStore)
                .queueManager(queueManager)
                .pushConfigStore(null)
                .pushNotificationsEnabled(false)
                .mainEventBusProcessor(mainEventBusProcessor)
                .executor(a2aInternalExecutor)
                .eventConsumerExecutor(a2aEventConsumerExecutor)
                // default do builder é true: sem TaskAuthorizationProvider todo SendMessage falha com TaskNotFoundError
                .authorizationRequired(false)
                .build();
        RequestHandlerTimeouts.configurar(requestHandler, agentTimeoutSeconds);
        return new JSONRPCHandler(agentCard, requestHandler, a2aInternalExecutor);
    }
}

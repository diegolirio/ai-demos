package poc.a2a.investimentos.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import poc.a2a.investimentos.especialista.EspecialistaInvestimentos;
import poc.a2a.investimentos.especialista.RespostaEspecialista;

/**
 * Teste de contrato: cliente REAL a2a-java (mesmo usado pela Ana, ver {@code InvestimentosA2aClient})
 * contra o servidor REAL Spring MVC deste projeto, resolvendo o Agent Card e enviando uma mensagem
 * via SendMessage não-streaming.
 *
 * <p>O Agent Card anuncia a URL de {@code a2a.public-url} (default {@code http://localhost:8081}). Como
 * este teste sobe em porta aleatória ({@code @SpringBootTest(RANDOM_PORT)}), essa URL não bate com a
 * porta real: a porta só é conhecida depois que o container embutido sobe, o que acontece em
 * {@code finishRefresh()} — depois que o bean {@code agentCard} (singleton comum) já foi criado. Não dá,
 * portanto, para injetar {@code local.server.port} em {@code a2a.public-url} via propriedade/
 * {@code @DynamicPropertySource}. A solução mais simples: resolve o card normalmente e reconstrói só a
 * URL da interface anunciada com a porta real de teste antes de montar o {@link Client}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class A2aClienteRealTest {

    @TestConfiguration
    static class EspecialistaFake {
        @Bean
        EspecialistaInvestimentos especialistaInvestimentos() {
            return (contextId, pedido) -> new RespostaEspecialista(
                    List.of("Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO"),
                    "Seu resgate esta em liquidacao e cai na conta em alguns minutos.",
                    0.9, List.of(), List.of("cdb-mcp"));
        }
    }

    @LocalServerPort
    int port;

    @Test
    void clienteRealResolveCardEnviaMensagemERecebeTaskCompletaNoSchemaNove() throws Exception {
        String baseUrl = "http://localhost:" + port;

        AgentCard cardAnunciado = A2ACardResolver.builder()
                .httpClient(A2AHttpClientFactory.create())
                .baseUrl(baseUrl)
                .build()
                .getAgentCard();

        AgentInterface interfaceAnunciada = cardAnunciado.supportedInterfaces().get(0);
        AgentCard card = AgentCard.builder(cardAnunciado)
                .supportedInterfaces(List.of(new AgentInterface(interfaceAnunciada.protocolBinding(), baseUrl)))
                .build();

        CompletableFuture<Task> resultado = new CompletableFuture<>();
        try (Client client = Client.builder(card)
                .clientConfig(new ClientConfig.Builder().setStreaming(false).build())
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                .addConsumer((evento, agentCard) -> {
                    if (evento instanceof TaskEvent taskEvent) {
                        resultado.complete(taskEvent.getTask());
                    }
                })
                .streamingErrorHandler(resultado::completeExceptionally)
                .build()) {

            Message mensagem = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(UUID.randomUUID().toString())
                    .contextId("ctx-contrato-real")
                    .parts(List.<Part<?>>of(new TextPart("cliente nao encontra dinheiro"),
                            new DataPart(Map.of("customerId", "cli-001"))))
                    .build();

            client.sendMessage(mensagem); // não-streaming: o consumer roda antes do retorno

            Task task = resultado.get(10, TimeUnit.SECONDS);

            assertThat(task.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
            assertThat(extrairTexto(task)).contains("liquidacao");

            Map<?, ?> dados = extrairDados(task);
            assertThat(dados.get("confidence")).isEqualTo(0.9);
            assertThat(dados.get("sources")).isEqualTo(List.of("cdb-mcp"));
            assertThat(dados.containsKey("facts")).isTrue();
            assertThat(dados.containsKey("answerDraft")).isTrue();
            assertThat(dados.containsKey("risks")).isTrue();
        }
    }

    private static String extrairTexto(Task task) {
        for (Artifact artifact : task.artifacts()) {
            for (Part<?> part : artifact.parts()) {
                if (part instanceof TextPart textPart) {
                    return textPart.text();
                }
            }
        }
        throw new AssertionError("Task " + task.id() + " sem TextPart");
    }

    private static Map<?, ?> extrairDados(Task task) {
        for (Artifact artifact : task.artifacts()) {
            for (Part<?> part : artifact.parts()) {
                if (part instanceof DataPart dataPart && dataPart.data() instanceof Map<?, ?> dados) {
                    return dados;
                }
            }
        }
        throw new AssertionError("Task " + task.id() + " sem DataPart");
    }
}

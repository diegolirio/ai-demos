package poc.a2a.investimentos.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import poc.a2a.investimentos.especialista.EspecialistaInvestimentos;
import poc.a2a.investimentos.especialista.RespostaEspecialista;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class A2aServerTest {

    static final AtomicReference<String> contextIdRecebido = new AtomicReference<>();
    static final AtomicReference<String> pedidoRecebido = new AtomicReference<>();

    @TestConfiguration
    static class EspecialistaFake {
        @Bean
        EspecialistaInvestimentos especialistaInvestimentos() {
            return (contextId, pedido) -> {
                contextIdRecebido.set(contextId);
                pedidoRecebido.set(pedido);
                if (pedido.contains("explode")) {
                    throw new IllegalStateException("falha simulada");
                }
                if (pedido.contains("lento")) {
                    try {
                        Thread.sleep(6_000); // acima dos 5s padrão do builder do SDK
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return new RespostaEspecialista(List.of("Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO"),
                        "Seu resgate esta em liquidacao e cai na conta em alguns minutos.", 0.9,
                        List.of(), List.of("cdb-mcp"));
            };
        }
    }

    @LocalServerPort
    int port;

    RestClient http;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
    }

    private JsonNode sendMessage(String contextId, String texto) {
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{
                  "messageId":"m-1","role":"ROLE_USER","contextId":"%s",
                  "parts":[{"text":"%s"},{"data":{"customerId":"cli-001"}}]}}}
                """.formatted(contextId, texto);
        String resposta = http.post().uri("/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("A2A-Version", "1.0")
                .body(body)
                .retrieve()
                .body(String.class);
        return json.readTree(resposta);
    }

    @Test
    void publicaAgentCard() {
        JsonNode card = json.readTree(http.get().uri("/.well-known/agent-card.json").retrieve().body(String.class));

        assertThat(card.path("name").asString()).isEqualTo("investimentos-agent");
        assertThat(card.path("skills").get(0).path("id").asString()).isEqualTo("localizar-dinheiro-investimentos");
        assertThat(card.path("supportedInterfaces").get(0).path("protocolBinding").asString()).isEqualTo("JSONRPC");
    }

    @Test
    void sendMessageDevolveTextPartEDataPartNoSchemaNove() {
        JsonNode task = sendMessage("ctx-1", "cliente nao encontra dinheiro").path("result").path("task");

        assertThat(task.path("contextId").asString()).isEqualTo("ctx-1");
        assertThat(task.path("status").path("state").asString()).isEqualTo("TASK_STATE_COMPLETED");
        JsonNode parts = task.path("artifacts").get(0).path("parts");
        assertThat(parts.get(0).path("text").asString()).contains("liquidacao");
        JsonNode dados = parts.get(1).path("data");
        assertThat(dados.path("confidence").asDouble()).isEqualTo(0.9);
        assertThat(dados.path("sources").get(0).asString()).isEqualTo("cdb-mcp");
        assertThat(dados.has("facts")).isTrue();
        assertThat(dados.has("risks")).isTrue();
        assertThat(dados.has("answerDraft")).isTrue();
        // contextId vira memoryId; customerId do DataPart entra no pedido ao especialista
        assertThat(contextIdRecebido.get()).isEqualTo("ctx-1");
        assertThat(pedidoRecebido.get()).contains("cli-001").contains("cliente nao encontra dinheiro");
    }

    @Test
    void especialistaLentoNaoEstouraOTimeoutPadraoDoSdk() {
        JsonNode task = sendMessage("ctx-2", "pedido lento").path("result").path("task");

        assertThat(task.path("status").path("state").asString()).isEqualTo("TASK_STATE_COMPLETED");
    }

    @Test
    void falhaDoEspecialistaViraTaskFailed() {
        JsonNode task = sendMessage("ctx-3", "isso explode").path("result").path("task");

        assertThat(task.path("status").path("state").asString()).isEqualTo("TASK_STATE_FAILED");
    }
}

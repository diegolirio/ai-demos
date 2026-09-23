package poc.a2a.investimentos.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import poc.a2a.investimentos.BaseIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servidor A2A de ponta a ponta via HTTP com LLM real (Ollama), tools MCP dubladas (ver BaseIntegrationTest)
 * e memoria no Postgres real. Asserções toleram um modelo pequeno: verificam o contrato (§9), nao o texto.
 */
class A2aJsonRpcControllerIT extends BaseIntegrationTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void publicaAgentCard() {
        String body = restTestClient.get().uri("/.well-known/agent-card.json")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        JsonNode card = json.readTree(body);

        assertThat(card.path("name").asString()).isEqualTo("investimentos-agent");
        assertThat(card.path("skills").get(0).path("id").asString()).isEqualTo("localizar-dinheiro-investimentos");
        assertThat(card.path("supportedInterfaces").get(0).path("protocolBinding").asString()).isEqualTo("JSONRPC");
    }

    @Test
    void sendMessageComLlmRealDevolveSchemaNoveEPersisteMemoria() throws Exception {
        String contextId = "it-ctx-" + UUID.randomUUID();
        String requisicao = """
                {"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{
                  "messageId":"%s","role":"ROLE_USER","contextId":"%s",
                  "parts":[{"text":"cliente nao encontra dinheiro que estava em investimentos"},
                           {"data":{"customerId":"cli-001"}}]}}}
                """.formatted(UUID.randomUUID(), contextId);

        String resposta = restTestClient.post().uri("/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("A2A-Version", "1.0")
                .body(requisicao)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        JsonNode task = json.readTree(resposta).path("result").path("task");

        assertThat(task.path("contextId").asString()).isEqualTo(contextId);
        assertThat(task.path("status").path("state").asString()).as(resposta).isEqualTo("TASK_STATE_COMPLETED");
        JsonNode dados = dataPart(task.path("artifacts").get(0).path("parts"));
        assertThat(dados).as("DataPart em " + resposta).isNotNull();
        assertThat(dados.has("facts")).isTrue();
        assertThat(dados.has("answerDraft")).isTrue();
        assertThat(dados.has("confidence")).isTrue();
        assertThat(dados.has("risks")).isTrue();
        assertThat(dados.has("sources")).isTrue();
        assertThat(dados.path("confidence").asDouble()).isBetween(0.0, 1.0);
        // contextId A2A vira memoryId do especialista, persistido no Postgres real
        assertThat(chatMemoryRows(contextId)).isEqualTo(1);
    }

    private static JsonNode dataPart(JsonNode parts) {
        for (JsonNode part : parts) {
            if (part.has("data")) {
                return part.path("data");
            }
        }
        return null;
    }
}

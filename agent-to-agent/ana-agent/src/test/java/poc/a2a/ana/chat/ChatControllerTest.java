package poc.a2a.ana.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import poc.a2a.ana.assistente.AnaAssistant;
import poc.a2a.ana.assistente.DelegacaoInvestimentosTool;
import poc.a2a.ana.assistente.UltimasRespostasInvestimentos;
import poc.a2a.ana.investimentos.RespostaInvestimentos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatControllerTest {

    @TestConfiguration
    static class AnaFake {
        /** Simula um turno com delegação: registra a resposta do especialista e ecoa os parâmetros. */
        @Bean
        AnaAssistant anaAssistant(UltimasRespostasInvestimentos ultimas) {
            return (sessionId, mensagem, parametros) -> {
                String requestId = parametros.get(DelegacaoInvestimentosTool.REQUEST_ID);
                ultimas.registrar(requestId, new RespostaInvestimentos(List.of("fato"), "rascunho", 0.8,
                        List.of(), List.of("cdb-mcp")));
                return "eco: " + mensagem + " cliente=" + parametros.get(DelegacaoInvestimentosTool.CUSTOMER_ID);
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

    private JsonNode chat(String query, String body) {
        return json.readTree(http.post().uri("/chat" + query)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class));
    }

    @Test
    void respondeComCustomerIdVindoDoRequest() {
        JsonNode resposta = chat("", """
                {"sessionId":"s-1","customerId":"cli-001","message":"meu dinheiro sumiu"}""");

        assertThat(resposta.path("sessionId").asString()).isEqualTo("s-1");
        assertThat(resposta.path("reply").asString()).isEqualTo("eco: meu dinheiro sumiu cliente=cli-001");
        assertThat(resposta.path("debug").isNull()).isTrue();
    }

    @Test
    void modoDebugDevolveOSchemaDoEspecialista() {
        JsonNode resposta = chat("?debug=true", """
                {"sessionId":"s-2","customerId":"cli-001","message":"estava em investimentos"}""");

        assertThat(resposta.path("debug").path("confidence").asDouble()).isEqualTo(0.8);
        assertThat(resposta.path("debug").path("sources").get(0).asString()).isEqualTo("cdb-mcp");
    }

    @Test
    void camposObrigatorios() {
        HttpStatusCode status = http.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"sessionId\":\"s-3\",\"message\":\"oi\"}")
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(400);
    }
}

package poc.a2a.ana.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import poc.a2a.ana.BaseIntegrationTest;
import poc.a2a.ana.investimentos.InvestimentosClient;
import poc.a2a.ana.investimentos.RespostaInvestimentos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /chat de ponta a ponta com LLM real (Ollama) e memoria no Postgres real. O especialista (outro sistema,
 * via A2A) e mockado. Asserções toleram um modelo pequeno: nao comparam o texto da resposta.
 */
class ChatControllerIT extends BaseIntegrationTest {

    static final String CUSTOMER_ID = "cli-001";
    static final String CPF = "111.001.001-05";

    // Mesmo conteudo que o investimentos-agent devolve para cli-001 (cdb-mcp + tracking-money-mcp).
    static final RespostaInvestimentos RESPOSTA_ESPECIALISTA = new RespostaInvestimentos(
            List.of("Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO",
                    "Credito mov-001 de R$ 5000.00 PROCESSANDO na conta corrente (transferencia trf-001)",
                    "Transferencia trf-001 EM_PROCESSAMENTO, previsao ate 30 minutos"),
            "Seu resgate de R$ 5000.00 esta em liquidacao e cai na conta corrente em ate 30 minutos.",
            0.9, List.of(), List.of("cdb-mcp", "tracking-money-mcp"));

    @MockitoBean
    private InvestimentosClient investimentosClient;

    private final JsonMapper json = JsonMapper.builder().build();

    private JsonNode chat(String sessionId, String message) {
        String body = json.writeValueAsString(new ChatRequisicao(sessionId, CPF, message));
        String resposta = restTestClient.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return json.readTree(resposta);
    }

    @Test
    void jornadaMeuDinheiroSumiuComLlmRealEMemoriaNoPostgres() throws Exception {
        String sessionId = "it-sess-" + UUID.randomUUID();
        when(investimentosClient.delegar(anyString(), anyString(), anyString())).thenReturn(RESPOSTA_ESPECIALISTA);

        JsonNode turno1 = chat(sessionId, "meu dinheiro sumiu");

        assertThat(turno1.path("sessionId").asString()).isEqualTo(sessionId);
        assertThat(turno1.path("reply").asString()).isNotBlank();
        assertThat(chatMemoryRows(sessionId)).isEqualTo(1);

        JsonNode turno2 = chat(sessionId, "estava em investimentos e agora nao consigo encontrar");

        assertThat(turno2.path("reply").asString()).isNotBlank();
        // customerId vem do request (InvocationParameters), nunca do LLM; contextId A2A = sessionId.
        // atLeastOnce: o prompt manda triar no turno 1, mas um modelo pequeno pode delegar ja nele.
        verify(investimentosClient, atLeastOnce()).delegar(eq(sessionId), eq(CUSTOMER_ID), anyString());
        // cada delegacao bem-sucedida vira um atendimento do cliente (lido nas proximas sessoes)
        assertThat(atendimentoRows(CUSTOMER_ID)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void camposObrigatoriosSemChamarLlmNemEspecialista() {
        restTestClient.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"sessionId\":\"it-sess-400\",\"message\":\"oi\"}")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(investimentosClient);
    }

    @Test
    void cpfForaDoCadastroSemChamarLlmNemEspecialista() {
        restTestClient.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"sessionId\":\"it-sess-cpf\",\"cpf\":\"123.456.789-09\",\"message\":\"oi\"}")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(investimentosClient);
    }
}

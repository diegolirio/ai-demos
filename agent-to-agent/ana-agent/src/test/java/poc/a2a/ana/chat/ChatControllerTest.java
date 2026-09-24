package poc.a2a.ana.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import poc.a2a.ana.assistente.AnaAssistant;
import poc.a2a.ana.assistente.DelegacaoInvestimentosTool;
import poc.a2a.ana.assistente.UltimasRespostasInvestimentos;
import poc.a2a.ana.atendimento.HistoricoAtendimentosEmMemoria;
import poc.a2a.ana.investimentos.RespostaInvestimentos;
import poc.a2a.ana.investimentos.SituacaoGarantia;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatControllerTest {

    static final AtomicReference<String> ultimaChamada = new AtomicReference<>();

    @TestConfiguration
    static class AnaFake {
        @Bean
        HistoricoAtendimentosEmMemoria historicoAtendimentos() {
            return new HistoricoAtendimentosEmMemoria();
        }

        /** Simula um turno com delegação: registra a resposta do especialista e ecoa o que recebeu. */
        @Bean
        AnaAssistant anaAssistant(UltimasRespostasInvestimentos ultimas) {
            return (sessionId, mensagem, anteriores, parametros) -> {
                ultimaChamada.set(sessionId + "|" + mensagem + "|" + anteriores + "|" + parametros.asMap());
                String requestId = parametros.get(DelegacaoInvestimentosTool.REQUEST_ID);
                ultimas.registrar(requestId, new RespostaInvestimentos(List.of("fato"), "rascunho", 0.8,
                        List.of(), List.of("cdb-mcp")));
                return "eco: " + mensagem + " cliente=" + parametros.get(DelegacaoInvestimentosTool.CUSTOMER_ID)
                        + " anteriores=" + anteriores;
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    HistoricoAtendimentosEmMemoria historico;

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

    private record Erro(int status, String corpo) {
    }

    private Erro chatComErro(String body) {
        return http.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new Erro(response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes())));
    }

    @Test
    void resolveOCustomerIdPeloCpf() {
        JsonNode resposta = chat("", """
                {"sessionId":"s-1","cpf":"111.001.001-05","message":"meu dinheiro sumiu"}""");

        assertThat(resposta.path("sessionId").asString()).isEqualTo("s-1");
        assertThat(resposta.path("reply").asString())
                .isEqualTo("eco: meu dinheiro sumiu cliente=cli-001 anteriores=nenhum");
        assertThat(resposta.path("debug").isNull()).isTrue();
    }

    @Test
    void aceitaCpfSemMascara() {
        JsonNode resposta = chat("", """
                {"sessionId":"s-1b","cpf":"22200200293","message":"oi"}""");

        assertThat(resposta.path("reply").asString()).contains("cliente=cli-002");
    }

    @Test
    void cpfNuncaChegaAoAssistente() {
        chat("", """
                {"sessionId":"s-1c","cpf":"333.003.003-80","message":"oi"}""");

        assertThat(ultimaChamada.get()).doesNotContain("33300300380").doesNotContain("333.003.003-80")
                .contains("cli-003");
    }

    @Test
    void modoDebugDevolveOSchemaDoEspecialista() {
        JsonNode resposta = chat("?debug=true", """
                {"sessionId":"s-2","cpf":"111.001.001-05","message":"estava em investimentos"}""");

        assertThat(resposta.path("debug").path("confidence").asDouble()).isEqualTo(0.8);
        assertThat(resposta.path("debug").path("sources").get(0).asString()).isEqualTo("cdb-mcp");
    }

    @Test
    void atendimentoDeOutraSessaoVaiParaOAssistente() {
        historico.registrar("cli-008", "s-antiga", new RespostaInvestimentos(List.of("f"),
                "Parte do resgate segue retida.", 0.9, List.of(), List.of("cred-mcp"),
                new SituacaoGarantia("RETIDO_PARCIAL", new BigDecimal("10000.00"), new BigDecimal("3500.00"),
                        new BigDecimal("6500.00"), "Pagar a fatura")));

        String novaSessao = chat("", """
                {"sessionId":"s-nova","cpf":"888.008.008-31","message":"oi, voltei"}""").path("reply").asString();
        String mesmaSessao = chat("", """
                {"sessionId":"s-antiga","cpf":"888.008.008-31","message":"oi"}""").path("reply").asString();

        assertThat(novaSessao).contains("Parte do resgate segue retida.")
                .contains("[garantia: RETIDO_PARCIAL, liberado 6500.00, retido 3500.00]");
        assertThat(mesmaSessao).endsWith("anteriores=nenhum");
    }

    @Test
    void cpfInvalido() {
        Erro erro = chatComErro("""
                {"sessionId":"s-3","cpf":"111.001.001-06","message":"oi"}""");

        assertThat(erro.status()).isEqualTo(400);
        assertThat(json.readTree(erro.corpo()).path("error").asString()).isEqualTo("CPF invalido");
    }

    @Test
    void cpfForaDoCadastro() {
        Erro erro = chatComErro("""
                {"sessionId":"s-4","cpf":"123.456.789-09","message":"oi"}""");

        assertThat(erro.status()).isEqualTo(400);
        assertThat(json.readTree(erro.corpo()).path("error").asString()).isEqualTo("cliente nao encontrado");
    }

    @Test
    void camposObrigatorios() {
        Erro erro = chatComErro("{\"sessionId\":\"s-5\",\"message\":\"oi\"}");

        assertThat(erro.status()).isEqualTo(400);
        assertThat(json.readTree(erro.corpo()).path("error").asString())
                .isEqualTo("sessionId, cpf e message sao obrigatorios");
    }
}

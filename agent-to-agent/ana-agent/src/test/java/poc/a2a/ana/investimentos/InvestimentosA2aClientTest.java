package poc.a2a.ana.investimentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvestimentosA2aClientTest {

    FakeA2aServer servidor;

    @BeforeEach
    void subir() throws Exception {
        servidor = new FakeA2aServer();
    }

    @AfterEach
    void derrubar() {
        servidor.close();
    }

    @Test
    void delegaEDevolveOSchemaNoveDoDataPart() {
        var client = new InvestimentosA2aClient(servidor.url(), Duration.ofSeconds(10));

        RespostaInvestimentos resposta = client.delegar("sess-1", "cli-001", "cliente nao encontra dinheiro");

        assertThat(resposta.answerDraft()).isEqualTo("Seu resgate esta em liquidacao.");
        assertThat(resposta.confidence()).isEqualTo(0.9);
        assertThat(resposta.facts()).containsExactly("Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO");
        assertThat(resposta.sources()).containsExactly("cdb-mcp", "tracking-money-mcp");
        assertThat(resposta.risks()).isEmpty();
        assertThat(servidor.requisicoes).singleElement().satisfies(body -> assertThat(body)
                .contains("SendMessage")
                .contains("sess-1")
                .contains("cli-001")
                .contains("cliente nao encontra dinheiro"));
    }

    @Test
    void taskFailedViraIndisponivel() {
        servidor.respostaSendMessage = FakeA2aServer.TASK_FAILED;
        var client = new InvestimentosA2aClient(servidor.url(), Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.delegar("sess-1", "cli-001", "x"))
                .isInstanceOf(InvestimentosIndisponivelException.class)
                .hasMessageContaining("TASK_STATE_FAILED");
    }

    @Test
    void timeoutViraIndisponivel() {
        servidor.atrasoMs = 3_000;
        var client = new InvestimentosA2aClient(servidor.url(), Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.delegar("sess-1", "cli-001", "x"))
                .isInstanceOf(InvestimentosIndisponivelException.class)
                .hasMessageContaining("Timeout");
    }

    @Test
    void servidorForaDoArViraIndisponivel() {
        var client = new InvestimentosA2aClient("http://localhost:1", Duration.ofSeconds(5));

        assertThatThrownBy(() -> client.delegar("sess-1", "cli-001", "x"))
                .isInstanceOf(InvestimentosIndisponivelException.class);
    }

    @Test
    void depoisDeUmTimeoutOClienteAindaAtendeUmaChamadaSeguinte() {
        servidor.atrasoMs = 3_000;
        var client = new InvestimentosA2aClient(servidor.url(), Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.delegar("sess-1", "cli-001", "x"))
                .isInstanceOf(InvestimentosIndisponivelException.class);

        servidor.atrasoMs = 0;
        RespostaInvestimentos resposta = client.delegar("sess-1", "cli-001", "cliente nao encontra dinheiro");

        assertThat(resposta.answerDraft()).isEqualTo("Seu resgate esta em liquidacao.");
    }
}

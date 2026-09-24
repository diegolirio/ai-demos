package poc.a2a.cred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import poc.a2a.cred.SolicitacoesCreditoRepository.SolicitacaoCredito;
import poc.a2a.cred.SolicitacoesCreditoRepository.StatusSolicitacao;
import poc.a2a.cred.SolicitacoesCreditoRepository.TipoSolicitacao;

class SolicitacoesCreditoRepositoryTest {

    final SolicitacoesCreditoRepository repository = new SolicitacoesCreditoRepository();

    static List<String> todosOsClientes() {
        return IntStream.rangeClosed(1, 12).mapToObj(i -> "cli-%03d".formatted(i)).toList();
    }

    @Test
    void motivoPreenchidoSeESomenteSeRecusada() {
        List<SolicitacaoCredito> todas = todosOsClientes().stream()
                .flatMap(c -> repository.solicitacoes(c).stream()).toList();

        assertThat(todas).hasSize(5);
        assertThat(todas).allSatisfy(s -> {
            boolean recusada = s.status() == StatusSolicitacao.RECUSADA;
            assertThat(s.motivoCodigo() != null).isEqualTo(recusada);
            assertThat(s.motivoCliente() != null).isEqualTo(recusada);
            assertThat(s.proximoPasso()).isNotBlank();
        });
    }

    @Test
    void clientesDaJornadaDeInvestimentosNaoTemSolicitacoes() {
        assertThat(IntStream.rangeClosed(1, 8).mapToObj(i -> "cli-00" + i))
                .allSatisfy(c -> assertThat(repository.solicitacoes(c)).isEmpty());
    }

    @Test
    void cenarios() {
        assertThat(repository.solicitacoes("cli-009")).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSolicitacao.EMPRESTIMO_PESSOAL);
            assertThat(s.status()).isEqualTo(StatusSolicitacao.RECUSADA);
            assertThat(s.motivoCodigo()).isEqualTo("RENDA_INSUFICIENTE");
            assertThat(s.valorSolicitado()).isEqualByComparingTo(new BigDecimal("30000.00"));
            assertThat(s.reavaliacaoApos()).isNull();
        });
        assertThat(repository.solicitacoes("cli-010")).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSolicitacao.CARTAO_CREDITO);
            assertThat(s.motivoCodigo()).isEqualTo("RESTRICAO_CADASTRAL");
            assertThat(s.reavaliacaoApos()).isEqualTo(LocalDate.of(2026, 10, 23));
        });
        assertThat(repository.solicitacoes("cli-011")).extracting(SolicitacaoCredito::status)
                .containsExactly(StatusSolicitacao.APROVADA, StatusSolicitacao.RECUSADA);
        assertThat(repository.solicitacoes("cli-011").get(1).motivoCodigo()).isEqualTo("RELACIONAMENTO_RECENTE");
        assertThat(repository.solicitacoes("cli-012")).singleElement()
                .extracting(SolicitacaoCredito::status).isEqualTo(StatusSolicitacao.EM_ANALISE);
    }
}

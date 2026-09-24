package poc.a2a.cred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import poc.a2a.cred.ContaGarantiaRepository.RetencaoGarantia;
import poc.a2a.cred.ContaGarantiaRepository.StatusGarantia;

class ContaGarantiaRepositoryTest {

    final ContaGarantiaRepository repository = new ContaGarantiaRepository();

    static List<String> todosOsClientes() {
        return IntStream.rangeClosed(1, 8).mapToObj(i -> "cli-00" + i).toList();
    }

    @Test
    void retidoMaisLiberadoEIgualAoResgatadoEmTodoOMock() {
        List<RetencaoGarantia> todas = todosOsClientes().stream().flatMap(c -> repository.retencoes(c).stream()).toList();

        assertThat(todas).hasSize(4);
        assertThat(todas).allSatisfy(r ->
                assertThat(r.valorRetido().add(r.valorLiberado())).isEqualByComparingTo(r.valorResgatado()));
    }

    @Test
    void cenariosAntigosNaoPassamPelaGarantia() {
        assertThat(List.of("cli-001", "cli-002", "cli-003", "cli-004"))
                .allSatisfy(c -> assertThat(repository.retencoes(c)).isEmpty());
    }

    @Test
    void umStatusPorCliente() {
        assertThat(repository.retencoes("cli-005")).singleElement()
                .extracting(RetencaoGarantia::status).isEqualTo(StatusGarantia.LIBERADO_CONTA);
        assertThat(repository.retencoes("cli-006")).singleElement()
                .extracting(RetencaoGarantia::status).isEqualTo(StatusGarantia.EM_ANALISE);
        assertThat(repository.retencoes("cli-007")).singleElement()
                .extracting(RetencaoGarantia::status).isEqualTo(StatusGarantia.RETIDO_ATE_PAGAMENTO_FATURA);
        assertThat(repository.retencoes("cli-008")).singleElement()
                .satisfies(r -> {
                    assertThat(r.status()).isEqualTo(StatusGarantia.RETIDO_PARCIAL);
                    assertThat(r.valorRetido()).isEqualByComparingTo(new BigDecimal("3500.00"));
                    assertThat(r.valorLiberado()).isEqualByComparingTo(new BigDecimal("6500.00"));
                    assertThat(r.vencimentoFatura()).isEqualTo(LocalDate.of(2026, 10, 5));
                });
    }
}

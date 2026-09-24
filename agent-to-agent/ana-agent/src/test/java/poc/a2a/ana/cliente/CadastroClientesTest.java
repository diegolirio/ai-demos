package poc.a2a.ana.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class CadastroClientesTest {

    final CadastroClientes cadastro = new CadastroClientes();

    @ParameterizedTest
    @CsvSource({
            "111.001.001-05,cli-001", "222.002.002-93,cli-002", "333.003.003-80,cli-003", "444.004.004-76,cli-004",
            "555.005.005-62,cli-005", "666.006.006-59,cli-006", "777.007.007-45,cli-007", "888.008.008-31,cli-008",
            "999.009.009-28,cli-009", "101.010.010-61,cli-010", "121.011.011-30,cli-011", "131.012.012-92,cli-012"})
    void resolveOsCpfsDeTeste(String cpf, String customerId) {
        assertThat(cadastro.customerId(Cpf.de(cpf).orElseThrow())).contains(customerId);
    }

    @Test
    void cpfValidoForaDoCadastro() {
        assertThat(cadastro.customerId(Cpf.de("123.456.789-09").orElseThrow())).isEmpty();
    }
}

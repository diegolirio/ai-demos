package poc.a2a.ana.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"888.008.008-31", "88800800831", " 888.008.008-31 ", "123.456.789-09"})
    void aceitaCpfValidoComOuSemMascara(String bruto) {
        assertThat(Cpf.de(bruto)).isPresent();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"888.008.008-32", "111.111.111-11", "1234567890", "888.008.008-311", "abc88800800831"})
    void rejeitaCpfInvalido(String bruto) {
        assertThat(Cpf.de(bruto)).isEmpty();
    }

    @Test
    void normalizaParaOnzeDigitos() {
        assertThat(Cpf.de("888.008.008-31")).get().extracting(Cpf::digitos).isEqualTo("88800800831");
    }

    @Test
    void mascaraMostraSoOsUltimosQuatroDigitos() {
        Cpf cpf = Cpf.de("888.008.008-31").orElseThrow();

        assertThat(cpf.mascarado()).isEqualTo("***.***.*08-31");
        assertThat(cpf.toString()).isEqualTo("***.***.*08-31");
    }
}

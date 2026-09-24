package poc.a2a.ana.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatRequisicaoTest {

    @Test
    void toStringMascaraOCpf() {
        ChatRequisicao requisicao = new ChatRequisicao("sess-1", "111.001.001-05", "meu dinheiro sumiu");

        assertThat(requisicao.toString())
                .contains("sess-1")
                .contains("meu dinheiro sumiu")
                .doesNotContain("111.001.001-05")
                .doesNotContain("11100100105");
    }

    @Test
    void toStringComCpfInvalidoNaoQuebra() {
        ChatRequisicao requisicao = new ChatRequisicao("sess-1", "abc", "oi");

        assertThat(requisicao.toString()).contains("***");
    }
}

package poc.a2a.investimentos.especialista;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** O LiteLLM prefixa as tools com "{servidor}-"; o especialista enxerga o nome original. */
class NomeToolTest {

    @Test
    void removeOPrefixoDoServidor() {
        assertThat(NomeTool.semPrefixo("cdb_mcp-listar_posicoes_cdb")).isEqualTo("listar_posicoes_cdb");
        assertThat(NomeTool.semPrefixo("tracking_money_mcp-listar_movimentacoes")).isEqualTo("listar_movimentacoes");
    }

    @Test
    void semHifenDevolveONomeIntacto() {
        assertThat(NomeTool.semPrefixo("listar_posicoes_cdb")).isEqualTo("listar_posicoes_cdb");
    }

    @Test
    void removeSoAteOPrimeiroHifen() {
        assertThat(NomeTool.semPrefixo("a-b-c")).isEqualTo("b-c");
    }

    @Test
    void nuloOuVazioDevolveOProprioValor() {
        assertThat(NomeTool.semPrefixo(null)).isNull();
        assertThat(NomeTool.semPrefixo("")).isEmpty();
    }
}

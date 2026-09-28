package poc.a2a.investimentos.especialista;

/**
 * O LiteLLM (MCP gateway) sempre expoe as tools como "{servidor}-{tool}". O especialista trabalha com o nome
 * original (prompt, allowlist, logs). Seguro porque nenhuma tool dos MCPs da POC tem "-" no nome (usam "_");
 * sem "-", o nome volta intacto (acesso direto ao MCP, fora do compose). Como so o primeiro "-" e removido,
 * dois servidores diferentes expondo a mesma tool sem prefixo colidiriam depois desta remocao.
 */
final class NomeTool {

    private NomeTool() {
    }

    static String semPrefixo(String nome) {
        if (nome == null) {
            return null;
        }
        int hifen = nome.indexOf('-');
        return hifen < 0 ? nome : nome.substring(hifen + 1);
    }
}

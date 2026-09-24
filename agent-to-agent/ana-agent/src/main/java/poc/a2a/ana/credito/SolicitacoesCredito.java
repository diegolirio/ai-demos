package poc.a2a.ana.credito;

import java.util.List;

/** Porta: solicitacoes de credito do cliente (emprestimo e cartao). */
public interface SolicitacoesCredito {

    /** @throws CreditoIndisponivelException se a fonte (cred-mcp) nao puder ser consultada */
    List<SolicitacaoCredito> consultar(String customerId);
}

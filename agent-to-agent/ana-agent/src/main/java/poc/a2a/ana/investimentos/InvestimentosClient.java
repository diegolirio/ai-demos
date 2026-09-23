package poc.a2a.ana.investimentos;

/** Delegação de intenção ao Especialista de Investimentos. */
public interface InvestimentosClient {

    /**
     * @throws InvestimentosIndisponivelException se o especialista estiver fora, estourar o timeout
     *                                            ou a Task não terminar COMPLETED.
     */
    RespostaInvestimentos delegar(String contextId, String customerId, String pedido);
}

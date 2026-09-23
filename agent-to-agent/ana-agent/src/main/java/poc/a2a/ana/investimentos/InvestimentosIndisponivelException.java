package poc.a2a.ana.investimentos;

public class InvestimentosIndisponivelException extends RuntimeException {

    public InvestimentosIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public InvestimentosIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

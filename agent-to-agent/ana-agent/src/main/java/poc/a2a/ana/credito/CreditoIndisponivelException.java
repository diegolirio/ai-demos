package poc.a2a.ana.credito;

public class CreditoIndisponivelException extends RuntimeException {

    public CreditoIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public CreditoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

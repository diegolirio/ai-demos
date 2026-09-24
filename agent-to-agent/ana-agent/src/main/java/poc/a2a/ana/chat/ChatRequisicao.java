package poc.a2a.ana.chat;

import poc.a2a.ana.cliente.Cpf;

/** O cliente e identificado pelo CPF; a Ana resolve o customerId (o CPF nao segue adiante). */
public record ChatRequisicao(String sessionId, String cpf, String message) {

    /** Mascara o CPF: o Spring MVC pode logar o corpo da requisicao em DEBUG. */
    @Override
    public String toString() {
        String cpfMascarado = Cpf.de(cpf).map(Cpf::mascarado).orElse("***");
        return "ChatRequisicao[sessionId=" + sessionId + ", cpf=" + cpfMascarado + ", message=" + message + "]";
    }
}

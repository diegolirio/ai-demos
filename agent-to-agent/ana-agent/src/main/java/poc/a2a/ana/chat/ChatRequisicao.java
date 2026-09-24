package poc.a2a.ana.chat;

/** O cliente e identificado pelo CPF; a Ana resolve o customerId (o CPF nao segue adiante). */
public record ChatRequisicao(String sessionId, String cpf, String message) {
}

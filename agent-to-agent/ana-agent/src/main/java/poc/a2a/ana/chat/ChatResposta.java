package poc.a2a.ana.chat;

import poc.a2a.ana.investimentos.RespostaInvestimentos;

public record ChatResposta(String sessionId, String reply, RespostaInvestimentos debug) {
}

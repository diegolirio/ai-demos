package poc.a2a.ana.chat;

import java.util.List;

import poc.a2a.ana.credito.SolicitacaoCredito;
import poc.a2a.ana.investimentos.RespostaInvestimentos;

/** debug: especialista de investimentos (A2A); credito: consulta direta ao cred-mcp (MCP). Ambos so com ?debug=true. */
public record ChatResposta(String sessionId, String reply, RespostaInvestimentos debug,
                           List<SolicitacaoCredito> credito) {
}

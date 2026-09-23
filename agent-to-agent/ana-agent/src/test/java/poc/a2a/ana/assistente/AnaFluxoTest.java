package poc.a2a.ana.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static poc.a2a.ana.assistente.ScriptedChatModel.chamarTool;
import static poc.a2a.ana.assistente.ScriptedChatModel.responder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;
import poc.a2a.ana.investimentos.InvestimentosClient;
import poc.a2a.ana.investimentos.InvestimentosIndisponivelException;
import poc.a2a.ana.investimentos.RespostaInvestimentos;

class AnaFluxoTest {

    static final RespostaInvestimentos RESPOSTA = new RespostaInvestimentos(
            List.of("Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO"),
            "Seu resgate esta em liquidacao e cai na conta em alguns minutos.", 0.9,
            List.of(), List.of("cdb-mcp"));

    final InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
    final ChatMemoryProvider memoria = id -> MessageWindowChatMemory.builder()
            .id(id).maxMessages(20).chatMemoryStore(store).build();
    final UltimasRespostasInvestimentos ultimas = new UltimasRespostasInvestimentos();

    static InvocationParameters parametros(String sessionId, String customerId) {
        return parametros(sessionId, customerId, sessionId + "-req");
    }

    static InvocationParameters parametros(String sessionId, String customerId, String requestId) {
        InvocationParameters parametros = new InvocationParameters();
        parametros.put(DelegacaoInvestimentosTool.SESSION_ID, sessionId);
        parametros.put(DelegacaoInvestimentosTool.CUSTOMER_ID, customerId);
        parametros.put(DelegacaoInvestimentosTool.REQUEST_ID, requestId);
        return parametros;
    }

    @Test
    void triagemNoPrimeiroTurnoEDelegacaoNoSegundo() {
        AtomicReference<String> chamada = new AtomicReference<>();
        InvestimentosClient client = (contextId, customerId, pedido) -> {
            chamada.set(contextId + "|" + customerId + "|" + pedido);
            return RESPOSTA;
        };
        ScriptedChatModel llm = new ScriptedChatModel(
                responder(r -> "Seu dinheiro estava aplicado onde? Na conta, em investimentos ou em outro lugar?"),
                chamarTool("delegar_investimentos", "{\"pedido\":\"cliente nao encontra dinheiro que estava em investimentos\"}"),
                responder(resultados -> "Encontrei! " + resultados.getFirst().text()));
        AnaAssistant ana = AnaFactory.criar(llm, memoria, new DelegacaoInvestimentosTool(client, ultimas));

        String turno1 = ana.conversar("sess-1", "meu dinheiro sumiu", parametros("sess-1", "cli-001"));
        String turno2 = ana.conversar("sess-1", "estava em investimentos e agora nao consigo encontrar",
                parametros("sess-1", "cli-001"));

        assertThat(turno1).contains("aplicado onde");
        assertThat(chamada.get()).isEqualTo("sess-1|cli-001|cliente nao encontra dinheiro que estava em investimentos");
        assertThat(turno2).contains("liquidacao");
        assertThat(ultimas.remover("sess-1-req")).isEqualTo(RESPOSTA);
        assertThat(llm.requisicoes().getFirst().toolSpecifications())
                .extracting(ToolSpecification::name).containsExactly("delegar_investimentos");
        // memória: o segundo turno enxerga o primeiro
        assertThat(llm.requisicoes().get(1).messages().toString()).contains("meu dinheiro sumiu");
    }

    @Test
    void especialistaIndisponivelViraMensagemParaOLlm() {
        InvestimentosClient client = (contextId, customerId, pedido) -> {
            throw new InvestimentosIndisponivelException("Timeout");
        };
        ScriptedChatModel llm = new ScriptedChatModel(
                chamarTool("delegar_investimentos", "{\"pedido\":\"localizar dinheiro\"}"),
                responder(resultados -> resultados.getFirst().text()));
        AnaAssistant ana = AnaFactory.criar(llm, memoria, new DelegacaoInvestimentosTool(client, ultimas));

        String resposta = ana.conversar("sess-2", "estava em investimentos", parametros("sess-2", "cli-001"));

        assertThat(resposta).startsWith("INDISPONIVEL:");
        assertThat(ultimas.remover("sess-2-req")).isNull();
    }

    @Test
    void duasRequisicoesNaMesmaSessaoNaoSeAtropelam() {
        RespostaInvestimentos respostaA = RESPOSTA;
        RespostaInvestimentos respostaB = new RespostaInvestimentos(
                List.of("outro fato"), "outro rascunho", 0.7, List.of(), List.of("outra-fonte"));
        DelegacaoInvestimentosTool tool = new DelegacaoInvestimentosTool(
                (contextId, customerId, pedido) -> pedido.contains("A") ? respostaA : respostaB, ultimas);

        tool.delegarInvestimentos("pedido A", parametros("sess-3", "cli-001", "req-A"));
        tool.delegarInvestimentos("pedido B", parametros("sess-3", "cli-001", "req-B"));

        assertThat(ultimas.remover("req-A")).isEqualTo(respostaA);
        assertThat(ultimas.remover("req-B")).isEqualTo(respostaB);
    }
}

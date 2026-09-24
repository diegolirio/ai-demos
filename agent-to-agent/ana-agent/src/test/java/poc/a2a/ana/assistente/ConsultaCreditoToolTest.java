package poc.a2a.ana.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static poc.a2a.ana.assistente.ScriptedChatModel.chamarTool;
import static poc.a2a.ana.assistente.ScriptedChatModel.responder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;
import poc.a2a.ana.atendimento.Atendimento;
import poc.a2a.ana.atendimento.HistoricoAtendimentos;
import poc.a2a.ana.atendimento.HistoricoAtendimentosEmMemoria;
import poc.a2a.ana.atendimento.Origem;
import poc.a2a.ana.credito.CreditoIndisponivelException;
import poc.a2a.ana.credito.SolicitacaoCredito;
import poc.a2a.ana.credito.SolicitacoesCredito;

class ConsultaCreditoToolTest {

    static final SolicitacaoCredito RECUSADA = new SolicitacaoCredito("sol-009", "EMPRESTIMO_PESSOAL",
            LocalDateTime.of(2026, 9, 20, 10, 30), "RECUSADA", new BigDecimal("30000.00"), "RENDA_INSUFICIENTE",
            "A parcela compromete mais do que o permitido da renda informada", "Simule um valor menor", null);

    final InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
    final ChatMemoryProvider memoria = id -> MessageWindowChatMemory.builder()
            .id(id).maxMessages(20).chatMemoryStore(store).build();
    final UltimasConsultasCredito ultimas = new UltimasConsultasCredito();
    final HistoricoAtendimentosEmMemoria historico = new HistoricoAtendimentosEmMemoria();

    static InvocationParameters parametros(String sessionId, String customerId, String requestId) {
        InvocationParameters parametros = new InvocationParameters();
        parametros.put(DelegacaoInvestimentosTool.SESSION_ID, sessionId);
        parametros.put(DelegacaoInvestimentosTool.CUSTOMER_ID, customerId);
        parametros.put(DelegacaoInvestimentosTool.REQUEST_ID, requestId);
        return parametros;
    }

    @Test
    void llmChamaAToolSemArgumentosEOCustomerIdVemDaRequisicao() {
        AtomicReference<String> consultado = new AtomicReference<>();
        SolicitacoesCredito credito = customerId -> {
            consultado.set(customerId);
            return List.of(RECUSADA);
        };
        ScriptedChatModel llm = new ScriptedChatModel(
                chamarTool("consultar_solicitacoes_credito", "{}"),
                responder(resultados -> resultados.getFirst().text()));
        AnaAssistant ana = AnaFactory.criar(llm, memoria,
                new DelegacaoInvestimentosTool((c, id, p) -> null, new UltimasRespostasInvestimentos(), historico),
                new ConsultaCreditoTool(credito, ultimas, historico));

        String resposta = ana.conversar("sess-c1", "meu emprestimo foi recusado, por que?", "nenhum",
                parametros("sess-c1", "cli-009", "req-c1"));

        assertThat(consultado.get()).isEqualTo("cli-009");
        assertThat(resposta).contains("A parcela compromete").doesNotContain("RENDA_INSUFICIENTE");
        assertThat(ultimas.remover("req-c1")).containsExactly(RECUSADA);
        ToolSpecification spec = llm.requisicoes().getFirst().toolSpecifications().stream()
                .filter(t -> t.name().equals("consultar_solicitacoes_credito")).findFirst().orElseThrow();
        assertThat(spec.parameters() == null || spec.parameters().properties().isEmpty()).isTrue();
        assertThat(llm.requisicoes().getFirst().toolSpecifications()).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("delegar_investimentos", "consultar_solicitacoes_credito");
    }

    @Test
    void consultaComResultadoGravaAtendimentoDeCredito() {
        ConsultaCreditoTool tool = new ConsultaCreditoTool(c -> List.of(RECUSADA), ultimas, historico);

        tool.consultarSolicitacoesCredito(parametros("sess-c2", "cli-009", "req-c2"));

        assertThat(historico.recentesDeOutrasSessoes("cli-009", "outra", 3)).singleElement().satisfies(a -> {
            assertThat(a.origem()).isEqualTo(Origem.CREDITO);
            assertThat(a.resumo()).isEqualTo(SolicitacaoCredito.resumo(List.of(RECUSADA)))
                    .doesNotContain("RENDA_INSUFICIENTE");
        });
    }

    @Test
    void semSolicitacoesDevolveNenhumaENaoGravaHistorico() {
        ConsultaCreditoTool tool = new ConsultaCreditoTool(c -> List.of(), ultimas, historico);

        String resultado = tool.consultarSolicitacoesCredito(parametros("sess-c3", "cli-001", "req-c3"));

        assertThat(resultado).isEqualTo(SolicitacaoCredito.NENHUMA);
        assertThat(ultimas.remover("req-c3")).isEmpty();
        assertThat(historico.recentesDeOutrasSessoes("cli-001", "outra", 3)).isEmpty();
    }

    @Test
    void credMcpForaViraIndisponivelSemDebugNemHistorico() {
        ConsultaCreditoTool tool = new ConsultaCreditoTool(
                c -> { throw new CreditoIndisponivelException("Connection refused"); }, ultimas, historico);

        String resultado = tool.consultarSolicitacoesCredito(parametros("sess-c4", "cli-009", "req-c4"));

        assertThat(resultado).isEqualTo(ConsultaCreditoTool.INDISPONIVEL).startsWith("INDISPONIVEL:");
        assertThat(ultimas.remover("req-c4")).isNull();
        assertThat(historico.recentesDeOutrasSessoes("cli-009", "outra", 3)).isEmpty();
    }

    @Test
    void falhaAoGravarHistoricoNaoQuebraATool() {
        HistoricoAtendimentos quebrado = new HistoricoAtendimentosEmMemoria() {
            @Override
            public void registrarCredito(String customerId, String sessionId, String resumo) {
                throw new IllegalStateException("banco fora");
            }
        };
        ConsultaCreditoTool tool = new ConsultaCreditoTool(c -> List.of(RECUSADA), ultimas, quebrado);

        String resultado = tool.consultarSolicitacoesCredito(parametros("sess-c5", "cli-009", "req-c5"));

        assertThat(resultado).startsWith("solicitacoes de credito do cliente:");
    }
}

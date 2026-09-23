package poc.a2a.investimentos.especialista;

import static org.assertj.core.api.Assertions.assertThat;
import static poc.a2a.investimentos.especialista.ScriptedChatModel.chamarTool;
import static poc.a2a.investimentos.especialista.ScriptedChatModel.responder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

class EspecialistaInvestimentosTest {

    static final String RESPOSTA_JSON = """
            {"facts":["Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO"],
             "answerDraft":"Seu resgate de R$ 5.000,00 esta em liquidacao e cai na conta em alguns minutos.",
             "confidence":0.92,"risks":[],"sources":["cdb-mcp"]}
            """;

    final InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
    final ChatMemoryProvider memoria = id -> MessageWindowChatMemory.builder()
            .id(id).maxMessages(20).chatMemoryStore(store).build();
    final AtomicReference<String> argumentosDaTool = new AtomicReference<>();

    final ToolProvider toolsFake = request -> new ToolProviderResult(Map.of(
            ToolSpecification.builder()
                    .name("listar_resgates_cdb")
                    .description("Lista resgates de CDB")
                    .parameters(JsonObjectSchema.builder().addStringProperty("customerId").required("customerId").build())
                    .build(),
            (toolRequest, memoryId) -> {
                argumentosDaTool.set(toolRequest.arguments());
                return "[{\"resgateId\":\"res-001\",\"valor\":5000.00,\"status\":\"EM_LIQUIDACAO\"}]";
            }));

    @Test
    void chamaToolMcpEDevolveSchemaNove() {
        ScriptedChatModel llm = new ScriptedChatModel(
                chamarTool("listar_resgates_cdb", "{\"customerId\":\"cli-001\"}"),
                responder(resultados -> {
                    assertThat(resultados).singleElement()
                            .satisfies(r -> assertThat(r.text()).contains("EM_LIQUIDACAO"));
                    return RESPOSTA_JSON;
                }));
        EspecialistaInvestimentos especialista = EspecialistaFactory.criar(llm, toolsFake, memoria);

        RespostaEspecialista resposta = especialista.investigar("ctx-1", "customerId: cli-001\nPedido: nao acho meu dinheiro");

        assertThat(argumentosDaTool.get()).contains("cli-001");
        assertThat(resposta.confidence()).isEqualTo(0.92);
        assertThat(resposta.answerDraft()).contains("liquidacao");
        assertThat(resposta.sources()).containsExactly("cdb-mcp");
        assertThat(resposta.facts()).hasSize(1);
        assertThat(resposta.risks()).isEmpty();
        assertThat(llm.requisicoes().getFirst().toolSpecifications())
                .extracting(ToolSpecification::name).containsExactly("listar_resgates_cdb");
    }

    @Test
    void memoriaEPorContextId() {
        ScriptedChatModel llm = new ScriptedChatModel(
                responder(r -> RESPOSTA_JSON),
                responder(r -> RESPOSTA_JSON));
        EspecialistaInvestimentos especialista = EspecialistaFactory.criar(llm, toolsFake, memoria);

        especialista.investigar("ctx-memoria", "primeiro pedido");
        especialista.investigar("ctx-memoria", "segundo pedido");

        assertThat(llm.requisicoes().get(1).messages().toString())
                .contains("primeiro pedido").contains("segundo pedido");
    }
}

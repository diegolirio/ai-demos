package poc.a2a.investimentos.especialista;

import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import dev.langchain4j.community.store.memory.chat.sql.PostgreSQLDialect;
import dev.langchain4j.community.store.memory.chat.sql.SQLChatMemoryStore;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Beans que dependem de LLM, MCP servers e Postgres. Fora do profile "test". */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class EspecialistaConfig {

    /** llm.timeout (default 60s) e configuravel para os testes de integracao com Ollama em CPU, que sao lentos. */
    @Bean
    ChatModel chatModel(@Value("${llm.base-url}") String baseUrl,
                        @Value("${llm.api-key}") String apiKey,
                        @Value("${llm.model}") String model,
                        @Value("${llm.timeout:60s}") Duration timeout) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(timeout)
                .build();
    }

    /** DefaultMcpClient conecta no construtor: o MCP server precisa estar no ar (compose usa depends_on healthy). */
    @Bean(destroyMethod = "close")
    McpClient cdbMcpClient(@Value("${mcp.cdb-url}") String url) {
        return mcpClient("cdb-mcp", url);
    }

    @Bean(destroyMethod = "close")
    McpClient trackingMoneyMcpClient(@Value("${mcp.tracking-money-url}") String url) {
        return mcpClient("tracking-money-mcp", url);
    }

    @Bean(destroyMethod = "close")
    McpClient credMcpClient(@Value("${mcp.cred-url}") String url) {
        return mcpClient("cred-mcp", url);
    }

    private static McpClient mcpClient(String chave, String url) {
        return DefaultMcpClient.builder()
                .key(chave)
                .clientName("investimentos-agent")
                .transport(StreamableHttpMcpTransport.builder()
                        .url(url)
                        .timeout(Duration.ofSeconds(30))
                        .build())
                .toolExecutionTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Tools da jornada de investimentos. consultar_solicitacoes_credito (cred-mcp) e da Ana, nao do especialista. */
    static final List<String> TOOLS_DO_ESPECIALISTA = List.of("listar_posicoes_cdb", "listar_resgates_cdb",
            "listar_movimentacoes", "consultar_status_transferencia", "consultar_conta_garantia");

    /** failIfOneServerFails=false: um MCP fora não derruba o especialista; a falha vira risk na resposta. */
    static McpToolProvider provedorMcp(McpClient... clients) {
        return McpToolProvider.builder()
                .mcpClients(clients)
                .failIfOneServerFails(false)
                .filterToolNames(TOOLS_DO_ESPECIALISTA)
                .build();
    }

    @Bean
    ToolProvider mcpToolProvider(McpClient cdbMcpClient, McpClient trackingMoneyMcpClient, McpClient credMcpClient) {
        return new ToolProviderComLog(provedorMcp(cdbMcpClient, trackingMoneyMcpClient, credMcpClient));
    }

    @Bean
    DataSource memoriaDataSource(@Value("${memoria.jdbc-url}") String jdbcUrl,
                                 @Value("${memoria.usuario}") String usuario,
                                 @Value("${memoria.senha}") String senha) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(jdbcUrl);
        dataSource.setUser(usuario);
        dataSource.setPassword(senha);
        return dataSource;
    }

    @Bean
    ChatMemoryProvider chatMemoryProvider(DataSource memoriaDataSource) {
        SQLChatMemoryStore store = SQLChatMemoryStore.builder()
                .dataSource(memoriaDataSource)
                .sqlDialect(new PostgreSQLDialect())
                .tableName("chat_memory")
                .autoCreateTable(true)
                .build();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(30)
                .chatMemoryStore(store)
                .build();
    }

    @Bean
    EspecialistaInvestimentos especialistaInvestimentos(ChatModel chatModel, ToolProvider mcpToolProvider,
                                                        ChatMemoryProvider chatMemoryProvider) {
        return EspecialistaFactory.criar(chatModel, mcpToolProvider, chatMemoryProvider);
    }
}

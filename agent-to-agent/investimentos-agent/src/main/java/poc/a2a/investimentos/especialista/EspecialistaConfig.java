package poc.a2a.investimentos.especialista;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    /**
     * DefaultMcpClient conecta no construtor: o MCP precisa estar no ar. No compose a URL e o LiteLLM
     * (MCP gateway, /{servidor}/mcp) e o compose espera ele ficar healthy.
     */
    @Bean(destroyMethod = "close")
    McpClient cdbMcpClient(@Value("${mcp.cdb-url}") String url, @Value("${mcp.gateway-key:}") String gatewayKey) {
        return mcpClient("cdb-mcp", url, gatewayKey);
    }

    @Bean(destroyMethod = "close")
    McpClient trackingMoneyMcpClient(@Value("${mcp.tracking-money-url}") String url,
                                     @Value("${mcp.gateway-key:}") String gatewayKey) {
        return mcpClient("tracking-money-mcp", url, gatewayKey);
    }

    @Bean(destroyMethod = "close")
    McpClient credMcpClient(@Value("${mcp.cred-url}") String url, @Value("${mcp.gateway-key:}") String gatewayKey) {
        return mcpClient("cred-mcp", url, gatewayKey);
    }

    private static McpClient mcpClient(String chave, String url, String gatewayKey) {
        return DefaultMcpClient.builder()
                .key(chave)
                .clientName("investimentos-agent")
                .transport(StreamableHttpMcpTransport.builder()
                        .url(url)
                        .timeout(Duration.ofSeconds(30))
                        .customHeaders(cabecalhosGateway(gatewayKey))
                        .build())
                .toolExecutionTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Autenticacao no LiteLLM (MCP gateway). Sem chave (acesso direto ao MCP, fora do compose), nenhum header. */
    static Map<String, String> cabecalhosGateway(String chave) {
        return chave == null || chave.isBlank() ? Map.of() : Map.of("x-litellm-api-key", "Bearer " + chave);
    }

    /**
     * Tools da jornada de investimentos. consultar_solicitacoes_credito (cred-mcp) e da Ana, nao do especialista.
     * Allowlist: uma tool nova de um MCP do especialista tambem precisa ser adicionada aqui, senao fica oculta.
     */
    static final List<String> TOOLS_DO_ESPECIALISTA = List.of("listar_posicoes_cdb", "listar_resgates_cdb",
            "listar_movimentacoes", "consultar_status_transferencia", "consultar_conta_garantia");

    /**
     * failIfOneServerFails=false: um MCP fora não derruba o especialista; a falha vira risk na resposta.
     * Pelo gateway as tools chegam como "{servidor}-{tool}": o filtro roda ANTES do mapper (compara sem prefixo)
     * e o mapper mostra ao LLM o nome original. A execucao usa o nome real (com prefixo), aceito pelo gateway.
     */
    static McpToolProvider provedorMcp(McpClient... clients) {
        return McpToolProvider.builder()
                .mcpClients(clients)
                .failIfOneServerFails(false)
                .filter((client, spec) -> TOOLS_DO_ESPECIALISTA.contains(NomeTool.semPrefixo(spec.name())))
                .toolNameMapper((client, spec) -> NomeTool.semPrefixo(spec.name()))
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

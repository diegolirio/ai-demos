package poc.a2a.ana.config;

import java.time.Duration;

import javax.sql.DataSource;

import dev.langchain4j.community.store.memory.chat.sql.PostgreSQLDialect;
import dev.langchain4j.community.store.memory.chat.sql.SQLChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import poc.a2a.ana.assistente.AnaAssistant;
import poc.a2a.ana.assistente.AnaFactory;
import poc.a2a.ana.assistente.ConsultaCreditoTool;
import poc.a2a.ana.assistente.DelegacaoInvestimentosTool;
import poc.a2a.ana.assistente.UltimasConsultasCredito;
import poc.a2a.ana.assistente.UltimasRespostasInvestimentos;
import poc.a2a.ana.atendimento.HistoricoAtendimentos;
import poc.a2a.ana.atendimento.JdbcHistoricoAtendimentos;
import poc.a2a.ana.credito.CredMcpSolicitacoesCredito;
import poc.a2a.ana.credito.SolicitacoesCredito;
import poc.a2a.ana.investimentos.InvestimentosA2aClient;
import poc.a2a.ana.investimentos.InvestimentosClient;

/** Beans que dependem de LLM e Postgres. Fora do profile "test". */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class AnaConfig {

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

    /**
     * Tipo de retorno InvestimentosA2aClient (nao a interface) para que o Spring enxergue AutoCloseable
     * e feche o executor de virtual threads no shutdown. Pontos de injecao que pedem InvestimentosClient
     * continuam funcionando normalmente.
     */
    @Bean(destroyMethod = "close")
    InvestimentosA2aClient investimentosClient(@Value("${investimentos.a2a-url}") String url,
                                                @Value("${investimentos.timeout}") Duration timeout) {
        return new InvestimentosA2aClient(url, timeout);
    }

    @Bean
    HistoricoAtendimentos historicoAtendimentos(DataSource memoriaDataSource) {
        return new JdbcHistoricoAtendimentos(memoriaDataSource);
    }

    /**
     * Tipo de retorno concreto para o Spring enxergar AutoCloseable. Nao conecta aqui: o McpClient e criado na
     * primeira consulta (a Ana sobe mesmo com o cred-mcp fora).
     */
    @Bean(destroyMethod = "close")
    CredMcpSolicitacoesCredito solicitacoesCredito(@Value("${cred.mcp-url}") String url,
                                                   @Value("${cred.timeout}") Duration timeout) {
        return CredMcpSolicitacoesCredito.conectandoEm(url, timeout);
    }

    @Bean
    AnaAssistant anaAssistant(ChatModel chatModel, ChatMemoryProvider chatMemoryProvider,
                              InvestimentosClient investimentosClient, UltimasRespostasInvestimentos ultimas,
                              SolicitacoesCredito solicitacoesCredito, UltimasConsultasCredito ultimasCredito,
                              HistoricoAtendimentos historicoAtendimentos) {
        return AnaFactory.criar(chatModel, chatMemoryProvider,
                new DelegacaoInvestimentosTool(investimentosClient, ultimas, historicoAtendimentos),
                new ConsultaCreditoTool(solicitacoesCredito, ultimasCredito, historicoAtendimentos));
    }
}

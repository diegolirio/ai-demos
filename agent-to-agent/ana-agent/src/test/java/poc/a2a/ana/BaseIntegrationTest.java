package poc.a2a.ana;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.ollama.OllamaContainer;

/**
 * Base dos testes de integracao (mesmo padrao do analizza-auction): aplicacao inteira em porta aleatoria, sem
 * profile "test" (AnaConfig carrega de verdade), Postgres e LLM reais em containers singleton.
 * Saidas para OUTROS sistemas (o especialista via A2A) sao mockadas com @MockitoBean em cada IT.
 */
@Tag("integration")
@SpringBootTest(
        classes = AnaAgentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public abstract class BaseIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER = startPostgresContainer();

    protected static final OllamaContainer OLLAMA_CONTAINER = OllamaTestContainer.OLLAMA;

    protected RestTestClient restTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource memoriaDataSource;

    @BeforeEach
    void configureRestTestClient() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    // chat_memory e criada pelo SQLChatMemoryStore (autoCreateTable) no startup; o guard cobre o caso de ainda nao existir.
    @BeforeEach
    void clearDatabase() throws SQLException {
        try (Connection connection = memoriaDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DO $$ BEGIN IF to_regclass('chat_memory') IS NOT NULL "
                    + "THEN DELETE FROM chat_memory; END IF; END $$");
        }
    }

    /** Linhas de memoria persistidas no Postgres para o memoryId (sessionId na Ana). */
    protected int chatMemoryRows(String memoryId) throws SQLException {
        try (Connection connection = memoriaDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM chat_memory WHERE memory_id = ?")) {
            statement.setString(1, memoryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Schema default "public" do container (sem currentSchema, ao contrario do compose que usa ?currentSchema=ana).
        registry.add("memoria.jdbc-url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("memoria.usuario", POSTGRES_CONTAINER::getUsername);
        registry.add("memoria.senha", POSTGRES_CONTAINER::getPassword);
        registry.add("llm.base-url", OllamaTestContainer::baseUrlOpenAi);
        registry.add("llm.api-key", () -> "ollama");
        registry.add("llm.model", () -> OllamaTestContainer.MODELO);
        // Inferencia em CPU e lenta: cada chamada ao modelo pode levar minutos.
        registry.add("llm.timeout", () -> "300s");
    }

    // Same image as the development docker-compose.yml (postgres:17-alpine).
    private static PostgreSQLContainer<?> startPostgresContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:17-alpine");
        container.start();
        return container;
    }

}

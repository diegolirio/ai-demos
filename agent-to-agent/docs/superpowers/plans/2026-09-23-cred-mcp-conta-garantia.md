# cred-mcp (conta garantia) + CPF + histórico de atendimentos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** localizar resgates de CDB retidos em conta garantia por gastos no cartão (novo `cred-mcp`), identificar o cliente por CPF no chat e fazer a Ana lembrar de atendimentos anteriores do mesmo cliente em sessões novas.

**Architecture:** novo MCP server `cred-mcp` (:8084) no mesmo molde do `cdb-mcp`, plugado como 3º `McpClient` do especialista; o §9 ganha `situacaoGarantia` opcional validado no executor A2A. A Ana resolve CPF → `customerId` (cadastro mock), grava um registro por delegação em `atendimento` (Postgres, JDBC) e injeta os 3 mais recentes de outras sessões no system prompt via `@V`. O chat-web troca o select de cliente por um campo de CPF.

**Tech Stack:** Java 25, Spring Boot 4.1.1, MCP Java SDK 2.0.1 (Jackson 3), LangChain4j 1.20.0, a2a-java, Postgres 17, JUnit 5 + AssertJ + Testcontainers, Next.js 16 + Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-23-cred-mcp-conta-garantia-design.md`

## Global Constraints

- JDK: `JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.2-tem` (o `Makefile` já exporta). Rodar Maven com esse `JAVA_HOME`.
- Status de garantia (valores exatos): `LIBERADO_CONTA`, `EM_ANALISE`, `RETIDO_ATE_PAGAMENTO_FATURA`, `RETIDO_PARCIAL`.
- Invariante: `valorRetido + valorLiberado = valorResgatado`.
- CPFs de teste → customerId: `111.001.001-05`→cli-001, `222.002.002-93`→cli-002, `333.003.003-80`→cli-003, `444.004.004-76`→cli-004, `555.005.005-62`→cli-005, `666.006.006-59`→cli-006, `777.007.007-45`→cli-007, `888.008.008-31`→cli-008.
- CPF nunca vai ao LLM, à memória de chat, ao A2A nem aos MCPs; em log só `Cpf.mascarado()` (`***.***.*XX-XX`).
- Mensagens de erro da Ana (400): `"CPF invalido"`, `"cliente nao encontrado"`, `"sessionId, cpf e message sao obrigatorios"`; corpo `{"error": "<motivo>"}`.
- Cenários `cli-001`..`cli-004` continuam com o mesmo comportamento (`situacaoGarantia` nulo).
- Porta do cred-mcp: 8084; env no especialista: `CRED_MCP_URL` (default `http://localhost:8084/mcp`).
- Textos de código/prompt em português sem acento (padrão atual dos `.java` e prompts); UI e docs com acento.
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: Serviço `cred-mcp` (tool `consultar_conta_garantia`) + compose/Makefile

**Files:**
- Create: `cred-mcp/pom.xml`, `cred-mcp/Dockerfile`, `cred-mcp/src/main/resources/application.yml`
- Create: `cred-mcp/src/main/java/poc/a2a/cred/CredMcpApplication.java`
- Create: `cred-mcp/src/main/java/poc/a2a/cred/McpServerConfig.java`
- Create: `cred-mcp/src/main/java/poc/a2a/cred/ContaGarantiaRepository.java`
- Create: `cred-mcp/src/main/java/poc/a2a/cred/ContaGarantiaTools.java`
- Test: `cred-mcp/src/test/java/poc/a2a/cred/CredMcpServerTest.java`, `cred-mcp/src/test/java/poc/a2a/cred/ContaGarantiaRepositoryTest.java`
- Modify: `docker-compose.yml`, `Makefile`

**Interfaces:**
- Produces: tool MCP `consultar_conta_garantia` com entrada `{customerId}`; saída array JSON de `RetencaoGarantia` (campos `resgateId, dataEntrada, status, valorResgatado, valorRetido, valorLiberado, gastoCartao, vencimentoFatura, detalhe`). Serviço `cred-mcp` no compose (porta 8084, healthcheck).

- [ ] **Step 1: Scaffold do módulo (pom, Dockerfile, application.yml, Application)**

`cred-mcp/pom.xml` — cópia do `cdb-mcp/pom.xml` trocando só o `artifactId`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>poc.a2a</groupId>
    <artifactId>cred-mcp</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>25</java.version>
        <mcp-sdk.version>2.0.1</mcp-sdk.version>
        <langchain4j-beta.version>1.20.0-beta30</langchain4j-beta.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <!-- "mcp" = mcp-core + mcp-json-jackson3 (Jackson 3, same as Boot 4) -->
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp</artifactId>
            <version>${mcp-sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- real MCP client for the integration test -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-mcp</artifactId>
            <version>${langchain4j-beta.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`cred-mcp/Dockerfile`:

```dockerfile
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`cred-mcp/src/main/resources/application.yml`:

```yaml
server:
  port: 8084
spring:
  application:
    name: cred-mcp
management:
  endpoints:
    web:
      exposure:
        include: health
```

`cred-mcp/src/main/java/poc/a2a/cred/CredMcpApplication.java`:

```java
package poc.a2a.cred;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CredMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(CredMcpApplication.class, args);
    }
}
```

- [ ] **Step 2: Escrever o teste do repositório (invariante + cenários)**

`cred-mcp/src/test/java/poc/a2a/cred/ContaGarantiaRepositoryTest.java`:

```java
package poc.a2a.cred;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import poc.a2a.cred.ContaGarantiaRepository.RetencaoGarantia;
import poc.a2a.cred.ContaGarantiaRepository.StatusGarantia;

class ContaGarantiaRepositoryTest {

    final ContaGarantiaRepository repository = new ContaGarantiaRepository();

    static List<String> todosOsClientes() {
        return IntStream.rangeClosed(1, 8).mapToObj(i -> "cli-00" + i).toList();
    }

    @Test
    void retidoMaisLiberadoEIgualAoResgatadoEmTodoOMock() {
        List<RetencaoGarantia> todas = todosOsClientes().stream().flatMap(c -> repository.retencoes(c).stream()).toList();

        assertThat(todas).hasSize(4);
        assertThat(todas).allSatisfy(r ->
                assertThat(r.valorRetido().add(r.valorLiberado())).isEqualByComparingTo(r.valorResgatado()));
    }

    @Test
    void cenariosAntigosNaoPassamPelaGarantia() {
        assertThat(List.of("cli-001", "cli-002", "cli-003", "cli-004"))
                .allSatisfy(c -> assertThat(repository.retencoes(c)).isEmpty());
    }

    @Test
    void umStatusPorCliente() {
        assertThat(repository.retencoes("cli-005")).singleElement()
                .extracting(RetencaoGarantia::status).isEqualTo(StatusGarantia.LIBERADO_CONTA);
        assertThat(repository.retencoes("cli-006")).singleElement()
                .extracting(RetencaoGarantia::status).isEqualTo(StatusGarantia.EM_ANALISE);
        assertThat(repository.retencoes("cli-007")).singleElement()
                .extracting(RetencaoGarantia::status).isEqualTo(StatusGarantia.RETIDO_ATE_PAGAMENTO_FATURA);
        assertThat(repository.retencoes("cli-008")).singleElement()
                .satisfies(r -> {
                    assertThat(r.status()).isEqualTo(StatusGarantia.RETIDO_PARCIAL);
                    assertThat(r.valorRetido()).isEqualByComparingTo(new BigDecimal("3500.00"));
                    assertThat(r.valorLiberado()).isEqualByComparingTo(new BigDecimal("6500.00"));
                    assertThat(r.vencimentoFatura()).isEqualTo(LocalDate.of(2026, 10, 5));
                });
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `cd cred-mcp && mvn -q test -Dtest=ContaGarantiaRepositoryTest`
Expected: FAIL de compilação (`ContaGarantiaRepository` não existe).

- [ ] **Step 4: Implementar o repositório**

`cred-mcp/src/main/java/poc/a2a/cred/ContaGarantiaRepository.java`:

```java
package poc.a2a.cred;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Mock em memoria: resgates de investimento retidos em conta garantia por gastos no cartao de credito. */
@Component
public class ContaGarantiaRepository {

    public enum StatusGarantia { LIBERADO_CONTA, EM_ANALISE, RETIDO_ATE_PAGAMENTO_FATURA, RETIDO_PARCIAL }

    public record RetencaoGarantia(String resgateId, LocalDateTime dataEntrada, StatusGarantia status,
                                   BigDecimal valorResgatado, BigDecimal valorRetido, BigDecimal valorLiberado,
                                   BigDecimal gastoCartao, LocalDate vencimentoFatura, String detalhe) {
    }

    private static final BigDecimal DEZ_MIL = new BigDecimal("10000.00");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final LocalDateTime ENTRADA = LocalDateTime.of(2026, 9, 22, 9, 5);
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 10, 5);

    private final Map<String, List<RetencaoGarantia>> retencoes = Map.of(
            "cli-005", List.of(new RetencaoGarantia("res-005", ENTRADA, StatusGarantia.LIBERADO_CONTA,
                    DEZ_MIL, ZERO, DEZ_MIL, ZERO, null,
                    "Analise concluida sem pendencias no cartao; valor encaminhado para a conta corrente")),
            "cli-006", List.of(new RetencaoGarantia("res-006", ENTRADA, StatusGarantia.EM_ANALISE,
                    DEZ_MIL, DEZ_MIL, ZERO, new BigDecimal("4200.00"), null,
                    "Resgate em analise na conta garantia por gastos no cartao de credito; sem prazo definido")),
            "cli-007", List.of(new RetencaoGarantia("res-007", ENTRADA, StatusGarantia.RETIDO_ATE_PAGAMENTO_FATURA,
                    DEZ_MIL, DEZ_MIL, ZERO, new BigDecimal("12000.00"), VENCIMENTO,
                    "Valor retido como garantia da fatura do cartao; liberado apos o pagamento da fatura")),
            "cli-008", List.of(new RetencaoGarantia("res-008", ENTRADA, StatusGarantia.RETIDO_PARCIAL,
                    DEZ_MIL, new BigDecimal("3500.00"), new BigDecimal("6500.00"), new BigDecimal("3500.00"),
                    VENCIMENTO,
                    "R$ 3500.00 retidos como garantia da fatura do cartao; R$ 6500.00 liberados para a conta corrente")));

    public List<RetencaoGarantia> retencoes(String customerId) {
        return retencoes.getOrDefault(customerId, List.of());
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `cd cred-mcp && mvn -q test -Dtest=ContaGarantiaRepositoryTest`
Expected: PASS (3 testes).

- [ ] **Step 6: Escrever o teste do servidor MCP (cliente MCP real)**

`cred-mcp/src/test/java/poc/a2a/cred/CredMcpServerTest.java`:

```java
package poc.a2a.cred;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CredMcpServerTest {

    @LocalServerPort
    int port;

    McpClient client;

    @BeforeEach
    void conectar() {
        client = DefaultMcpClient.builder()
                .key("cred-mcp")
                .transport(StreamableHttpMcpTransport.builder()
                        .url("http://localhost:" + port + "/mcp")
                        .timeout(Duration.ofSeconds(10))
                        .build())
                .build();
    }

    @AfterEach
    void fechar() throws Exception {
        client.close();
    }

    private String consultar(String customerId) {
        return client.executeTool(ToolExecutionRequest.builder()
                .id("1").name("consultar_conta_garantia")
                .arguments("{\"customerId\":\"" + customerId + "\"}").build()).resultText();
    }

    @Test
    void listaAToolComCustomerIdObrigatorio() {
        List<ToolSpecification> tools = client.listTools();

        assertThat(tools).extracting(ToolSpecification::name).containsExactly("consultar_conta_garantia");
        assertThat(tools.getFirst().parameters().required()).containsExactly("customerId");
    }

    @Test
    void cli005FoiLiberadoParaAConta() {
        assertThat(consultar("cli-005"))
                .contains("\"resgateId\":\"res-005\"").contains("\"status\":\"LIBERADO_CONTA\"")
                .contains("\"valorLiberado\":10000.00");
    }

    @Test
    void cli006EstaEmAnalise() {
        assertThat(consultar("cli-006")).contains("\"status\":\"EM_ANALISE\"").contains("4200.00");
    }

    @Test
    void cli007RetidoAtePagarAFatura() {
        assertThat(consultar("cli-007"))
                .contains("\"status\":\"RETIDO_ATE_PAGAMENTO_FATURA\"")
                .contains("\"gastoCartao\":12000.00").contains("\"vencimentoFatura\":\"2026-10-05\"");
    }

    @Test
    void cli008RetidoParcialmente() {
        assertThat(consultar("cli-008"))
                .contains("\"status\":\"RETIDO_PARCIAL\"")
                .contains("\"valorRetido\":3500.00").contains("\"valorLiberado\":6500.00");
    }

    @Test
    void semRetencaoDevolveListaVazia() {
        assertThat(consultar("cli-001")).isEqualTo("[]");
    }

    @Test
    void rejeitaEntradaForaDoSchema() {
        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                .id("1").name("consultar_conta_garantia").arguments("{\"foo\":\"bar\"}").build()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("input validation failed");
    }
}
```

- [ ] **Step 7: Rodar e ver falhar**

Run: `cd cred-mcp && mvn -q test -Dtest=CredMcpServerTest`
Expected: FAIL (nenhum `/mcp` registrado — conexão MCP falha / tool inexistente).

- [ ] **Step 8: Implementar tools e config do servidor**

`cred-mcp/src/main/java/poc/a2a/cred/ContaGarantiaTools.java`:

```java
package poc.a2a.cred;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ContaGarantiaTools {

    private static final Logger log = LoggerFactory.getLogger(ContaGarantiaTools.class);

    private static final Map<String, Object> CUSTOMER_ID_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("customerId", Map.of(
                    "type", "string",
                    "minLength", 1,
                    "description", "Identificador do cliente, ex.: cli-001")),
            "required", List.of("customerId"),
            "additionalProperties", false);

    private final ContaGarantiaRepository repository;
    private final JsonMapper jsonMapper;

    public ContaGarantiaTools(ContaGarantiaRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(consultarContaGarantia());
    }

    private SyncToolSpecification consultarContaGarantia() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("consultar_conta_garantia", CUSTOMER_ID_SCHEMA)
                        .description("Lista as retencoes em conta garantia de resgates de investimento do cliente, "
                                + "motivadas por gastos no cartao de credito: resgateId, status (LIBERADO_CONTA | "
                                + "EM_ANALISE | RETIDO_ATE_PAGAMENTO_FATURA | RETIDO_PARCIAL), valorResgatado, "
                                + "valorRetido, valorLiberado, gastoCartao, vencimentoFatura e detalhe. "
                                + "Retorna um array JSON (vazio se o resgate nao passou pela conta garantia).")
                        .build())
                .callHandler((exchange, request) -> {
                    String customerId = (String) request.arguments().get("customerId");
                    var resultado = repository.retencoes(customerId);
                    log.info("mcp.tool.call tool=consultar_conta_garantia customerId={} itens={}",
                            customerId, resultado.size());
                    return CallToolResult.builder()
                            .addTextContent(jsonMapper.writeValueAsString(resultado))
                            .isError(false)
                            .build();
                })
                .build();
    }
}
```

`cred-mcp/src/main/java/poc/a2a/cred/McpServerConfig.java`:

```java
package poc.a2a.cred;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class McpServerConfig {

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransportProvider(JsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        var registration = new ServletRegistrationBean<>(transport, "/mcp");
        registration.setName("mcpServlet");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "closeGracefully")
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, ContaGarantiaTools tools,
                            JsonMapper jsonMapper) {
        return McpServer.sync(transport)
                .serverInfo("cred-mcp", "0.0.1")
                .instructions("Credito: resgates de investimento retidos em conta garantia por gastos no cartao.")
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();
    }
}
```

- [ ] **Step 9: Rodar todos os testes do módulo**

Run: `cd cred-mcp && mvn -q test`
Expected: PASS (9 testes). Se `"valorLiberado":10000.00` vier como `10000.0`, o Jackson está normalizando o `BigDecimal` — os valores foram criados com escala 2 via `new BigDecimal("...")`, então deve sair `10000.00` como no `cdb-mcp` (`5000.00`).

- [ ] **Step 10: Compose e Makefile**

`docker-compose.yml` — adicionar, depois do serviço `tracking-money-mcp`:

```yaml
  cred-mcp:
    build: ./cred-mcp
    ports:
      - "8084:8084"
    healthcheck:
      <<: *healthcheck
      test: ["CMD-SHELL", "curl -fs http://localhost:8084/actuator/health || exit 1"]
```

(A env `CRED_MCP_URL` e o `depends_on` do `investimentos-agent` entram na Task 4, junto com o cliente MCP.)

`Makefile`:
- `SERVICES := cdb-mcp tracking-money-mcp cred-mcp investimentos-agent ana-agent`
- Substituir o comentário e o alvo `run-mcps` por:

```makefile
# Os três MCP servers em background com saída prefixada (Ctrl+C encerra todos). Não usam Postgres nem LLM.
run-mcps:
	@echo "cdb-mcp            -> http://localhost:8083/mcp"
	@echo "tracking-money-mcp -> http://localhost:8082/mcp"
	@echo "cred-mcp           -> http://localhost:8084/mcp"
	@trap 'kill 0' INT TERM; \
		( cd cdb-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[cdb-mcp] " $$0; fflush() }' ) & \
		( cd tracking-money-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[tracking-money-mcp] " $$0; fflush() }' ) & \
		( cd cred-mcp && mvn -q spring-boot:run 2>&1 | awk '{ print "[cred-mcp] " $$0; fflush() }' ) & \
		wait
```

- [ ] **Step 11: Validar compose e build**

Run: `LLM_API_KEY=x docker compose config --services && (cd cred-mcp && mvn -q -DskipTests package) && ls cred-mcp/target/*.jar`
Expected: lista inclui `cred-mcp`; jar gerado.

- [ ] **Step 12: Commit**

```bash
git add cred-mcp docker-compose.yml Makefile
git commit -m "feat(cred-mcp): MCP server da conta garantia (consultar_conta_garantia)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Cenários cli-005..cli-008 no `cdb-mcp` e no `tracking-money-mcp`

**Files:**
- Modify: `cdb-mcp/src/main/java/poc/a2a/cdb/CdbRepository.java`
- Modify: `tracking-money-mcp/src/main/java/poc/a2a/trackingmoney/TrackingMoneyRepository.java`
- Test: `cdb-mcp/src/test/java/poc/a2a/cdb/CdbMcpServerTest.java`, `tracking-money-mcp/src/test/java/poc/a2a/trackingmoney/TrackingMoneyMcpServerTest.java`

**Interfaces:**
- Produces: `listar_resgates_cdb` retorna `res-00N` (`pos-00N`, 10000.00, `LIQUIDADO`, `2026-09-22T09:00:00`) para cli-005..008; `listar_movimentacoes` retorna `mov-005` (10000.00 CONCLUIDA, `trf-005`) e `mov-008` (6500.00 CONCLUIDA, `trf-008`); `consultar_status_transferencia` conhece `trf-005` e `trf-008` (`CONCLUIDA`).

- [ ] **Step 1: Testes que falham (cdb-mcp)**

Adicionar em `CdbMcpServerTest`:

```java
    @Test
    void cli005A008TemResgateLiquidadoDeDezMil() {
        for (int i = 5; i <= 8; i++) {
            assertThat(chamar("listar_resgates_cdb", "{\"customerId\":\"cli-00" + i + "\"}"))
                    .contains("\"resgateId\":\"res-00" + i + "\"")
                    .contains("\"status\":\"LIQUIDADO\"").contains("10000.00");
        }
    }
```

- [ ] **Step 2: Testes que falham (tracking-money-mcp)**

Adicionar em `TrackingMoneyMcpServerTest`:

```java
    @Test
    void cli005RecebeuOResgateIntegral() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-005\"}"))
                .contains("\"status\":\"CONCLUIDA\"").contains("10000.00").contains("\"transferenciaId\":\"trf-005\"");
        assertThat(chamar("consultar_status_transferencia", "{\"transferenciaId\":\"trf-005\"}"))
                .contains("\"status\":\"CONCLUIDA\"");
    }

    @Test
    void cli008RecebeuSoAParteLiberada() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-008\"}"))
                .contains("\"status\":\"CONCLUIDA\"").contains("6500.00").contains("\"transferenciaId\":\"trf-008\"");
        assertThat(chamar("consultar_status_transferencia", "{\"transferenciaId\":\"trf-008\"}"))
                .contains("\"status\":\"CONCLUIDA\"");
    }

    @Test
    void cli006ECli007NaoRecebemCredito() {
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-006\"}")).isEqualTo("[]");
        assertThat(chamar("listar_movimentacoes", "{\"customerId\":\"cli-007\"}")).isEqualTo("[]");
    }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `(cd cdb-mcp && mvn -q test) ; (cd tracking-money-mcp && mvn -q test)`
Expected: FAIL em `cli005A008TemResgateLiquidadoDeDezMil`, `cli005RecebeuOResgateIntegral`, `cli008RecebeuSoAParteLiberada` (`cli006ECli007...` já passa).

- [ ] **Step 4: Implementar mocks**

Em `CdbRepository`, trocar o `Map.of` de `resgates` por:

```java
    private static ResgateCdb liquidadoDezMil(int n) {
        return new ResgateCdb("res-00" + n, "pos-00" + n, new BigDecimal("10000.00"),
                StatusResgate.LIQUIDADO, LocalDateTime.of(2026, 9, 22, 9, 0));
    }

    private final Map<String, List<ResgateCdb>> resgates = Map.of(
            "cli-001", List.of(new ResgateCdb("res-001", "pos-001", new BigDecimal("5000.00"),
                    StatusResgate.EM_LIQUIDACAO, LocalDateTime.of(2026, 9, 22, 10, 0))),
            "cli-002", List.of(new ResgateCdb("res-002", "pos-002", new BigDecimal("3000.00"),
                    StatusResgate.LIQUIDADO, LocalDateTime.of(2026, 9, 21, 9, 30))),
            // cli-005..cli-008: resgate liquidado que passou pela conta garantia (ver cred-mcp)
            "cli-005", List.of(liquidadoDezMil(5)),
            "cli-006", List.of(liquidadoDezMil(6)),
            "cli-007", List.of(liquidadoDezMil(7)),
            "cli-008", List.of(liquidadoDezMil(8)));
```

(o método estático fica acima do campo, pois inicializadores de instância podem chamar métodos estáticos.)

Em `TrackingMoneyRepository`, trocar os dois mapas por:

```java
    private final Map<String, List<Movimentacao>> movimentacoes = Map.of(
            "cli-001", List.of(new Movimentacao("mov-001", TipoMovimentacao.CREDITO, new BigDecimal("5000.00"),
                    "Resgate CDB res-001", LocalDateTime.of(2026, 9, 22, 10, 1),
                    StatusMovimentacao.PROCESSANDO, "trf-001")),
            "cli-002", List.of(new Movimentacao("mov-002", TipoMovimentacao.CREDITO, new BigDecimal("3000.00"),
                    "Resgate CDB res-002", LocalDateTime.of(2026, 9, 21, 9, 45),
                    StatusMovimentacao.CONCLUIDA, "trf-002")),
            "cli-005", List.of(new Movimentacao("mov-005", TipoMovimentacao.CREDITO, new BigDecimal("10000.00"),
                    "Resgate CDB res-005 liberado da conta garantia", LocalDateTime.of(2026, 9, 22, 11, 0),
                    StatusMovimentacao.CONCLUIDA, "trf-005")),
            "cli-008", List.of(new Movimentacao("mov-008", TipoMovimentacao.CREDITO, new BigDecimal("6500.00"),
                    "Resgate CDB res-008 liberado parcialmente da conta garantia", LocalDateTime.of(2026, 9, 22, 11, 0),
                    StatusMovimentacao.CONCLUIDA, "trf-008")));

    private final Map<String, StatusTransferencia> transferencias = Map.of(
            "trf-001", new StatusTransferencia("trf-001", "EM_PROCESSAMENTO", "ate 30 minutos",
                    "Liquidacao do resgate em andamento; o credito sera efetuado na conta corrente"),
            "trf-002", new StatusTransferencia("trf-002", "CONCLUIDA", "ja efetuado",
                    "Credito efetuado na conta corrente"),
            "trf-005", new StatusTransferencia("trf-005", "CONCLUIDA", "ja efetuado",
                    "Credito efetuado na conta corrente apos liberacao da conta garantia"),
            "trf-008", new StatusTransferencia("trf-008", "CONCLUIDA", "ja efetuado",
                    "Credito parcial efetuado; parte do resgate segue retida na conta garantia"));
```

- [ ] **Step 5: Rodar e ver passar**

Run: `(cd cdb-mcp && mvn -q test) && (cd tracking-money-mcp && mvn -q test)`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add cdb-mcp tracking-money-mcp
git commit -m "feat(mcp): cenarios cli-005..cli-008 de resgate liquidado com conta garantia

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: §9 com `situacaoGarantia` validado no investimentos-agent

**Files:**
- Create: `investimentos-agent/src/main/java/poc/a2a/investimentos/especialista/SituacaoGarantia.java`
- Modify: `investimentos-agent/src/main/java/poc/a2a/investimentos/especialista/RespostaEspecialista.java`
- Modify: `investimentos-agent/src/main/java/poc/a2a/investimentos/a2a/InvestimentosAgentExecutor.java`
- Test: `investimentos-agent/src/test/java/poc/a2a/investimentos/especialista/RespostaEspecialistaTest.java` (novo), `investimentos-agent/src/test/java/poc/a2a/investimentos/a2a/A2aServerTest.java`

**Interfaces:**
- Produces:
  - `record SituacaoGarantia(String status, BigDecimal valorResgatado, BigDecimal valorRetido, BigDecimal valorLiberado, String proximoPasso)` com `boolean consistente()` e `Map<String, Object> comoMapa()`.
  - `RespostaEspecialista(List<String> facts, String answerDraft, double confidence, List<String> risks, List<String> sources, SituacaoGarantia situacaoGarantia)` + construtor de 5 args (situacaoGarantia = null) + `RespostaEspecialista validada()`.
  - Constante `RespostaEspecialista.RISCO_GARANTIA_DESCARTADA = "situacaoGarantia inconsistente descartada"`.
  - DataPart A2A: chave `situacaoGarantia` presente só quando não nula, com `status, valorResgatado, valorRetido, valorLiberado, proximoPasso`.

- [ ] **Step 1: Teste unitário que falha**

`investimentos-agent/src/test/java/poc/a2a/investimentos/especialista/RespostaEspecialistaTest.java`:

```java
package poc.a2a.investimentos.especialista;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RespostaEspecialistaTest {

    static SituacaoGarantia parcial(String status, String retido, String liberado) {
        return new SituacaoGarantia(status, new BigDecimal("10000.00"), new BigDecimal(retido),
                new BigDecimal(liberado), "Pagar a fatura do cartao para liberar o restante");
    }

    static RespostaEspecialista com(SituacaoGarantia situacao) {
        return new RespostaEspecialista(List.of("fato"), "rascunho", 0.9, List.of(), List.of("cred-mcp"), situacao);
    }

    @Test
    void mantemSituacaoConsistente() {
        RespostaEspecialista resposta = com(parcial("RETIDO_PARCIAL", "3500.00", "6500.00")).validada();

        assertThat(resposta.situacaoGarantia().status()).isEqualTo("RETIDO_PARCIAL");
        assertThat(resposta.risks()).isEmpty();
    }

    @Test
    void descartaStatusDesconhecido() {
        RespostaEspecialista resposta = com(parcial("BLOQUEADO", "3500.00", "6500.00")).validada();

        assertThat(resposta.situacaoGarantia()).isNull();
        assertThat(resposta.risks()).containsExactly(RespostaEspecialista.RISCO_GARANTIA_DESCARTADA);
    }

    @Test
    void descartaSomaQueNaoFecha() {
        RespostaEspecialista resposta = com(parcial("RETIDO_PARCIAL", "3500.00", "7000.00")).validada();

        assertThat(resposta.situacaoGarantia()).isNull();
        assertThat(resposta.risks()).containsExactly(RespostaEspecialista.RISCO_GARANTIA_DESCARTADA);
    }

    @Test
    void semSituacaoNaoMudaNada() {
        RespostaEspecialista original = new RespostaEspecialista(List.of("f"), "r", 0.5, List.of("x"), List.of("cdb-mcp"));

        assertThat(original.validada()).isEqualTo(original);
        assertThat(original.comoMapa()).doesNotContainKey("situacaoGarantia");
    }

    @Test
    void mapaDoDataPartIncluiASituacao() {
        Map<String, Object> mapa = com(parcial("RETIDO_PARCIAL", "3500.00", "6500.00")).comoMapa();

        assertThat(mapa.get("situacaoGarantia")).isInstanceOfSatisfying(Map.class, s -> {
            assertThat(s.get("status")).isEqualTo("RETIDO_PARCIAL");
            assertThat(s.get("valorRetido")).isEqualTo(new BigDecimal("3500.00"));
        });
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd investimentos-agent && mvn -q test -Dtest=RespostaEspecialistaTest`
Expected: FAIL de compilação (`SituacaoGarantia` não existe).

- [ ] **Step 3: Implementar**

`SituacaoGarantia.java`:

```java
package poc.a2a.investimentos.especialista;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Onde esta o dinheiro de um resgate retido em conta garantia (preenchido pelo LLM a partir do cred-mcp). */
public record SituacaoGarantia(String status, BigDecimal valorResgatado, BigDecimal valorRetido,
                               BigDecimal valorLiberado, String proximoPasso) {

    static final Set<String> STATUS_VALIDOS =
            Set.of("LIBERADO_CONTA", "EM_ANALISE", "RETIDO_ATE_PAGAMENTO_FATURA", "RETIDO_PARCIAL");

    /** Status conhecido e retido + liberado = resgatado. O LLM pode errar valores; o executor descarta se falhar. */
    public boolean consistente() {
        return status != null && STATUS_VALIDOS.contains(status)
                && valorResgatado != null && valorRetido != null && valorLiberado != null
                && valorRetido.add(valorLiberado).compareTo(valorResgatado) == 0;
    }

    public Map<String, Object> comoMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("status", status);
        mapa.put("valorResgatado", valorResgatado);
        mapa.put("valorRetido", valorRetido);
        mapa.put("valorLiberado", valorLiberado);
        mapa.put("proximoPasso", proximoPasso == null ? "" : proximoPasso);
        return mapa;
    }
}
```

`RespostaEspecialista.java` (substituir inteiro):

```java
package poc.a2a.investimentos.especialista;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema padrão de resposta de especialista (guideline §9) + situacaoGarantia opcional. */
public record RespostaEspecialista(List<String> facts, String answerDraft, double confidence,
                                   List<String> risks, List<String> sources, SituacaoGarantia situacaoGarantia) {

    public static final String RISCO_GARANTIA_DESCARTADA = "situacaoGarantia inconsistente descartada";

    public RespostaEspecialista(List<String> facts, String answerDraft, double confidence,
                                List<String> risks, List<String> sources) {
        this(facts, answerDraft, confidence, risks, sources, null);
    }

    /** Descarta situacaoGarantia inconsistente (status fora do enum ou soma que nao fecha) e registra em risks. */
    public RespostaEspecialista validada() {
        if (situacaoGarantia == null || situacaoGarantia.consistente()) {
            return this;
        }
        List<String> novosRisks = new ArrayList<>(risks == null ? List.of() : risks);
        novosRisks.add(RISCO_GARANTIA_DESCARTADA);
        return new RespostaEspecialista(facts, answerDraft, confidence, List.copyOf(novosRisks), sources, null);
    }

    /** Formato do DataPart A2A. */
    public Map<String, Object> comoMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("facts", facts == null ? List.of() : facts);
        mapa.put("answerDraft", answerDraft);
        mapa.put("confidence", confidence);
        mapa.put("risks", risks == null ? List.of() : risks);
        mapa.put("sources", sources == null ? List.of() : sources);
        if (situacaoGarantia != null) {
            mapa.put("situacaoGarantia", situacaoGarantia.comoMapa());
        }
        return mapa;
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd investimentos-agent && mvn -q test -Dtest=RespostaEspecialistaTest`
Expected: PASS (5 testes).

- [ ] **Step 5: Teste A2A que falha (validação no executor + serialização do DataPart)**

Em `A2aServerTest`, no `EspecialistaFake`, antes do `return new RespostaEspecialista(...)` final, adicionar:

```java
                if (pedido.contains("garantia-ok")) {
                    return new RespostaEspecialista(List.of("Resgate res-008 RETIDO_PARCIAL"),
                            "Parte do resgate foi liberada e parte segue retida.", 0.9, List.of(),
                            List.of("cdb-mcp", "cred-mcp"), new SituacaoGarantia("RETIDO_PARCIAL",
                            new BigDecimal("10000.00"), new BigDecimal("3500.00"), new BigDecimal("6500.00"),
                            "Pagar a fatura do cartao"));
                }
                if (pedido.contains("garantia-errada")) {
                    return new RespostaEspecialista(List.of("x"), "y", 0.9, List.of(), List.of("cred-mcp"),
                            new SituacaoGarantia("RETIDO_PARCIAL", new BigDecimal("10000.00"),
                                    new BigDecimal("3500.00"), new BigDecimal("9999.00"), "z"));
                }
```

imports: `java.math.BigDecimal`, `poc.a2a.investimentos.especialista.SituacaoGarantia`. Novos testes:

```java
    @Test
    void dataPartLevaASituacaoGarantia() {
        JsonNode dados = sendMessage("ctx-4", "pedido garantia-ok").path("result").path("task")
                .path("artifacts").get(0).path("parts").get(1).path("data");

        JsonNode situacao = dados.path("situacaoGarantia");
        assertThat(situacao.path("status").asString()).isEqualTo("RETIDO_PARCIAL");
        assertThat(situacao.path("valorRetido").decimalValue()).isEqualByComparingTo("3500.00");
        assertThat(situacao.path("valorLiberado").decimalValue()).isEqualByComparingTo("6500.00");
    }

    @Test
    void situacaoInconsistenteEDescartadaSemFalharATask() {
        JsonNode task = sendMessage("ctx-5", "pedido garantia-errada").path("result").path("task");

        assertThat(task.path("status").path("state").asString()).isEqualTo("TASK_STATE_COMPLETED");
        JsonNode dados = task.path("artifacts").get(0).path("parts").get(1).path("data");
        assertThat(dados.has("situacaoGarantia")).isFalse();
        assertThat(dados.path("risks").get(0).asString()).isEqualTo(RespostaEspecialista.RISCO_GARANTIA_DESCARTADA);
    }
```

- [ ] **Step 6: Rodar e ver falhar**

Run: `cd investimentos-agent && mvn -q test -Dtest=A2aServerTest`
Expected: `situacaoInconsistenteEDescartadaSemFalharATask` FAIL (situacao chega sem validação). `dataPartLevaASituacaoGarantia` já deve passar; se falhar na leitura de `decimalValue()` porque o SDK serializou como string, ajustar `SituacaoGarantia.comoMapa()` para `valor.doubleValue()` e manter o teste com `isEqualByComparingTo`.

- [ ] **Step 7: Validar no executor**

Em `InvestimentosAgentExecutor.execute`, trocar:

```java
            RespostaEspecialista resposta = especialista.investigar(context.getContextId(), pedido);
```

por:

```java
            RespostaEspecialista resposta = especialista.investigar(context.getContextId(), pedido).validada();
```

e acrescentar `situacaoGarantia={}` ao log `a2a.task.completed`:

```java
            log.info("a2a.task.completed contextId={} taskId={} confidence={} sources={} situacaoGarantia={} durationMs={}",
                    context.getContextId(), context.getTaskId(), resposta.confidence(), resposta.sources(),
                    resposta.situacaoGarantia() == null ? "-" : resposta.situacaoGarantia().status(),
                    (System.nanoTime() - inicio) / 1_000_000);
```

- [ ] **Step 8: Rodar todos os unitários do módulo**

Run: `cd investimentos-agent && mvn -q test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add investimentos-agent
git commit -m "feat(investimentos): situacaoGarantia no schema §9, validada no executor A2A

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Especialista consulta o `cred-mcp` (cliente MCP, prompt, fake do IT)

**Files:**
- Modify: `investimentos-agent/src/main/java/poc/a2a/investimentos/especialista/EspecialistaConfig.java`
- Modify: `investimentos-agent/src/main/resources/application.yml`
- Modify: `investimentos-agent/src/main/resources/prompts/especialista-investimentos.txt`
- Modify: `investimentos-agent/src/main/java/poc/a2a/investimentos/a2a/A2aServerConfig.java` (textos do Agent Card)
- Modify: `investimentos-agent/src/test/java/poc/a2a/investimentos/BaseIntegrationTest.java`, `investimentos-agent/src/test/java/poc/a2a/investimentos/McpToolProviderFake.java`
- Test: `investimentos-agent/src/test/java/poc/a2a/investimentos/especialista/EspecialistaInvestimentosTest.java`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: tool `consultar_conta_garantia` (Task 1); `SituacaoGarantia`/`RespostaEspecialista` (Task 3).
- Produces: bean `McpClient credMcpClient` (nome usado pelo `@MockitoBean` do IT); property `mcp.cred-url`.

- [ ] **Step 1: Teste que falha (LLM roteirizado caminho cli-008)**

Em `EspecialistaInvestimentosTest`, adicionar imports `java.math.BigDecimal`, `java.util.LinkedHashMap` e o teste:

```java
    static ToolSpecification toolPorCliente(String nome) {
        return ToolSpecification.builder().name(nome).description(nome)
                .parameters(JsonObjectSchema.builder().addStringProperty("customerId").required("customerId").build())
                .build();
    }

    @Test
    void cli008ConsultaContaGarantiaEPreencheSituacao() {
        Map<ToolSpecification, dev.langchain4j.service.tool.ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(toolPorCliente("listar_resgates_cdb"), (r, m) ->
                "[{\"resgateId\":\"res-008\",\"valor\":10000.00,\"status\":\"LIQUIDADO\"}]");
        tools.put(toolPorCliente("consultar_conta_garantia"), (r, m) ->
                "[{\"resgateId\":\"res-008\",\"status\":\"RETIDO_PARCIAL\",\"valorResgatado\":10000.00,"
                        + "\"valorRetido\":3500.00,\"valorLiberado\":6500.00,\"gastoCartao\":3500.00,"
                        + "\"vencimentoFatura\":\"2026-10-05\"}]");
        ToolProvider provider = request -> new ToolProviderResult(tools);
        ScriptedChatModel llm = new ScriptedChatModel(
                chamarTool("listar_resgates_cdb", "{\"customerId\":\"cli-008\"}"),
                chamarTool("consultar_conta_garantia", "{\"customerId\":\"cli-008\"}"),
                responder(resultados -> {
                    assertThat(resultados).hasSize(2);
                    return """
                            {"facts":["Resgate res-008 RETIDO_PARCIAL na conta garantia"],
                             "answerDraft":"R$ 6.500,00 foram liberados e R$ 3.500,00 seguem retidos ate o pagamento da fatura.",
                             "confidence":0.9,"risks":[],"sources":["cdb-mcp","cred-mcp"],
                             "situacaoGarantia":{"status":"RETIDO_PARCIAL","valorResgatado":10000.00,
                               "valorRetido":3500.00,"valorLiberado":6500.00,
                               "proximoPasso":"Pagar a fatura do cartao com vencimento em 05/10"}}
                            """;
                }));

        RespostaEspecialista resposta = EspecialistaFactory.criar(llm, provider, memoria)
                .investigar("ctx-8", "customerId: cli-008\nPedido: nao acho meu dinheiro");

        assertThat(resposta.situacaoGarantia()).isNotNull();
        assertThat(resposta.situacaoGarantia().status()).isEqualTo("RETIDO_PARCIAL");
        assertThat(resposta.situacaoGarantia().valorRetido()).isEqualByComparingTo(new BigDecimal("3500.00"));
        assertThat(resposta.situacaoGarantia().consistente()).isTrue();
        assertThat(resposta.sources()).contains("cred-mcp");
    }

    @Test
    void promptMandaConsultarAContaGarantiaQuandoOResgateLiquidou() {
        ScriptedChatModel llm = new ScriptedChatModel(responder(r -> RESPOSTA_JSON));

        EspecialistaFactory.criar(llm, toolsFake, memoria).investigar("ctx-prompt", "qualquer");

        assertThat(llm.requisicoes().getFirst().messages().getFirst().toString())
                .contains("consultar_conta_garantia").contains("RETIDO_PARCIAL").contains("situacaoGarantia");
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd investimentos-agent && mvn -q test -Dtest=EspecialistaInvestimentosTest`
Expected: `promptMandaConsultar...` FAIL (prompt atual não cita a tool). `cli008...` provavelmente PASS (só depende do record da Task 3) — isso confirma que o LangChain4j desserializa o record aninhado com `BigDecimal`.

- [ ] **Step 3: Prompt do especialista**

Substituir `investimentos-agent/src/main/resources/prompts/especialista-investimentos.txt` inteiro por:

```text
Voce e o Especialista de Investimentos. Voce recebe um pedido em linguagem natural e o customerId do cliente.
Seu objetivo: descobrir onde esta o dinheiro do cliente que estava em investimentos.

Passos:
1. Consulte listar_resgates_cdb e listar_posicoes_cdb com o customerId informado.
2. Se houver resgate, consulte listar_movimentacoes para ver se o credito ja entrou na conta corrente e,
   se houver transferenciaId, consultar_status_transferencia.
3. Se houver resgate LIQUIDADO, consulte SEMPRE consultar_conta_garantia: o resgate pode ter passado pela
   conta garantia por causa de gastos no cartao de credito. Lista vazia = nao passou pela garantia.
4. Cruze os dados antes de concluir.

Interpretacao:
- Resgate SOLICITADO ou EM_LIQUIDACAO (ou credito PROCESSANDO): o dinheiro esta em liquidacao e cai na conta em alguns minutos.
- Resgate LIQUIDADO com credito CONCLUIDA e sem retencao na garantia: o dinheiro ja esta na conta corrente; informe valor e data.
- Sem resgate e com posicao ativa: o dinheiro continua aplicado no CDB; informe emissor e valor atual.
- Conta garantia (consultar_conta_garantia):
  - LIBERADO_CONTA: passou pela conta garantia e ja foi encaminhado para a conta corrente; informe valor e data.
  - EM_ANALISE: esta na conta garantia em analise por causa de gastos no cartao de credito; ainda sem prazo.
  - RETIDO_ATE_PAGAMENTO_FATURA: esta retido na conta garantia ate o cliente pagar a fatura do cartao;
    informe o gasto no cartao e o vencimento da fatura.
  - RETIDO_PARCIAL: o gasto no cartao foi menor que o resgate; informe quanto foi liberado para a conta,
    quanto segue retido e que o restante e liberado apos o pagamento da fatura.
- Nada encontrado: confidence abaixo de 0.5 e um risk recomendando atendimento humano.
- Se uma ferramenta falhar: registre a falha em risks, reduza a confidence e nao inclua essa fonte em sources.

Regras:
- Use somente o customerId informado no pedido. Nunca invente valores, datas ou status.
- facts: fatos objetivos encontrados (um por item).
- answerDraft: resposta curta e objetiva ao cliente, em portugues.
- confidence: numero entre 0 e 1.
- risks: limitacoes ou incertezas (lista vazia se nao houver).
- sources: nomes dos sistemas que forneceram evidencias: cdb-mcp, tracking-money-mcp e/ou cred-mcp
  (cred-mcp somente se ele retornou alguma retencao).
- situacaoGarantia: preencha somente quando consultar_conta_garantia retornou uma retencao; copie status,
  valorResgatado, valorRetido e valorLiberado exatamente como vieram e escreva proximoPasso (o que o cliente
  precisa fazer ou esperar). Caso contrario, situacaoGarantia = null.
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd investimentos-agent && mvn -q test -Dtest=EspecialistaInvestimentosTest`
Expected: PASS (4 testes).

- [ ] **Step 5: Cliente MCP, properties, Agent Card, compose**

`application.yml`, bloco `mcp`:

```yaml
mcp:
  cdb-url: ${CDB_MCP_URL:http://localhost:8083/mcp}
  tracking-money-url: ${TRACKING_MONEY_MCP_URL:http://localhost:8082/mcp}
  cred-url: ${CRED_MCP_URL:http://localhost:8084/mcp}
```

`EspecialistaConfig` — novo bean depois de `trackingMoneyMcpClient` e o provider com os 3 clientes:

```java
    @Bean(destroyMethod = "close")
    McpClient credMcpClient(@Value("${mcp.cred-url}") String url) {
        return mcpClient("cred-mcp", url);
    }
```

```java
    /** failIfOneServerFails=false: um MCP fora não derruba o especialista; a falha vira risk na resposta. */
    @Bean
    ToolProvider mcpToolProvider(McpClient cdbMcpClient, McpClient trackingMoneyMcpClient, McpClient credMcpClient) {
        ToolProvider mcpToolProvider = McpToolProvider.builder()
                .mcpClients(cdbMcpClient, trackingMoneyMcpClient, credMcpClient)
                .failIfOneServerFails(false)
                .build();
        return new ToolProviderComLog(mcpToolProvider);
    }
```

`A2aServerConfig.agentCard` — atualizar os dois textos e as tags:

```java
                .description("Especialista de investimentos: localiza dinheiro do cliente em investimentos (CDB), "
                        + "verifica resgates, liquidacao, credito em conta e retencao em conta garantia por gastos no "
                        + "cartao. Responde no schema padrao (facts, answerDraft, confidence, risks, sources, "
                        + "situacaoGarantia) em um DataPart.")
```

```java
                        .description("Descobre onde esta o dinheiro que o cliente tinha em investimentos: "
                                + "aplicado, em liquidacao de resgate, retido em conta garantia ou ja creditado na conta. "
                                + "Envie a intencao em texto e um DataPart {\"customerId\": \"...\"}.")
                        .tags(List.of("investimentos", "cdb", "resgate", "liquidacao", "conta-garantia"))
```

`docker-compose.yml`, serviço `investimentos-agent`: em `environment` adicionar `CRED_MCP_URL: http://cred-mcp:8084/mcp`; em `depends_on` adicionar:

```yaml
      cred-mcp:
        condition: service_healthy
```

- [ ] **Step 6: IT — mock do novo cliente e 5ª tool no fake**

`BaseIntegrationTest` (investimentos), depois do `trackingMoneyMcpClient`:

```java
    @MockitoBean(name = "credMcpClient")
    private McpClient credMcpClient;
```

e no javadoc trocar "os dois beans viram mocks" por "os três beans viram mocks" e "com os 4 nomes de tool reais" por "com os 5 nomes de tool reais".

`McpToolProviderFake` — trocar "os mesmos 4 nomes" por "os mesmos 5 nomes" no javadoc e adicionar, antes do `.build()` do `ToolProviderResult`:

```java
                .add(porCliente("consultar_conta_garantia",
                        "Lista as retencoes em conta garantia de resgates de investimento do cliente, "
                                + "motivadas por gastos no cartao de credito: resgateId, status (LIBERADO_CONTA | "
                                + "EM_ANALISE | RETIDO_ATE_PAGAMENTO_FATURA | RETIDO_PARCIAL), valorResgatado, "
                                + "valorRetido, valorLiberado, gastoCartao, vencimentoFatura e detalhe. "
                                + "Retorna um array JSON (vazio se o resgate nao passou pela conta garantia)."),
                        executor("customerId", id -> "[]"))
```

(cli-001 não passa pela garantia no mock real, então `[]` é a resposta fiel.)

- [ ] **Step 7: Rodar unitários e compilar os ITs**

Run: `cd investimentos-agent && mvn -q test && mvn -q -P integration-test test-compile`
Expected: PASS / compila. (Rodar `mvn -P integration-test test` é opcional aqui — exige Docker + Ollama; será rodado na verificação final.)

- [ ] **Step 8: Commit**

```bash
git add investimentos-agent docker-compose.yml
git commit -m "feat(investimentos): especialista consulta a conta garantia no cred-mcp

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: `Cpf` e `CadastroClientes` na Ana

**Files:**
- Create: `ana-agent/src/main/java/poc/a2a/ana/cliente/Cpf.java`, `ana-agent/src/main/java/poc/a2a/ana/cliente/CadastroClientes.java`
- Test: `ana-agent/src/test/java/poc/a2a/ana/cliente/CpfTest.java`, `ana-agent/src/test/java/poc/a2a/ana/cliente/CadastroClientesTest.java`

**Interfaces:**
- Produces:
  - `record Cpf(String digitos)`: `static Optional<Cpf> de(String bruto)`, `String mascarado()`, `toString()` = `mascarado()`.
  - `@Component class CadastroClientes`: `Optional<String> customerId(Cpf cpf)`.

- [ ] **Step 1: Testes que falham**

`CpfTest.java`:

```java
package poc.a2a.ana.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"888.008.008-31", "88800800831", " 888.008.008-31 ", "123.456.789-09"})
    void aceitaCpfValidoComOuSemMascara(String bruto) {
        assertThat(Cpf.de(bruto)).isPresent();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"888.008.008-32", "111.111.111-11", "1234567890", "888.008.008-311", "abc88800800831"})
    void rejeitaCpfInvalido(String bruto) {
        assertThat(Cpf.de(bruto)).isEmpty();
    }

    @Test
    void normalizaParaOnzeDigitos() {
        assertThat(Cpf.de("888.008.008-31")).get().extracting(Cpf::digitos).isEqualTo("88800800831");
    }

    @Test
    void mascaraMostraSoOsUltimosQuatroDigitos() {
        Cpf cpf = Cpf.de("888.008.008-31").orElseThrow();

        assertThat(cpf.mascarado()).isEqualTo("***.***.*08-31");
        assertThat(cpf.toString()).isEqualTo("***.***.*08-31");
    }
}
```

Verificar se `junit-jupiter-params` está no classpath (vem no `spring-boot-starter-test`). 

`CadastroClientesTest.java`:

```java
package poc.a2a.ana.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class CadastroClientesTest {

    final CadastroClientes cadastro = new CadastroClientes();

    @ParameterizedTest
    @CsvSource({
            "111.001.001-05,cli-001", "222.002.002-93,cli-002", "333.003.003-80,cli-003", "444.004.004-76,cli-004",
            "555.005.005-62,cli-005", "666.006.006-59,cli-006", "777.007.007-45,cli-007", "888.008.008-31,cli-008"})
    void resolveOsCpfsDeTeste(String cpf, String customerId) {
        assertThat(cadastro.customerId(Cpf.de(cpf).orElseThrow())).contains(customerId);
    }

    @Test
    void cpfValidoForaDoCadastro() {
        assertThat(cadastro.customerId(Cpf.de("123.456.789-09").orElseThrow())).isEmpty();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd ana-agent && mvn -q test -Dtest='CpfTest,CadastroClientesTest'`
Expected: FAIL de compilação.

- [ ] **Step 3: Implementar**

`Cpf.java`:

```java
package poc.a2a.ana.cliente;

import java.util.Optional;

/** CPF normalizado (11 digitos). toString() mascarado para nunca vazar em log. */
public record Cpf(String digitos) {

    public Cpf {
        if (!valido(digitos)) {
            throw new IllegalArgumentException("CPF invalido");
        }
    }

    /** Aceita com ou sem mascara (digitos, pontos, hifen e espacos). */
    public static Optional<Cpf> de(String bruto) {
        if (bruto == null || !bruto.matches("[\\d.\\-\\s]+")) {
            return Optional.empty();
        }
        String digitos = bruto.replaceAll("\\D", "");
        return valido(digitos) ? Optional.of(new Cpf(digitos)) : Optional.empty();
    }

    public String mascarado() {
        return "***.***.*" + digitos.substring(7, 9) + "-" + digitos.substring(9);
    }

    @Override
    public String toString() {
        return mascarado();
    }

    private static boolean valido(String d) {
        if (d == null || !d.matches("\\d{11}") || d.chars().distinct().count() == 1) {
            return false;
        }
        return d.charAt(9) - '0' == digitoVerificador(d, 9) && d.charAt(10) - '0' == digitoVerificador(d, 10);
    }

    /** Pesos n+1..2 sobre os n primeiros digitos. */
    private static int digitoVerificador(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (n + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
```

`CadastroClientes.java`:

```java
package poc.a2a.ana.cliente;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Cadastro mock CPF -> customerId (CPFs de teste ficticios, com digitos verificadores validos). */
@Component
public class CadastroClientes {

    private final Map<String, String> customerIdPorCpf = Map.of(
            "11100100105", "cli-001",
            "22200200293", "cli-002",
            "33300300380", "cli-003",
            "44400400476", "cli-004",
            "55500500562", "cli-005",
            "66600600659", "cli-006",
            "77700700745", "cli-007",
            "88800800831", "cli-008");

    public Optional<String> customerId(Cpf cpf) {
        return Optional.ofNullable(customerIdPorCpf.get(cpf.digitos()));
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd ana-agent && mvn -q test -Dtest='CpfTest,CadastroClientesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ana-agent/src/main/java/poc/a2a/ana/cliente ana-agent/src/test/java/poc/a2a/ana/cliente
git commit -m "feat(ana): CPF validado e cadastro mock CPF -> customerId

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: `situacaoGarantia` na Ana + histórico de atendimentos (JDBC)

**Files:**
- Create: `ana-agent/src/main/java/poc/a2a/ana/investimentos/SituacaoGarantia.java`
- Modify: `ana-agent/src/main/java/poc/a2a/ana/investimentos/RespostaInvestimentos.java`
- Create: `ana-agent/src/main/java/poc/a2a/ana/atendimento/Atendimento.java`, `HistoricoAtendimentos.java`, `JdbcHistoricoAtendimentos.java`, `FormatadorAtendimentos.java`
- Create (test): `ana-agent/src/test/java/poc/a2a/ana/atendimento/HistoricoAtendimentosEmMemoria.java`
- Test: `ana-agent/src/test/java/poc/a2a/ana/investimentos/RespostaInvestimentosTest.java`, `ana-agent/src/test/java/poc/a2a/ana/atendimento/FormatadorAtendimentosTest.java`, `ana-agent/src/test/java/poc/a2a/ana/atendimento/JdbcHistoricoAtendimentosIT.java`
- Modify: `ana-agent/src/test/java/poc/a2a/ana/BaseIntegrationTest.java`

**Interfaces:**
- Consumes: formato do DataPart da Task 3 (`situacaoGarantia` com `status, valorResgatado, valorRetido, valorLiberado, proximoPasso`).
- Produces:
  - `record SituacaoGarantia(String status, BigDecimal valorResgatado, BigDecimal valorRetido, BigDecimal valorLiberado, String proximoPasso)` com `static SituacaoGarantia deMapa(Object valor)` (null se não for mapa).
  - `RespostaInvestimentos(..., SituacaoGarantia situacaoGarantia)` + construtor de 5 args.
  - `record Atendimento(OffsetDateTime criadoEm, String resumo, double confidence, SituacaoGarantia situacaoGarantia)`.
  - `interface HistoricoAtendimentos { void registrar(String customerId, String sessionId, RespostaInvestimentos resposta); List<Atendimento> recentesDeOutrasSessoes(String customerId, String sessionIdAtual, int limite); }`
  - `class JdbcHistoricoAtendimentos implements HistoricoAtendimentos` — `new JdbcHistoricoAtendimentos(DataSource)` cria a tabela.
  - `final class FormatadorAtendimentos { static String formatar(List<Atendimento>) }` — `"nenhum"` se vazio.
  - Test double `HistoricoAtendimentosEmMemoria implements HistoricoAtendimentos` (público, `poc.a2a.ana.atendimento`, test sources).
  - `BaseIntegrationTest.atendimentoRows(String customerId)`.

- [ ] **Step 1: Teste que falha (leitura do DataPart)**

`RespostaInvestimentosTest.java`:

```java
package poc.a2a.ana.investimentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RespostaInvestimentosTest {

    @Test
    void leSituacaoGarantiaDoDataPart() {
        RespostaInvestimentos resposta = RespostaInvestimentos.deMapa(Map.of(
                "facts", List.of("f"), "answerDraft", "r", "confidence", 0.9, "risks", List.of(),
                "sources", List.of("cred-mcp"),
                "situacaoGarantia", Map.of("status", "RETIDO_PARCIAL", "valorResgatado", 10000.0,
                        "valorRetido", 3500, "valorLiberado", "6500.00", "proximoPasso", "Pagar a fatura")));

        SituacaoGarantia situacao = resposta.situacaoGarantia();
        assertThat(situacao.status()).isEqualTo("RETIDO_PARCIAL");
        assertThat(situacao.valorResgatado()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(situacao.valorRetido()).isEqualTo(new BigDecimal("3500.00"));
        assertThat(situacao.valorLiberado()).isEqualTo(new BigDecimal("6500.00"));
        assertThat(resposta.paraTextoLlm()).contains("situacaoGarantia").contains("RETIDO_PARCIAL");
    }

    @Test
    void semSituacaoGarantiaFicaNula() {
        RespostaInvestimentos resposta = RespostaInvestimentos.deMapa(Map.of("answerDraft", "r", "confidence", 0.5));

        assertThat(resposta.situacaoGarantia()).isNull();
        assertThat(resposta.paraTextoLlm()).doesNotContain("situacaoGarantia");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd ana-agent && mvn -q test -Dtest=RespostaInvestimentosTest`
Expected: FAIL de compilação.

- [ ] **Step 3: Implementar**

`ana-agent/src/main/java/poc/a2a/ana/investimentos/SituacaoGarantia.java`:

```java
package poc.a2a.ana.investimentos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Retencao em conta garantia informada pelo especialista (DataPart §9, campo opcional). */
public record SituacaoGarantia(String status, BigDecimal valorResgatado, BigDecimal valorRetido,
                               BigDecimal valorLiberado, String proximoPasso) {

    static SituacaoGarantia deMapa(Object valor) {
        if (!(valor instanceof Map<?, ?> mapa) || mapa.get("status") == null) {
            return null;
        }
        return new SituacaoGarantia(mapa.get("status").toString(), decimal(mapa.get("valorResgatado")),
                decimal(mapa.get("valorRetido")), decimal(mapa.get("valorLiberado")),
                mapa.get("proximoPasso") == null ? "" : mapa.get("proximoPasso").toString());
    }

    /** O DataPart pode trazer Double, Integer, BigDecimal ou String: normaliza para 2 casas. */
    private static BigDecimal decimal(Object valor) {
        if (valor == null) {
            return null;
        }
        return new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
```

`RespostaInvestimentos.java` (substituir inteiro):

```java
package poc.a2a.ana.investimentos;

import java.util.List;
import java.util.Map;

/** Schema padrão de resposta de especialista (guideline §9), como recebido no DataPart A2A. */
public record RespostaInvestimentos(List<String> facts, String answerDraft, double confidence,
                                    List<String> risks, List<String> sources, SituacaoGarantia situacaoGarantia) {

    public RespostaInvestimentos(List<String> facts, String answerDraft, double confidence,
                                 List<String> risks, List<String> sources) {
        this(facts, answerDraft, confidence, risks, sources, null);
    }

    public static RespostaInvestimentos deMapa(Map<?, ?> mapa) {
        return new RespostaInvestimentos(
                lista(mapa.get("facts")),
                mapa.get("answerDraft") == null ? "" : mapa.get("answerDraft").toString(),
                mapa.get("confidence") instanceof Number n ? n.doubleValue() : 0.0,
                lista(mapa.get("risks")),
                lista(mapa.get("sources")),
                SituacaoGarantia.deMapa(mapa.get("situacaoGarantia")));
    }

    private static List<String> lista(Object valor) {
        return valor instanceof List<?> itens ? itens.stream().map(String::valueOf).toList() : List.of();
    }

    /** Texto devolvido ao LLM da Ana como resultado da tool. */
    public String paraTextoLlm() {
        String texto = "answerDraft: " + answerDraft
                + "\nfacts: " + facts
                + "\nconfidence: " + confidence
                + "\nrisks: " + risks
                + "\nsources: " + sources;
        return situacaoGarantia == null ? texto : texto + "\nsituacaoGarantia: " + situacaoGarantia;
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `cd ana-agent && mvn -q test -Dtest=RespostaInvestimentosTest`
Expected: PASS.

- [ ] **Step 5: Teste do formatador que falha**

`FormatadorAtendimentosTest.java`:

```java
package poc.a2a.ana.atendimento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import poc.a2a.ana.investimentos.SituacaoGarantia;

class FormatadorAtendimentosTest {

    @Test
    void semAtendimentos() {
        assertThat(FormatadorAtendimentos.formatar(List.of())).isEqualTo("nenhum");
    }

    @Test
    void umaLinhaPorAtendimentoNoHorarioDeBrasilia() {
        Atendimento comGarantia = new Atendimento(OffsetDateTime.of(2026, 9, 22, 17, 3, 0, 0, ZoneOffset.UTC),
                "Parte do resgate foi liberada.", 0.9,
                new SituacaoGarantia("RETIDO_PARCIAL", new BigDecimal("10000.00"), new BigDecimal("3500.00"),
                        new BigDecimal("6500.00"), "Pagar a fatura"));
        Atendimento semGarantia = new Atendimento(OffsetDateTime.of(2026, 9, 21, 12, 0, 0, 0, ZoneOffset.UTC),
                "Seu resgate esta em liquidacao.", 0.8, null);

        assertThat(FormatadorAtendimentos.formatar(List.of(comGarantia, semGarantia))).isEqualTo("""
                22/09 14:03 — Parte do resgate foi liberada. [garantia: RETIDO_PARCIAL, liberado 6500.00, retido 3500.00]
                21/09 09:00 — Seu resgate esta em liquidacao.""");
    }
}
```

- [ ] **Step 6: Rodar e ver falhar**

Run: `cd ana-agent && mvn -q test -Dtest=FormatadorAtendimentosTest`
Expected: FAIL de compilação.

- [ ] **Step 7: Implementar tipos do histórico, formatador e fake**

`Atendimento.java`:

```java
package poc.a2a.ana.atendimento;

import java.time.OffsetDateTime;

import poc.a2a.ana.investimentos.SituacaoGarantia;

/** Um atendimento anterior (uma delegacao bem-sucedida ao especialista). */
public record Atendimento(OffsetDateTime criadoEm, String resumo, double confidence,
                          SituacaoGarantia situacaoGarantia) {
}
```

`HistoricoAtendimentos.java`:

```java
package poc.a2a.ana.atendimento;

import java.util.List;

import poc.a2a.ana.investimentos.RespostaInvestimentos;

/** Atendimentos por cliente (customerId), para a Ana lembrar do que ja foi visto em outras sessoes. */
public interface HistoricoAtendimentos {

    void registrar(String customerId, String sessionId, RespostaInvestimentos resposta);

    /** Mais recentes primeiro, excluindo a sessao atual. */
    List<Atendimento> recentesDeOutrasSessoes(String customerId, String sessionIdAtual, int limite);
}
```

`FormatadorAtendimentos.java`:

```java
package poc.a2a.ana.atendimento;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Texto curto dos atendimentos anteriores para o system prompt da Ana. */
public final class FormatadorAtendimentos {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private FormatadorAtendimentos() {
    }

    public static String formatar(List<Atendimento> atendimentos) {
        if (atendimentos.isEmpty()) {
            return "nenhum";
        }
        return atendimentos.stream().map(FormatadorAtendimentos::linha).collect(Collectors.joining("\n"));
    }

    private static String linha(Atendimento a) {
        String linha = DATA_HORA.format(a.criadoEm().atZoneSameInstant(BRASILIA)) + " — " + a.resumo();
        var g = a.situacaoGarantia();
        return g == null ? linha : linha + " [garantia: %s, liberado %s, retido %s]".formatted(
                g.status(), g.valorLiberado().toPlainString(), g.valorRetido().toPlainString());
    }
}
```

`ana-agent/src/test/java/poc/a2a/ana/atendimento/HistoricoAtendimentosEmMemoria.java`:

```java
package poc.a2a.ana.atendimento;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import poc.a2a.ana.investimentos.RespostaInvestimentos;

/** Dublê em memória do histórico (testes sem Postgres). */
public class HistoricoAtendimentosEmMemoria implements HistoricoAtendimentos {

    private record Registro(String customerId, String sessionId, Atendimento atendimento) {
    }

    private final List<Registro> registros = new ArrayList<>();

    @Override
    public synchronized void registrar(String customerId, String sessionId, RespostaInvestimentos resposta) {
        registros.add(new Registro(customerId, sessionId, new Atendimento(OffsetDateTime.now(),
                resposta.answerDraft(), resposta.confidence(), resposta.situacaoGarantia())));
    }

    @Override
    public synchronized List<Atendimento> recentesDeOutrasSessoes(String customerId, String sessionIdAtual, int limite) {
        return registros.reversed().stream()
                .filter(r -> r.customerId().equals(customerId) && !r.sessionId().equals(sessionIdAtual))
                .limit(limite)
                .map(Registro::atendimento)
                .toList();
    }
}
```

- [ ] **Step 8: Rodar e ver passar**

Run: `cd ana-agent && mvn -q test -Dtest=FormatadorAtendimentosTest`
Expected: PASS.

- [ ] **Step 9: IT do repositório JDBC (Postgres real) que falha**

Em `ana-agent/src/test/java/poc/a2a/ana/BaseIntegrationTest.java`, trocar `clearDatabase` e adicionar o helper:

```java
    // chat_memory e atendimento sao criadas no startup (autoCreateTable / JdbcHistoricoAtendimentos); o guard cobre o caso de ainda nao existirem.
    @BeforeEach
    void clearDatabase() throws SQLException {
        try (Connection connection = memoriaDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DO $$ BEGIN IF to_regclass('chat_memory') IS NOT NULL "
                    + "THEN DELETE FROM chat_memory; END IF; END $$");
            statement.execute("DO $$ BEGIN IF to_regclass('atendimento') IS NOT NULL "
                    + "THEN DELETE FROM atendimento; END IF; END $$");
        }
    }

    /** Atendimentos gravados para o cliente. */
    protected int atendimentoRows(String customerId) throws SQLException {
        try (Connection connection = memoriaDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM atendimento WHERE customer_id = ?")) {
            statement.setString(1, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
```

`ana-agent/src/test/java/poc/a2a/ana/atendimento/JdbcHistoricoAtendimentosIT.java`:

```java
package poc.a2a.ana.atendimento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import poc.a2a.ana.BaseIntegrationTest;
import poc.a2a.ana.investimentos.RespostaInvestimentos;
import poc.a2a.ana.investimentos.SituacaoGarantia;

/** Histórico de atendimentos no Postgres real (tabela criada pelo JdbcHistoricoAtendimentos no startup). */
class JdbcHistoricoAtendimentosIT extends BaseIntegrationTest {

    @Autowired
    HistoricoAtendimentos historico;

    static RespostaInvestimentos resposta(String resumo, SituacaoGarantia situacao) {
        return new RespostaInvestimentos(List.of("f"), resumo, 0.9, List.of(), List.of("cred-mcp"), situacao);
    }

    @Test
    void gravaELeDeOutrasSessoesMaisRecentesPrimeiro() throws Exception {
        SituacaoGarantia parcial = new SituacaoGarantia("RETIDO_PARCIAL", new BigDecimal("10000.00"),
                new BigDecimal("3500.00"), new BigDecimal("6500.00"), "Pagar a fatura");
        historico.registrar("cli-008", "sess-1", resposta("primeiro", parcial));
        historico.registrar("cli-008", "sess-2", resposta("segundo", null));
        historico.registrar("cli-008", "sess-atual", resposta("da sessao atual", null));
        historico.registrar("cli-001", "sess-9", resposta("outro cliente", null));

        List<Atendimento> anteriores = historico.recentesDeOutrasSessoes("cli-008", "sess-atual", 3);

        assertThat(anteriores).extracting(Atendimento::resumo).containsExactly("segundo", "primeiro");
        assertThat(anteriores.get(1).situacaoGarantia()).isEqualTo(parcial);
        assertThat(anteriores.get(0).situacaoGarantia()).isNull();
        assertThat(atendimentoRows("cli-008")).isEqualTo(3);
    }

    @Test
    void respeitaOLimite() {
        for (int i = 1; i <= 5; i++) {
            historico.registrar("cli-005", "sess-" + i, resposta("atendimento " + i, null));
        }

        assertThat(historico.recentesDeOutrasSessoes("cli-005", "nova", 3))
                .extracting(Atendimento::resumo).containsExactly("atendimento 5", "atendimento 4", "atendimento 3");
    }
}
```

- [ ] **Step 10: Compilar o IT e ver falhar**

Run: `cd ana-agent && mvn -q -P integration-test test -Dtest=JdbcHistoricoAtendimentosIT` (precisa de Docker; a primeira execução baixa as imagens)
Expected: compila (a interface já existe), mas FAIL no startup do contexto com `NoSuchBeanDefinitionException` para `HistoricoAtendimentos` — ainda não há implementação JDBC nem bean.

- [ ] **Step 11: Implementar o repositório JDBC e o bean**

`JdbcHistoricoAtendimentos.java`:

```java
package poc.a2a.ana.atendimento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import poc.a2a.ana.investimentos.RespostaInvestimentos;
import poc.a2a.ana.investimentos.SituacaoGarantia;

/** Historico no Postgres (schema do currentSchema da URL). Cria a tabela no startup, como o SQLChatMemoryStore. */
public class JdbcHistoricoAtendimentos implements HistoricoAtendimentos {

    private static final String CRIAR_TABELA = """
            CREATE TABLE IF NOT EXISTS atendimento (
              id                       BIGSERIAL PRIMARY KEY,
              customer_id              TEXT         NOT NULL,
              session_id               TEXT         NOT NULL,
              criado_em                TIMESTAMPTZ  NOT NULL DEFAULT now(),
              resumo                   TEXT         NOT NULL,
              confidence               NUMERIC(3,2) NOT NULL,
              garantia_status          TEXT,
              garantia_valor_resgatado NUMERIC(15,2),
              garantia_valor_retido    NUMERIC(15,2),
              garantia_valor_liberado  NUMERIC(15,2),
              garantia_proximo_passo   TEXT
            )""";

    private static final String CRIAR_INDICE =
            "CREATE INDEX IF NOT EXISTS atendimento_customer_criado ON atendimento (customer_id, criado_em DESC)";

    private static final String INSERIR = """
            INSERT INTO atendimento (customer_id, session_id, resumo, confidence, garantia_status,
              garantia_valor_resgatado, garantia_valor_retido, garantia_valor_liberado, garantia_proximo_passo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String RECENTES = """
            SELECT criado_em, resumo, confidence, garantia_status, garantia_valor_resgatado,
                   garantia_valor_retido, garantia_valor_liberado, garantia_proximo_passo
              FROM atendimento
             WHERE customer_id = ? AND session_id <> ?
             ORDER BY criado_em DESC, id DESC
             LIMIT ?""";

    private final DataSource dataSource;

    public JdbcHistoricoAtendimentos(DataSource dataSource) {
        this.dataSource = dataSource;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CRIAR_TABELA);
            statement.execute(CRIAR_INDICE);
        } catch (SQLException e) {
            throw new IllegalStateException("Nao foi possivel criar a tabela atendimento", e);
        }
    }

    @Override
    public void registrar(String customerId, String sessionId, RespostaInvestimentos resposta) {
        SituacaoGarantia g = resposta.situacaoGarantia();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERIR)) {
            statement.setString(1, customerId);
            statement.setString(2, sessionId);
            statement.setString(3, resposta.answerDraft());
            statement.setBigDecimal(4, BigDecimal.valueOf(resposta.confidence()).setScale(2, RoundingMode.HALF_UP));
            statement.setString(5, g == null ? null : g.status());
            statement.setBigDecimal(6, g == null ? null : g.valorResgatado());
            statement.setBigDecimal(7, g == null ? null : g.valorRetido());
            statement.setBigDecimal(8, g == null ? null : g.valorLiberado());
            statement.setString(9, g == null ? null : g.proximoPasso());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar atendimento", e);
        }
    }

    @Override
    public List<Atendimento> recentesDeOutrasSessoes(String customerId, String sessionIdAtual, int limite) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(RECENTES)) {
            statement.setString(1, customerId);
            statement.setString(2, sessionIdAtual);
            statement.setInt(3, limite);
            List<Atendimento> atendimentos = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("garantia_status");
                    SituacaoGarantia g = status == null ? null : new SituacaoGarantia(status,
                            rs.getBigDecimal("garantia_valor_resgatado"), rs.getBigDecimal("garantia_valor_retido"),
                            rs.getBigDecimal("garantia_valor_liberado"), rs.getString("garantia_proximo_passo"));
                    atendimentos.add(new Atendimento(rs.getObject("criado_em", OffsetDateTime.class),
                            rs.getString("resumo"), rs.getBigDecimal("confidence").doubleValue(), g));
                }
            }
            return atendimentos;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar atendimentos", e);
        }
    }
}
```

Em `AnaConfig`, adicionar (import `poc.a2a.ana.atendimento.HistoricoAtendimentos` e `...JdbcHistoricoAtendimentos`):

```java
    @Bean
    HistoricoAtendimentos historicoAtendimentos(DataSource memoriaDataSource) {
        return new JdbcHistoricoAtendimentos(memoriaDataSource);
    }
```

- [ ] **Step 12: Rodar e ver passar**

Run: `cd ana-agent && mvn -q test && mvn -q -P integration-test test -Dtest=JdbcHistoricoAtendimentosIT`
Expected: PASS (unitários + 2 ITs).

- [ ] **Step 13: Commit**

```bash
git add ana-agent
git commit -m "feat(ana): situacaoGarantia no retorno do especialista e historico de atendimentos no Postgres

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Ana usa CPF e lembra de atendimentos anteriores (controller, tool, prompt)

**Files:**
- Modify: `ana-agent/src/main/java/poc/a2a/ana/chat/ChatRequisicao.java`, `ana-agent/src/main/java/poc/a2a/ana/chat/ChatController.java`
- Modify: `ana-agent/src/main/java/poc/a2a/ana/assistente/AnaAssistant.java`, `ana-agent/src/main/java/poc/a2a/ana/assistente/DelegacaoInvestimentosTool.java`
- Modify: `ana-agent/src/main/java/poc/a2a/ana/config/AnaConfig.java`
- Modify: `ana-agent/src/main/resources/prompts/ana-system.txt`
- Test: `ana-agent/src/test/java/poc/a2a/ana/chat/ChatControllerTest.java`, `ana-agent/src/test/java/poc/a2a/ana/assistente/AnaFluxoTest.java`, `ana-agent/src/test/java/poc/a2a/ana/chat/ChatControllerIT.java`

**Interfaces:**
- Consumes: `Cpf`, `CadastroClientes` (Task 5); `HistoricoAtendimentos`, `FormatadorAtendimentos`, `HistoricoAtendimentosEmMemoria`, `SituacaoGarantia`, `atendimentoRows` (Task 6).
- Produces:
  - `record ChatRequisicao(String sessionId, String cpf, String message)`.
  - `AnaAssistant.conversar(@MemoryId String sessionId, @UserMessage String mensagem, @V("atendimentosAnteriores") String atendimentosAnteriores, InvocationParameters parametros)`.
  - `new DelegacaoInvestimentosTool(InvestimentosClient, UltimasRespostasInvestimentos, HistoricoAtendimentos)`.
  - `POST /chat` 400 com corpo `{"error": "..."}`.
  - `ChatController.LIMITE_ATENDIMENTOS = 3`.

- [ ] **Step 1: Testes do fluxo (AnaFluxoTest) que falham**

Em `AnaFluxoTest`:
- adicionar campo `final HistoricoAtendimentosEmMemoria historico = new HistoricoAtendimentosEmMemoria();` (import `poc.a2a.ana.atendimento.HistoricoAtendimentosEmMemoria` e `poc.a2a.ana.atendimento.HistoricoAtendimentos`);
- trocar toda construção `new DelegacaoInvestimentosTool(client, ultimas)` por `new DelegacaoInvestimentosTool(client, ultimas, historico)` (e o de `duasRequisicoes...` idem);
- trocar toda chamada `ana.conversar(sessao, msg, params)` por `ana.conversar(sessao, msg, "nenhum", params)`.

Novos testes:

```java
    @Test
    void delegacaoBemSucedidaGravaAtendimentoDoCliente() {
        ScriptedChatModel llm = new ScriptedChatModel(
                chamarTool("delegar_investimentos", "{\"pedido\":\"localizar dinheiro\"}"),
                responder(resultados -> "ok"));
        AnaAssistant ana = AnaFactory.criar(llm, memoria,
                new DelegacaoInvestimentosTool((c, id, p) -> RESPOSTA, ultimas, historico));

        ana.conversar("sess-h1", "estava em investimentos", "nenhum", parametros("sess-h1", "cli-008"));

        assertThat(historico.recentesDeOutrasSessoes("cli-008", "outra-sessao", 3))
                .singleElement().satisfies(a -> assertThat(a.resumo()).isEqualTo(RESPOSTA.answerDraft()));
    }

    @Test
    void especialistaIndisponivelNaoGravaAtendimento() {
        ScriptedChatModel llm = new ScriptedChatModel(
                chamarTool("delegar_investimentos", "{\"pedido\":\"localizar dinheiro\"}"),
                responder(resultados -> "ok"));
        AnaAssistant ana = AnaFactory.criar(llm, memoria, new DelegacaoInvestimentosTool(
                (c, id, p) -> { throw new InvestimentosIndisponivelException("fora"); }, ultimas, historico));

        ana.conversar("sess-h2", "estava em investimentos", "nenhum", parametros("sess-h2", "cli-007"));

        assertThat(historico.recentesDeOutrasSessoes("cli-007", "outra-sessao", 3)).isEmpty();
    }

    @Test
    void falhaAoGravarHistoricoNaoQuebraARespostaDaTool() {
        HistoricoAtendimentos quebrado = new HistoricoAtendimentosEmMemoria() {
            @Override
            public void registrar(String customerId, String sessionId, RespostaInvestimentos resposta) {
                throw new IllegalStateException("banco fora");
            }
        };
        DelegacaoInvestimentosTool tool = new DelegacaoInvestimentosTool((c, id, p) -> RESPOSTA, ultimas, quebrado);

        String resultado = tool.delegarInvestimentos("pedido", parametros("sess-h3", "cli-001"));

        assertThat(resultado).contains("answerDraft: " + RESPOSTA.answerDraft());
    }

    @Test
    void atendimentosAnterioresVaoNoSystemPrompt() {
        ScriptedChatModel llm = new ScriptedChatModel(responder(r -> "Da ultima vez vimos que..."));
        AnaAssistant ana = AnaFactory.criar(llm, memoria,
                new DelegacaoInvestimentosTool((c, id, p) -> RESPOSTA, ultimas, historico));

        ana.conversar("sess-h4", "oi, voltei", "22/09 14:03 — resgate [garantia: RETIDO_PARCIAL, liberado 6500.00, retido 3500.00]",
                parametros("sess-h4", "cli-008"));

        assertThat(llm.requisicoes().getFirst().messages().getFirst().toString())
                .contains("RETIDO_PARCIAL").contains("Atendimentos anteriores");
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd ana-agent && mvn -q test -Dtest=AnaFluxoTest`
Expected: FAIL de compilação (construtor de 3 args e `conversar` de 4 args não existem).

- [ ] **Step 3: Implementar assistente, tool e prompt**

`AnaAssistant.java`:

```java
package poc.a2a.ana.assistente;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Supervisor. InvocationParameters leva sessionId/customerId até a tool sem passar pelo LLM.
 * atendimentosAnteriores (outras sessões do mesmo cliente) entra no system prompt de forma determinística.
 */
public interface AnaAssistant {

    @SystemMessage(fromResource = "/prompts/ana-system.txt")
    String conversar(@MemoryId String sessionId, @UserMessage String mensagem,
                     @V("atendimentosAnteriores") String atendimentosAnteriores, InvocationParameters parametros);
}
```

`DelegacaoInvestimentosTool.java` — construtor e gravação:

```java
    private final InvestimentosClient investimentos;
    private final UltimasRespostasInvestimentos ultimas;
    private final HistoricoAtendimentos historico;

    public DelegacaoInvestimentosTool(InvestimentosClient investimentos, UltimasRespostasInvestimentos ultimas,
                                      HistoricoAtendimentos historico) {
        this.investimentos = investimentos;
        this.ultimas = ultimas;
        this.historico = historico;
    }
```

No `try`, logo depois de `ultimas.registrar(requestId, resposta);`:

```java
            registrarAtendimento(customerId, sessionId, resposta);
```

e o método:

```java
    /** O historico e acessorio: falha ao gravar nao pode derrubar o atendimento. */
    private void registrarAtendimento(String customerId, String sessionId, RespostaInvestimentos resposta) {
        try {
            historico.registrar(customerId, sessionId, resposta);
        } catch (RuntimeException e) {
            log.warn("ana.atendimento.registro.falhou sessionId={} customerId={} erro={}", sessionId, customerId,
                    e.toString());
        }
    }
```

Atualizar a descrição da `@Tool` para citar a garantia:

```java
    @Tool(name = "delegar_investimentos", value = "Delega ao Especialista de Investimentos a tarefa de descobrir onde "
            + "esta o dinheiro que o cliente tinha em investimentos (aplicado, em liquidacao de resgate, retido em "
            + "conta garantia por gastos no cartao ou ja na conta). "
            + "Use quando o cliente disser que o dinheiro estava em investimentos.")
```

(import `poc.a2a.ana.atendimento.HistoricoAtendimentos`.)

`ana-system.txt` (substituir inteiro):

```text
Voce e a Ana, assistente virtual do banco. Tom acolhedor, direto, frases curtas, em portugues.

Atendimentos anteriores deste cliente (outras conversas, mais recentes primeiro):
{{atendimentosAnteriores}}

Jornada "meu dinheiro sumiu":
0. Se houver atendimento anterior (diferente de "nenhum") e esta for a sua primeira resposta nesta conversa,
   comece lembrando dele em uma frase (ex.: "Da ultima vez vimos que parte do seu resgate estava retida na
   conta garantia.") e pergunte se o assunto e o mesmo. Se for, chame delegar_investimentos para trazer o
   status atualizado. Nunca afirme o status atual so com base no historico: ele pode ter mudado.
1. Se o cliente disser que o dinheiro sumiu ou que nao encontra um valor e ainda nao disse onde ele estava,
   pergunte: "Seu dinheiro estava aplicado onde? Na conta, em investimentos ou em outro lugar?"
2. Se o cliente disser que estava em investimentos, chame a ferramenta delegar_investimentos descrevendo a
   intencao do cliente em linguagem natural (ex.: "cliente nao encontra dinheiro que estava em investimentos").
   Nao pergunte o identificador nem o CPF do cliente: a ferramenta ja sabe quem e.
3. Com o resultado da ferramenta (answerDraft, facts, confidence, risks, sources, situacaoGarantia), responda
   ao cliente com base no answerDraft e nos facts, no seu tom. Nunca invente valores, datas ou status.
   Se houver situacaoGarantia, explique onde o dinheiro esta (conta garantia por gastos no cartao de credito),
   quanto foi liberado, quanto segue retido e o proximoPasso.
   Se confidence for menor que 0.5 ou houver risks relevantes, diga que vai encaminhar para um atendente humano.
4. Se o resultado da ferramenta comecar com INDISPONIVEL, responda:
   "Nao consegui consultar seus investimentos agora. Tente novamente em instantes."
5. Se o dinheiro estava na conta ou em outro lugar, diga que nesta versao voce so consegue ajudar com investimentos.
```

`AnaConfig.anaAssistant`:

```java
    @Bean
    AnaAssistant anaAssistant(ChatModel chatModel, ChatMemoryProvider chatMemoryProvider,
                              InvestimentosClient investimentosClient, UltimasRespostasInvestimentos ultimas,
                              HistoricoAtendimentos historicoAtendimentos) {
        return AnaFactory.criar(chatModel, chatMemoryProvider,
                new DelegacaoInvestimentosTool(investimentosClient, ultimas, historicoAtendimentos));
    }
```

- [ ] **Step 4: Rodar AnaFluxoTest e ver passar**

Run: `cd ana-agent && mvn -q test -Dtest=AnaFluxoTest`
Expected: PASS (7 testes). (`ChatControllerTest` ainda não compila — próximo step.)

- [ ] **Step 5: Testes do controller que falham**

Substituir `ChatControllerTest` inteiro:

```java
package poc.a2a.ana.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import poc.a2a.ana.assistente.AnaAssistant;
import poc.a2a.ana.assistente.DelegacaoInvestimentosTool;
import poc.a2a.ana.assistente.UltimasRespostasInvestimentos;
import poc.a2a.ana.atendimento.HistoricoAtendimentosEmMemoria;
import poc.a2a.ana.investimentos.RespostaInvestimentos;
import poc.a2a.ana.investimentos.SituacaoGarantia;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatControllerTest {

    static final AtomicReference<String> ultimaChamada = new AtomicReference<>();

    @TestConfiguration
    static class AnaFake {
        @Bean
        HistoricoAtendimentosEmMemoria historicoAtendimentos() {
            return new HistoricoAtendimentosEmMemoria();
        }

        /** Simula um turno com delegação: registra a resposta do especialista e ecoa o que recebeu. */
        @Bean
        AnaAssistant anaAssistant(UltimasRespostasInvestimentos ultimas) {
            return (sessionId, mensagem, anteriores, parametros) -> {
                ultimaChamada.set(sessionId + "|" + mensagem + "|" + anteriores + "|" + parametros.asMap());
                String requestId = parametros.get(DelegacaoInvestimentosTool.REQUEST_ID);
                ultimas.registrar(requestId, new RespostaInvestimentos(List.of("fato"), "rascunho", 0.8,
                        List.of(), List.of("cdb-mcp")));
                return "eco: " + mensagem + " cliente=" + parametros.get(DelegacaoInvestimentosTool.CUSTOMER_ID)
                        + " anteriores=" + anteriores;
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    HistoricoAtendimentosEmMemoria historico;

    RestClient http;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
    }

    private JsonNode chat(String query, String body) {
        return json.readTree(http.post().uri("/chat" + query)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class));
    }

    private record Erro(int status, String corpo) {
    }

    private Erro chatComErro(String body) {
        return http.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> new Erro(response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes())));
    }

    @Test
    void resolveOCustomerIdPeloCpf() {
        JsonNode resposta = chat("", """
                {"sessionId":"s-1","cpf":"111.001.001-05","message":"meu dinheiro sumiu"}""");

        assertThat(resposta.path("sessionId").asString()).isEqualTo("s-1");
        assertThat(resposta.path("reply").asString())
                .isEqualTo("eco: meu dinheiro sumiu cliente=cli-001 anteriores=nenhum");
        assertThat(resposta.path("debug").isNull()).isTrue();
    }

    @Test
    void aceitaCpfSemMascara() {
        JsonNode resposta = chat("", """
                {"sessionId":"s-1b","cpf":"22200200293","message":"oi"}""");

        assertThat(resposta.path("reply").asString()).contains("cliente=cli-002");
    }

    @Test
    void cpfNuncaChegaAoAssistente() {
        chat("", """
                {"sessionId":"s-1c","cpf":"333.003.003-80","message":"oi"}""");

        assertThat(ultimaChamada.get()).doesNotContain("33300300380").doesNotContain("333.003.003-80")
                .contains("cli-003");
    }

    @Test
    void modoDebugDevolveOSchemaDoEspecialista() {
        JsonNode resposta = chat("?debug=true", """
                {"sessionId":"s-2","cpf":"111.001.001-05","message":"estava em investimentos"}""");

        assertThat(resposta.path("debug").path("confidence").asDouble()).isEqualTo(0.8);
        assertThat(resposta.path("debug").path("sources").get(0).asString()).isEqualTo("cdb-mcp");
    }

    @Test
    void atendimentoDeOutraSessaoVaiParaOAssistente() {
        historico.registrar("cli-008", "s-antiga", new RespostaInvestimentos(List.of("f"),
                "Parte do resgate segue retida.", 0.9, List.of(), List.of("cred-mcp"),
                new SituacaoGarantia("RETIDO_PARCIAL", new BigDecimal("10000.00"), new BigDecimal("3500.00"),
                        new BigDecimal("6500.00"), "Pagar a fatura")));

        String novaSessao = chat("", """
                {"sessionId":"s-nova","cpf":"888.008.008-31","message":"oi, voltei"}""").path("reply").asString();
        String mesmaSessao = chat("", """
                {"sessionId":"s-antiga","cpf":"888.008.008-31","message":"oi"}""").path("reply").asString();

        assertThat(novaSessao).contains("Parte do resgate segue retida.")
                .contains("[garantia: RETIDO_PARCIAL, liberado 6500.00, retido 3500.00]");
        assertThat(mesmaSessao).endsWith("anteriores=nenhum");
    }

    @Test
    void cpfInvalido() {
        Erro erro = chatComErro("""
                {"sessionId":"s-3","cpf":"111.001.001-06","message":"oi"}""");

        assertThat(erro.status()).isEqualTo(400);
        assertThat(json.readTree(erro.corpo()).path("error").asString()).isEqualTo("CPF invalido");
    }

    @Test
    void cpfForaDoCadastro() {
        Erro erro = chatComErro("""
                {"sessionId":"s-4","cpf":"123.456.789-09","message":"oi"}""");

        assertThat(erro.status()).isEqualTo(400);
        assertThat(json.readTree(erro.corpo()).path("error").asString()).isEqualTo("cliente nao encontrado");
    }

    @Test
    void camposObrigatorios() {
        Erro erro = chatComErro("{\"sessionId\":\"s-5\",\"message\":\"oi\"}");

        assertThat(erro.status()).isEqualTo(400);
        assertThat(json.readTree(erro.corpo()).path("error").asString())
                .isEqualTo("sessionId, cpf e message sao obrigatorios");
    }
}
```

(Remover o import de `InvocationParameters` se o IDE apontar como não usado.)

- [ ] **Step 6: Rodar e ver falhar**

Run: `cd ana-agent && mvn -q test -Dtest=ChatControllerTest`
Expected: FAIL (compilação: `ChatRequisicao` ainda tem `customerId`; depois, 400 sem corpo `{error}`).

- [ ] **Step 7: Implementar requisição e controller**

`ChatRequisicao.java`:

```java
package poc.a2a.ana.chat;

/** O cliente e identificado pelo CPF; a Ana resolve o customerId (o CPF nao segue adiante). */
public record ChatRequisicao(String sessionId, String cpf, String message) {
}
```

`ChatController.java` (substituir inteiro):

```java
package poc.a2a.ana.chat;

import java.util.Map;
import java.util.UUID;

import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import poc.a2a.ana.assistente.AnaAssistant;
import poc.a2a.ana.assistente.DelegacaoInvestimentosTool;
import poc.a2a.ana.assistente.UltimasRespostasInvestimentos;
import poc.a2a.ana.atendimento.FormatadorAtendimentos;
import poc.a2a.ana.atendimento.HistoricoAtendimentos;
import poc.a2a.ana.cliente.CadastroClientes;
import poc.a2a.ana.cliente.Cpf;
import poc.a2a.ana.investimentos.RespostaInvestimentos;

@RestController
public class ChatController {

    static final int LIMITE_ATENDIMENTOS = 3;

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AnaAssistant ana;
    private final UltimasRespostasInvestimentos ultimas;
    private final CadastroClientes cadastro;
    private final HistoricoAtendimentos historico;

    public ChatController(AnaAssistant ana, UltimasRespostasInvestimentos ultimas, CadastroClientes cadastro,
                          HistoricoAtendimentos historico) {
        this.ana = ana;
        this.ultimas = ultimas;
        this.cadastro = cadastro;
        this.historico = historico;
    }

    @PostMapping("/chat")
    public ChatResposta chat(@RequestBody ChatRequisicao requisicao,
                             @RequestParam(defaultValue = "false") boolean debug) {
        if (vazio(requisicao.sessionId()) || vazio(requisicao.cpf()) || vazio(requisicao.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId, cpf e message sao obrigatorios");
        }
        Cpf cpf = Cpf.de(requisicao.cpf())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF invalido"));
        String customerId = cadastro.customerId(cpf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "cliente nao encontrado"));
        long inicio = System.nanoTime();
        String requestId = UUID.randomUUID().toString();

        InvocationParameters parametros = new InvocationParameters();
        parametros.put(DelegacaoInvestimentosTool.SESSION_ID, requisicao.sessionId());
        parametros.put(DelegacaoInvestimentosTool.CUSTOMER_ID, customerId);
        parametros.put(DelegacaoInvestimentosTool.REQUEST_ID, requestId);
        String anteriores = atendimentosAnteriores(customerId, requisicao.sessionId());
        String reply;
        RespostaInvestimentos respostaEspecialista;
        try {
            reply = ana.conversar(requisicao.sessionId(), requisicao.message(), anteriores, parametros);
        } finally {
            respostaEspecialista = ultimas.remover(requestId);
        }
        log.info("ana.chat sessionId={} cpf={} customerId={} comHistorico={} delegou={} durationMs={}",
                requisicao.sessionId(), cpf.mascarado(), customerId, !"nenhum".equals(anteriores),
                respostaEspecialista != null, (System.nanoTime() - inicio) / 1_000_000);
        return new ChatResposta(requisicao.sessionId(), reply, debug ? respostaEspecialista : null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> erro(ResponseStatusException e) {
        String motivo = e.getReason() == null ? "requisicao invalida" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", motivo));
    }

    /** Historico e acessorio: se o banco falhar, a Ana atende sem ele. */
    private String atendimentosAnteriores(String customerId, String sessionId) {
        try {
            return FormatadorAtendimentos.formatar(
                    historico.recentesDeOutrasSessoes(customerId, sessionId, LIMITE_ATENDIMENTOS));
        } catch (RuntimeException e) {
            log.warn("ana.atendimento.leitura.falhou sessionId={} customerId={} erro={}", sessionId, customerId,
                    e.toString());
            return "nenhum";
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
```

- [ ] **Step 8: Rodar todos os unitários da Ana**

Run: `cd ana-agent && mvn -q test`
Expected: PASS. (Se `InvestimentosA2aClientTest`/`FakeA2aServer` comparam `RespostaInvestimentos` com `isEqualTo`, continuam passando: o DataPart sem `situacaoGarantia` gera `null`, igual ao construtor de 5 args.)

- [ ] **Step 9: Ajustar o IT do controller (Postgres + Ollama reais)**

Em `ChatControllerIT`:
- `static final String CPF = "111.001.001-05";` ao lado de `CUSTOMER_ID`;
- no helper `chat`, `new ChatRequisicao(sessionId, CPF, message)`;
- ao final de `jornadaMeuDinheiroSumiu...`, depois do `verify`:

```java
        // cada delegacao bem-sucedida vira um atendimento do cliente (lido nas proximas sessoes)
        assertThat(atendimentoRows(CUSTOMER_ID)).isGreaterThanOrEqualTo(1);
```

- novo teste:

```java
    @Test
    void cpfForaDoCadastroSemChamarLlmNemEspecialista() {
        restTestClient.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"sessionId\":\"it-sess-cpf\",\"cpf\":\"123.456.789-09\",\"message\":\"oi\"}")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(investimentosClient);
    }
```

- [ ] **Step 10: Rodar os ITs da Ana**

Run: `cd ana-agent && mvn -q -P integration-test test`
Expected: PASS (inclui `EntrypointHasIntegrationTestRuleIT` — nenhum entrypoint novo). Lento em CPU (minutos).

- [ ] **Step 11: Commit**

```bash
git add ana-agent
git commit -m "feat(ana): identifica o cliente por CPF e lembra atendimentos de outras sessoes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: chat-web com CPF, CPFs de teste e bloco "Conta garantia"

**Files:**
- Create: `chat-web/lib/cpf.ts`, `chat-web/lib/cpf.test.ts`
- Modify: `chat-web/lib/clientes.ts`, `chat-web/lib/tipos.ts`
- Modify: `chat-web/components/Chat.tsx`, `chat-web/components/PainelDebug.tsx`, `chat-web/components/Chat.module.css`
- Modify: `chat-web/app/api/chat/route.ts`
- Test: `chat-web/components/Chat.test.tsx`, `chat-web/app/api/chat/route.test.ts`

**Interfaces:**
- Consumes: `POST /chat` `{sessionId, cpf, message}`; 400 `{error}`; `debug.situacaoGarantia` (Task 7).
- Produces: `formatarCpf(valor: string): string`, `soDigitos(valor: string): string`; tipos `SituacaoGarantia`, `ChatRequisicao = {sessionId, cpf, message}`.

- [ ] **Step 1: Testes de `lib/cpf` que falham**

`chat-web/lib/cpf.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { formatarCpf, soDigitos } from "./cpf";

describe("cpf", () => {
  it("mantém só dígitos, no máximo 11", () => {
    expect(soDigitos("888.008.008-31")).toBe("88800800831");
    expect(soDigitos("888.008.008-3199")).toBe("88800800831");
  });

  it("aplica a máscara progressivamente", () => {
    expect(formatarCpf("888")).toBe("888");
    expect(formatarCpf("8880")).toBe("888.0");
    expect(formatarCpf("8880080")).toBe("888.008.0");
    expect(formatarCpf("88800800831")).toBe("888.008.008-31");
  });
});
```

Run: `cd chat-web && npx vitest run lib/cpf.test.ts` → FAIL (módulo não existe).

- [ ] **Step 2: Implementar `lib/cpf.ts`**

```ts
/** Só os dígitos, limitado a 11. */
export function soDigitos(valor: string): string {
  return valor.replace(/\D/g, "").slice(0, 11);
}

/** Máscara 000.000.000-00 aplicada conforme o usuário digita. */
export function formatarCpf(valor: string): string {
  const d = soDigitos(valor);
  if (d.length <= 3) return d;
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`;
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}
```

Run: `cd chat-web && npx vitest run lib/cpf.test.ts` → PASS.

- [ ] **Step 3: Tipos e cenários**

`chat-web/lib/tipos.ts` — substituir `RespostaEspecialista` e `ChatRequisicao`:

```ts
/** Retenção em conta garantia por gastos no cartão (presente só quando o especialista usou o cred-mcp). */
export type SituacaoGarantia = {
  status: "LIBERADO_CONTA" | "EM_ANALISE" | "RETIDO_ATE_PAGAMENTO_FATURA" | "RETIDO_PARCIAL";
  valorResgatado: number;
  valorRetido: number;
  valorLiberado: number;
  proximoPasso: string;
};

/** Retorno do especialista no schema padrão (guideline §9), exposto pela Ana com ?debug=true. */
export type RespostaEspecialista = {
  facts: string[];
  answerDraft: string;
  confidence: number;
  risks: string[];
  sources: string[];
  situacaoGarantia?: SituacaoGarantia | null;
};

export type ChatRequisicao = {
  sessionId: string;
  cpf: string;
  message: string;
};
```

`chat-web/lib/clientes.ts` (substituir):

```ts
/** CPFs de teste (fictícios) dos cenários mock; a Ana resolve CPF → customerId. */
export const CLIENTES = [
  { cpf: "111.001.001-05", id: "cli-001", descricao: "Resgate de CDB em liquidação" },
  { cpf: "222.002.002-93", id: "cli-002", descricao: "Resgate liquidado, crédito na conta" },
  { cpf: "333.003.003-80", id: "cli-003", descricao: "CDB ativo, sem resgate" },
  { cpf: "444.004.004-76", id: "cli-004", descricao: "Nada encontrado" },
  { cpf: "555.005.005-62", id: "cli-005", descricao: "Garantia: liberado para a conta" },
  { cpf: "666.006.006-59", id: "cli-006", descricao: "Garantia: em análise (cartão)" },
  { cpf: "777.007.007-45", id: "cli-007", descricao: "Garantia: retido até pagar a fatura" },
  { cpf: "888.008.008-31", id: "cli-008", descricao: "Garantia: retido parcialmente" },
] as const;
```

- [ ] **Step 4: Reescrever os testes do Chat (falham)**

Substituir `chat-web/components/Chat.test.tsx` inteiro:

```tsx
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MENSAGEM_ANA_INDISPONIVEL } from "@/lib/mensagens";
import { Chat } from "./Chat";

const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

const SESSION_ID_INICIAL = "sessao-inicial";

function renderChat() {
  return render(<Chat sessionIdInicial={SESSION_ID_INICIAL} />);
}

async function iniciar(cpf = "111.001.001-05") {
  const user = userEvent.setup();
  await user.clear(screen.getByLabelText("CPF"));
  await user.type(screen.getByLabelText("CPF"), cpf);
  await user.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
  return user;
}

async function enviar(texto: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Mensagem"), texto);
  await user.click(screen.getByRole("button", { name: "Enviar" }));
  return user;
}

describe("Chat", () => {
  it("não deixa conversar antes de iniciar o atendimento com um CPF", () => {
    renderChat();

    expect(screen.getByLabelText("Mensagem")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Iniciar atendimento" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Nova conversa" })).toBeDisabled();
  });

  it("aplica a máscara no CPF digitado", async () => {
    renderChat();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("CPF"), "88800800831");

    expect(screen.getByLabelText("CPF")).toHaveValue("888.008.008-31");
  });

  it("envia a mensagem com o CPF e mostra a resposta da Ana, sem delegação no debug", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ sessionId: "s", reply: "Seu dinheiro estava aplicado onde?", debug: null }),
    );
    renderChat();
    await iniciar("111.001.001-05");

    await enviar("meu dinheiro sumiu");

    const conversa = screen.getByRole("list", { name: "Conversa" });
    expect(within(conversa).getByText("meu dinheiro sumiu")).toHaveAttribute("data-autor", "cliente");
    expect(await within(conversa).findByText("Seu dinheiro estava aplicado onde?")).toHaveAttribute(
      "data-autor",
      "ana",
    );
    expect(screen.getByText("sem delegação")).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/chat");
    const corpo = JSON.parse(init.body);
    expect(corpo).toEqual({
      sessionId: screen.getByTestId("session-id").textContent,
      cpf: "111.001.001-05",
      message: "meu dinheiro sumiu",
    });
  });

  it("CPF de teste preenche o campo e inicia o atendimento", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /888\.008\.008-31/ }));
    await enviar("oi");
    await screen.findByText("olá");

    expect(screen.getByLabelText("CPF")).toHaveValue("888.008.008-31");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).cpf).toBe("888.008.008-31");
  });

  it("envia a mensagem ao pressionar Enter no campo de texto", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    const user = await iniciar();

    await user.type(screen.getByLabelText("Mensagem"), "oi{Enter}");

    expect(await screen.findByText("olá")).toHaveAttribute("data-autor", "ana");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).message).toBe("oi");
  });

  it("preenche o painel de debug e o bloco Conta garantia quando houver retenção", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        sessionId: "s",
        reply: "Parte do resgate segue retida.",
        debug: {
          facts: ["Resgate res-008 RETIDO_PARCIAL"],
          answerDraft: "Parte do resgate segue retida.",
          confidence: 0.9,
          risks: [],
          sources: ["cdb-mcp", "cred-mcp"],
          situacaoGarantia: {
            status: "RETIDO_PARCIAL",
            valorResgatado: 10000,
            valorRetido: 3500,
            valorLiberado: 6500,
            proximoPasso: "Pagar a fatura do cartão",
          },
        },
      }),
    );
    renderChat();
    await iniciar("888.008.008-31");

    await enviar("estava em investimentos");

    const painel = screen.getByRole("complementary", { name: "Debug do especialista" });
    expect(await within(painel).findByText("Resgate res-008 RETIDO_PARCIAL")).toBeInTheDocument();
    expect(within(painel).getByText("confidence 0.90")).toBeInTheDocument();
    const garantia = within(painel).getByRole("region", { name: "Conta garantia" });
    expect(within(garantia).getByText("RETIDO_PARCIAL")).toBeInTheDocument();
    expect(within(garantia).getByText("R$ 3.500,00")).toBeInTheDocument();
    expect(within(garantia).getByText("R$ 6.500,00")).toBeInTheDocument();
    expect(within(garantia).getByText("Pagar a fatura do cartão")).toBeInTheDocument();
  });

  it("não mostra Conta garantia quando o especialista não trouxe retenção", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        sessionId: "s",
        reply: "Em liquidação.",
        debug: { facts: [], answerDraft: "x", confidence: 0.9, risks: [], sources: ["cdb-mcp"] },
      }),
    );
    renderChat();
    await iniciar();

    await enviar("estava em investimentos");

    await screen.findByText("Em liquidação.");
    expect(screen.queryByRole("region", { name: "Conta garantia" })).not.toBeInTheDocument();
  });

  it("mostra aviso quando a Ana está indisponível, mantendo o histórico", async () => {
    fetchMock.mockResolvedValue(Response.json({ error: MENSAGEM_ANA_INDISPONIVEL }, { status: 502 }));
    renderChat();
    await iniciar();

    await enviar("oi");

    expect(await screen.findByText(MENSAGEM_ANA_INDISPONIVEL)).toHaveAttribute("data-autor", "sistema");
    expect(screen.getByText("oi")).toBeInTheDocument();
  });

  it("mostra o motivo quando a Ana recusa o CPF", async () => {
    fetchMock.mockResolvedValue(Response.json({ error: "cliente nao encontrado" }, { status: 400 }));
    renderChat();
    await iniciar("123.456.789-09");

    await enviar("oi");

    expect(await screen.findByText("cliente nao encontrado")).toHaveAttribute("data-autor", "sistema");
  });

  it("nova conversa com o mesmo CPF limpa a tela e troca o sessionId", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    await iniciar("888.008.008-31");
    const user = await enviar("oi");
    await screen.findByText("olá");
    const sessaoAnterior = screen.getByTestId("session-id").textContent;

    await user.click(screen.getByRole("button", { name: "Nova conversa" }));
    await enviar("voltei");
    await screen.findAllByText("olá");

    expect(screen.queryByText("oi")).not.toBeInTheDocument();
    expect(screen.getByTestId("session-id").textContent).not.toBe(sessaoAnterior);
    const segunda = JSON.parse(fetchMock.mock.calls[1][1].body);
    expect(segunda.cpf).toBe("888.008.008-31");
    expect(segunda.sessionId).toBe(screen.getByTestId("session-id").textContent);
  });

  it("trocar o CPF inicia nova conversa com o novo CPF", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessionId: "s", reply: "olá", debug: null }));
    renderChat();
    await iniciar("111.001.001-05");
    const sessaoAnterior = screen.getByTestId("session-id").textContent;

    await iniciar("333.003.003-80");
    await enviar("oi");
    await screen.findByText("olá");

    expect(screen.getByTestId("session-id").textContent).not.toBe(sessaoAnterior);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).cpf).toBe("333.003.003-80");
  });

  it("bloqueia troca de CPF e nova conversa enquanto a requisição está pendente", async () => {
    let resolver: (value: Response) => void;
    fetchMock.mockReturnValue(
      new Promise<Response>((resolve) => {
        resolver = resolve;
      }),
    );
    renderChat();
    await iniciar();

    await enviar("oi");

    expect(screen.getByLabelText("CPF")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Nova conversa" })).toBeDisabled();

    resolver!(Response.json({ sessionId: "s", reply: "olá", debug: null }));

    expect(await screen.findByText("olá")).toBeInTheDocument();
    expect(screen.getByLabelText("CPF")).not.toBeDisabled();
    expect(screen.getByRole("button", { name: "Nova conversa" })).not.toBeDisabled();
  });
});
```

Run: `cd chat-web && npx vitest run components/Chat.test.tsx` → FAIL (sem campo CPF).

- [ ] **Step 5: Implementar o Chat**

`chat-web/components/Chat.tsx` (substituir inteiro):

```tsx
"use client";

import { type FormEvent, useState } from "react";
import { CLIENTES } from "@/lib/clientes";
import { formatarCpf, soDigitos } from "@/lib/cpf";
import { MENSAGEM_ANA_INDISPONIVEL } from "@/lib/mensagens";
import type { ChatRequisicao, ChatResposta, ErroResposta } from "@/lib/tipos";
import styles from "./Chat.module.css";
import { PainelDebug, type TurnoDebug } from "./PainelDebug";

type Mensagem = {
  id: number;
  autor: "cliente" | "ana" | "sistema";
  texto: string;
};

let proximoId = 0;
const novoId = () => ++proximoId;

export function Chat({ sessionIdInicial }: { sessionIdInicial: string }) {
  const [cpfDigitado, setCpfDigitado] = useState("");
  const [cpfAtivo, setCpfAtivo] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState(sessionIdInicial);
  const [mensagens, setMensagens] = useState<Mensagem[]>([]);
  const [turnos, setTurnos] = useState<TurnoDebug[]>([]);
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);

  function novaConversa(cpf: string) {
    setCpfAtivo(cpf);
    setSessionId(crypto.randomUUID());
    setMensagens([]);
    setTurnos([]);
  }

  function iniciarAtendimento(evento: FormEvent) {
    evento.preventDefault();
    if (soDigitos(cpfDigitado).length === 11) novaConversa(cpfDigitado);
  }

  function usarCpfDeTeste(cpf: string) {
    setCpfDigitado(cpf);
    novaConversa(cpf);
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const mensagem = texto.trim();
    if (!mensagem || enviando || !cpfAtivo) return;

    setTexto("");
    setEnviando(true);
    setMensagens((atuais) => [...atuais, { id: novoId(), autor: "cliente", texto: mensagem }]);
    try {
      const requisicao: ChatRequisicao = { sessionId, cpf: cpfAtivo, message: mensagem };
      const resposta = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requisicao),
      });
      if (!resposta.ok) {
        const erro = (await resposta.json()) as ErroResposta;
        setMensagens((atuais) => [...atuais, { id: novoId(), autor: "sistema", texto: erro.error }]);
        return;
      }
      const dados = (await resposta.json()) as ChatResposta;
      setMensagens((atuais) => [...atuais, { id: novoId(), autor: "ana", texto: dados.reply }]);
      setTurnos((atuais) => [...atuais, { id: novoId(), mensagem, debug: dados.debug }]);
    } catch {
      setMensagens((atuais) => [...atuais, { id: novoId(), autor: "sistema", texto: MENSAGEM_ANA_INDISPONIVEL }]);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className={styles.layout}>
      <main className={styles.principal}>
        <header className={styles.cabecalho}>
          <h1>Ana</h1>
          <form className={styles.cpf} onSubmit={iniciarAtendimento}>
            <label>
              CPF
              <input
                value={cpfDigitado}
                onChange={(e) => setCpfDigitado(formatarCpf(e.target.value))}
                placeholder="000.000.000-00"
                inputMode="numeric"
                disabled={enviando}
              />
            </label>
            <button type="submit" disabled={enviando || soDigitos(cpfDigitado).length !== 11}>
              Iniciar atendimento
            </button>
          </form>
          <button type="button" onClick={() => cpfAtivo && novaConversa(cpfAtivo)} disabled={enviando || !cpfAtivo}>
            Nova conversa
          </button>
          <small className={styles.sessao}>
            {cpfAtivo ? `Atendendo ${cpfAtivo} · ` : "Nenhum atendimento · "}
            <span data-testid="session-id">{sessionId}</span>
          </small>
          <details className={styles.cpfsTeste}>
            <summary>CPFs de teste</summary>
            <ul>
              {CLIENTES.map((cliente) => (
                <li key={cliente.cpf}>
                  <button type="button" onClick={() => usarCpfDeTeste(cliente.cpf)} disabled={enviando}>
                    {cliente.cpf} — {cliente.descricao}
                  </button>
                </li>
              ))}
            </ul>
          </details>
        </header>

        <ol className={styles.mensagens} aria-label="Conversa" aria-live="polite">
          {mensagens.map((m) => (
            <li key={m.id} className={styles[m.autor]} data-autor={m.autor}>
              {m.texto}
            </li>
          ))}
          {enviando && <li className={styles.digitando}>Ana está digitando…</li>}
        </ol>

        <form className={styles.entrada} onSubmit={enviar}>
          <input
            aria-label="Mensagem"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder={cpfAtivo ? "Escreva para a Ana…" : "Informe o CPF para iniciar"}
            disabled={enviando || !cpfAtivo}
          />
          <button type="submit" disabled={enviando || !cpfAtivo || !texto.trim()}>
            Enviar
          </button>
        </form>
      </main>
      <PainelDebug turnos={turnos} />
    </div>
  );
}
```

Nota: o `<details>` fechado ainda renderiza os botões no DOM (jsdom), então o teste "CPF de teste preenche o campo" encontra o botão sem abrir o `summary`. Se o Testing Library considerar os botões inacessíveis dentro de `<details>` fechado, adicionar `{ hidden: true }` ao `getByRole` desse teste.

`chat-web/components/Chat.module.css` — acrescentar ao fim:

```css
.cpf {
  display: flex;
  gap: 0.5rem;
  align-items: end;
}

.cpfsTeste {
  flex-basis: 100%;
  font-size: 0.85rem;
}

.cpfsTeste ul {
  list-style: none;
  padding: 0;
  margin: 0.25rem 0 0;
  display: grid;
  gap: 0.25rem;
}

.cpfsTeste button {
  text-align: left;
}

.garantia dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.15rem 0.75rem;
  margin: 0;
}

.garantia dt {
  font-weight: 600;
}
```

- [ ] **Step 6: Bloco Conta garantia no PainelDebug**

Em `chat-web/components/PainelDebug.tsx`: trocar o import de tipos por `import type { RespostaEspecialista, SituacaoGarantia } from "@/lib/tipos";`, adicionar:

```tsx
const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

/** Onde está o dinheiro retido em conta garantia (cred-mcp). */
function ContaGarantia({ situacao }: { situacao: SituacaoGarantia }) {
  return (
    <section className={styles.garantia} aria-label="Conta garantia">
      <h4>Conta garantia</h4>
      <dl>
        <dt>status</dt>
        <dd>{situacao.status}</dd>
        <dt>resgatado</dt>
        <dd>{BRL.format(situacao.valorResgatado)}</dd>
        <dt>retido</dt>
        <dd>{BRL.format(situacao.valorRetido)}</dd>
        <dt>liberado</dt>
        <dd>{BRL.format(situacao.valorLiberado)}</dd>
        <dt>próximo passo</dt>
        <dd>{situacao.proximoPasso}</dd>
      </dl>
    </section>
  );
}
```

e, depois de `<Lista titulo="sources" ... />`:

```tsx
              {turno.debug.situacaoGarantia && <ContaGarantia situacao={turno.debug.situacaoGarantia} />}
```

Atenção: `Intl` em pt-BR usa espaço não separável (`R$ 6.500,00`). Se `getByText("R$ 6.500,00")` falhar por isso, trocar no teste por `getByText(/R\$\s6\.500,00/)` (o regex `\s` casa com ` `).

- [ ] **Step 7: Rodar os testes do Chat**

Run: `cd chat-web && npx vitest run components/Chat.test.tsx`
Expected: PASS (12 testes).

- [ ] **Step 8: BFF repassa o motivo do 400 (teste que falha)**

Em `chat-web/app/api/chat/route.test.ts`: trocar `const corpo = { sessionId: "s-1", customerId: "cli-001", message: "meu dinheiro sumiu" };` por `const corpo = { sessionId: "s-1", cpf: "111.001.001-05", message: "meu dinheiro sumiu" };` e adicionar dentro do `describe`:

```ts
  it("repassa o motivo do 400 da Ana", async () => {
    fetchMock.mockResolvedValue(Response.json({ error: "CPF invalido" }, { status: 400 }));

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(400);
    expect(await resposta.json()).toEqual({ error: "CPF invalido" });
  });

  it("usa a mensagem genérica quando o 400 da Ana não traz motivo", async () => {
    fetchMock.mockResolvedValue(new Response("bad request", { status: 400 }));

    const resposta = await POST(requisicao());

    expect(resposta.status).toBe(400);
    expect(await resposta.json()).toEqual({ error: MENSAGEM_REQUISICAO_INVALIDA });
  });
```

(Se já existir um teste de 400 que espera `MENSAGEM_REQUISICAO_INVALIDA` com corpo JSON sem `error`, ele continua válido.)

Run: `cd chat-web && npx vitest run app/api/chat/route.test.ts` → FAIL em "repassa o motivo".

- [ ] **Step 9: Implementar no BFF**

Em `route.ts`, trocar o bloco do 400 por:

```ts
  if (resposta.status === 400) {
    return Response.json({ error: await motivoDoErro(resposta) }, { status: 400 });
  }
```

e adicionar ao fim do arquivo:

```ts
/** A Ana responde 400 como {"error": "..."} (ex.: CPF inválido); sem motivo legível, usa a mensagem genérica. */
async function motivoDoErro(resposta: Response): Promise<string> {
  try {
    const corpo = (await resposta.json()) as { error?: unknown };
    return typeof corpo.error === "string" && corpo.error.trim() ? corpo.error : MENSAGEM_REQUISICAO_INVALIDA;
  } catch {
    return MENSAGEM_REQUISICAO_INVALIDA;
  }
}
```

- [ ] **Step 10: Suite completa do front**

Run: `make test-web`
Expected: typecheck, lint e vitest PASS.

- [ ] **Step 11: Commit**

```bash
git add chat-web
git commit -m "feat(chat-web): atendimento por CPF, CPFs de teste e bloco Conta garantia no debug

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Smoke por CPF (cli-001..008 + retorno) e documentação

**Files:**
- Modify: `smoke-test.sh`, `README.md`, `docs/GUIA-TESTES.md`

**Interfaces:**
- Consumes: todo o fluxo (Tasks 1–8) no compose.

- [ ] **Step 1: Smoke**

Em `smoke-test.sh`:

`chat()` passa a receber CPF:

```bash
chat() { # sessionId cpf mensagem
  curl -s -X POST "$ANA_URL/chat?debug=true" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg s "$1" --arg c "$2" --arg m "$3" '{sessionId:$s, cpf:$c, message:$m}')"
}
```

`cenario()` recebe CPF e um 4º parâmetro opcional com o status de garantia esperado:

```bash
cenario() { # cpf palavra-esperada|BAIXA_CONFIANCA [facts-esperado] [status-garantia]
  local cpf=$1 esperado=$2 factsEsperado=${3:-} garantiaEsperada=${4:-} sessao="smoke-${1//[^0-9]/}-$(date +%s)"
  echo "== $cpf"
  local r1 r2 reply conf facts garantia
  r1=$(chat "$sessao" "$cpf" "meu dinheiro sumiu")
  echo "  Ana: $(jq -r .reply <<<"$r1")"
  grep -qi "onde" <<<"$(jq -r .reply <<<"$r1")"; verificar "turno 1 pergunta onde estava o dinheiro" $?

  r2=$(chat "$sessao" "$cpf" "estava em investimentos e agora nao consigo encontrar")
  reply=$(jq -r .reply <<<"$r2")
  conf=$(jq -r '.debug.confidence // "null"' <<<"$r2")
  facts=$(jq -c .debug.facts <<<"$r2")
  garantia=$(jq -r '.debug.situacaoGarantia.status // "null"' <<<"$r2")
  echo "  Ana: $reply"
  echo "  especialista: confidence=$conf sources=$(jq -c .debug.sources <<<"$r2") garantia=$garantia facts=$facts"
  [[ "$conf" != "null" ]]; verificar "turno 2 delegou via A2A (debug presente)" $?
  if [[ "$esperado" == "BAIXA_CONFIANCA" ]]; then
    awk -v c="$conf" 'BEGIN { exit !(c != "null" && c < 0.5) }'; verificar "confidence < 0.5" $?
  else
    grep -Eqi "$esperado" <<<"$reply"; verificar "resposta menciona '$esperado'" $?
  fi
  if [[ -n "$factsEsperado" ]]; then
    grep -qi "$factsEsperado" <<<"$facts"; verificar "debug.facts contem '$factsEsperado'" $?
  fi
  if [[ -n "$garantiaEsperada" ]]; then
    [[ "$garantia" == "$garantiaEsperada" ]]; verificar "debug.situacaoGarantia.status = $garantiaEsperada" $?
  fi
}
```

Bloco `--investimentos-fora`: trocar `cli-001` por `111.001.001-05` no `chat` e, no `-d` do `curl`, `\"customerId\":\"cli-001\"` por `\"cpf\":\"111.001.001-05\"`.

Novo cenário de retorno (antes do resumo final):

```bash
retorno() { # cpf customerId — sessão 1 delega, sessão 2 (mesmo CPF) deve ter o atendimento no banco
  local cpf=$1 cliente=$2 s1="smoke-ret1-$(date +%s)" s2="smoke-ret2-$(date +%s)" linhas
  echo "== retorno com o mesmo CPF ($cpf)"
  chat "$s1" "$cpf" "meu dinheiro sumiu" >/dev/null
  chat "$s1" "$cpf" "estava em investimentos e agora nao consigo encontrar" >/dev/null
  linhas=$(docker compose exec -T postgres psql -U agents -d agents -tAc \
    "select count(*) from ana.atendimento where customer_id = '$cliente'")
  [[ "${linhas:-0}" -ge 1 ]]; verificar "ana.atendimento tem registro de $cliente ($linhas)" $?
  echo "  Ana (sessão nova): $(jq -r .reply <<<"$(chat "$s2" "$cpf" "oi, voltei")")"
  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ANA_URL/chat" -H 'Content-Type: application/json' \
    -d '{"sessionId":"smoke-cpf","cpf":"123.456.789-09","message":"oi"}')
  [[ "$status" == "400" ]]; verificar "CPF fora do cadastro -> 400" $?
}
```

Chamadas (substituem as 4 atuais):

```bash
cenario 111.001.001-05 "liquida"
cenario 222.002.002-93 "conta" "LIQUIDADO"
cenario 333.003.003-80 "aplicad"
cenario 444.004.004-76 BAIXA_CONFIANCA
cenario 555.005.005-62 "conta" "" LIBERADO_CONTA
cenario 666.006.006-59 "an[aá]lise" "" EM_ANALISE
cenario 777.007.007-45 "fatura" "" RETIDO_ATE_PAGAMENTO_FATURA
cenario 888.008.008-31 "6[.,]?500" "" RETIDO_PARCIAL
retorno 888.008.008-31 cli-008
```

Run: `bash -n smoke-test.sh`
Expected: sem erro de sintaxe.

- [ ] **Step 2: README**

Em `README.md`:
- Título/diagrama ASCII: acrescentar a linha `                                                                               └─MCP─▶ cred-mcp:8084` abaixo da do tracking-money.
- Mermaid `graph LR`: adicionar `CRED[cred-mcp :8084]` e `Invest -->|MCP| CRED`.
- Sequence diagram: participante `participant CRED as cred-mcp (:8084)<br/>ContaGarantiaTools / ContaGarantiaRepository`; no `alt`, novo ramo:

```
        else tool de conta garantia
            TP->>CRED: MCP tools/call consultar_conta_garantia
            CRED-->>TP: JSON com retenções (status, retido, liberado)
```

  e, antes de `AA->>PG: SQLChatMemoryStore.getMessages`, as linhas:

```
    CC->>CC: Cpf.de(cpf) + CadastroClientes → customerId (400 se inválido/desconhecido)
    CC->>PG: HistoricoAtendimentos.recentesDeOutrasSessoes(customerId) [tabela atendimento]
```

  e, depois de `DT->>DT: UltimasRespostasInvestimentos.registrar(requestId)`: `DT->>PG: HistoricoAtendimentos.registrar(customerId, sessionId, §9)`. No passo `CH->>BFF`, trocar `customerId` por `cpf`.
- Tabela "Objetos do fluxo": novas linhas

| Objeto | App | Como funciona |
|---|---|---|
| `Cpf` / `CadastroClientes` | ana-agent | Valida o CPF (dígitos verificadores) e resolve `customerId` num cadastro mock. O CPF só aparece mascarado em log e não vai ao LLM, à memória nem ao A2A. |
| `HistoricoAtendimentos` / `JdbcHistoricoAtendimentos` | ana-agent | Um registro por delegação bem-sucedida na tabela `atendimento` (por `customerId`). O `ChatController` injeta os 3 mais recentes de outras sessões no system prompt (`@V("atendimentosAnteriores")`), formatados pelo `FormatadorAtendimentos`. |
| `SituacaoGarantia` | investimentos-agent e ana-agent | Campo opcional do §9 (`status`, `valorResgatado`, `valorRetido`, `valorLiberado`, `proximoPasso`). O executor A2A descarta valores inconsistentes e registra em `risks`. |
| `ContaGarantiaTools` / `ContaGarantiaRepository` | cred-mcp | Tool `consultar_conta_garantia` (`customerId`): retenções em conta garantia por gastos no cartão, com dados mock em memória. |

  e ajustar as linhas existentes: `Chat` (campo CPF + CPFs de teste), `POST /api/chat` (repassa o `error` do 400 da Ana), `ChatController` (CPF → customerId e histórico), `ToolProviderComLog / McpToolProvider` ("dos três MCP servers"), `McpServerConfig` (inclui cred-mcp), Postgres (tabela `atendimento` no schema `ana`).
- "Conversa manual": trocar `"customerId":"cli-001"` por `"cpf":"111.001.001-05"` nos dois curls.
- "Cenários mock": nova coluna CPF e 4 linhas:

| CPF | customerId | Situação | Esperado |
|---|---|---|---|
| 111.001.001-05 | cli-001 | Resgate CDB em liquidação, crédito processando | "em liquidação, aguarde alguns minutos" |
| 222.002.002-93 | cli-002 | Resgate liquidado, crédito na conta | "já está na sua conta" |
| 333.003.003-80 | cli-003 | CDB ativo, sem resgate | "continua aplicado" |
| 444.004.004-76 | cli-004 | Nada encontrado | confidence < 0.5, encaminha para humano |
| 555.005.005-62 | cli-005 | Resgate passou pela conta garantia e foi liberado | `LIBERADO_CONTA`, "já está na sua conta" |
| 666.006.006-59 | cli-006 | Resgate na conta garantia em análise (gastos no cartão) | `EM_ANALISE`, "em análise, sem prazo" |
| 777.007.007-45 | cli-007 | Resgate retido até pagar a fatura (gasto R$ 12.000) | `RETIDO_ATE_PAGAMENTO_FATURA`, fatura vence 05/10 |
| 888.008.008-31 | cli-008 | Gasto R$ 3.500: R$ 6.500 liberados, R$ 3.500 retidos | `RETIDO_PARCIAL` |

  e uma frase abaixo: "Voltar depois: com o mesmo CPF, clique em **Nova conversa** — a Ana abre lembrando do atendimento anterior (tabela `ana.atendimento`)."
- Seção "Rodando": `make run-mcps` passa a citar os 3 MCPs; `make smoke` "jornada completa para os 8 CPFs de teste + retorno".

- [ ] **Step 3: GUIA-TESTES**

Em `docs/GUIA-TESTES.md`:
- §3: `make smoke       # 8 CPFs de teste + retorno + health do chat-web → "SMOKE OK"`.
- §4: explicar que o chat pede CPF (lista "CPFs de teste" preenche e inicia); tabela com as 8 linhas (CPF, o que a Ana deve dizer, o que olhar no painel — para cli-005..008, o bloco **Conta garantia** e `cred-mcp` em `sources`).
- Nova §4.1 "Voltar depois com o mesmo CPF":
  1. CPF `888.008.008-31`, "meu dinheiro sumiu" → "estava em investimentos…" (Ana explica a retenção parcial).
  2. Clique em **Nova conversa** (mesmo CPF, `sessionId` novo) e diga "oi, voltei".
  3. Esperado: a Ana cita o atendimento anterior e pergunta se é o mesmo assunto; se "sim", delega de novo.
  4. Conferir no banco: `docker compose exec postgres psql -U agents -d agents -c "select criado_em, customer_id, session_id, garantia_status, resumo from ana.atendimento order by criado_em desc"`.
- §5.2 e §5.3: curls da Ana com `"cpf":"111.001.001-05"` (o A2A continua com `customerId` no DataPart — deixar explícito que o CPF para na Ana).
- §5.4: acrescentar a consulta de `ana.atendimento`.
- §6: linha "cred-mcp fora | `docker compose stop cred-mcp` e conversar com `888.008.008-31` | o especialista responde sem a garantia, cita a falha em `risks` e baixa a `confidence`".

- [ ] **Step 4: Verificação ponta a ponta**

Run (precisa de `.env` com LLM configurado e Docker):

```bash
make test && make test-web && make up && make smoke && make smoke-falha; make down
```

Expected: `SMOKE OK`; `smoke-falha` sem falhas. Os cenários dependem do LLM: se um modelo pequeno errar uma palavra-chave (ex.: "fatura"), registrar o caso e não afrouxar a asserção sem avisar.

- [ ] **Step 5: Commit**

```bash
git add smoke-test.sh README.md docs/GUIA-TESTES.md
git commit -m "docs: cred-mcp, CPF e retorno no smoke, README e guia de testes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

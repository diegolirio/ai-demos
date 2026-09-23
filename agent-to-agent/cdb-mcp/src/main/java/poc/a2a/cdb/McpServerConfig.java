package poc.a2a.cdb;

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

    /** O transporte é um HttpServlet (Streamable HTTP). */
    @Bean
    HttpServletStreamableServerTransportProvider mcpTransportProvider(JsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint("/mcp")
                .build();
    }

    /** Registrado em /mcp ao lado do DispatcherServlet. Async é obrigatório (SSE). */
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
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, CdbTools tools,
                            JsonMapper jsonMapper) {
        return McpServer.sync(transport)
                .serverInfo("cdb-mcp", "0.0.1")
                .instructions("Consultas de posicoes e resgates de CDB por cliente.")
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();
    }
}

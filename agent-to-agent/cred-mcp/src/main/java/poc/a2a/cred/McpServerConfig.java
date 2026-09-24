package poc.a2a.cred;

import java.util.stream.Stream;

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
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, ContaGarantiaTools garantia,
                            SolicitacoesCreditoTools solicitacoes, JsonMapper jsonMapper) {
        return McpServer.sync(transport)
                .serverInfo("cred-mcp", "0.0.1")
                .instructions("Credito: resgates retidos em conta garantia por gastos no cartao e "
                        + "solicitacoes de emprestimo/cartao (status e motivo de recusa).")
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(Stream.concat(garantia.specifications().stream(), solicitacoes.specifications().stream())
                        .toList())
                .build();
    }
}

package poc.a2a.investimentos.a2a;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Registrado via META-INF/services para o AgentCardValidator (ServiceLoader) reconhecer JSONRPC.
 * Equivalente ao QuarkusJSONRPCTransportMetadata do servidor de referência.
 */
public class SpringJsonRpcTransportMetadata implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }
}

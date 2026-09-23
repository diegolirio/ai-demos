package org.a2aproject.sdk.server.requesthandlers;

/**
 * O builder do DefaultRequestHandler fixa agentCompletionTimeoutSeconds=5 (o caminho CDI usa 30) e os campos
 * são package-private. Esta classe fica no mesmo pacote para ajustá-los sem reflection.
 */
public final class RequestHandlerTimeouts {

    private RequestHandlerTimeouts() {
    }

    public static void configurar(DefaultRequestHandler handler, int agentCompletionSeconds) {
        handler.agentCompletionTimeoutSeconds = agentCompletionSeconds;
        handler.consumptionCompletionTimeoutSeconds = 5;
    }
}

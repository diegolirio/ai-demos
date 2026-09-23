package poc.a2a.ana.investimentos;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Servidor A2A 1.0 falso (JDK HttpServer) com respostas canned no formato real do a2a-java 1.3.1. */
class FakeA2aServer implements AutoCloseable {

    static final String TASK_COMPLETED = """
            {"jsonrpc":"2.0","id":%s,"result":{"task":{"id":"task-1","contextId":"sess-1",
             "status":{"state":"TASK_STATE_COMPLETED"},
             "artifacts":[{"artifactId":"art-1","name":"resposta-investimentos","parts":[
               {"text":"Seu resgate esta em liquidacao."},
               {"data":{"facts":["Resgate res-001 de R$ 5000.00 EM_LIQUIDACAO"],
                        "answerDraft":"Seu resgate esta em liquidacao.","confidence":0.9,
                        "risks":[],"sources":["cdb-mcp","tracking-money-mcp"]}}]}]}}}
            """;

    static final String TASK_FAILED = """
            {"jsonrpc":"2.0","id":%s,"result":{"task":{"id":"task-2","contextId":"sess-1",
             "status":{"state":"TASK_STATE_FAILED"},"artifacts":[]}}}
            """;

    private static final String CARD = """
            {"name":"investimentos-agent","description":"fake","version":"1.0.0",
             "capabilities":{"streaming":false,"pushNotifications":false,"extendedAgentCard":false},
             "defaultInputModes":["text"],"defaultOutputModes":["text","application/json"],
             "skills":[{"id":"localizar-dinheiro-investimentos","name":"Localizar dinheiro",
                        "description":"fake","tags":["investimentos"]}],
             "supportedInterfaces":[{"protocolBinding":"JSONRPC","url":"%s","protocolVersion":"1.0"}],
             "preferredTransport":"JSONRPC"}
            """;

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|\\d+)");

    private final HttpServer server;
    final List<String> requisicoes = new CopyOnWriteArrayList<>();
    volatile String respostaSendMessage = TASK_COMPLETED;
    volatile long atrasoMs = 0;

    FakeA2aServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/.well-known/agent-card.json", ex -> responder(ex, CARD.formatted(url())));
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), UTF_8);
            requisicoes.add(body);
            dormir(atrasoMs);
            Matcher id = ID.matcher(body);
            responder(ex, respostaSendMessage.formatted(id.find() ? id.group(1) : "1"));
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void responder(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

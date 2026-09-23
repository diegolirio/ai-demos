package poc.a2a.investimentos.a2a;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;
import static org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys.HEADERS_KEY;
import static org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys.METHOD_NAME_KEY;
import static org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys.TENANT_KEY;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.IdJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.InvalidParamsJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.json.MethodNotFoundJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetExtendedAgentCardRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetExtendedAgentCardResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.NonStreamingJSONRPCRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port para Spring MVC do A2AServerRoutes (Quarkus) do a2a-java: JSON-RPC, somente não-streaming.
 * Os corpos trafegam como String: o SDK serializa com Gson + protobuf JsonFormat, o Jackson 3 do Spring não participa.
 */
@RestController
public class A2aJsonRpcController {

    private static final Logger log = LoggerFactory.getLogger(A2aJsonRpcController.class);

    private final JSONRPCHandler jsonRpcHandler;

    public A2aJsonRpcController(JSONRPCHandler jsonRpcHandler) {
        this.jsonRpcHandler = jsonRpcHandler;
    }

    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> agentCard() throws JsonProcessingException {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(JsonUtil.toJson(jsonRpcHandler.getAgentCard()));
    }

    @PostMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jsonRpc(@RequestBody String body, HttpServletRequest http) {
        ServerCallContext context = criarCallContext(http);
        A2AResponse<?> response;
        try {
            A2ARequest<?> request = JSONRPCUtils.parseRequestBody(body, "");
            context.getState().put(METHOD_NAME_KEY, request.getMethod());
            if (request instanceof NonStreamingJSONRPCRequest<?> naoStreaming) {
                response = processarNaoStreaming(naoStreaming, context);
            } else {
                response = new A2AErrorResponse(request.getId(), new UnsupportedOperationError());
            }
        } catch (A2AError e) {
            response = new A2AErrorResponse(e);
        } catch (InvalidParamsJsonMappingException e) {
            response = new A2AErrorResponse(e.getId(), new InvalidParamsError(null, e.getMessage(), null));
        } catch (MethodNotFoundJsonMappingException e) {
            response = new A2AErrorResponse(e.getId(), new MethodNotFoundError(null, e.getMessage(), null));
        } catch (IdJsonMappingException e) {
            response = new A2AErrorResponse(e.getId(), new InvalidRequestError(null, e.getMessage(), null));
        } catch (JsonMappingException e) {
            response = new A2AErrorResponse(new InvalidRequestError(null, e.getMessage(), null));
        } catch (JsonSyntaxException | JsonProcessingException e) {
            response = new A2AErrorResponse(new JSONParseError(e.getMessage()));
        } catch (Throwable t) {
            log.error("Erro interno processando JSON-RPC A2A", t);
            response = new A2AErrorResponse(new InternalError("Internal error"));
        }
        // JSON-RPC: sempre HTTP 200, erros vão no envelope
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(serializar(response));
    }

    private A2AResponse<?> processarNaoStreaming(NonStreamingJSONRPCRequest<?> request, ServerCallContext ctx) {
        if (request instanceof SendMessageRequest req) {
            return jsonRpcHandler.onMessageSend(req, ctx);
        }
        if (request instanceof GetTaskRequest req) {
            return jsonRpcHandler.onGetTask(req, ctx);
        }
        if (request instanceof CancelTaskRequest req) {
            return jsonRpcHandler.onCancelTask(req, ctx);
        }
        if (request instanceof ListTasksRequest req) {
            return jsonRpcHandler.onListTasks(req, ctx);
        }
        if (request instanceof GetExtendedAgentCardRequest req) {
            return jsonRpcHandler.onGetExtendedCardRequest(req, ctx);
        }
        return new A2AErrorResponse(request.getId(), new UnsupportedOperationError());
    }

    private ServerCallContext criarCallContext(HttpServletRequest http) {
        Map<String, Object> state = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        Collections.list(http.getHeaderNames()).forEach(nome -> headers.put(nome, http.getHeader(nome)));
        state.put(HEADERS_KEY, headers);
        state.put(TENANT_KEY, "");
        state.put(TRANSPORT_KEY, TransportProtocol.JSONRPC);
        String versao = http.getHeader(A2AHeaders.A2A_VERSION);
        List<String> extensoes = Collections.list(http.getHeaders(A2AHeaders.A2A_EXTENSIONS));
        Set<String> extensoesPedidas = A2AExtensions.getRequestedExtensions(extensoes);
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, state, extensoesPedidas, versao);
    }

    private static String serializar(A2AResponse<?> response) {
        if (response instanceof A2AErrorResponse erro) {
            return JSONRPCUtils.toJsonRPCErrorResponse(erro.getId(), erro.getError());
        }
        if (response.getError() != null) {
            return JSONRPCUtils.toJsonRPCErrorResponse(response.getId(), response.getError());
        }
        com.google.protobuf.MessageOrBuilder proto;
        if (response instanceof SendMessageResponse r) {
            proto = ProtoUtils.ToProto.taskOrMessage(r.getResult());
        } else if (response instanceof GetTaskResponse r) {
            proto = ProtoUtils.ToProto.task(r.getResult());
        } else if (response instanceof CancelTaskResponse r) {
            proto = ProtoUtils.ToProto.task(r.getResult());
        } else if (response instanceof ListTasksResponse r) {
            proto = ProtoUtils.ToProto.listTasksResult(r.getResult());
        } else if (response instanceof GetExtendedAgentCardResponse r) {
            proto = ProtoUtils.ToProto.getExtendedCardResponse(r.getResult());
        } else {
            throw new IllegalArgumentException("Tipo de resposta desconhecido: " + response.getClass().getName());
        }
        return JSONRPCUtils.toJsonRPCResultResponse(response.getId(), proto);
    }
}

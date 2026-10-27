package de.prgrm.topology.deployment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal client for the Dev UI JSON-RPC websocket, as used by the web components.
 */
class DevUiJsonRpcClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final WebSocket webSocket;

    DevUiJsonRpcClient(int port) throws Exception {
        webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/q/dev-ui/json-rpc-ws"), new Listener())
                .get(10, TimeUnit.SECONDS);
    }

    /**
     * Calls {@code <namespace>_<method>} and returns the {@code result.object} of the response.
     */
    JsonNode call(String method, Map<String, Object> params) throws Exception {
        int id = ids.incrementAndGet();
        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        pending.put(id, response);
        webSocket.sendText(MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", "quarkus-messaging-topology_" + method,
                "params", params)), true);

        JsonNode message = response.get(10, TimeUnit.SECONDS);
        if (message.has("error")) {
            throw new IllegalStateException("JSON-RPC error: " + message.get("error"));
        }
        return message.path("result").path("object");
    }

    @Override
    public void close() {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    private class Listener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    JsonNode message = MAPPER.readTree(buffer.toString());
                    CompletableFuture<JsonNode> future = pending.remove(message.path("id").asInt(-1));
                    if (future != null) {
                        future.complete(message);
                    }
                } catch (Exception e) {
                    pending.values().forEach(f -> f.completeExceptionally(e));
                }
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }
}

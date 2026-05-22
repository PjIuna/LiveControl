package com.livecontrol.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KickChatBridge {
    private static final String CHANNEL_URL = "https://kick.com/api/v2/channels/%s";
    private static final String CHANNEL_CHATROOM_URL = "https://kick.com/api/v2/channels/%s/chatroom";
    private static final String PUSHER_URL = "wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679?protocol=7&client=js&version=8.4.0-rc2&flash=false";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36";
    private static final int MAX_SEEN_MESSAGES = 200;
    private static final int RECONNECT_SECONDS = 10;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LiveControl Kick Chat");
        thread.setDaemon(true);
        return thread;
    });

    private final Set<String> seenMessageIds = new HashSet<>();
    private final AtomicBoolean connectInProgress = new AtomicBoolean(false);

    private ScheduledFuture<?> reconnectTask;
    private WebSocket webSocket;
    private String chatroomId = "";
    private boolean running = false;
    private boolean warnedAboutResolveFailure = false;

    public synchronized void restart() {
        stop();
        LiveControlConfig config = LiveControlClient.config();
        if (!config.isReadyForKick()) {
            return;
        }

        running = true;
        scheduleConnect(0);
    }

    public synchronized void ensureRunning() {
        LiveControlConfig config = LiveControlClient.config();
        if (!config.isReadyForKick()) {
            stop();
            return;
        }

        if (!running) {
            restart();
        } else if (webSocket == null && (reconnectTask == null || reconnectTask.isDone() || reconnectTask.isCancelled())) {
            scheduleConnect(0);
        }
    }

    public synchronized void stop() {
        running = false;
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        if (webSocket != null) {
            webSocket.abort();
            webSocket = null;
        }
        seenMessageIds.clear();
        chatroomId = "";
        warnedAboutResolveFailure = false;
        connectInProgress.set(false);
    }

    private synchronized void scheduleConnect(int delaySeconds) {
        if (!running) {
            return;
        }
        if (reconnectTask != null && !reconnectTask.isDone() && !reconnectTask.isCancelled()) {
            return;
        }

        reconnectTask = scheduler.schedule(this::connectSafely, delaySeconds, TimeUnit.SECONDS);
    }

    private void connectSafely() {
        if (!connectInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            connect();
        } finally {
            connectInProgress.set(false);
        }
    }

    private void connect() {
        LiveControlConfig config = LiveControlClient.config();
        try {
            if (chatroomId.isBlank()) {
                chatroomId = resolveChatroomId(config.kickChannel);
            }
            if (chatroomId.isBlank()) {
                warnResolveFailure();
                scheduleConnect(RECONNECT_SECONDS);
                return;
            }

            httpClient.newWebSocketBuilder()
                    .header("User-Agent", USER_AGENT)
                    .connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(URI.create(PUSHER_URL), new KickWebSocketListener(chatroomId))
                    .whenComplete((socket, throwable) -> {
                        if (throwable != null) {
                            LiveControlClient.LOGGER.warn("Kick websocket connection failed", throwable);
                            scheduleConnect(RECONNECT_SECONDS);
                            return;
                        }

                        webSocket = socket;
                    });
        } catch (RuntimeException exception) {
            LiveControlClient.LOGGER.warn("Could not start Kick chat websocket", exception);
            scheduleConnect(RECONNECT_SECONDS);
        }
    }

    private String resolveChatroomId(String channel) {
        if (channel == null || channel.isBlank()) {
            return "";
        }

        String trimmed = channel.trim();
        if (trimmed.matches("\\d+")) {
            return trimmed;
        }

        String id = fetchChatroomId(CHANNEL_URL, trimmed);
        return id.isBlank() ? fetchChatroomId(CHANNEL_CHATROOM_URL, trimmed) : id;
    }

    private String fetchChatroomId(String endpoint, String channel) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(endpoint, encode(channel))))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "";
            }

            JsonElement root = parseJsonResponse(response.body());
            if (root == null || !root.isJsonObject()) {
                return "";
            }

            JsonObject object = root.getAsJsonObject();
            if (object.has("chatroom") && object.get("chatroom").isJsonObject()) {
                JsonObject chatroom = object.getAsJsonObject("chatroom");
                if (chatroom.has("id")) {
                    return chatroom.get("id").getAsString();
                }
            }
            if (object.has("id") && endpoint.equals(CHANNEL_CHATROOM_URL)) {
                return object.get("id").getAsString();
            }
            if (object.has("chatroom_id")) {
                return object.get("chatroom_id").getAsString();
            }
        } catch (IOException exception) {
            LiveControlClient.LOGGER.warn("Network error while resolving Kick chatroom", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            LiveControlClient.LOGGER.warn("Unexpected Kick channel response", exception);
        }

        return "";
    }

    private static JsonElement parseJsonResponse(String body) {
        if (body == null) {
            return null;
        }

        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }

        try {
            return JsonParser.parseString(trimmed);
        } catch (JsonSyntaxException exception) {
            return null;
        }
    }

    private void warnResolveFailure() {
        if (warnedAboutResolveFailure) {
            return;
        }

        warnedAboutResolveFailure = true;
        LiveControlClient.LOGGER.warn("Could not resolve Kick chatroom. Try putting the numeric chatroom ID in the Kick field if Kick blocks channel lookup.");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void handleSocketMessage(String raw) {
        JsonElement parsed = parseJsonResponse(raw);
        if (parsed == null || !parsed.isJsonObject()) {
            return;
        }

        JsonObject event = parsed.getAsJsonObject();
        String eventName = event.has("event") ? event.get("event").getAsString() : "";
        if ("pusher:ping".equals(eventName)) {
            sendSocketEvent("{\"event\":\"pusher:pong\",\"data\":{}}");
            return;
        }
        if (!"App\\Events\\ChatMessageEvent".equals(eventName) || !event.has("data")) {
            return;
        }

        JsonElement dataElement = event.get("data");
        JsonElement messageData = dataElement.isJsonPrimitive()
                ? parseJsonResponse(dataElement.getAsString())
                : dataElement;
        if (messageData == null || !messageData.isJsonObject()) {
            return;
        }

        JsonObject message = messageData.getAsJsonObject();
        String id = extractId(message);
        if (!seenMessageIds.add(id)) {
            return;
        }
        if (seenMessageIds.size() > MAX_SEEN_MESSAGES) {
            seenMessageIds.clear();
        }

        String content = extractContent(message);
        if (content != null && !content.isBlank()) {
            handleChatMessage(content);
        }
    }

    private static String extractId(JsonObject message) {
        if (message.has("id")) {
            return message.get("id").getAsString();
        }
        if (message.has("message_id")) {
            return message.get("message_id").getAsString();
        }
        return message.toString();
    }

    private static String extractContent(JsonObject message) {
        if (message.has("content")) {
            return message.get("content").getAsString();
        }
        if (message.has("message")) {
            return message.get("message").getAsString();
        }
        return null;
    }

    private void sendSocketEvent(String message) {
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.sendText(message, true);
        }
    }

    private static void handleChatMessage(String message) {
        if (!LiveControlClient.areChatCommandsEnabled()) {
            return;
        }

        LiveControlCommands.fromChatMessage(message).ifPresent(command -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> LiveControlClient.runLiveChatCommand(command));
        });
    }

    private final class KickWebSocketListener implements WebSocket.Listener {
        private final String resolvedChatroomId;
        private final StringBuilder partialMessage = new StringBuilder();

        private KickWebSocketListener(String resolvedChatroomId) {
            this.resolvedChatroomId = resolvedChatroomId;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            KickChatBridge.this.webSocket = webSocket;
            webSocket.request(1);
            webSocket.sendText("{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"\",\"channel\":\"chatrooms." + resolvedChatroomId + ".v2\"}}", true);
            webSocket.sendText("{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"\",\"channel\":\"chatrooms." + resolvedChatroomId + "\"}}", true);
            webSocket.sendText("{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"\",\"channel\":\"chatroom_" + resolvedChatroomId + "\"}}", true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data);
            if (last) {
                handleSocketMessage(partialMessage.toString());
                partialMessage.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (running) {
                LiveControlClient.LOGGER.warn("Kick websocket stopped", error);
                KickChatBridge.this.webSocket = null;
                scheduleConnect(RECONNECT_SECONDS);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            KickChatBridge.this.webSocket = null;
            if (running) {
                scheduleConnect(RECONNECT_SECONDS);
            }
            return null;
        }
    }
}

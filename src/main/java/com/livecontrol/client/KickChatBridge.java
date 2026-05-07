package com.livecontrol.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KickChatBridge {
    private static final String API_URL = "https://kick.com/api/v2/channels/%s/messages";
    private static final int MAX_SEEN_MESSAGES = 200;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LiveControl Kick Chat");
        thread.setDaemon(true);
        return thread;
    });

    private final Set<String> seenMessageIds = new HashSet<>();
    private final AtomicBoolean pollInProgress = new AtomicBoolean(false);

    private ScheduledFuture<?> pollingTask;

    public synchronized void restart() {
        stop();
        LiveControlConfig config = LiveControlClient.config();
        if (!config.isReadyForKick()) {
            return;
        }

        pollingTask = scheduler.scheduleWithFixedDelay(
                () -> pollSafely(config),
                0,
                config.pollSeconds,
                TimeUnit.SECONDS
        );
    }

    public synchronized void ensureRunning() {
        LiveControlConfig config = LiveControlClient.config();
        if (!config.isReadyForKick()) {
            stop();
            return;
        }

        if (pollingTask == null || pollingTask.isCancelled() || pollingTask.isDone()) {
            restart();
        }
    }

    public synchronized void stop() {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
        seenMessageIds.clear();
    }

    private void pollSafely(LiveControlConfig config) {
        if (!pollInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            poll(config);
        } finally {
            pollInProgress.set(false);
        }
    }

    private void poll(LiveControlConfig config) {
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri(config))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "LiveControl Minecraft Mod")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LiveControlClient.LOGGER.warn("Kick chat poll failed with HTTP {}", response.statusCode());
                return;
            }

            JsonElement root = JsonParser.parseString(response.body());
            handleMessages(root);
        } catch (IOException exception) {
            LiveControlClient.LOGGER.warn("Network error while polling Kick chat", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            LiveControlClient.LOGGER.warn("Unexpected Kick chat response", exception);
        }
    }

    private URI buildUri(LiveControlConfig config) {
        return URI.create(String.format(API_URL, encode(config.kickChannel)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void handleMessages(JsonElement root) {
        if (root == null || root.isJsonNull()) {
            return;
        }

        if (root.isJsonArray()) {
            handleMessageArray(root.getAsJsonArray());
        } else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (object.has("data") && object.get("data").isJsonObject()) {
                JsonObject data = object.getAsJsonObject("data");
                if (data.has("messages") && data.get("messages").isJsonArray()) {
                    handleMessageArray(data.getAsJsonArray("messages"));
                    return;
                }
            }
            if (object.has("messages") && object.get("messages").isJsonArray()) {
                handleMessageArray(object.getAsJsonArray("messages"));
            }
        }
    }

    private void handleMessageArray(JsonArray messages) {
        for (JsonElement messageElement : messages) {
            if (!messageElement.isJsonObject()) {
                continue;
            }

            JsonObject message = messageElement.getAsJsonObject();
            String id = message.has("id") ? message.get("id").getAsString() : message.toString();
            if (!seenMessageIds.add(id)) {
                continue;
            }

            if (seenMessageIds.size() > MAX_SEEN_MESSAGES) {
                seenMessageIds.clear();
            }

            String content = extractContent(message);
            if (content != null) {
                handleChatMessage(content);
            }
        }
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

    private static void handleChatMessage(String message) {
        if (!LiveControlClient.areChatCommandsEnabled()) {
            return;
        }

        LiveControlCommands.fromChatMessage(message).ifPresent(command -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> LiveControlClient.runLiveChatCommand(command));
        });
    }
}

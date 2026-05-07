package com.livecontrol.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class YoutubeChatBridge {
    private static final String API_URL = "https://www.googleapis.com/youtube/v3/liveChat/messages";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LiveControl YouTube Chat");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean pollInProgress = new AtomicBoolean(false);

    private ScheduledFuture<?> pollingTask;
    private String nextPageToken = "";

    public synchronized void restart() {
        stop();
        LiveControlConfig config = LiveControlClient.config();
        if (!config.isReadyForYoutube()) {
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
        if (!config.isReadyForYoutube()) {
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
        nextPageToken = "";
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
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LiveControlClient.LOGGER.warn("YouTube chat poll failed with HTTP {}", response.statusCode());
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (root.has("nextPageToken")) {
                nextPageToken = root.get("nextPageToken").getAsString();
            }

            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
            for (JsonElement item : items) {
                handleItem(item.getAsJsonObject());
            }
        } catch (IOException exception) {
            LiveControlClient.LOGGER.warn("Network error while polling YouTube chat", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            LiveControlClient.LOGGER.warn("Unexpected YouTube chat response", exception);
        }
    }

    private URI buildUri(LiveControlConfig config) {
        StringBuilder url = new StringBuilder(API_URL)
                .append("?part=snippet")
                .append("&profileImageSize=16")
                .append("&liveChatId=").append(encode(config.youtubeLiveChatId))
                .append("&key=").append(encode(config.youtubeApiKey));
        if (!nextPageToken.isBlank()) {
            url.append("&pageToken=").append(encode(nextPageToken));
        }
        return URI.create(url.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void handleItem(JsonObject item) {
        JsonObject snippet = item.has("snippet") ? item.getAsJsonObject("snippet") : null;
        if (snippet == null || !snippet.has("displayMessage")) {
            return;
        }

        if (!LiveControlClient.areChatCommandsEnabled()) {
            return;
        }

        String displayMessage = snippet.get("displayMessage").getAsString();
        LiveControlCommands.fromChatMessage(displayMessage).ifPresent(command -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> sendMinecraftChatMessage(client, command));
        });
    }

    private static void sendMinecraftChatMessage(MinecraftClient client, LiveControlCommands command) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.getNetworkHandler().sendChatMessage(command.chatCommand());
        client.player.sendMessage(Text.literal("LiveControl ran " + command.chatCommand()), true);
    }
}

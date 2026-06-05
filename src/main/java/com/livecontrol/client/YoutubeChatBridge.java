package com.livecontrol.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YoutubeChatBridge {
    private static final String LIVE_CHAT_URL = "https://www.youtube.com/live_chat?is_popout=1&v=%s";
    private static final String LIVE_CHAT_POLL_URL = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=%s";
    private static final Pattern INNERTUBE_API_KEY_PATTERN = Pattern.compile("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern INNERTUBE_CLIENT_VERSION_PATTERN = Pattern.compile("\"INNERTUBE_CONTEXT_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VISITOR_DATA_PATTERN = Pattern.compile("\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONTINUATION_PATTERN = Pattern.compile("\"continuation\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAGE_VIDEO_ID_PATTERN = Pattern.compile("\"videoId\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"");
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final String DEFAULT_CLIENT_VERSION = "2.20260520.01.00";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36";
    private static final int MAX_SEEN_MESSAGES = 300;

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
    private final List<StreamState> streams = new ArrayList<>();

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
        streams.clear();
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
        if (streams.isEmpty()) {
            streams.addAll(buildStreamStates(config));
        }

        for (StreamState stream : streams) {
            try {
                ensureContinuation(stream);
                if (stream.continuation.isBlank() || stream.apiKey.isBlank()) {
                    continue;
                }

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(buildPollUri(stream))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Origin", "https://www.youtube.com")
                        .header("Referer", String.format(LIVE_CHAT_URL, encode(stream.videoId)))
                        .POST(HttpRequest.BodyPublishers.ofString(buildPollBody(stream)));
                if (!stream.visitorData.isBlank()) {
                    requestBuilder.header("X-Goog-Visitor-Id", stream.visitorData);
                }

                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LiveControlClient.LOGGER.warn("YouTube chat poll failed with HTTP {}", response.statusCode());
                    if (response.statusCode() == 400 || response.statusCode() == 403) {
                        stream.resetPageContext();
                    }
                    continue;
                }

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                updateContinuation(stream, root);
                handleActions(stream, root);
            } catch (IOException exception) {
                LiveControlClient.LOGGER.warn("Network error while polling YouTube chat", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                stream.continuation = "";
                stream.apiKey = "";
                LiveControlClient.LOGGER.warn("Unexpected YouTube chat response", exception);
            }
        }
    }

    private void ensureContinuation(StreamState stream) throws IOException, InterruptedException {
        if (!stream.continuation.isBlank() && !stream.apiKey.isBlank()) {
            return;
        }

        if (stream.videoId.isBlank()) {
            resolveVideoId(stream);
        }
        if (stream.videoId.isBlank()) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(LIVE_CHAT_URL, encode(stream.videoId))))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LiveControlClient.LOGGER.warn("YouTube live chat page failed with HTTP {}", response.statusCode());
            return;
        }

        stream.apiKey = firstMatch(INNERTUBE_API_KEY_PATTERN, response.body());
        stream.clientVersion = firstMatch(INNERTUBE_CLIENT_VERSION_PATTERN, response.body());
        stream.visitorData = firstMatch(VISITOR_DATA_PATTERN, response.body());
        stream.continuation = firstMatch(CONTINUATION_PATTERN, response.body());
    }

    private void resolveVideoId(StreamState stream) throws IOException, InterruptedException {
        if (stream.sourceUrl.isBlank()) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(stream.sourceUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LiveControlClient.LOGGER.warn("YouTube stream page failed with HTTP {}", response.statusCode());
            return;
        }

        stream.videoId = firstMatch(PAGE_VIDEO_ID_PATTERN, response.body());
    }

    private static void updateContinuation(StreamState stream, JsonObject root) {
        JsonObject continuation = getObject(root, "continuationContents", "liveChatContinuation");
        if (continuation == null || !continuation.has("continuations") || !continuation.get("continuations").isJsonArray()) {
            return;
        }

        for (JsonElement element : continuation.getAsJsonArray("continuations")) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            for (String key : List.of("timedContinuationData", "invalidationContinuationData", "reloadContinuationData")) {
                if (object.has(key) && object.get(key).isJsonObject()) {
                    JsonObject data = object.getAsJsonObject(key);
                    if (data.has("continuation")) {
                        stream.continuation = data.get("continuation").getAsString();
                        return;
                    }
                }
            }
        }
    }

    private static void handleActions(StreamState stream, JsonObject root) {
        JsonObject continuation = getObject(root, "continuationContents", "liveChatContinuation");
        if (continuation == null || !continuation.has("actions") || !continuation.get("actions").isJsonArray()) {
            return;
        }

        for (JsonElement actionElement : continuation.getAsJsonArray("actions")) {
            if (!actionElement.isJsonObject()) {
                continue;
            }

            JsonObject renderer = getObject(actionElement.getAsJsonObject(), "addChatItemAction", "item", "liveChatTextMessageRenderer");
            if (renderer == null) {
                continue;
            }

            String id = renderer.has("id") ? renderer.get("id").getAsString() : renderer.toString();
            if (!stream.seenMessageIds.add(id)) {
                continue;
            }
            if (stream.seenMessageIds.size() > MAX_SEEN_MESSAGES) {
                stream.seenMessageIds.clear();
            }

            String message = extractMessage(renderer);
            if (!message.isBlank()) {
                handleChatMessage(message);
            }
        }
    }

    private static String extractMessage(JsonObject renderer) {
        JsonObject message = renderer.has("message") && renderer.get("message").isJsonObject()
                ? renderer.getAsJsonObject("message")
                : null;
        if (message == null || !message.has("runs") || !message.get("runs").isJsonArray()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (JsonElement runElement : message.getAsJsonArray("runs")) {
            if (runElement.isJsonObject() && runElement.getAsJsonObject().has("text")) {
                text.append(runElement.getAsJsonObject().get("text").getAsString());
            }
        }
        return text.toString();
    }

    private static JsonObject getObject(JsonObject object, String... path) {
        JsonElement current = object;
        for (String key : path) {
            if (current == null || !current.isJsonObject() || !current.getAsJsonObject().has(key)) {
                return null;
            }
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
    }

    private static List<StreamState> buildStreamStates(LiveControlConfig config) {
        List<StreamState> states = new ArrayList<>();
        addStreamState(states, config.youtubeStreamUrl);
        addStreamState(states, config.youtubeStreamUrl2);
        addStreamState(states, config.youtubeLiveChatId);
        return states;
    }

    private static void addStreamState(List<StreamState> states, String url) {
        String videoId = extractVideoId(url);
        String sourceUrl = normalizeYoutubeUrl(url);
        if (!videoId.isBlank() && states.stream().noneMatch(state -> state.videoId.equals(videoId))) {
            states.add(new StreamState(sourceUrl, videoId));
        } else if (!sourceUrl.isBlank() && states.stream().noneMatch(state -> state.sourceUrl.equals(sourceUrl))) {
            states.add(new StreamState(sourceUrl, ""));
        }
    }

    private static String extractVideoId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String trimmed = value.trim();
        if (VIDEO_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.endsWith("youtu.be")) {
                return path.replaceFirst("^/", "").split("/")[0];
            }
            if (path.startsWith("/watch")) {
                return queryParam(uri.getRawQuery(), "v");
            }
            if (path.startsWith("/live/") || path.startsWith("/shorts/") || path.startsWith("/embed/")) {
                String[] parts = path.split("/");
                return parts.length > 2 ? parts[2] : "";
            }
        } catch (IllegalArgumentException ignored) {
            return "";
        }

        return "";
    }

    private static String normalizeYoutubeUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String trimmed = value.trim();
        if (VIDEO_ID_PATTERN.matcher(trimmed).matches()) {
            return "https://www.youtube.com/watch?v=" + trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (!host.endsWith("youtube.com") && !host.endsWith("youtu.be")) {
                return "";
            }
            return uri.toString();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String queryParam(String query, String name) {
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            String[] pieces = part.split("=", 2);
            if (pieces.length == 2 && pieces[0].equals(name)) {
                return URLDecoder.decode(pieces[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static URI buildPollUri(StreamState stream) {
        return URI.create(String.format(LIVE_CHAT_POLL_URL, encode(stream.apiKey)));
    }

    private static String buildPollBody(StreamState stream) {
        String clientVersion = stream.clientVersion.isBlank() ? DEFAULT_CLIENT_VERSION : stream.clientVersion;
        String visitorData = stream.visitorData.isBlank() ? "" : ",\"visitorData\":\"" + jsonEscape(stream.visitorData) + "\"";
        return "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\""
                + jsonEscape(clientVersion)
                + "\""
                + visitorData
                + "}},\"continuation\":\""
                + jsonEscape(stream.continuation)
                + "\"}";
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void handleChatMessage(String displayMessage) {
        if (!LiveControlClient.areChatCommandsEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> LiveControlClient.handleChatMessage(displayMessage));
    }

    private static final class StreamState {
        private final String sourceUrl;
        private final Set<String> seenMessageIds = new HashSet<>();
        private String videoId;
        private String apiKey = "";
        private String clientVersion = "";
        private String visitorData = "";
        private String continuation = "";

        private StreamState(String sourceUrl, String videoId) {
            this.sourceUrl = sourceUrl;
            this.videoId = videoId;
        }

        private void resetPageContext() {
            apiKey = "";
            clientVersion = "";
            visitorData = "";
            continuation = "";
        }
    }
}

package com.livecontrol.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LiveControlConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("livecontrol.json");

    public boolean youtubeIntegrationEnabled = false;
    public String youtubeApiKey = "";
    public String youtubeLiveChatId = "";
    public int pollSeconds = 5;

    public static LiveControlConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            LiveControlConfig config = new LiveControlConfig();
            config.save();
            return config;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            LiveControlConfig config = GSON.fromJson(reader, LiveControlConfig.class);
            return config == null ? new LiveControlConfig() : config.sanitized();
        } catch (IOException exception) {
            LiveControlClient.LOGGER.warn("Could not load LiveControl config; using defaults", exception);
            return new LiveControlConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(sanitized(), writer);
            }
        } catch (IOException exception) {
            LiveControlClient.LOGGER.warn("Could not save LiveControl config", exception);
        }
    }

    public boolean isReadyForYoutube() {
        return youtubeIntegrationEnabled
                && !youtubeApiKey.isBlank()
                && !youtubeLiveChatId.isBlank();
    }

    private LiveControlConfig sanitized() {
        youtubeApiKey = youtubeApiKey == null ? "" : youtubeApiKey.trim();
        youtubeLiveChatId = youtubeLiveChatId == null ? "" : youtubeLiveChatId.trim();
        pollSeconds = Math.max(2, Math.min(60, pollSeconds));
        return this;
    }
}

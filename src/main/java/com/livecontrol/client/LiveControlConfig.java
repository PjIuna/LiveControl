package com.livecontrol.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LiveControlConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("livecontrol.json");

    public boolean youtubeIntegrationEnabled = false;
    public String youtubeApiKey = "";
    public String youtubeLiveChatId = "";
    public String youtubeStreamUrl = "";
    public String youtubeStreamUrl2 = "";
    public boolean twitchIntegrationEnabled = false;
    public String twitchChannel = "";
    public boolean kickIntegrationEnabled = false;
    public String kickChannel = "";
    public int pollSeconds = 5;
    public List<SafeZone> safeZones = new ArrayList<>();

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
                && (!youtubeStreamUrl.isBlank() || !youtubeStreamUrl2.isBlank() || !youtubeLiveChatId.isBlank());
    }

    public boolean isReadyForTwitch() {
        return twitchIntegrationEnabled && !twitchChannel.isBlank();
    }

    public boolean isReadyForKick() {
        return kickIntegrationEnabled && !kickChannel.isBlank();
    }

    private static String sanitizeChannel(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value.trim().replaceFirst("^@", "");
        int queryStart = sanitized.indexOf('?');
        if (queryStart >= 0) {
            sanitized = sanitized.substring(0, queryStart);
        }
        sanitized = sanitized.replaceFirst("/+$", "");
        int slash = sanitized.lastIndexOf('/');
        return slash >= 0 ? sanitized.substring(slash + 1).replaceFirst("^@", "") : sanitized;
    }

    private LiveControlConfig sanitized() {
        youtubeApiKey = youtubeApiKey == null ? "" : youtubeApiKey.trim();
        youtubeLiveChatId = youtubeLiveChatId == null ? "" : youtubeLiveChatId.trim();
        youtubeStreamUrl = youtubeStreamUrl == null ? "" : youtubeStreamUrl.trim();
        youtubeStreamUrl2 = youtubeStreamUrl2 == null ? "" : youtubeStreamUrl2.trim();
        twitchChannel = sanitizeChannel(twitchChannel);
        kickChannel = sanitizeChannel(kickChannel);
        pollSeconds = Math.max(2, Math.min(60, pollSeconds));
        if (safeZones == null) safeZones = new ArrayList<>();
        for (SafeZone safeZone : safeZones) {
            if (safeZone == null) continue;
            safeZone.id = safeZone.id == null ? "" : safeZone.id;
            safeZone.name = safeZone.name == null ? "" : safeZone.name;
            safeZone.world = safeZone.world == null ? "" : safeZone.world;
            safeZone.radius = safeZone.radius > 0.0 ? safeZone.radius : 20.0;
        }
        return this;
    }

    public static final class SafeZone {
        public String id = "";
        public String name = "";
        public String world = "";
        public double x = 0.0;
        public double y = 0.0;
        public double z = 0.0;
        public double radius = 20.0;

        // default constructor for GSON
        public SafeZone() {}

        public SafeZone(String id, String name, String world, double x, double y, double z) {
            this.id = id;
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = 20.0;
        }
    }
}

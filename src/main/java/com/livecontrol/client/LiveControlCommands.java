package com.livecontrol.client;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum LiveControlCommands {
    STONE("Stone", "#mine stone"),
    WOOD("Wood", "#mine oak_logs"),
    HOME("Home", "#home");

    private final String displayName;
    private final String minecraftMessage;

    LiveControlCommands(String displayName, String minecraftMessage) {
        this.displayName = displayName;
        this.minecraftMessage = minecraftMessage;
    }

    public String displayName() {
        return displayName;
    }

    public String minecraftMessage() {
        return minecraftMessage;
    }

    public static Optional<LiveControlCommands> fromChatMessage(String message) {
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(command -> command.displayName.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public static String bossBarHint() {
        return "YouTube chat: Stone → #mine stone   Wood → #mine oak_logs   Home → #home";
    }
}

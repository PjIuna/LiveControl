package com.livecontrol.client;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum LiveControlCommands {
    STONE("Stone", "#mine stone"),
    WOOD("Wood", "#mine oak_log"),
    HOME("Home", "#home");

    private final String displayName;
    private final String chatCommand;

    LiveControlCommands(String displayName, String chatCommand) {
        this.displayName = displayName;
        this.chatCommand = chatCommand;
    }

    public String displayName() {
        return displayName;
    }

    public String chatCommand() {
        return chatCommand;
    }

    public static Optional<LiveControlCommands> fromChatMessage(String message) {
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(command -> command.chatCommand.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public static String bossBarHint() {
        return Arrays.stream(values())
                .map(LiveControlCommands::chatCommand)
                .collect(Collectors.joining("   "));
    }
}

package com.livecontrol.client;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum LiveControlCommands {
    STONE("Mine", "#mine stone", "mine", "#mine", "stone", "#stone", "#mine stone"),
    WOOD("Wood", "#mine oak_log", "wood", "#wood", "#mine wood", "#mine oak_log"),
    FARM("Farm", "#farm", "farm", "#farm"),
    NETHER("Nether", "#goto minecraft:nether_portal", "nether", "#nether", "go nether", "goto nether"),
    GOLD("Gold", "#mine minecraft:nether_gold_ore", "gold", "#gold", "mine gold", "#mine gold"),
    JUMP("Jump", "#jump", "jump", "#jump"),
    OPEN_INVENTORY("Open Inventory", "#openinv", "open inventory", "open", "inventory", "#open inventory"),
    BREAK("Break", new String[0], "break"),
    HOME("Home", "#home", "home", "#home"),
    LOOT("Loot", "#pickup", "loot", "#loot", "pickup", "#pickup"),
    EXPLORE("Explore", "#explore", "explore", "#explore"),
    CRAFT("Craft", new String[0], "craft", "#craft"),
    CLOSE("Close", "#close", "close", "#close"),
    SLEEP("Sleep", new String[0], "sleep", "#sleep"),
    STOP("Stop", "#stop", "stop", "#stop");

    private static final String BOSS_BAR_PREFIX = "";

    private final String displayName;
    private final String[] chatCommands;
    private final String[] liveChatTriggers;

    LiveControlCommands(String displayName, String chatCommand, String... liveChatTriggers) {
        this(displayName, new String[]{chatCommand}, liveChatTriggers);
    }

    LiveControlCommands(String displayName, String[] chatCommands, String... liveChatTriggers) {
        this.displayName = displayName;
        this.chatCommands = chatCommands;
        this.liveChatTriggers = liveChatTriggers;
    }

    public String displayName() {
        return displayName;
    }

    public String chatCommand() {
        return chatCommands.length == 0 ? "" : chatCommands[0];
    }

    public String[] chatCommands() {
        return Arrays.copyOf(chatCommands, chatCommands.length);
    }

    public static Optional<LiveControlCommands> fromChatMessage(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(command -> command.matchesLiveChatTrigger(normalized))
                .findFirst();
    }

    public static String bossBarHint() {
        return Arrays.stream(values())
                .map(LiveControlCommands::displayName)
                .collect(Collectors.joining("   "));
    }

    public static String liveChatHint() {
        return Arrays.stream(values())
                .map(LiveControlCommands::displayName)
                .collect(Collectors.joining(", "));
    }

    private boolean matchesLiveChatTrigger(String normalizedMessage) {
        return Stream.concat(Stream.of(displayName), Arrays.stream(liveChatTriggers))
                .map(LiveControlCommands::normalize)
                .anyMatch(normalizedMessage::equals);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

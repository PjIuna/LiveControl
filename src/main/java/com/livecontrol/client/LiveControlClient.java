package com.livecontrol.client;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LiveControlClient implements ClientModInitializer {
    public static final String MOD_ID = "livecontrol";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final YoutubeChatBridge YOUTUBE_CHAT_BRIDGE = new YoutubeChatBridge();
    private static LiveControlConfig config;
    private static boolean afkBarVisible = false;

    @Override
    public void onInitializeClient() {
        config = LiveControlConfig.load();
        registerAfkCommand();
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> LiveControlHud.render(drawContext));
        YOUTUBE_CHAT_BRIDGE.restart();
    }

    public static LiveControlConfig config() {
        if (config == null) {
            config = LiveControlConfig.load();
        }
        return config;
    }

    public static void saveConfig(LiveControlConfig updatedConfig) {
        config = updatedConfig;
        config.save();
        YOUTUBE_CHAT_BRIDGE.restart();
    }

    public static boolean isAfkBarVisible() {
        return afkBarVisible;
    }

    private static void registerAfkCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("afk")
                        .executes(context -> {
                            afkBarVisible = !afkBarVisible;
                            context.getSource().sendFeedback(Text.literal(
                                    afkBarVisible
                                            ? "LiveControl AFK boss bar enabled."
                                            : "LiveControl AFK boss bar disabled."
                            ));
                            return Command.SINGLE_SUCCESS;
                        })
        ));
    }
}

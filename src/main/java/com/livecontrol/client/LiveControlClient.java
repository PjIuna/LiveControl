package com.livecontrol.client;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LiveControlClient implements ClientModInitializer {
    public static final String MOD_ID = "livecontrol";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final YoutubeChatBridge YOUTUBE_CHAT_BRIDGE = new YoutubeChatBridge();
    private static final int ATTACK_ESCAPE_TICKS = 60;
    private static LiveControlConfig config;
    private static boolean chatCommandsEnabled = true;
    private static int previousHurtTime = 0;
    private static int escapeTicks = 0;
    private static double escapeYaw = 0.0D;

    @Override
    public void onInitializeClient() {
        config = LiveControlConfig.load();
        registerCommands();
        ClientTickEvents.END_CLIENT_TICK.register(LiveControlClient::tickAttackEscape);
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

    public static boolean areChatCommandsEnabled() {
        return chatCommandsEnabled;
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("afk")
                    .executes(context -> {
                        boolean visible = !LiveControlBossBar.isVisible();
                        chatCommandsEnabled = visible;
                        LiveControlBossBar.setVisible(visible);
                        context.getSource().sendFeedback(Text.literal(
                                visible
                                        ? "LiveControl AFK boss bar and chat commands enabled."
                                        : "LiveControl AFK boss bar and chat commands disabled."
                        ));
                        return Command.SINGLE_SUCCESS;
                    }));

            dispatcher.register(ClientCommandManager.literal("back")
                    .executes(context -> {
                        goBack();
                        context.getSource().sendFeedback(Text.literal("LiveControl chat commands disabled and #stop sent."));
                        return Command.SINGLE_SUCCESS;
                    }));
        });
    }

    private static void goBack() {
        chatCommandsEnabled = false;
        LiveControlBossBar.setVisible(false);
        sendMinecraftChatMessage("#stop");
    }

    private static void tickAttackEscape(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            previousHurtTime = 0;
            escapeTicks = 0;
            return;
        }

        if (player.hurtTime > previousHurtTime) {
            Entity attacker = getAttacker(player);
            if (attacker != null) {
                sendMinecraftChatMessage("#stop");
                startEscape(player, attacker);
            }
        }
        previousHurtTime = player.hurtTime;

        if (escapeTicks > 0) {
            runAway(player);
            escapeTicks--;
        }
    }

    private static Entity getAttacker(ClientPlayerEntity player) {
        DamageSource damageSource = player.getRecentDamageSource();
        if (damageSource != null && damageSource.getAttacker() != null) {
            return damageSource.getAttacker();
        }
        return player.getAttacker();
    }

    private static void startEscape(ClientPlayerEntity player, Entity attacker) {
        Vec3d away = player.getPos().subtract(attacker.getPos());
        if (away.horizontalLengthSquared() < 1.0E-6D) {
            away = player.getRotationVector();
        }

        escapeYaw = Math.toDegrees(Math.atan2(away.z, away.x)) - 90.0D;
        escapeTicks = ATTACK_ESCAPE_TICKS;
    }

    private static void runAway(ClientPlayerEntity player) {
        player.setYaw((float) escapeYaw);
        player.setSprinting(true);
        player.input.pressingForward = true;
        player.input.pressingBack = false;
        player.input.pressingLeft = false;
        player.input.pressingRight = false;
        player.input.movementForward = 1.0F;
        player.input.movementSideways = 0.0F;
    }

    private static void sendMinecraftChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.getNetworkHandler().sendChatMessage(message);
    }
}

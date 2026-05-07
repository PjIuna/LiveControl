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
    private static final TwitchChatBridge TWITCH_CHAT_BRIDGE = new TwitchChatBridge();
    private static final KickChatBridge KICK_CHAT_BRIDGE = new KickChatBridge();
    private static final int ATTACK_ESCAPE_TICKS = 60;
    private static final int CHAT_BRIDGE_WATCHDOG_INTERVAL_TICKS = 20 * 20;
    private static LiveControlConfig config;
    private static boolean chatCommandsEnabled = true;
    private static int previousHurtTime = 0;
    private static int escapeTicks = 0;
    private static double escapeYaw = 0.0D;
    private static int chatBridgeWatchdogTicks = 0;

    @Override
    public void onInitializeClient() {
        config = LiveControlConfig.load();
        registerCommands();
        ClientTickEvents.START_CLIENT_TICK.register(LiveControlClient::tickAttackEscape);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LiveControlBossBar.tick();
            tickChatBridgeWatchdog();
        });
        restartChatBridges();
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
        restartChatBridges();
    }

    private static void restartChatBridges() {
        chatBridgeWatchdogTicks = 0;
        YOUTUBE_CHAT_BRIDGE.restart();
        TWITCH_CHAT_BRIDGE.restart();
        KICK_CHAT_BRIDGE.restart();
    }

    private static void tickChatBridgeWatchdog() {
        chatBridgeWatchdogTicks++;
        if (chatBridgeWatchdogTicks < CHAT_BRIDGE_WATCHDOG_INTERVAL_TICKS) {
            return;
        }

        chatBridgeWatchdogTicks = 0;
        YOUTUBE_CHAT_BRIDGE.ensureRunning();
        TWITCH_CHAT_BRIDGE.ensureRunning();
        KICK_CHAT_BRIDGE.ensureRunning();
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
            runAway(client, player);
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

    private static void runAway(MinecraftClient client, ClientPlayerEntity player) {
        player.setYaw((float) escapeYaw);
        player.setSprinting(true);
        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        player.input.pressingForward = true;
        player.input.pressingBack = false;
        player.input.pressingLeft = false;
        player.input.pressingRight = false;
        player.input.movementForward = 1.0F;
        player.input.movementSideways = 0.0F;
        Vec3d forward = Vec3d.fromPolar(0.0F, (float) escapeYaw).normalize().multiply(0.18D);
        Vec3d velocity = player.getVelocity();
        player.setVelocity(forward.x, velocity.y, forward.z);
    }

    private static void sendMinecraftChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.getNetworkHandler().sendChatMessage(message);
    }
}

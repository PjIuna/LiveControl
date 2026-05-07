package com.livecontrol.client;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

public final class LiveControlClient implements ClientModInitializer {
    public static final String MOD_ID = "livecontrol";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final YoutubeChatBridge YOUTUBE_CHAT_BRIDGE = new YoutubeChatBridge();
    private static final TwitchChatBridge TWITCH_CHAT_BRIDGE = new TwitchChatBridge();
    private static final KickChatBridge KICK_CHAT_BRIDGE = new KickChatBridge();
    private static final int CHAT_BRIDGE_WATCHDOG_INTERVAL_TICKS = 20 * 20;
    private static final long LIVE_CHAT_COMMAND_COOLDOWN_MILLIS = 30_000L;
    private static final double ATTACK_CANCEL_DISTANCE = 24.0D;
    private static final double ATTACK_REACH_DISTANCE = 3.0D;
    private static final double MOB_RUN_START_DISTANCE = 8.0D;
    private static final double MOB_RUN_STOP_DISTANCE = 12.0D;
    private static final double MOB_CORNERED_ATTACK_DISTANCE = 4.5D;
    private static final int COMMAND_SEQUENCE_DELAY_TICKS = 25;
    private static final int OBSTACLE_AVOIDANCE_TICKS = 12;
    private static final int CORNERED_ATTACK_TICKS = 10;
    private static final double CAMERA_FOLLOW_MOVEMENT_THRESHOLD = 1.0E-4D;
    private static LiveControlConfig config;
    private static boolean chatCommandsEnabled = true;
    private static boolean cameraFollowsPlayerMovement = false;
    private static int previousHurtTime = 0;
    private static int chatBridgeWatchdogTicks = 0;
    private static long lastLiveChatCommandTime = 0L;
    private static Entity attackTarget;
    private static boolean runningFromMobs = false;
    private static final Queue<String> queuedLiveChatCommands = new ArrayDeque<>();
    private static int queuedLiveChatCommandTicks = 0;
    private static int obstacleAvoidanceTicks = 0;
    private static int obstacleAvoidanceDirection = 1;
    private static int corneredTicks = 0;
    private static Vec3d previousCameraFollowPosition;

    @Override
    public void onInitializeClient() {
        config = LiveControlConfig.load();
        registerCommands();
        setAutoJumpEnabled(chatCommandsEnabled);
        ClientTickEvents.START_CLIENT_TICK.register(LiveControlClient::tickCombatAutomation);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickAutoRespawn(client);
            tickCameraFollowMovement(client);
            tickQueuedLiveChatCommands(client);
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

    public static void runLiveChatCommand(LiveControlCommands command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null || !chatCommandsEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextAllowedTime = lastLiveChatCommandTime + LIVE_CHAT_COMMAND_COOLDOWN_MILLIS;
        if (now < nextAllowedTime) {
            long remainingSeconds = Math.max(1L, (nextAllowedTime - now + 999L) / 1000L);
            client.player.sendMessage(Text.literal("LiveControl command cooldown: wait " + remainingSeconds + "s."), true);
            return;
        }

        lastLiveChatCommandTime = now;
        String[] commandMessages = command.chatCommands();
        queueLiveChatCommands(commandMessages);
        String feedback = commandMessages.length == 1
                ? commandMessages[0]
                : command.displayName() + " sequence (" + commandMessages.length + " commands)";
        client.player.sendMessage(Text.literal("LiveControl ran " + feedback), true);
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("afk")
                    .executes(context -> {
                        boolean visible = !LiveControlBossBar.isVisible();
                        chatCommandsEnabled = visible;
                        cameraFollowsPlayerMovement = visible;
                        if (!visible) {
                            previousCameraFollowPosition = null;
                        }
                        setAutoJumpEnabled(visible);
                        LiveControlBossBar.setVisible(visible);
                        context.getSource().sendFeedback(Text.literal(
                                visible
                                        ? "LiveControl AFK boss bar, chat commands, and movement camera follow enabled."
                                        : "LiveControl AFK boss bar, chat commands, and movement camera follow disabled."
                        ));
                        return Command.SINGLE_SUCCESS;
                    }));

            dispatcher.register(ClientCommandManager.literal("back")
                    .executes(context -> {
                        goBack();
                        context.getSource().sendFeedback(Text.literal("LiveControl chat commands and movement camera follow disabled; #stop sent."));
                        return Command.SINGLE_SUCCESS;
                    }));
        });
    }

    private static void goBack() {
        chatCommandsEnabled = false;
        cameraFollowsPlayerMovement = false;
        previousCameraFollowPosition = null;
        setAutoJumpEnabled(false);
        LiveControlBossBar.setVisible(false);
        cancelQueuedLiveChatCommands();
        sendMinecraftChatMessage("#stop");
    }

    private static void tickCombatAutomation(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            previousHurtTime = 0;
            attackTarget = null;
            runningFromMobs = false;
            obstacleAvoidanceTicks = 0;
            corneredTicks = 0;
            return;
        }

        if (player.hurtTime > previousHurtTime) {
            Entity attacker = getAttacker(player);
            if (attacker != null) {
                beginAutoFight(attacker);
            }
        }
        previousHurtTime = player.hurtTime;

        if (attackTarget != null) {
            if (tickAttackTarget(client, player)) {
                return;
            }
        }

        tickMobEscape(client, player);
    }

    private static void tickAutoRespawn(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        if (!player.isAlive() || player.getHealth() <= 0.0F) {
            player.requestRespawn();
            if (client.currentScreen instanceof DeathScreen) {
                client.setScreen(null);
            }
        }
    }

    private static void tickCameraFollowMovement(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !cameraFollowsPlayerMovement || !player.isAlive()) {
            previousCameraFollowPosition = player == null ? null : player.getPos();
            return;
        }

        Vec3d currentPosition = player.getPos();
        if (previousCameraFollowPosition != null) {
            Vec3d movement = currentPosition.subtract(previousCameraFollowPosition);
            if (movement.horizontalLengthSquared() > CAMERA_FOLLOW_MOVEMENT_THRESHOLD) {
                setYawFromVector(player, movement);
            }
        }
        previousCameraFollowPosition = currentPosition;
    }

    private static Entity getAttacker(ClientPlayerEntity player) {
        DamageSource damageSource = player.getRecentDamageSource();
        if (damageSource != null && damageSource.getAttacker() != null) {
            return damageSource.getAttacker();
        }
        return player.getAttacker();
    }

    private static boolean tickAttackTarget(MinecraftClient client, ClientPlayerEntity player) {
        if (!isValidAttackTarget(player, attackTarget)) {
            attackTarget = null;
            stopMovement(client, player);
            return false;
        }

        double squaredDistance = player.squaredDistanceTo(attackTarget);
        if (squaredDistance > ATTACK_CANCEL_DISTANCE * ATTACK_CANCEL_DISTANCE) {
            attackTarget = null;
            stopMovement(client, player);
            return false;
        }

        faceEntity(player, attackTarget);
        if (squaredDistance > ATTACK_REACH_DISTANCE * ATTACK_REACH_DISTANCE) {
            moveForward(client, player, true);
        } else {
            stopMovement(client, player);
            if (client.interactionManager != null && player.getAttackCooldownProgress(0.0F) >= 1.0F) {
                client.interactionManager.attackEntity(player, attackTarget);
                player.swingHand(Hand.MAIN_HAND);
            }
        }
        return true;
    }

    private static boolean isValidAttackTarget(ClientPlayerEntity player, Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) {
            return false;
        }
        return target.isAlive()
                && !target.isRemoved()
                && livingTarget.getHealth() > 0.0F
                && target.getWorld() == player.getWorld();
    }

    private static void tickMobEscape(MinecraftClient client, ClientPlayerEntity player) {
        HostileEntity nearestMob = nearestHostileMob(player, runningFromMobs ? MOB_RUN_STOP_DISTANCE : MOB_RUN_START_DISTANCE);
        if (nearestMob == null) {
            if (runningFromMobs) {
                runningFromMobs = false;
                obstacleAvoidanceTicks = 0;
                corneredTicks = 0;
                stopMovement(client, player);
            }
            return;
        }

        runningFromMobs = true;
        if (isCorneredByMob(player, nearestMob)) {
            beginAutoFight(nearestMob);
            return;
        }
        runAwayFrom(client, player, nearestMob);
    }

    private static HostileEntity nearestHostileMob(ClientPlayerEntity player, double distance) {
        double squaredDistance = distance * distance;
        List<HostileEntity> mobs = player.getWorld().getEntitiesByClass(
                HostileEntity.class,
                player.getBoundingBox().expand(distance),
                mob -> mob.isAlive() && !mob.isRemoved() && player.squaredDistanceTo(mob) <= squaredDistance
        );
        return mobs.stream()
                .min(Comparator.comparingDouble(player::squaredDistanceTo))
                .orElse(null);
    }

    private static void runAwayFrom(MinecraftClient client, ClientPlayerEntity player, Entity threat) {
        Vec3d away = player.getPos().subtract(threat.getPos());
        if (away.horizontalLengthSquared() < 1.0E-6D) {
            away = player.getRotationVector();
        }

        Vec3d escape = away;
        if (player.horizontalCollision || obstacleAvoidanceTicks > 0) {
            if (obstacleAvoidanceTicks <= 0) {
                obstacleAvoidanceDirection = -obstacleAvoidanceDirection;
                obstacleAvoidanceTicks = OBSTACLE_AVOIDANCE_TICKS;
            } else {
                obstacleAvoidanceTicks--;
            }
            escape = rotateHorizontal(away, obstacleAvoidanceDirection * 55.0D);
        }

        setYawFromVector(player, escape);
        moveForward(client, player, true);
        Vec3d forward = Vec3d.fromPolar(0.0F, player.getYaw()).normalize().multiply(0.18D);
        Vec3d velocity = player.getVelocity();
        player.setVelocity(forward.x, velocity.y, forward.z);
    }

    private static boolean isCorneredByMob(ClientPlayerEntity player, Entity threat) {
        if (player.squaredDistanceTo(threat) > MOB_CORNERED_ATTACK_DISTANCE * MOB_CORNERED_ATTACK_DISTANCE) {
            corneredTicks = 0;
            return false;
        }

        if (player.horizontalCollision) {
            corneredTicks++;
        } else {
            corneredTicks = 0;
        }
        return corneredTicks >= CORNERED_ATTACK_TICKS;
    }

    private static Vec3d rotateHorizontal(Vec3d vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3d(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
    }

    private static void faceEntity(ClientPlayerEntity player, Entity entity) {
        Vec3d toward = entity.getPos().subtract(player.getPos());
        setYawFromVector(player, toward);
    }

    private static void setYawFromVector(ClientPlayerEntity player, Vec3d vector) {
        player.setYaw((float) (Math.toDegrees(Math.atan2(vector.z, vector.x)) - 90.0D));
    }

    private static void moveForward(MinecraftClient client, ClientPlayerEntity player, boolean sprinting) {
        player.setSprinting(sprinting);
        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(sprinting);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        player.input.pressingForward = true;
        player.input.pressingBack = false;
        player.input.pressingLeft = false;
        player.input.pressingRight = false;
        player.input.movementForward = 1.0F;
        player.input.movementSideways = 0.0F;
    }

    private static void stopMovement(MinecraftClient client, ClientPlayerEntity player) {
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        player.setSprinting(false);
        player.input.pressingForward = false;
        player.input.pressingBack = false;
        player.input.pressingLeft = false;
        player.input.pressingRight = false;
        player.input.movementForward = 0.0F;
        player.input.movementSideways = 0.0F;
    }

    private static void beginAutoFight(Entity target) {
        cancelQueuedLiveChatCommands();
        sendMinecraftChatMessage("#stop");
        attackTarget = target;
        runningFromMobs = false;
        obstacleAvoidanceTicks = 0;
        corneredTicks = 0;
    }

    private static void setAutoJumpEnabled(boolean enabled) {
        MinecraftClient.getInstance().options.getAutoJump().setValue(enabled);
    }

    private static void tickQueuedLiveChatCommands(MinecraftClient client) {
        if (queuedLiveChatCommands.isEmpty()) {
            return;
        }

        if (queuedLiveChatCommandTicks > 0) {
            queuedLiveChatCommandTicks--;
            return;
        }

        String message = queuedLiveChatCommands.poll();
        sendMinecraftChatMessage(client, message);
        queuedLiveChatCommandTicks = COMMAND_SEQUENCE_DELAY_TICKS;
    }

    private static void queueLiveChatCommands(String[] messages) {
        cancelQueuedLiveChatCommands();
        queuedLiveChatCommands.addAll(Arrays.asList(messages));
        queuedLiveChatCommandTicks = 0;
        tickQueuedLiveChatCommands(MinecraftClient.getInstance());
    }

    private static void cancelQueuedLiveChatCommands() {
        queuedLiveChatCommands.clear();
        queuedLiveChatCommandTicks = 0;
    }

    private static void sendMinecraftChatMessage(String message) {
        sendMinecraftChatMessage(MinecraftClient.getInstance(), message);
    }

    private static void sendMinecraftChatMessage(MinecraftClient client, String message) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.getNetworkHandler().sendChatMessage(message);
    }
}

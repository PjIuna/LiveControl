package com.livecontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class LiveControlBossBar {
    private static final UUID BOSS_BAR_ID = UUID.nameUUIDFromBytes("livecontrol-afk-bossbar".getBytes(StandardCharsets.UTF_8));
    private static final Formatting[] RAINBOW_COLORS = {
            Formatting.RED,
            Formatting.GOLD,
            Formatting.YELLOW,
            Formatting.GREEN,
            Formatting.AQUA,
            Formatting.BLUE,
            Formatting.LIGHT_PURPLE
    };
    private static final BossBar.Color[] BAR_COLORS = {
            BossBar.Color.RED,
            BossBar.Color.YELLOW,
            BossBar.Color.GREEN,
            BossBar.Color.BLUE,
            BossBar.Color.PURPLE
    };
    private static boolean visible = false;
    private static long lastRainbowSecond = -1L;
    private static int rainbowOffset = 0;

    private LiveControlBossBar() {
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void setVisible(boolean shouldBeVisible) {
        if (visible == shouldBeVisible) {
            return;
        }

        visible = shouldBeVisible;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }

        if (visible) {
            lastRainbowSecond = -1L;
            ClientBossBar bossBar = new ClientBossBar(
                    BOSS_BAR_ID,
                    rainbowTitle(),
                    1.0F,
                    currentBarColor(),
                    BossBar.Style.PROGRESS,
                    false,
                    false,
                    false
            );
            client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.add(bossBar));
        } else {
            client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.remove(BOSS_BAR_ID));
        }
    }

    public static void tick() {
        if (!visible) {
            return;
        }

        long currentSecond = System.currentTimeMillis() / 1000L;
        if (currentSecond == lastRainbowSecond) {
            return;
        }

        lastRainbowSecond = currentSecond;
        rainbowOffset++;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }

        ClientBossBar bossBar = new ClientBossBar(
                BOSS_BAR_ID,
                rainbowTitle(),
                1.0F,
                currentBarColor(),
                BossBar.Style.PROGRESS,
                false,
                false,
                false
        );
        client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.updateName(bossBar));
        client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.updateStyle(bossBar));
    }

    private static Text rainbowTitle() {
        String title = LiveControlCommands.bossBarHint();
        MutableText text = Text.empty();
        for (int index = 0; index < title.length(); index++) {
            Formatting color = RAINBOW_COLORS[(index + rainbowOffset) % RAINBOW_COLORS.length];
            text.append(Text.literal(String.valueOf(title.charAt(index))).formatted(color));
        }
        return text;
    }

    private static BossBar.Color currentBarColor() {
        return BAR_COLORS[rainbowOffset % BAR_COLORS.length];
    }
}

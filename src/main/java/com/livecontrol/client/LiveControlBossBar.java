package com.livecontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class LiveControlBossBar {
    private static final UUID BOSS_BAR_ID = UUID.nameUUIDFromBytes("livecontrol-afk-bossbar".getBytes(StandardCharsets.UTF_8));
    private static final BossBar.Color BAR_COLOR = BossBar.Color.BLUE;
    private static final Text BOSS_BAR_TITLE = Text.literal("LiveControl AFK").formatted(Formatting.AQUA);
    private static boolean visible = false;

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
            ClientBossBar bossBar = createBossBar();
            client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.add(bossBar));
        } else {
            client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.remove(BOSS_BAR_ID));
        }
    }

    public static void tick() {
        if (!visible) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }

        ClientBossBar bossBar = createBossBar();
        client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.updateName(bossBar));
        client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.updateStyle(bossBar));
    }

    private static ClientBossBar createBossBar() {
        return new ClientBossBar(
                BOSS_BAR_ID,
                BOSS_BAR_TITLE,
                1.0F,
                BAR_COLOR,
                BossBar.Style.PROGRESS,
                false,
                false,
                false
        );
    }
}

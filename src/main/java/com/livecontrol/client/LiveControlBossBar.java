package com.livecontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class LiveControlBossBar {
    private static final UUID BOSS_BAR_ID = UUID.nameUUIDFromBytes("livecontrol-afk-bossbar".getBytes(StandardCharsets.UTF_8));
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
            ClientBossBar bossBar = new ClientBossBar(
                    BOSS_BAR_ID,
                    Text.literal(LiveControlCommands.bossBarHint()),
                    1.0F,
                    BossBar.Color.BLUE,
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
}

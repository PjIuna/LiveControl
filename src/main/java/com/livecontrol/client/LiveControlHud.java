package com.livecontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public final class LiveControlHud {
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 12;
    private static final int CYAN = 0xFF00E5FF;
    private static final int DARK_CYAN = 0xCC004A52;
    private static final int BLACK = 0xAA000000;

    private LiveControlHud() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LiveControlClient.isAfkBarVisible() || client.options.hudHidden || client.player == null) {
            return;
        }

        int centerX = context.getScaledWindowWidth() / 2;
        int barX = centerX - BAR_WIDTH / 2;
        int titleY = 4;
        int barY = 16;
        int hintY = barY + BAR_HEIGHT + 4;

        drawCenteredText(context, Text.literal("LiveControl"), centerX, titleY, 0xFFFFFFFF);
        drawBossBar(context, barX, barY);
        drawCenteredText(context, Text.literal(LiveControlCommands.bossBarHint()), centerX, hintY, CYAN);
    }

    private static void drawBossBar(DrawContext context, int x, int y) {
        context.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BLACK);
        context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, DARK_CYAN);
        context.fill(x + 1, y + 1, x + BAR_WIDTH - 1, y + BAR_HEIGHT - 1, CYAN);
        context.fill(x + 3, y + 3, x + BAR_WIDTH - 3, y + BAR_HEIGHT - 3, 0xAA7FFFFF);
    }

    private static void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        context.drawCenteredTextWithShadow(client.textRenderer, text, centerX, y, color);
        matrices.pop();
    }
}

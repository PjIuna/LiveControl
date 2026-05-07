package com.livecontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class LiveControlConfigScreen extends Screen {
    private static final int FIELD_WIDTH = 260;
    private static final int FIELD_HEIGHT = 20;

    private final Screen parent;
    private TextFieldWidget apiKeyField;
    private TextFieldWidget liveChatIdField;
    private TextFieldWidget pollSecondsField;
    private boolean youtubeEnabled;

    public LiveControlConfigScreen(Screen parent) {
        super(Text.literal("LiveControl Configuration"));
        this.parent = parent;
        this.youtubeEnabled = LiveControlClient.config().youtubeIntegrationEnabled;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = height / 4;
        LiveControlConfig config = LiveControlClient.config();

        apiKeyField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 34, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("YouTube API Key"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setText(config.youtubeApiKey);
        addDrawableChild(apiKeyField);

        liveChatIdField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 74, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("YouTube Live Chat ID"));
        liveChatIdField.setMaxLength(256);
        liveChatIdField.setText(config.youtubeLiveChatId);
        addDrawableChild(liveChatIdField);

        pollSecondsField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 114, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("Poll seconds"));
        pollSecondsField.setMaxLength(2);
        pollSecondsField.setText(Integer.toString(config.pollSeconds));
        addDrawableChild(pollSecondsField);

        addDrawableChild(ButtonWidget.builder(enabledText(), button -> {
            youtubeEnabled = !youtubeEnabled;
            button.setMessage(enabledText());
        }).dimensions(centerX - FIELD_WIDTH / 2, startY + 145, FIELD_WIDTH, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveAndClose())
                .dimensions(centerX - 104, startY + 175, 100, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(centerX + 4, startY + 175, 100, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int centerX = width / 2;
        int startY = height / 4;

        context.drawCenteredTextWithShadow(textRenderer, title, centerX, startY - 10, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "YouTube Data API key", centerX - FIELD_WIDTH / 2, startY + 22, 0xFFA0FFFF);
        context.drawTextWithShadow(textRenderer, "Live chat ID", centerX - FIELD_WIDTH / 2, startY + 62, 0xFFA0FFFF);
        context.drawTextWithShadow(textRenderer, "Poll interval, 2-60 seconds", centerX - FIELD_WIDTH / 2, startY + 102, 0xFFA0FFFF);
        context.drawCenteredTextWithShadow(textRenderer, LiveControlCommands.bossBarHint(), centerX, startY + 210, 0xFF00E5FF);
        context.drawCenteredTextWithShadow(textRenderer, "Chat messages Stone, Wood, and Home send Minecraft chat commands.", centerX, startY + 224, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private Text enabledText() {
        return Text.literal("YouTube integration: " + (youtubeEnabled ? "Enabled" : "Disabled"));
    }

    private void saveAndClose() {
        LiveControlConfig config = new LiveControlConfig();
        config.youtubeIntegrationEnabled = youtubeEnabled;
        config.youtubeApiKey = apiKeyField.getText();
        config.youtubeLiveChatId = liveChatIdField.getText();
        config.pollSeconds = parsePollSeconds(pollSecondsField.getText());
        LiveControlClient.saveConfig(config);
        close();
    }

    private static int parsePollSeconds(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 5;
        }
    }
}

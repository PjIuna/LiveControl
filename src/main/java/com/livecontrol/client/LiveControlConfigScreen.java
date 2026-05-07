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
    private TextFieldWidget twitchChannelField;
    private TextFieldWidget kickChannelField;
    private TextFieldWidget pollSecondsField;
    private boolean youtubeEnabled;
    private boolean twitchEnabled;
    private boolean kickEnabled;

    public LiveControlConfigScreen(Screen parent) {
        super(Text.literal("LiveControl Configuration"));
        this.parent = parent;
        LiveControlConfig config = LiveControlClient.config();
        this.youtubeEnabled = config.youtubeIntegrationEnabled;
        this.twitchEnabled = config.twitchIntegrationEnabled;
        this.kickEnabled = config.kickIntegrationEnabled;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY = 24;
        LiveControlConfig config = LiveControlClient.config();

        apiKeyField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 28, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("YouTube API Key"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setText(config.youtubeApiKey);
        addDrawableChild(apiKeyField);

        liveChatIdField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 62, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("YouTube Live Chat ID"));
        liveChatIdField.setMaxLength(256);
        liveChatIdField.setText(config.youtubeLiveChatId);
        addDrawableChild(liveChatIdField);

        twitchChannelField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 96, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("Twitch Channel"));
        twitchChannelField.setMaxLength(64);
        twitchChannelField.setText(config.twitchChannel);
        addDrawableChild(twitchChannelField);

        kickChannelField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 130, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("Kick Channel"));
        kickChannelField.setMaxLength(64);
        kickChannelField.setText(config.kickChannel);
        addDrawableChild(kickChannelField);

        pollSecondsField = new TextFieldWidget(textRenderer, centerX - FIELD_WIDTH / 2, startY + 164, FIELD_WIDTH, FIELD_HEIGHT, Text.literal("Poll seconds"));
        pollSecondsField.setMaxLength(2);
        pollSecondsField.setText(Integer.toString(config.pollSeconds));
        addDrawableChild(pollSecondsField);

        addDrawableChild(ButtonWidget.builder(youtubeEnabledText(), button -> {
            youtubeEnabled = !youtubeEnabled;
            button.setMessage(youtubeEnabledText());
        }).dimensions(centerX - FIELD_WIDTH / 2, startY + 194, 84, 20).build());

        addDrawableChild(ButtonWidget.builder(twitchEnabledText(), button -> {
            twitchEnabled = !twitchEnabled;
            button.setMessage(twitchEnabledText());
        }).dimensions(centerX - 42, startY + 194, 84, 20).build());

        addDrawableChild(ButtonWidget.builder(kickEnabledText(), button -> {
            kickEnabled = !kickEnabled;
            button.setMessage(kickEnabledText());
        }).dimensions(centerX + FIELD_WIDTH / 2 - 84, startY + 194, 84, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveAndClose())
                .dimensions(centerX - 104, startY + 220, 100, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(centerX + 4, startY + 220, 100, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int centerX = width / 2;
        int startY = 24;

        context.drawCenteredTextWithShadow(textRenderer, title, centerX, startY - 14, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "YouTube Data API key", centerX - FIELD_WIDTH / 2, startY + 16, 0xFFA0FFFF);
        context.drawTextWithShadow(textRenderer, "YouTube live chat ID", centerX - FIELD_WIDTH / 2, startY + 50, 0xFFA0FFFF);
        context.drawTextWithShadow(textRenderer, "Twitch channel", centerX - FIELD_WIDTH / 2, startY + 84, 0xFFA0FFFF);
        context.drawTextWithShadow(textRenderer, "Kick channel", centerX - FIELD_WIDTH / 2, startY + 118, 0xFFA0FFFF);
        context.drawTextWithShadow(textRenderer, "Poll interval, 2-60 seconds", centerX - FIELD_WIDTH / 2, startY + 152, 0xFFA0FFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Chat commands: " + LiveControlCommands.bossBarHint(), centerX, startY + 248, 0xFF00E5FF);
        context.drawCenteredTextWithShadow(textRenderer, "Only the live chat command is shown and sent in Minecraft chat.", centerX, startY + 262, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private Text youtubeEnabledText() {
        return Text.literal("YouTube: " + enabledLabel(youtubeEnabled));
    }

    private Text twitchEnabledText() {
        return Text.literal("Twitch: " + enabledLabel(twitchEnabled));
    }

    private Text kickEnabledText() {
        return Text.literal("Kick: " + enabledLabel(kickEnabled));
    }

    private static String enabledLabel(boolean enabled) {
        return enabled ? "On" : "Off";
    }

    private void saveAndClose() {
        LiveControlConfig config = new LiveControlConfig();
        config.youtubeIntegrationEnabled = youtubeEnabled;
        config.youtubeApiKey = apiKeyField.getText();
        config.youtubeLiveChatId = liveChatIdField.getText();
        config.twitchIntegrationEnabled = twitchEnabled;
        config.twitchChannel = twitchChannelField.getText();
        config.kickIntegrationEnabled = kickEnabled;
        config.kickChannel = kickChannelField.getText();
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

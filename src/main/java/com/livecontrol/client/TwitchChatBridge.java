package com.livecontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class TwitchChatBridge {
    private static final String IRC_HOST = "irc.chat.twitch.tv";
    private static final int IRC_PORT = 6667;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LiveControl Twitch Chat");
        thread.setDaemon(true);
        return thread;
    });

    private Future<?> chatTask;
    private Socket socket;

    public synchronized void restart() {
        stop();
        LiveControlConfig config = LiveControlClient.config();
        if (!config.isReadyForTwitch()) {
            return;
        }

        String channel = config.twitchChannel.toLowerCase(Locale.ROOT);
        chatTask = executor.submit(() -> listen(channel));
    }

    public synchronized void stop() {
        if (chatTask != null) {
            chatTask.cancel(true);
            chatTask = null;
        }
        closeSocket();
    }

    private void listen(String channel) {
        try (Socket activeSocket = new Socket(IRC_HOST, IRC_PORT)) {
            socket = activeSocket;
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));

            writer.write("PASS SCHMOOPIIE\r\n");
            writer.write("NICK justinfan" + (System.currentTimeMillis() % 100000) + "\r\n");
            writer.write("JOIN #" + channel + "\r\n");
            writer.flush();

            String line;
            while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                if (line.startsWith("PING")) {
                    writer.write("PONG :tmi.twitch.tv\r\n");
                    writer.flush();
                    continue;
                }

                String message = extractPrivMessage(line);
                if (message != null) {
                    handleChatMessage(message);
                }
            }
        } catch (IOException exception) {
            if (!Thread.currentThread().isInterrupted()) {
                LiveControlClient.LOGGER.warn("Twitch chat connection stopped", exception);
            }
        } finally {
            socket = null;
        }
    }

    private static String extractPrivMessage(String line) {
        int marker = line.indexOf(" PRIVMSG ");
        if (marker < 0) {
            return null;
        }

        int messageStart = line.indexOf(" :", marker);
        return messageStart < 0 ? null : line.substring(messageStart + 2);
    }

    private static void handleChatMessage(String message) {
        if (!LiveControlClient.areChatCommandsEnabled()) {
            return;
        }

        LiveControlCommands.fromChatMessage(message).ifPresent(command -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> sendMinecraftChatMessage(client, command));
        });
    }

    private static void sendMinecraftChatMessage(MinecraftClient client, LiveControlCommands command) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        client.getNetworkHandler().sendChatMessage(command.chatCommand());
        client.player.sendMessage(Text.literal("LiveControl ran " + command.chatCommand()), true);
    }

    private void closeSocket() {
        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException exception) {
            LiveControlClient.LOGGER.debug("Could not close Twitch chat socket", exception);
        }
        socket = null;
    }
}

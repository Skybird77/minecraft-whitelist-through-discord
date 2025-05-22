package com.github.skybird77.bot;

import com.mojang.brigadier.ParseResults;
import com.github.skybird77.DiscordFabricCommands;
import com.github.skybird77.server.CapturingCommandOutput;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DiscordBotListener extends ListenerAdapter {
    private final MinecraftServer server;

    public DiscordBotListener(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("whitelist")) {
            return;
        }

        String subcommandName = event.getSubcommandName();
        if (subcommandName == null) {
            event.reply("Invalid whitelist command structure.").setEphemeral(true).queue();
            return;
        }

        String fullMinecraftCommand;
        OptionMapping playerNameOption = event.getOption("playername");

        switch (subcommandName) {
            case "add":
                if (playerNameOption == null) {
                    event.reply("Player name is required for `add`.").setEphemeral(true).queue();
                    return;
                }
                fullMinecraftCommand = "whitelist add " + playerNameOption.getAsString();
                break;
            case "remove":
                if (playerNameOption == null) {
                    event.reply("Player name is required for `remove`.").setEphemeral(true).queue();
                    return;
                }
                fullMinecraftCommand = "whitelist remove " + playerNameOption.getAsString();
                break;
            case "list":
                fullMinecraftCommand = "whitelist list";
                break;
            case "reload":
                fullMinecraftCommand = "whitelist reload";
                break;
            default:
                event.reply("Unknown or disallowed whitelist subcommand: " + subcommandName).setEphemeral(true).queue();
                return;
        }

        DiscordFabricCommands.LOGGER.info("Processing slash command /whitelist " + subcommandName + " from Discord user " + event.getUser().getAsTag());
        event.deferReply().queue();

        executeMinecraftCommand(fullMinecraftCommand, output -> {
            if (output.isEmpty()) {
                output = "(Command executed, no output)";
            }
            if (output.length() > 1990) {
                event.getHook().sendMessage("Output too long, truncating:\n```\n" + output.substring(0, 1900) + "\n... (truncated)\n```").queue();
            } else {
                event.getHook().sendMessage("```\n" + output + "\n```").queue();
            }
        });
    }

    private void executeMinecraftCommand(String commandToExecute, java.util.function.Consumer<String> callback) {
        CompletableFuture<String> commandResultFuture = new CompletableFuture<>();
        server.execute(() -> {
            try {
                CapturingCommandOutput commandOutput = new CapturingCommandOutput();
                ServerCommandSource commandSource = new ServerCommandSource(
                        commandOutput,
                        server.getOverworld() != null ? Vec3d.ofCenter(server.getOverworld().getSpawnPos()) : Vec3d.ZERO,
                        Vec2f.ZERO,
                        server.getOverworld(),
                        4,
                        "DiscordBotExecutor",
                        Text.literal("DiscordBotExecutor"),
                        server,
                        null
                );

                ParseResults<ServerCommandSource> parseResults = server.getCommandManager().getDispatcher().parse(commandToExecute, commandSource);
                server.getCommandManager().getDispatcher().execute(parseResults);
                commandResultFuture.complete(commandOutput.getCapturedOutput());

            } catch (Exception e) {
                DiscordFabricCommands.LOGGER.error("Error executing Minecraft command: ", e);
                commandResultFuture.complete("Error executing command on server: " + e.getMessage() + "\nInput: " + commandToExecute);
            }
        });

        try {
            String result = commandResultFuture.get(15, TimeUnit.SECONDS);
            callback.accept(result);
        } catch (Exception e) {
            DiscordFabricCommands.LOGGER.error("Error or timeout getting Minecraft command result: ", e);
            callback.accept("Error processing command on server or timeout: " + e.getMessage());
        }
    }
}
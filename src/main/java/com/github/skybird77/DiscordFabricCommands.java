package com.github.skybird77;

import com.github.skybird77.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.skybird77.bot.DiscordBotListener;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class DiscordFabricCommands implements ModInitializer {
	public static final String MOD_ID = "discordfabriccommands";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static ModConfig CONFIG;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static File configFile;


	private static MinecraftServer minecraftServerInstance;
	private JDA jda;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Discord Fabric Commands Mod!");

		configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), MOD_ID + ".json");
		loadConfig();


		if (CONFIG.discordBotToken.equals("YOUR_DISCORD_BOT_TOKEN_HERE") || CONFIG.discordBotToken.trim().isEmpty()) {
			LOGGER.error("************************************************************");
			LOGGER.error("DISCORD BOT TOKEN NOT SET IN CONFIG FILE! The bot will not start.");
			LOGGER.error("Please set your discordBotToken in '" + configFile.getAbsolutePath() + "'");
			LOGGER.error("************************************************************");
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			minecraftServerInstance = server;
			new Thread(() -> {
				try {
					LOGGER.info("Minecraft server started. Attempting to start Discord bot in a new thread...");
					jda = JDABuilder.createDefault(CONFIG.discordBotToken) // Use token from config
							.enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
							.setMemberCachePolicy(MemberCachePolicy.NONE)
							.addEventListeners(new DiscordBotListener(server))
							.setActivity(Activity.playing("Minecraft Server"))
							.build();
					jda.awaitReady();
					LOGGER.info("Discord Bot connected and ready as: " + jda.getSelfUser().getAsTag());

					registerSlashCommands();

				} catch (InterruptedException e) {
					LOGGER.error("JDA startup was interrupted.", e);
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					LOGGER.error("Failed to start or connect Discord Bot.", e);
					if (jda != null) {
						jda.shutdownNow();
						jda = null;
					}
				}
			}, "DiscordBotInitializerThread").start();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Minecraft server stopping. Shutting down Discord bot...");
			if (jda != null) {
				jda.shutdown();
				try {
					if (!jda.awaitShutdown(10, java.util.concurrent.TimeUnit.SECONDS)) {
						LOGGER.warn("JDA did not shut down gracefully in 10 seconds, forcing shutdown.");
						jda.shutdownNow();
					}
				} catch (InterruptedException e) {
					LOGGER.warn("JDA shutdown interrupted, forcing shutdown.");
					jda.shutdownNow();
					Thread.currentThread().interrupt();
				}
				LOGGER.info("Discord Bot shut down.");
			}
			minecraftServerInstance = null;
		});
	}

	public static void loadConfig() {
		ModConfig defaultConfig = new ModConfig();
		if (!configFile.exists()) {
			LOGGER.info("Config file not found, creating default config at " + configFile.getAbsolutePath());
			CONFIG = defaultConfig;
			saveConfig();
		} else {
			try (FileReader reader = new FileReader(configFile)) {
				CONFIG = GSON.fromJson(reader, ModConfig.class);
				if (CONFIG == null) {
					throw new IOException("Config file was empty or invalid.");
				}

				boolean changed = false;
				if (CONFIG.discordBotToken == null || CONFIG.discordBotToken.trim().isEmpty() || CONFIG.discordBotToken.equals("YOUR_DISCORD_BOT_TOKEN_HERE_FROM_OLD_VERSION")) {
					CONFIG.discordBotToken = defaultConfig.discordBotToken; // Reset to current POJO default
					changed = true;
				}

				if (changed) {
					saveConfig(); // Save if we had to apply defaults
				}

			} catch (IOException | com.google.gson.JsonSyntaxException e) {
				LOGGER.error("Failed to load config file at " + configFile.getAbsolutePath() + ". Using default values and attempting to save a fresh one.", e);
				CONFIG = defaultConfig;
				saveConfig();
			}
		}
	}

	public static void saveConfig() {
		try (FileWriter writer = new FileWriter(configFile)) {
			GSON.toJson(CONFIG, writer);
			LOGGER.info("Saved config to " + configFile.getAbsolutePath());
		} catch (IOException e) {
			LOGGER.error("Failed to save config file at " + configFile.getAbsolutePath(), e);
		}
	}


	private void registerSlashCommands() {
		if (jda == null) {
			LOGGER.error("JDA instance is null, cannot register slash commands.");
			return;
		}
		LOGGER.info("Registering GLOBAL slash commands with Discord (this may take up to an hour to propagate)...");

		jda.updateCommands().addCommands(
				Commands.slash("whitelist", "Manage the server whitelist.")
						.addSubcommands(
								new SubcommandData("add", "Add a player to the whitelist.")
										.addOption(OptionType.STRING, "playername", "The Minecraft username of the player to add.", true),
								new SubcommandData("remove", "Remove a player from the whitelist.")
										.addOption(OptionType.STRING, "playername", "The Minecraft username of the player to remove.", true),
								new SubcommandData("list", "List all players on the whitelist."),
								new SubcommandData("reload", "Reload the whitelist from disk.")
						)
		).queue(
				success -> LOGGER.info("Successfully registered GLOBAL slash commands!"),
				error -> LOGGER.error("Failed to register GLOBAL slash commands: " + error.getMessage(), error)
		);
	}
}

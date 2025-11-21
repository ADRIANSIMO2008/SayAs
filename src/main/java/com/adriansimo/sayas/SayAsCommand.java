package com.adriansimo.sayas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class SayAsCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommand(dispatcher)
        );
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("sayas")
                        .requires(src -> src.hasPermission(2)) // LP si to vyrieši svojim pluginom
                        .then(Commands.argument("playerName", StringArgumentType.string())
                                // autocomplete iba pre online hráčov
                                .suggests((ctx, builder) -> {
                                    for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                        builder.suggest(player.getName().getString());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("message", MessageArgument.message())
                                        .executes(ctx -> {

                                            String playerName = StringArgumentType.getString(ctx, "playerName");
                                            String msg = MessageArgument.getMessage(ctx, "message").getString();

                                            // fake message in chat
                                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                                    Component.literal("<" + playerName + "> " + msg),
                                                    false
                                            );

                                            // logovanie
                                            logSayAs(
                                                    ctx.getSource().getTextName(),
                                                    playerName,
                                                    msg
                                            );

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Message sent as " + playerName),
                                                    false
                                            );

                                            return 1;
                                        })
                                )
                        )
        );
    }

    private static void logSayAs(String executor, String target, String message) {
        try {
            File folder = new File("sayas/log");
            if (!folder.exists()) folder.mkdirs();

            File logFile = new File(folder, "sayas.log");
            if (!logFile.exists()) logFile.createNewFile();

            FileWriter fw = new FileWriter(logFile, true);
            fw.write("[" + LocalDateTime.now() + "] " + executor + " -> " + target + ": " + message + "\n");
            fw.close();

        } catch (IOException e) {
            System.out.println("[SayAs] Log error:");
            e.printStackTrace();
        }
    }
}

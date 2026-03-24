package com.adriansimo.sayas;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SayAsCommand {
    private static final Map<UUID, Boolean> MIMIC_BY_USER = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ALIAS_BY_USER = new ConcurrentHashMap<>();
    private static final File ALIAS_FILE = new File("sayas/aliases.properties");
    private static volatile boolean chatHookRegistered = false;

    public static void register() {
        loadAliases();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommand(dispatcher)
        );
        registerChatHook();
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sayas")
                        .then(Commands.literal("mimic")
                                .requires(src -> hasPermission(src, "sayas.mimic") || hasPermission(src, "sayas.use"))
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("on");
                                            builder.suggest("off");
                                            builder.suggest("status");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                            if (executor == null) {
                                                ctx.getSource().sendFailure(Component.literal("This command can only be used by a player."));
                                                return 0;
                                            }

                                            String state = StringArgumentType.getString(ctx, "state").toLowerCase(Locale.ROOT);
                                            UUID executorId = executor.getUUID();
                                            if ("status".equals(state)) {
                                                boolean enabled = MIMIC_BY_USER.getOrDefault(executorId, false);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("Your mimic mode is " + (enabled ? "ON" : "OFF")),
                                                        false
                                                );
                                                return 1;
                                            }

                                            if ("on".equals(state)) {
                                                MIMIC_BY_USER.put(executorId, true);
                                                ctx.getSource().sendSuccess(() -> Component.literal("Your mimic mode is now enabled."), false);
                                                return 1;
                                            }

                                            if ("off".equals(state)) {
                                                MIMIC_BY_USER.put(executorId, false);
                                                ctx.getSource().sendSuccess(() -> Component.literal("Your mimic mode is now disabled."), false);
                                                return 1;
                                            }

                                            ctx.getSource().sendFailure(Component.literal("Usage: /sayas mimic <on|off|status>"));
                                            return 0;
                                        })
                                )
                        )
                        .then(Commands.literal("name")
                                .requires(src -> hasPermission(src, "sayas.use"))
                                .then(Commands.literal("show")
                                        .executes(ctx -> {
                                            ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                            if (executor == null) {
                                                ctx.getSource().sendFailure(Component.literal("This command can only be used by a player."));
                                                return 0;
                                            }

                                            String alias = ALIAS_BY_USER.get(executor.getUUID());
                                            if (alias == null || alias.isBlank()) {
                                                ctx.getSource().sendSuccess(() -> Component.literal("You currently use your real name."), false);
                                            } else {
                                                ctx.getSource().sendSuccess(() -> Component.literal("Your fake name is: " + alias), false);
                                            }

                                            return 1;
                                        })
                                )
                                .then(Commands.literal("clear")
                                        .executes(ctx -> {
                                            ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                            if (executor == null) {
                                                ctx.getSource().sendFailure(Component.literal("This command can only be used by a player."));
                                                return 0;
                                            }

                                            ALIAS_BY_USER.remove(executor.getUUID());
                                            saveAliases();
                                            ctx.getSource().sendSuccess(() -> Component.literal("Your fake name has been cleared."), false);
                                            return 1;
                                        })
                                )
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer executor = getExecutorPlayer(ctx.getSource());
                                            if (executor == null) {
                                                ctx.getSource().sendFailure(Component.literal("This command can only be used by a player."));
                                                return 0;
                                            }

                                            String alias = StringArgumentType.getString(ctx, "name").trim();
                                            if (alias.isEmpty() || alias.length() > 32) {
                                                ctx.getSource().sendFailure(Component.literal("Name must be between 1 and 32 characters."));
                                                return 0;
                                            }

                                            ALIAS_BY_USER.put(executor.getUUID(), alias);
                                            saveAliases();
                                            ctx.getSource().sendSuccess(() -> Component.literal("Your fake name is now: " + alias), false);
                                            return 1;
                                        })
                                )
                        )
                        // /sayas as <name> <message>
                        .then(Commands.literal("as")
                                .requires(src -> hasPermission(src, "sayas.use"))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                builder.suggest(player.getName().getString());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("message", MessageArgument.message())
                                                .executes(ctx -> {
                                                    String oneTimeName = StringArgumentType.getString(ctx, "name").trim();
                                                    if (oneTimeName.isEmpty() || oneTimeName.length() > 32) {
                                                        ctx.getSource().sendFailure(Component.literal("Name must be between 1 and 32 characters."));
                                                        return 0;
                                                    }

                                                    String msg = MessageArgument.getMessage(ctx, "message").getString();
                                                    sendAsConfiguredIdentity(ctx.getSource(), ctx.getSource().getTextName(), oneTimeName, msg, true);

                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("One-time message sent as " + oneTimeName),
                                                            false
                                                    );
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        // /sayas <message>
                        .then(Commands.argument("message", MessageArgument.message())
                                .requires(src -> hasPermission(src, "sayas.use"))
                                .executes(ctx -> {
                                    String msg = MessageArgument.getMessage(ctx, "message").getString();
                                    String displayName = getConfiguredDisplayName(ctx.getSource());
                                    boolean mimicUsed = sendAsConfiguredIdentity(ctx.getSource(), ctx.getSource().getTextName(), displayName, msg, true);
                                    final boolean mimicUsedFinal = mimicUsed;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Message sent as " + displayName + (mimicUsedFinal ? " (mimic)" : "")),
                                            false
                                    );

                                    return 1;
                                })
                        )
                        // /sayas json <player> <json>
                        .then(Commands.literal("json")
                                .requires(src -> hasPermission(src, "sayas.json") || hasPermission(src, "sayas.use"))
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                builder.suggest(player.getName().getString());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String target = StringArgumentType.getString(ctx, "player");
                                                    String rawJson = StringArgumentType.getString(ctx, "json");

                                                    // basic protection
                                                    if (rawJson.length() > 1024) {
                                                        ctx.getSource().sendFailure(Component.literal("JSON too long"));
                                                        return 0;
                                                    }

                                                    // escape player name
                                                    String tellrawJson =
                                                            "{\"text\":\"<" + target + "> \"," +
                                                                    "\"extra\":[" + rawJson + "]}";

                                                    // execute tellraw as server
                                                    ctx.getSource().getServer().getCommands().performPrefixedCommand(
                                                            ctx.getSource().getServer().createCommandSourceStack(),
                                                            "tellraw @a " + tellrawJson
                                                    );

                                                    logSayAs(
                                                            ctx.getSource().getTextName(),
                                                            target,
                                                            null,
                                                            rawJson
                                                    );

                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("JSON message sent"),
                                                            false
                                                    );

                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }

    private static void registerChatHook() {
        if (chatHookRegistered) {
            return;
        }
        chatHookRegistered = true;

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!MIMIC_BY_USER.getOrDefault(sender.getUUID(), false)) {
                return true;
            }

            String msg = extractChatMessageContent(message);
            if (msg == null || msg.isBlank()) {
                return true;
            }

            String displayName = getConfiguredDisplayName(sender.createCommandSourceStack());
            sendAsConfiguredIdentity(sender.createCommandSourceStack(), sender.getName().getString(), displayName, msg, false);

            // Suppress normal chat line; mimic/fallback already handled above.
            return false;
        });
    }

    private static boolean sendAsConfiguredIdentity(CommandSourceStack source, String executorName, String displayName, String message, boolean notifyFailures) {
        ServerPlayer executor = getExecutorPlayer(source);
        boolean mimicEnabled = executor != null && MIMIC_BY_USER.getOrDefault(executor.getUUID(), false);

        ServerPlayer mimicTarget = null;
        boolean sentByMimic = false;
        if (mimicEnabled) {
            mimicTarget = source.getServer().getPlayerList().getPlayerByName(displayName);
            sentByMimic = mimicTarget != null && trySendMimicMessage(mimicTarget, message);
        }

        if (!sentByMimic) {
            sendLegacyFormat(source, displayName, mimicTarget, message);
            if (notifyFailures && mimicEnabled) {
                if (mimicTarget == null) {
                    source.sendFailure(Component.literal("Mimic target is not online with your configured name. Sent with fallback format."));
                } else {
                    source.sendFailure(Component.literal("Mimic mode failed on this server build. Sent with fallback format."));
                }
            }
        }

        logSayAs(
                executorName,
                displayName,
                (sentByMimic ? "[MIMIC] " : "") + message,
                null
        );

        return sentByMimic;
    }

    private static String extractChatMessageContent(Object message) {
        String[] methodNames = new String[]{"signedContent", "content", "getContent", "plain"};
        for (String methodName : methodNames) {
            try {
                Object value = message.getClass().getMethod(methodName).invoke(message);
                if (value instanceof String text && !text.isBlank()) {
                    return text;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try other method names for mapping/runtime compatibility.
            }
        }

        return null;
    }

    private static ServerPlayer getExecutorPlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private static void loadAliases() {
        ALIAS_BY_USER.clear();
        if (!ALIAS_FILE.exists()) {
            return;
        }

        Properties properties = new Properties();
        try (FileReader reader = new FileReader(ALIAS_FILE)) {
            properties.load(reader);
            for (String key : properties.stringPropertyNames()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String alias = properties.getProperty(key);
                    if (alias != null && !alias.isBlank() && alias.length() <= 32) {
                        ALIAS_BY_USER.put(uuid, alias);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed UUID entries.
                }
            }
        } catch (IOException e) {
            System.out.println("[SayAs] Failed to load aliases: " + e.getMessage());
        }
    }

    private static void saveAliases() {
        try {
            File folder = ALIAS_FILE.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }

            Properties properties = new Properties();
            for (Map.Entry<UUID, String> entry : ALIAS_BY_USER.entrySet()) {
                String alias = entry.getValue();
                if (alias != null && !alias.isBlank()) {
                    properties.setProperty(entry.getKey().toString(), alias);
                }
            }

            try (FileWriter writer = new FileWriter(ALIAS_FILE)) {
                properties.store(writer, "SayAs aliases");
            }
        } catch (IOException e) {
            System.out.println("[SayAs] Failed to save aliases: " + e.getMessage());
        }
    }

    private static String getConfiguredDisplayName(CommandSourceStack source) {
        ServerPlayer player = getExecutorPlayer(source);
        if (player == null) {
            return source.getTextName();
        }

        String alias = ALIAS_BY_USER.get(player.getUUID());
        if (alias == null || alias.isBlank()) {
            return player.getName().getString();
        }

        return alias;
    }

    private static void sendLegacyFormat(CommandSourceStack source, String playerName, ServerPlayer targetPlayer, String message) {
        Component nameComponent = targetPlayer != null ? targetPlayer.getDisplayName() : Component.literal(playerName);
        Component chatLine = Component.literal("<").append(nameComponent).append(Component.literal("> " + message));
        source.getServer().getPlayerList().broadcastSystemMessage(chatLine, false);
    }

    private static boolean trySendMimicMessage(ServerPlayer targetPlayer, String message) {
        return invokeStringMessageMethod(targetPlayer, "sendChatSelection", message)
                || invokeStringMessageMethod(targetPlayer, "chat", message);
    }

    private static boolean invokeStringMessageMethod(ServerPlayer targetPlayer, String methodName, String message) {
        for (Method method : targetPlayer.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }

            Object[] args = buildMessageArgs(method.getParameterTypes(), message);
            if (args == null) {
                continue;
            }

            try {
                method.invoke(targetPlayer, args);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try another overload on this server version/mapping.
            }
        }

        return false;
    }

    private static Object[] buildMessageArgs(Class<?>[] parameterTypes, String message) {
        Object[] args = new Object[parameterTypes.length];
        boolean assignedMessage = false;

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];

            if (!assignedMessage && parameterType == String.class) {
                args[i] = message;
                assignedMessage = true;
                continue;
            }

            Object defaultValue = getDefaultValue(parameterType);
            if (defaultValue == null && parameterType.isPrimitive()) {
                return null;
            }

            args[i] = defaultValue;
        }

        return assignedMessage ? args : null;
    }

    private static Object getDefaultValue(Class<?> parameterType) {
        if (parameterType == boolean.class || parameterType == Boolean.class) {
            return false;
        }
        if (parameterType == int.class || parameterType == Integer.class) {
            return 0;
        }
        if (parameterType == long.class || parameterType == Long.class) {
            return 0L;
        }
        if (parameterType == float.class || parameterType == Float.class) {
            return 0.0f;
        }
        if (parameterType == double.class || parameterType == Double.class) {
            return 0.0d;
        }
        if (parameterType == byte.class || parameterType == Byte.class) {
            return (byte) 0;
        }
        if (parameterType == short.class || parameterType == Short.class) {
            return (short) 0;
        }
        if (parameterType == char.class || parameterType == Character.class) {
            return '\0';
        }

        return null;
    }

    private static boolean hasPermission(CommandSourceStack source, String node) {
        // Console and server contexts keep full access.
        if (source.getEntity() == null) {
            return true;
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            if (hasLuckPermsPermission(player, node)) {
                return true;
            }

            // Fallback when LuckPerms is missing or user data is unavailable.
            return source.getServer().getPlayerList().isOp(new NameAndId(player.getUUID(), player.getName().getString()));
        }

        return false;
    }

    private static boolean hasLuckPermsPermission(ServerPlayer player, String node) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUUID());

            if (user == null) {
                return false;
            }

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, node);
            Object asBoolean = result.getClass().getMethod("asBoolean").invoke(result);
            return asBoolean instanceof Boolean && (Boolean) asBoolean;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static void logSayAs(String executor, String target, String message, String rawJson) {
        try {
            File folder = new File("sayas/log");
            if (!folder.exists()) folder.mkdirs();

            File logFile = new File(folder, "sayas.log");
            if (!logFile.exists()) logFile.createNewFile();

            FileWriter fw = new FileWriter(logFile, true);
            if (rawJson == null) fw.write("[" + LocalDateTime.now() + "] " + executor + " -> " + target + ": " + message + "\n");
            else fw.write("JSON " + "[" + LocalDateTime.now() + "] " + executor + " -> " + target + ": " + rawJson + "\n");
            fw.close();

        } catch (IOException e) {
            System.out.println("[SayAs] Log error:");
            e.printStackTrace();
        }
    }
}

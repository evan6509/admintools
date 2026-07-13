package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.UUID;

public class AdminRoleCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("adminrole")
                .requires(src -> src.hasPermissionLevel(3));

        root.then(CommandManager.literal("grant")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                .executes(ctx -> {
                                    var target = EntityArgumentType.getPlayer(ctx, "player");
                                    String perm = StringArgumentType.getString(ctx, "permission");
                                    AdminToolsMod.getPermissionManager().grant(target.getUuid(), perm);
                                    ctx.getSource().sendFeedback(() -> Text.literal("§aGranted permission '" + perm + "' to " + target.getName().getString()), true);
                                    AdminToolsMod.getActionLogger().log(ctx.getSource().getName(), "ROLE_GRANT", target.getName().getString(), perm);
                                    return 1;
                                }))));

        root.then(CommandManager.literal("remove")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                .executes(ctx -> {
                                    var target = EntityArgumentType.getPlayer(ctx, "player");
                                    String perm = StringArgumentType.getString(ctx, "permission");
                                    AdminToolsMod.getPermissionManager().remove(target.getUuid(), perm);
                                    ctx.getSource().sendFeedback(() -> Text.literal("§eRemoved permission '" + perm + "' from " + target.getName().getString()), true);
                                    AdminToolsMod.getActionLogger().log(ctx.getSource().getName(), "ROLE_REMOVE", target.getName().getString(), perm);
                                    return 1;
                                }))));

        root.then(CommandManager.literal("assign")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("role", StringArgumentType.word())
                                .executes(ctx -> {
                                    var target = EntityArgumentType.getPlayer(ctx, "player");
                                    String role = StringArgumentType.getString(ctx, "role");
                                    AdminToolsMod.getPermissionManager().assignRole(target.getUuid(), role);
                                    ctx.getSource().sendFeedback(() -> Text.literal("§aAssigned role '" + role + "' to " + target.getName().getString()), true);
                                    AdminToolsMod.getActionLogger().log(ctx.getSource().getName(), "ROLE_ASSIGN", target.getName().getString(), role);
                                    return 1;
                                }))));

        dispatcher.register(root);
    }
}

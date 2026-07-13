package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.UUID;

public class AdminRoleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("adminrole")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN));

        root.then(Commands.literal("grant")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(ctx -> {
                                    var target = EntityArgument.getPlayer(ctx, "player");
                                    String perm = StringArgumentType.getString(ctx, "permission");
                                    AdminToolsMod.getPermissionManager().grant(target.getUUID(), perm);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aGranted permission '" + perm + "' to " + target.getScoreboardName()), true);
                                    AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ROLE_GRANT", target.getScoreboardName(), perm);
                                    return 1;
                                }))));

        root.then(Commands.literal("remove")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(ctx -> {
                                    var target = EntityArgument.getPlayer(ctx, "player");
                                    String perm = StringArgumentType.getString(ctx, "permission");
                                    AdminToolsMod.getPermissionManager().remove(target.getUUID(), perm);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§eRemoved permission '" + perm + "' from " + target.getScoreboardName()), true);
                                    AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ROLE_REMOVE", target.getScoreboardName(), perm);
                                    return 1;
                                }))));

        root.then(Commands.literal("assign")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("role", StringArgumentType.word())
                                .executes(ctx -> {
                                    var target = EntityArgument.getPlayer(ctx, "player");
                                    String role = StringArgumentType.getString(ctx, "role");
                                    AdminToolsMod.getPermissionManager().assignRole(target.getUUID(), role);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aAssigned role '" + role + "' to " + target.getScoreboardName()), true);
                                    AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ROLE_ASSIGN", target.getScoreboardName(), role);
                                    return 1;
                                }))));

        dispatcher.register(root);
    }
}

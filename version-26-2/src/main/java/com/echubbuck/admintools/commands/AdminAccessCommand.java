package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.PermissionNodes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;
import java.util.Set;

/** Manages AdminTools command access directly per player UUID. */
public final class AdminAccessCommand {
    private AdminAccessCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var permission = Commands.argument("permission", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(PermissionNodes.VALUES, builder));

        dispatcher.register(Commands.literal("adminaccess")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(permission.executes(ctx -> change(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "player"),
                                        StringArgumentType.getString(ctx, "permission"), true)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("permission", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(PermissionNodes.VALUES, builder))
                                        .executes(ctx -> change(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "player"),
                                                StringArgumentType.getString(ctx, "permission"), false)))))
                .then(Commands.literal("list")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> list(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "player"))))));
    }

    private static int change(CommandSourceStack source, Collection<NameAndId> players,
                              String permission, boolean grant) throws CommandSyntaxException {
        int changed = 0;
        for (NameAndId player : players) {
            boolean didChange = grant
                    ? AdminToolsMod.getPermissionManager().grant(player.id(), permission)
                    : AdminToolsMod.getPermissionManager().remove(player.id(), permission);
            if (didChange) changed++;
            String verb = grant ? "Granted" : "Removed";
            source.sendSuccess(() -> Component.literal((didChange ? "§a" : "§7") + verb
                    + " '" + permission + "' " + (grant ? "to " : "from ") + player.name()
                    + (didChange ? "" : " (no change)")), true);
            if (didChange) {
                AdminToolsMod.getActionLogger().log(source.getTextName(),
                        grant ? "ACCESS_GRANT" : "ACCESS_REMOVE", player.name(), permission);
            }
        }
        return changed;
    }

    private static int list(CommandSourceStack source, Collection<NameAndId> players) {
        for (NameAndId player : players) {
            Set<String> permissions = AdminToolsMod.getPermissionManager().permissionsFor(player.id());
            source.sendSuccess(() -> Component.literal("§eAdminTools access for §f" + player.name()
                    + "§e: §f" + (permissions.isEmpty() ? "none" : String.join(", ", permissions))), false);
        }
        return players.size();
    }
}

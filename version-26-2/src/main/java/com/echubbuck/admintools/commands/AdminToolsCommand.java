package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/** Root maintenance command for live AdminTools configuration. */
public final class AdminToolsCommand {
    private AdminToolsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("admintools")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            boolean loaded = AdminToolsMod.reloadConfiguration();
                            if (!loaded) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "One or more AdminTools files failed validation; invalid files kept their previous settings. Check the server log."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§aReloaded AdminTools configuration and per-player access."), true);
                            AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(),
                                    "RELOAD", "admintools", "success");
                            return 1;
                        })));
    }
}

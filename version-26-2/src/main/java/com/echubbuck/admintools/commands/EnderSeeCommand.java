package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.gui.EnderSeeScreenHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.SimpleMenuProvider;

public class EnderSeeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("endersee")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer admin = source.getPlayer();
        if (admin == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        if (!AdminToolsMod.getConfigManager().getBoolean("enable_ender_chest_viewer", true)) {
            source.sendFailure(Component.literal("Ender chest viewer is disabled."));
            return 0;
        }

        admin.openMenu(new SimpleMenuProvider(
                (syncId, inv, player) -> new EnderSeeScreenHandler(syncId, inv, target),
                Component.literal("Ender Chest: " + target.getScoreboardName())));
        AdminToolsMod.getActionLogger().log(source.getTextName(), "ENDERSEE", target.getScoreboardName(), "opened");
        return 1;
    }
}
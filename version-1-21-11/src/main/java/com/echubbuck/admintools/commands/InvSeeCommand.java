package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.gui.InvSeeScreenHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.SimpleMenuProvider;

public class InvSeeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("invsee")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))
                        .then(Commands.literal("edit")
                                .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true)))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer target, boolean editable) {
        ServerPlayer admin = source.getPlayer();
        if (admin == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        if (admin == target) {
            source.sendFailure(Component.literal("You cannot invsee yourself."));
            return 0;
        }

        if (!AdminToolsMod.getConfigManager().getBoolean("enable_inventory_viewer", true)) {
            source.sendFailure(Component.literal("Inventory viewer is disabled."));
            return 0;
        }

        String suffix = editable ? " (editing)" : "";
        admin.openMenu(new SimpleMenuProvider(
                (syncId, inv, player) -> new InvSeeScreenHandler(syncId, inv, target, editable),
                Component.literal("Inventory: " + target.getScoreboardName() + suffix)));
        AdminToolsMod.getActionLogger().log(source.getTextName(), editable ? "INVSEE_EDIT" : "INVSEE", target.getScoreboardName(), "opened");
        return 1;
    }
}
package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.gui.EnderSeeScreenHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class EnderSeeCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("endersee")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))));
    }

    private static int execute(ServerCommandSource source, ServerPlayerEntity target) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        if (!AdminToolsMod.getConfigManager().getBoolean("enable_ender_chest_viewer", true)) {
            source.sendError(Text.literal("Ender chest viewer is disabled."));
            return 0;
        }

        admin.openHandledScreen(new EnderSeeScreenHandler.Provider(target));
        AdminToolsMod.getActionLogger().log(source.getName(), "ENDERSEE", target.getName().getString(), "opened");
        return 1;
    }
}

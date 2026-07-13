package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class XrayAuditCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xrayaudit")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer target) {
        String dim = target.level().dimension().identifier().toString();
        double risk = AdminToolsMod.getHeuristicEngine().calculateRisk(target.getUUID(), dim);
        String label = AdminToolsMod.getHeuristicEngine().getRiskLabel(risk);
        var data = AdminToolsMod.getHeuristicEngine().getData(target.getUUID());

        source.sendSuccess(() -> Component.literal("§6--- X-Ray Audit: " + target.getScoreboardName() + " ---"), false);
        source.sendSuccess(() -> Component.literal("§eRisk Score: §f" + String.format("%.1f", risk) + "% §7(" + label + ")"), false);

        if (data != null) {
            source.sendSuccess(() -> Component.literal("§eMining Speed: §f" + String.format("%.1f", data.miningSpeed()) + " blocks/min"), false);
            source.sendSuccess(() -> Component.literal("§eTorch Ratio: §f" + String.format("%.2f", data.torchRatio())), false);
            source.sendSuccess(() -> Component.literal("§eOre Exposure Rate: §f" + String.format("%.2f", data.oreExposureRate())), false);
            source.sendSuccess(() -> Component.literal("§eChunk Updates: §f" + data.chunkUpdates()), false);
        }

        AdminToolsMod.getActionLogger().log(source.getTextName(), "XRAY_AUDIT", target.getScoreboardName(), label + " (" + risk + "%)");
        return 1;
    }
}

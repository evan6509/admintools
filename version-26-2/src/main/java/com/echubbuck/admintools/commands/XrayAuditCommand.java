package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class XrayAuditCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("xrayaudit")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))));
    }

    private static int execute(ServerCommandSource source, ServerPlayerEntity target) {
        String dim = target.getServerWorld().getRegistryKey().getValue().toString();
        double risk = AdminToolsMod.getHeuristicEngine().calculateRisk(target.getUuid(), dim);
        String label = AdminToolsMod.getHeuristicEngine().getRiskLabel(risk);
        var data = AdminToolsMod.getHeuristicEngine().getData(target.getUuid());

        source.sendFeedback(() -> Text.literal("§6--- X-Ray Audit: " + target.getName().getString() + " ---"), false);
        source.sendFeedback(() -> Text.literal("§eRisk Score: §f" + String.format("%.1f", risk) + "% §7(" + label + ")"), false);

        if (data != null) {
            source.sendFeedback(() -> Text.literal("§eMining Speed: §f" + String.format("%.1f", data.miningSpeed()) + " blocks/min"), false);
            source.sendFeedback(() -> Text.literal("§eTorch Ratio: §f" + String.format("%.2f", data.torchRatio())), false);
            source.sendFeedback(() -> Text.literal("§eOre Exposure Rate: §f" + String.format("%.2f", data.oreExposureRate())), false);
            source.sendFeedback(() -> Text.literal("§eChunk Updates: §f" + data.chunkUpdates()), false);
        }

        AdminToolsMod.getActionLogger().log(source.getName(), "XRAY_AUDIT", target.getName().getString(), label + " (" + risk + "%)");
        return 1;
    }
}

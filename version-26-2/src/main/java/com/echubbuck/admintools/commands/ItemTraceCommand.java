package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.PermissionNodes;
import com.echubbuck.admintools.common.ItemLedgerEntry;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ItemTraceCommand {
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("itemtrace")
                .requires(src -> AdminToolsMod.hasAccess(src, PermissionNodes.ITEMTRACE))
                .then(Commands.argument("uid", StringArgumentType.word())
                        .executes(ctx -> execute(ctx.getSource(), StringArgumentType.getString(ctx, "uid")))));
    }

    private static int execute(CommandSourceStack source, String uidArg) {
        UUID uid;
        try {
            uid = UUID.fromString(uidArg);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid UUID: " + uidArg));
            return 0;
        }
        String uidStr = uid.toString();
        ItemLedgerEntry entry = AdminToolsMod.getItemLedger().entry(uidStr);
        if (entry == null) {
            source.sendFailure(Component.literal("No identity found for " + uidStr));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6--- Item Trace: " + uidStr + " ---"), false);
        source.sendSuccess(() -> Component.literal("§eItem: §f" + entry.itemId + " §7(" + entry.count + ")"), false);
        source.sendSuccess(() -> Component.literal("§eCreated: §f" + FMT.format(new Date(entry.creationTime))), false);
        source.sendSuccess(() -> Component.literal("§eSource: §f" + entry.creationSource + " §7by " + (entry.creatorName != null ? entry.creatorName : entry.creatorUuid)), false);
        source.sendSuccess(() -> Component.literal("§eOwner: §f" + entry.currentOwner + " §7@" + (entry.currentLocation != null ? entry.currentLocation : "?")), false);
        source.sendSuccess(() -> Component.literal("§eStatus: §f" + entry.status), false);
        if (entry.parents != null && !entry.parents.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eLineage parents: §f" + String.join(", ", entry.parents)), false);
        }

        List<ItemMovementEvent> events = AdminToolsMod.getItemLedger().eventsByUid(uidStr);
        source.sendSuccess(() -> Component.literal("§eRecent movements: §7" + events.size()), false);
        for (ItemMovementEvent e : events) {
            source.sendSuccess(() -> Component.literal("  §7" + FMT.format(new Date(e.timestamp())) + " §f" + e.action()
                    + " §7x" + e.count() + " " + (e.from() != null ? e.from() : "-") + " -> " + (e.to() != null ? e.to() : "-")), false);
        }

        int dup = 0;
        for (ItemMovementEvent d : AdminToolsMod.getItemLedger().duplicateAlerts()) {
            if (uidStr.equals(d.uid())) dup++;
        }
        final int dupFinal = dup;
        source.sendSuccess(() -> Component.literal("§eDuplicate alerts: §f" + dupFinal), false);
        return 1;
    }
}

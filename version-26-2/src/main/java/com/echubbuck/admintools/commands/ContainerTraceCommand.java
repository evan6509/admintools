package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.container.ContainerAuditEvent;
import com.echubbuck.admintools.container.ContainerAuditTracker;
import com.echubbuck.admintools.container.ContainerKey;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Displays the recent player sessions recorded for a world container. */
public final class ContainerTraceCommand {
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private ContainerTraceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("containertrace")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> execute(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                IntegerArgumentType.getInteger(ctx, "z")))))));
    }

    private static int execute(CommandSourceStack source, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockEntity blockEntity = source.getLevel().getBlockEntity(pos);
        ContainerAuditTracker tracker = AdminToolsMod.getContainerAuditTracker();
        ContainerKey key = blockEntity == null ? null : tracker.keyFor(blockEntity);
        if (key == null) {
            source.sendFailure(Component.literal("No supported container found at " + x + " " + y + " " + z + "."));
            return 0;
        }

        List<ContainerAuditEvent> events = tracker.recentFor(key, 20);
        source.sendSuccess(() -> Component.literal("§6--- Container Trace ---"), false);
        source.sendSuccess(() -> Component.literal("§eType: §f" + key.type()), false);
        source.sendSuccess(() -> Component.literal("§eLocation: §f" + key.dimension()
                + " " + key.x() + " " + key.y() + " " + key.z()), false);
        source.sendSuccess(() -> Component.literal("§eSessions: §f" + events.size()), false);

        for (ContainerAuditEvent event : events) {
            source.sendSuccess(() -> Component.literal("§7[" + FMT.format(new Date(event.openedAt())) + "] §f"
                    + event.player() + " §7opened for " + formatDuration(event.openedAt(), event.closedAt())), false);
            source.sendSuccess(() -> Component.literal("  §aAdded: §f" + formatItems(event.added())), false);
            source.sendSuccess(() -> Component.literal("  §cRemoved: §f" + formatItems(event.removed())), false);
        }
        return 1;
    }

    private static String formatDuration(long openedAt, long closedAt) {
        long seconds = Math.max(0, closedAt - openedAt) / 1000;
        return seconds + "s";
    }

    private static String formatItems(Map<String, Integer> items) {
        if (items == null || items.isEmpty()) return "none";
        return items.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey())
                .collect(Collectors.joining(", "));
    }
}

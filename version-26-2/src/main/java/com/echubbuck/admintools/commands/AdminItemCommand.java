package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

public class AdminItemCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        var give = Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> give(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));

        var remove = Commands.literal("remove")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> remove(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> remove(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));

        dispatcher.register(Commands.literal("adminitem")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(give)
                .then(remove));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ItemInput input = ItemArgument.getItem(ctx, "item");
        ItemStack stack;
        try {
            stack = input.createItemStack(count);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Could not create item: " + e.getMessage()));
            return 0;
        }

        var inventory = target.getInventory();
        int[] beforeCounts = new int[inventory.getContainerSize()];
        for (int i = 0; i < beforeCounts.length; i++) {
            ItemStack existing = inventory.getItem(i);
            if (!existing.isEmpty() && existing.getItem() == input.item().value()) {
                beforeCounts[i] = existing.getCount();
            }
        }

        String itemId = AdminToolsMod.getItemIdentityManager().itemId(stack);
        String itemName = stack.getItemName().getString();
        boolean added = target.addItem(stack);
        int delivered = 0;
        for (int i = 0; i < beforeCounts.length; i++) {
            ItemStack output = inventory.getItem(i);
            if (output.isEmpty() || output.getItem() != input.item().value()) continue;
            int delta = output.getCount() - beforeCounts[i];
            if (delta > 0) {
                AdminToolsMod.getItemEventSink().onDeliveredGive(
                        target, output, delta, ctx.getSource().getTextName());
                delivered += delta;
            }
        }

        if (delivered < count) {
            int undelivered = count - delivered;
            if (stack.isEmpty()) {
                stack = new ItemStack(input.item().value(), undelivered);
            }
            AdminToolsMod.getItemEventSink().onUndeliveredGive(
                    target, stack, undelivered, ctx.getSource().getTextName());
        }

        if (!added) {
            ctx.getSource().sendFailure(Component.literal(target.getScoreboardName() + "'s inventory is full."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aGave " + count + "x " + itemName
                + " to " + target.getScoreboardName()), true);
        AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ADMIN_ITEM_GIVE", target.getScoreboardName(),
                count + "x " + itemId);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ItemInput input = ItemArgument.getItem(ctx, "item");
        var item = input.item().value();
        int remaining = count;
        int removed = 0;

        var inv = target.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() != item) continue;
            int take = Math.min(remaining, s.getCount());
            var uid = AdminToolsMod.getItemIdentityManager().getIdentity(s);
            String itemId = AdminToolsMod.getItemIdentityManager().itemId(s);
            inv.removeItem(i, take);
            if (uid != null) {
                ItemStack remainingStack = inv.getItem(i);
                if (remainingStack.isEmpty()) {
                    AdminToolsMod.getItemLedger().setStatus(uid.toString(), "REMOVED");
                    AdminToolsMod.getItemLedger().setOwnerLocation(uid.toString(), target.getScoreboardName(), "removed");
                } else {
                    AdminToolsMod.getItemLedger().updateCount(uid.toString(), remainingStack.getCount());
                }
                AdminToolsMod.getItemLedger().recordEvent(ItemMovementEvent.of(
                        uid.toString(), ItemAction.ADMIN_REMOVE, itemId,
                        take, ctx.getSource().getTextName(), target.getScoreboardName(), "removed", "removed"));
            }
            removed += take;
            remaining -= take;
        }

        final int removedFinal = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("§aRemoved " + removedFinal + "x from " + target.getScoreboardName()
                + " (§7requested " + count + "§a)"), true);
        AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ADMIN_ITEM_REMOVE", target.getScoreboardName(),
                removedFinal + "x " + AdminToolsMod.getItemIdentityManager().itemId(new ItemStack(item)));
        return 1;
    }
}

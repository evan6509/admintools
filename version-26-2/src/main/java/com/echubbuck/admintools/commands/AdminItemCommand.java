package com.echubbuck.admintools.commands;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.echubbuck.admintools.common.PermissionNodes;
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
import net.minecraft.world.item.ItemStack;

public class AdminItemCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        var give = Commands.literal("give")
                .requires(src -> AdminToolsMod.hasAccess(src, PermissionNodes.ADMINITEM_GIVE))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> give(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));

        var remove = Commands.literal("remove")
                .requires(src -> AdminToolsMod.hasAccess(src, PermissionNodes.ADMINITEM_REMOVE))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> remove(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> remove(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));

        dispatcher.register(Commands.literal("adminitem")
                .requires(src -> AdminToolsMod.hasAccess(src, PermissionNodes.ADMINITEM_GIVE)
                        || AdminToolsMod.hasAccess(src, PermissionNodes.ADMINITEM_REMOVE))
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
        ItemStack template = input.createItemStack(1);
        int[] beforeCounts = new int[inventory.getContainerSize()];
        for (int i = 0; i < beforeCounts.length; i++) {
            ItemStack existing = inventory.getItem(i);
            if (matchesInput(existing, input, template)) {
                beforeCounts[i] = existing.getCount();
            }
        }

        String itemId = AdminToolsMod.getItemIdentityManager().itemId(stack);
        String itemName = stack.getItemName().getString();
        target.addItem(stack);
        int delivered = 0;
        for (int i = 0; i < beforeCounts.length; i++) {
            ItemStack output = inventory.getItem(i);
            if (!matchesInput(output, input, template)) continue;
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

        if (delivered == 0) {
            ctx.getSource().sendFailure(Component.literal(target.getScoreboardName() + "'s inventory is full."));
            return 0;
        }
        final int deliveredFinal = delivered;
        final int undeliveredFinal = count - delivered;
        ctx.getSource().sendSuccess(() -> Component.literal("§aGave " + deliveredFinal + "x " + itemName
                + " to " + target.getScoreboardName()
                + (undeliveredFinal > 0 ? " §e(" + undeliveredFinal + " could not be delivered)" : "")), true);
        AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ADMIN_ITEM_GIVE", target.getScoreboardName(),
                deliveredFinal + "x " + itemId + (undeliveredFinal > 0 ? ", undelivered=" + undeliveredFinal : ""));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ItemInput input = ItemArgument.getItem(ctx, "item");
        var item = input.item().value();
        ItemStack template = input.createItemStack(1);
        int remaining = count;
        int removed = 0;

        var inv = target.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!matchesInput(s, input, template)) continue;
            int take = Math.min(remaining, s.getCount());
            var originalUid = AdminToolsMod.getItemIdentityManager().getIdentity(s);
            ItemStack removedStack = inv.removeItem(i, take);
            if (removedStack.isEmpty()) continue;
            var uid = AdminToolsMod.getItemIdentityManager().ensureIdentity(
                    removedStack, "ADMIN_REMOVE", target.getUUID(), target.getScoreboardName());
            String itemId = AdminToolsMod.getItemIdentityManager().itemId(removedStack);
            if (uid != null) {
                ItemStack remainingStack = inv.getItem(i);
                AdminToolsMod.getItemLedger().setStatus(uid.toString(), "REMOVED");
                AdminToolsMod.getItemLedger().setOwnerLocation(uid.toString(), target.getScoreboardName(), "removed");
                if (originalUid != null) {
                    AdminToolsMod.getItemMovementTracker().noteAdminMove(originalUid.toString());
                    if (!remainingStack.isEmpty()) {
                        AdminToolsMod.getItemLedger().updateCount(originalUid.toString(), remainingStack.getCount());
                    }
                }
                AdminToolsMod.getItemMovementTracker().noteAdminMove(uid.toString());
                AdminToolsMod.getItemLedger().recordEvent(ItemMovementEvent.of(
                        uid.toString(), ItemAction.ADMIN_REMOVE, itemId,
                        take, ctx.getSource().getTextName(), target.getScoreboardName(), "removed", "removed"));
            }
            removed += take;
            remaining -= take;
        }

        if (removed == 0) {
            ctx.getSource().sendFailure(Component.literal("No matching items were found in "
                    + target.getScoreboardName() + "'s inventory."));
            return 0;
        }
        final int removedFinal = removed;
        final int missingFinal = count - removed;
        ctx.getSource().sendSuccess(() -> Component.literal("§aRemoved " + removedFinal + "x from "
                + target.getScoreboardName()
                + (missingFinal > 0 ? " §e(" + missingFinal + " requested items were not found)" : "")), true);
        AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ADMIN_ITEM_REMOVE", target.getScoreboardName(),
                removedFinal + "x " + AdminToolsMod.getItemIdentityManager().itemId(new ItemStack(item)));
        return 1;
    }

    private static boolean matchesInput(ItemStack stack, ItemInput input, ItemStack template) {
        if (stack == null || stack.isEmpty() || stack.getItem() != input.item().value()) return false;
        if (input.components().isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(stack, template);
    }
}

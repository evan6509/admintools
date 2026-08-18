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

        AdminToolsMod.getItemEventSink().onGive(target, stack);
        boolean added = target.addItem(stack);
        if (!added) {
            ctx.getSource().sendFailure(Component.literal(target.getScoreboardName() + "'s inventory is full."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aGave " + count + "x " + stack.getItemName().getString()
                + " to " + target.getScoreboardName()), true);
        AdminToolsMod.getActionLogger().log(ctx.getSource().getTextName(), "ADMIN_ITEM_GIVE", target.getScoreboardName(),
                count + "x " + AdminToolsMod.getItemIdentityManager().itemId(stack));
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
            inv.removeItem(i, take);
            if (uid != null) {
                AdminToolsMod.getItemLedger().setStatus(uid.toString(), "REMOVED");
                AdminToolsMod.getItemLedger().recordEvent(ItemMovementEvent.of(
                        uid.toString(), ItemAction.ADMIN_REMOVE, AdminToolsMod.getItemIdentityManager().itemId(s),
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
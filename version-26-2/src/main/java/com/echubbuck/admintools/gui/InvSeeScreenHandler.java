package com.echubbuck.admintools.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class InvSeeScreenHandler extends AbstractContainerMenu {
    private static final int TARGET_SLOTS = 54;

    private final ServerPlayer target;
    private final boolean editable;

    public InvSeeScreenHandler(int syncId, Inventory adminInventory, ServerPlayer target, boolean editable) {
        super(MenuType.GENERIC_9x6, syncId);
        this.target = target;
        this.editable = editable;

        Container targetContainer = new LivePlayerInventory(target.getInventory(), editable);

        for (int i = 0; i < TARGET_SLOTS; i++) {
            final int slotIndex = i;
            int slotX = 8 + (i % 9) * 18;
            int slotY = 18 + (i / 9) * 18;
            addSlot(new Slot(targetContainer, i, slotX, slotY) {
                @Override
                public boolean mayPlace(ItemStack stack) { return editable && slotIndex < 41; }

                @Override
                public boolean mayPickup(Player player) { return editable && slotIndex < 41; }

                @Override
                public int getMaxStackSize() { return slotIndex <= 3 ? 1 : super.getMaxStackSize(); }
            });
        }

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 9; j++)
                addSlot(new Slot(adminInventory, j + i * 9 + 9, 8 + j * 18, 140 + i * 18));
        for (int i = 0; i < 9; i++)
            addSlot(new Slot(adminInventory, i, 8 + i * 18, 198));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!editable) return ItemStack.EMPTY;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < TARGET_SLOTS) {
                if (!this.moveItemStackTo(stack, TARGET_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, TARGET_SLOTS, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (result.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return target.isAlive();
    }
}
package com.echubbuck.admintools.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class LivePlayerInventory implements Container {
    private static final int SIZE = 54;

    private final Inventory targetInventory;
    private final boolean editable;

    public LivePlayerInventory(Inventory targetInventory, boolean editable) {
        this.targetInventory = targetInventory;
        this.editable = editable;
    }

    public static int mapToPlayerSlot(int chestSlot) {
        if (chestSlot >= 0 && chestSlot <= 3) return 36 + chestSlot;
        if (chestSlot == 4) return 40;
        if (chestSlot >= 5 && chestSlot <= 31) return 9 + (chestSlot - 5);
        if (chestSlot >= 32 && chestSlot <= 40) return chestSlot - 32;
        return -1;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return targetInventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        int mapped = mapToPlayerSlot(slot);
        return mapped == -1 ? ItemStack.EMPTY : targetInventory.getItem(mapped);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        int mapped = mapToPlayerSlot(slot);
        if (mapped == -1 || !editable) return ItemStack.EMPTY;
        return targetInventory.removeItem(mapped, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        int mapped = mapToPlayerSlot(slot);
        if (mapped == -1 || !editable) return ItemStack.EMPTY;
        return targetInventory.removeItemNoUpdate(mapped);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        int mapped = mapToPlayerSlot(slot);
        if (mapped != -1 && editable) {
            targetInventory.setItem(mapped, stack);
        }
    }

    @Override
    public void setChanged() {
        targetInventory.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        if (editable) {
            for (int i = 0; i < SIZE; i++) {
                int mapped = mapToPlayerSlot(i);
                if (mapped != -1) {
                    targetInventory.setItem(mapped, ItemStack.EMPTY);
                }
            }
        }
    }
}
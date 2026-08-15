package com.echubbuck.admintools.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EnderSeeScreenHandler extends AbstractContainerMenu {
    private final ServerPlayer target;

    public EnderSeeScreenHandler(int syncId, Inventory adminInventory, ServerPlayer target) {
        super(MenuType.GENERIC_9x3, syncId);
        this.target = target;

        Container enderChest = target.getEnderChestInventory();

        for (int i = 0; i < 27; i++) {
            int slotX = 8 + (i % 9) * 18;
            int slotY = 18 + (i / 9) * 18;
            addSlot(new Slot(enderChest, i, slotX, slotY) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }

                @Override
                public boolean mayPickup(Player player) { return false; }
            });
        }

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 9; j++)
                addSlot(new Slot(adminInventory, j + i * 9 + 9, 8 + j * 18, 140 + i * 18));
        for (int i = 0; i < 9; i++)
            addSlot(new Slot(adminInventory, i, 8 + i * 18, 198));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return target.isAlive();
    }
}
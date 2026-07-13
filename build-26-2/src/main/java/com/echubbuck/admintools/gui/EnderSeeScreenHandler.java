package com.echubbuck.admintools.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.flag.FeatureFlagSet;

public class EnderSeeScreenHandler extends AbstractContainerMenu {
    public static final MenuType<EnderSeeScreenHandler> TYPE = new MenuType<>(EnderSeeScreenHandler::new, FeatureFlagSet.of());

    private final Container targetInventory;

    public static void register() {}

    // Server: create with target data
    public static EnderSeeScreenHandler createForTarget(int syncId, Inventory playerInventory, ServerPlayer target) {
        var inventory = new SimpleContainer(27);
        var enderChest = target.getEnderChestInventory();
        for (int i = 0; i < 27 && i < enderChest.getContainerSize(); i++) {
            inventory.setItem(i, enderChest.getItem(i));
        }
        return new EnderSeeScreenHandler(syncId, playerInventory, inventory);
    }

    public EnderSeeScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new SimpleContainer(27));
    }

    public EnderSeeScreenHandler(int syncId, Inventory playerInventory, Container inventory) {
        super(TYPE, syncId);
        this.targetInventory = inventory;

        // Ender chest slots
        for (int i = 0; i < 27; i++) {
            int slotX = 8 + (i % 9) * 18;
            int slotY = 18 + (i / 9) * 18;
            addSlot(new Slot(inventory, i, slotX, slotY) {
                @Override public boolean mayPickup(Player p) { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        // Admin's inventory
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 9; j++)
                addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 140 + i * 18));
        for (int i = 0; i < 9; i++)
            addSlot(new Slot(playerInventory, i, 8 + i * 18, 198));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override
    public boolean stillValid(Player player) { return true; }
    public Container getTargetInventory() { return targetInventory; }

    public static class Provider implements MenuProvider {
        private final ServerPlayer target;
        public Provider(ServerPlayer target) { this.target = target; }
        @Override
        public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
            return EnderSeeScreenHandler.createForTarget(syncId, inv, target);
        }
        @Override
        public Component getDisplayName() { return Component.literal("Ender Chest: " + target.getScoreboardName()); }
    }
}

package com.echubbuck.admintools.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class EnderSeeScreenHandler extends ScreenHandler {
    public static final ScreenHandlerType<EnderSeeScreenHandler> TYPE = new ScreenHandlerType<>(EnderSeeScreenHandler::new);

    private final Inventory targetInventory;

    public static void register() {}

    // Server: create with target data
    public static EnderSeeScreenHandler createForTarget(int syncId, PlayerInventory playerInventory, ServerPlayerEntity target) {
        var inventory = new SimpleInventory(27);
        var enderChest = target.getEnderChestInventory();
        for (int i = 0; i < 27 && i < enderChest.size(); i++) {
            inventory.setStack(i, enderChest.getStack(i));
        }
        return new EnderSeeScreenHandler(syncId, playerInventory, inventory);
    }

    public EnderSeeScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27));
    }

    public EnderSeeScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(TYPE, syncId);
        this.targetInventory = inventory;

        // Ender chest slots
        for (int i = 0; i < 27; i++) {
            int slotX = 8 + (i % 9) * 18;
            int slotY = 18 + (i / 9) * 18;
            addSlot(new Slot(inventory, i, slotX, slotY) {
                @Override public boolean canTakeItems(PlayerEntity p) { return false; }
                @Override public boolean canInsert(ItemStack stack) { return false; }
                @Override public boolean canTakePartial(PlayerEntity p) { return false; }
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
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    @Override
    public boolean canUse(PlayerEntity player) { return true; }
    public Inventory getTargetInventory() { return targetInventory; }

    public static class Provider implements net.minecraft.screen.ScreenHandlerFactory {
        private final ServerPlayerEntity target;
        public Provider(ServerPlayerEntity target) { this.target = target; }
        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
            return EnderSeeScreenHandler.createForTarget(syncId, inv, target);
        }
        @Override
        public Text getDisplayName() { return Text.literal("Ender Chest: " + target.getName().getString()); }
    }
}

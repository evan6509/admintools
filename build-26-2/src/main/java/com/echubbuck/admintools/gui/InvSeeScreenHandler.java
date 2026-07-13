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

public class InvSeeScreenHandler extends AbstractContainerMenu {
    public static final MenuType<InvSeeScreenHandler> TYPE = new MenuType<>(InvSeeScreenHandler::new, FeatureFlagSet.of());
    public static final Component TITLE = Component.literal("Inventory Viewer");

    private final Container targetInventory;

    public static void register() {
        // TYPE is static-initialized; registration done via fabric screen handler registry
    }

    // Server-side: full constructor with target player
    public InvSeeScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new SimpleContainer(54));
    }

    // Client-side: type factory constructor
    public InvSeeScreenHandler(int syncId, Inventory playerInventory, Container targetInventory) {
        super(TYPE, syncId);
        this.targetInventory = targetInventory;
    }

    public static InvSeeScreenHandler createForTarget(int syncId, Inventory playerInventory, ServerPlayer target) {
        var inventory = new SimpleContainer(54);

        for (int i = 0; i < 4; i++) inventory.setItem(i, target.getInventory().getItem(36 + i));
        inventory.setItem(4, target.getOffhandItem());
        for (int i = 0; i < 27; i++) inventory.setItem(5 + i, target.getInventory().getItem(9 + i));
        for (int i = 0; i < 9; i++) inventory.setItem(32 + i, target.getInventory().getItem(i));

        var handler = new InvSeeScreenHandler(syncId, playerInventory, inventory);

        // Target inventory slots (41 slots: 5 rows of 9, last row has 5 used + empty)
        for (int idx = 0; idx < 41; idx++) {
            int s = idx;
            int slotX = 8 + (idx % 9) * 18;
            int slotY = 18 + (idx / 9) * 18;
            handler.addSlot(new Slot(inventory, s, slotX, slotY) {
                @Override public boolean mayPickup(Player p) { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        // Admin's own inventory
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 9; j++)
                handler.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 140 + i * 18));
        for (int i = 0; i < 9; i++)
            handler.addSlot(new Slot(playerInventory, i, 8 + i * 18, 198));

        return handler;
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
            return InvSeeScreenHandler.createForTarget(syncId, inv, target);
        }
        @Override
        public Component getDisplayName() { return Component.literal("Inventory: " + target.getScoreboardName()); }
    }
}

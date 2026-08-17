package com.echubbuck.admintools.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Opens a live, vanilla-client-compatible view into another player's main
 * inventory (36 slots). Backed directly by the target's real Inventory, so
 * edits (when editable=true) sync automatically through vanilla slot
 * mechanics. Read-only is enforced per-slot via mayPlace/mayPickup, matching
 * the pattern used in EnderSeeScreenHandler.
 *
 * Note: armor and offhand slots are NOT shown here — a vanilla chest menu
 * only supports a flat grid. If you need those visible, don't use this
 * class; keep a hand-built AbstractContainerMenu instead.
 */
public class InvSeeScreenHandler extends ChestMenu {
    private static final int TARGET_SLOTS = 36; // main inventory only (slots 0-35)

    private final ServerPlayer target;
    private final boolean editable;

    public InvSeeScreenHandler(int syncId, Inventory adminInventory, ServerPlayer target, boolean editable) {
        super(MenuType.GENERIC_9x4, syncId, adminInventory, target.getInventory(), 4);
        this.target = target;
        this.editable = editable;

        // ChestMenu builds its own Slot list in super(); swap the target's
        // slots for read-only-aware ones now that we have access to `this.slots`.
        for (int i = 0; i < TARGET_SLOTS; i++) {
            Slot original = this.slots.get(i);
            this.slots.set(i, new Slot(original.container, i, original.x, original.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return editable;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return editable;
                }
            });
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!editable) return ItemStack.EMPTY;
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean stillValid(Player player) {
        return target.isAlive();
    }
}

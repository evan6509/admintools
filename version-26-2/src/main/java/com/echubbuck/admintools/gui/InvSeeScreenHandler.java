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

public class InvSeeScreenHandler extends ScreenHandler {
    public static final ScreenHandlerType<InvSeeScreenHandler> TYPE = new ScreenHandlerType<>(InvSeeScreenHandler::new);
    public static final Text TITLE = Text.literal("Inventory Viewer");

    private final Inventory targetInventory;

    public static void register() {
        // TYPE is static-initialized; registration done via fabric screen handler registry
    }

    // Server-side: full constructor with target player
    public InvSeeScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(54));
    }

    // Client-side: type factory constructor
    public InvSeeScreenHandler(int syncId, PlayerInventory playerInventory, Inventory targetInventory) {
        super(TYPE, syncId);
        this.targetInventory = targetInventory;
    }

    public static InvSeeScreenHandler createForTarget(int syncId, PlayerInventory playerInventory, ServerPlayerEntity target) {
        var inventory = new SimpleInventory(54);

        for (int i = 0; i < 4; i++) inventory.setStack(i, target.getInventory().armor.get(i));
        inventory.setStack(4, target.getOffHandStack());
        for (int i = 0; i < 27; i++) inventory.setStack(5 + i, target.getInventory().main.get(i + 9));
        for (int i = 0; i < 9; i++) inventory.setStack(32 + i, target.getInventory().main.get(i));

        var handler = new InvSeeScreenHandler(syncId, playerInventory, inventory);

        // Target inventory slots (41 slots: 5 rows of 9, last row has 5 used + empty)
        for (int idx = 0; idx < 41; idx++) {
            int s = idx;
            int slotX = 8 + (idx % 9) * 18;
            int slotY = 18 + (idx / 9) * 18;
            handler.addSlot(new Slot(inventory, s, slotX, slotY) {
                @Override public boolean canTakeItems(PlayerEntity p) { return false; }
                @Override public boolean canInsert(ItemStack stack) { return false; }
                @Override public boolean canTakePartial(PlayerEntity p) { return false; }
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
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    @Override
    public boolean canUse(PlayerEntity player) { return true; }
    public Inventory getTargetInventory() { return targetInventory; }

    public static class Provider implements net.minecraft.screen.ScreenHandlerFactory {
        private final ServerPlayerEntity target;
        public Provider(ServerPlayerEntity target) { this.target = target; }
        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
            return InvSeeScreenHandler.createForTarget(syncId, inv, target);
        }
        @Override
        public Text getDisplayName() { return Text.literal("Inventory: " + target.getName().getString()); }
    }
}

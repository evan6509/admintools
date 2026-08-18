package com.echubbuck.admintools.identity;

import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ItemEventSink {
    private final ItemIdentityManager identityManager;
    private volatile UUID lastBreakPlayer = null;
    private long lastBreakTime = 0;

    public ItemEventSink(ItemIdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    public ItemIdentityManager identityManager() {
        return identityManager;
    }

    public void onGive(Player target, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = target.getScoreboardName();
        UUID uid = identityManager.ensureIdentity(stack, "ADMIN_GIVE", target.getUUID(), name);
        identityManager.ledger().setOwnerLocation(uid.toString(), name, "player:" + name);
        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                uid.toString(), ItemAction.ADMIN_GIVE, identityManager.itemId(stack), stack.getCount(),
                name, null, name, "player:" + name));
    }

    public void onPickup(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = player.getScoreboardName();
        UUID uid = identityManager.ensureIdentity(stack, "PICKUP", player.getUUID(), name);
        identityManager.ledger().setOwnerLocation(uid.toString(), name, "player:" + name);
        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                uid.toString(), ItemAction.PICKUP, identityManager.itemId(stack), stack.getCount(),
                name, "ground", name, "player:" + name));
    }

    public void onDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = player.getScoreboardName();
        UUID uid = identityManager.ensureIdentity(stack, "DROP", player.getUUID(), name);
        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                uid.toString(), ItemAction.DROP, identityManager.itemId(stack), stack.getCount(),
                name, name, "ground", "ground"));
    }

    public void onPlace(Player player, ItemStack handStack) {
        if (handStack.isEmpty()) return;
        String name = player.getScoreboardName();
        UUID uid = identityManager.getIdentity(handStack);
        if (uid == null) return; // un-tagged legacy item placed; count change tracked by diff
        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                uid.toString(), ItemAction.PLACE, identityManager.itemId(handStack), 1,
                name, name, "placed", "placed"));
    }

    public void onBreak(Player player) {
        lastBreakPlayer = player.getUUID();
        lastBreakTime = System.currentTimeMillis();
    }

    /** Called when a new ground ItemEntity is spawned. */
    public void onItemEntitySpawn(ItemStack stack) {
        if (stack.isEmpty()) return;
        String source = "DROP";
        UUID creator = null;
        if (lastBreakPlayer != null && System.currentTimeMillis() - lastBreakTime < 2000) {
            source = "BREAK";
            creator = lastBreakPlayer;
            lastBreakPlayer = null;
        }
        identityManager.ensureIdentity(stack, source, creator, null);
    }
}
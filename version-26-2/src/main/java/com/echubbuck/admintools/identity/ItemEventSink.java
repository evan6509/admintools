package com.echubbuck.admintools.identity;

import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ItemEventSink {
    private static final long BREAK_WINDOW_MS = 2000;
    private static final long DROP_MEMORY_MS = 5000;

    private final ItemIdentityManager identityManager;
    private final ItemMovementTracker movementTracker;

    /** Most recent block break per player, so ground items attribute to the breaker. */
    private final Map<UUID, Long> lastBreakTimes = new HashMap<>();
    /** Uids of stacks this player dropped via Player.drop; lets spawned ItemEntities be attributed exactly. */
    private final Map<String, Long> expectedDropUids = new HashMap<>();

    public ItemEventSink(ItemIdentityManager identityManager, ItemMovementTracker movementTracker) {
        this.identityManager = identityManager;
        this.movementTracker = movementTracker;
    }

    public ItemIdentityManager identityManager() {
        return identityManager;
    }

    public void onGive(Player target, ItemStack stack) {
        if (stack.isEmpty()) return;
        onDeliveredGive(target, stack, stack.getCount(), target.getScoreboardName());
    }

    /** Records the portion of a give that actually entered an inventory. */
    public void onDeliveredGive(Player target, ItemStack stack, int count, String actor) {
        if (stack.isEmpty() || count <= 0) return;
        String name = target.getScoreboardName();
        UUID uid = identityManager.ensureIdentity(stack, "ADMIN_GIVE", target.getUUID(), name);
        if (movementTracker != null) movementTracker.noteAdminMove(uid.toString());
        String eventActor = actor == null ? name : actor;
        identityManager.ledger().updateCount(uid.toString(), stack.getCount());
        identityManager.ledger().setOwnerLocation(uid.toString(), name, ItemIdentityManager.ownerKey(name));
        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                uid.toString(), ItemAction.ADMIN_GIVE, identityManager.itemId(stack), count,
                eventActor, null, name, ItemIdentityManager.ownerKey(name)));
    }

    /** Records a give remainder that could not be delivered to the target. */
    public void onUndeliveredGive(Player target, ItemStack stack, int count, String actor) {
        if (stack.isEmpty() || count <= 0) return;
        String name = target.getScoreboardName();
        UUID uid = identityManager.ensureIdentity(stack, "ADMIN_GIVE", target.getUUID(), name);
        String eventActor = actor == null ? name : actor;
        identityManager.ledger().setOwnerLocation(uid.toString(), "unattributed", "undelivered");
        identityManager.ledger().setStatus(uid.toString(), "UNDELIVERED");
        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                uid.toString(), ItemAction.UNDELIVERED, identityManager.itemId(stack), count,
                eventActor, name, "undelivered", "undelivered"));
    }

    /** Wraps the actual insertion, before vanilla restores the count of fully picked-up entities. */
    public boolean addPickedUpStack(Player player, ItemStack groundStack) {
        var inventory = player.getInventory();
        if (groundStack.isEmpty()) return inventory.add(groundStack);
        String name = player.getScoreboardName();
        UUID groundUid = identityManager.ensureIdentity(groundStack, "PICKUP", null, null);
        ItemStack original = groundStack.copy();
        ItemStack[] before = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < before.length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) identityManager.ensureIdentity(stack, "OBSERVE", player.getUUID(), name);
            before[slot] = stack.copy();
        }
        boolean inserted = inventory.add(groundStack);
        boolean remainder = !groundStack.isEmpty();
        boolean retainedGroundUid = false;
        java.util.List<String> destinations = new java.util.ArrayList<>();
        for (int slot = 0; slot < before.length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(original, stack)) continue;
            int previous = ItemStack.isSameItemSameComponents(before[slot], stack) ? before[slot].getCount() : 0;
            int received = stack.getCount() - previous;
            if (received <= 0) continue;
            UUID uid = identityManager.ensureIdentity(stack, "PICKUP", player.getUUID(), name);
            if (uid.equals(groundUid)) {
                // A copied partial destination cannot share the live ground remainder's UID.
                if (remainder || retainedGroundUid) uid = identityManager.identifySplit(groundUid, stack);
                else retainedGroundUid = true;
            } else {
                identityManager.ledger().addParent(uid.toString(), groundUid.toString());
            }
            destinations.add(uid.toString());
            if (movementTracker != null) movementTracker.noteSinkPickup(uid.toString());
            identityManager.ledger().updateCount(uid.toString(), stack.getCount());
            identityManager.ledger().setOwnerLocation(uid.toString(), name, ItemIdentityManager.ownerKey(name));
            identityManager.ledger().recordEvent(ItemMovementEvent.of(
                    uid.toString(), ItemAction.PICKUP, identityManager.itemId(stack), received,
                    name, "ground", name, ItemIdentityManager.ownerKey(name)));
        }
        if (remainder) {
            identityManager.ledger().updateCount(groundUid.toString(), groundStack.getCount());
            identityManager.ledger().setOwnerLocation(groundUid.toString(), "ground", "ground");
        } else if (!retainedGroundUid && !destinations.isEmpty()) {
            identityManager.ledger().setStatus(groundUid.toString(), "MERGED");
            identityManager.ledger().updateCount(groundUid.toString(), 0);
            identityManager.ledger().setOwnerLocation(groundUid.toString(), name, String.join(",", destinations));
            identityManager.ledger().recordEvent(ItemMovementEvent.of(
                    groundUid.toString(), ItemAction.MERGE, identityManager.itemId(original), original.getCount(),
                    name, groundUid.toString(), String.join(",", destinations), ItemIdentityManager.ownerKey(name)));
        }
        return inserted;
    }

    public void onPickup(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = player.getScoreboardName();
        UUID uid = identityManager.ensureIdentity(stack, "PICKUP", player.getUUID(), name);
        if (movementTracker != null) movementTracker.noteSinkPickup(uid.toString());
        identityManager.ledger().setOwnerLocation(uid.toString(), name, ItemIdentityManager.ownerKey(name));
        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                uid.toString(), ItemAction.PICKUP, identityManager.itemId(stack), stack.getCount(),
                name, "ground", name, ItemIdentityManager.ownerKey(name)));
    }

    public void onDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = player.getScoreboardName();
        UUID uid = identityManager.ensureIdentity(stack, "DROP", player.getUUID(), name);
        if (movementTracker != null) movementTracker.noteSinkDrop(uid.toString());
        expectedDropUids.put(uid.toString(), System.currentTimeMillis());
        pruneExpectedDrops();
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
        lastBreakTimes.put(player.getUUID(), System.currentTimeMillis());
        if (lastBreakTimes.size() > 128) {
            lastBreakTimes.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > BREAK_WINDOW_MS);
        }
    }

    /** Called when a new ground ItemEntity is spawned (no player context available). */
    public void onItemEntitySpawn(ItemStack stack) {
        if (stack.isEmpty()) return;

        // Exact attribution: this entity's uid was just dropped by a known player.
        UUID uid = identityManager.getIdentity(stack);
        if (uid != null && expectedDropUids.remove(uid.toString()) != null) {
            return; // DROP already recorded by onDrop; nothing more to do.
        }

        // Otherwise attribute to the most recent breaker within the window.
        String source = "DROP";
        UUID creator = null;
        UUID breaker = null;
        long best = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : lastBreakTimes.entrySet()) {
            if (now - e.getValue() < BREAK_WINDOW_MS && e.getValue() > best) {
                best = e.getValue();
                breaker = e.getKey();
            }
        }
        if (breaker != null) {
            source = "BREAK";
            creator = breaker;
            lastBreakTimes.remove(breaker);
        }
        identityManager.ensureIdentity(stack, source, creator, null);
    }

    /** Drops all per-player attribution state on logout. */
    public void forgetPlayer(UUID uuid) {
        lastBreakTimes.remove(uuid);
    }

    private void pruneExpectedDrops() {
        long cutoff = System.currentTimeMillis() - DROP_MEMORY_MS;
        Iterator<Map.Entry<String, Long>> it = expectedDropUids.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) it.remove();
        }
    }
}

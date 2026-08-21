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

package com.echubbuck.admintools.gui;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final ServerPlayer admin;
    private final boolean editable;

    public InvSeeScreenHandler(int syncId, Inventory adminInventory, ServerPlayer target, boolean editable) {
        super(MenuType.GENERIC_9x4, syncId, adminInventory, target.getInventory(), 4);
        this.target = target;
        this.admin = (ServerPlayer) adminInventory.player;
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
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (!editable || player != admin) {
            super.clicked(slotId, button, input, player);
            return;
        }
        Snapshot targetBefore = snapshot(target.getInventory(), target.getScoreboardName(), "target-slot", null);
        Snapshot adminBefore = snapshot(admin.getInventory(), admin.getScoreboardName(), "admin-slot", getCarried());
        super.clicked(slotId, button, input, player);
        Snapshot targetAfter = snapshot(target.getInventory(), target.getScoreboardName(), "target-slot", null);
        Snapshot adminAfter = snapshot(admin.getInventory(), admin.getScoreboardName(), "admin-slot", getCarried());
        auditChanges(targetBefore, targetAfter, adminBefore, adminAfter);
    }

    @Override
    public boolean stillValid(Player player) {
        return target.isAlive() && !target.hasDisconnected();
    }

    private Snapshot snapshot(net.minecraft.world.Container inventory, String owner,
                              String slotPrefix, ItemStack carried) {
        Snapshot snapshot = new Snapshot();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            add(snapshot, inventory.getItem(i), owner, slotPrefix + ":" + i);
        }
        add(snapshot, carried, owner, "cursor");
        return snapshot;
    }

    private void add(Snapshot snapshot, ItemStack stack, String owner, String location) {
        if (stack == null || stack.isEmpty()) return;
        UUID uid = AdminToolsMod.getItemIdentityManager().ensureIdentity(
                stack, "INVSEE_OBSERVE", null, owner);
        String key = uid.toString();
        snapshot.counts.merge(key, stack.getCount(), Integer::sum);
        snapshot.items.putIfAbsent(key, AdminToolsMod.getItemIdentityManager().itemId(stack));
        snapshot.locations.computeIfAbsent(key, ignored -> new HashSet<>()).add(location);
    }

    private void auditChanges(Snapshot targetBefore, Snapshot targetAfter,
                              Snapshot adminBefore, Snapshot adminAfter) {
        Map<String, Change> targetChanges = changes(targetBefore, targetAfter);
        Map<String, Change> adminChanges = changes(adminBefore, adminAfter);
        Set<String> handledTarget = new HashSet<>();
        Set<String> handledAdmin = new HashSet<>();

        // First correlate moves that preserve their UID.
        for (Map.Entry<String, Change> entry : targetChanges.entrySet()) {
            Change other = adminChanges.get(entry.getKey());
            if (other != null && entry.getValue().delta == -other.delta) {
                boolean targetToAdmin = entry.getValue().delta < 0;
                recordMove(entry.getKey(), entry.getValue().itemId, Math.abs(entry.getValue().delta),
                        targetToAdmin,
                        targetToAdmin ? entry.getValue() : other,
                        targetToAdmin ? other : entry.getValue());
                handledTarget.add(entry.getKey());
                handledAdmin.add(entry.getKey());
            }
        }

        // Then correlate partial splits, whose destination child has a new UID.
        correlateByItem(targetChanges, adminChanges, handledTarget, handledAdmin, true);
        correlateByItem(adminChanges, targetChanges, handledAdmin, handledTarget, false);

        // Preserve an audit record even for unusual cursor/merge cases that could not pair exactly.
        for (Map.Entry<String, Change> entry : targetChanges.entrySet()) {
            if (handledTarget.contains(entry.getKey())) continue;
            recordMove(entry.getKey(), entry.getValue().itemId, Math.abs(entry.getValue().delta),
                    entry.getValue().delta < 0, entry.getValue(), null);
        }
        for (String uid : adminChanges.keySet()) {
            AdminToolsMod.getItemMovementTracker().noteAdminMove(uid);
        }
        for (String uid : targetChanges.keySet()) {
            AdminToolsMod.getItemMovementTracker().noteAdminMove(uid);
        }
    }

    private void correlateByItem(Map<String, Change> source, Map<String, Change> destination,
                                 Set<String> handledSource, Set<String> handledDestination,
                                 boolean targetIsSource) {
        for (Map.Entry<String, Change> loss : source.entrySet()) {
            if (handledSource.contains(loss.getKey()) || loss.getValue().delta >= 0) continue;
            for (Map.Entry<String, Change> gain : destination.entrySet()) {
                if (handledDestination.contains(gain.getKey()) || gain.getValue().delta <= 0) continue;
                if (-loss.getValue().delta != gain.getValue().delta
                        || !loss.getValue().itemId.equals(gain.getValue().itemId)) continue;
                recordMove(gain.getKey(), gain.getValue().itemId, gain.getValue().delta,
                        targetIsSource, loss.getValue(), gain.getValue());
                handledSource.add(loss.getKey());
                handledDestination.add(gain.getKey());
                break;
            }
        }
    }

    private void recordMove(String uid, String itemId, int count, boolean targetToAdmin,
                            Change sourceChange, Change destinationChange) {
        if (count <= 0) return;
        String adminName = admin.getScoreboardName();
        String targetName = target.getScoreboardName();
        String from = targetToAdmin ? targetName : adminName;
        String to = targetToAdmin ? adminName : targetName;
        String fromSlot = sourceChange == null ? "unknown" : sourceChange.beforeLocation;
        String toSlot = destinationChange == null ? "unknown" : destinationChange.afterLocation;
        String location = "invsee:" + targetName + " " + fromSlot + " -> " + toSlot;
        AdminToolsMod.getItemLedger().setOwnerLocation(uid, to, "player:" + to);
        AdminToolsMod.getItemLedger().recordEvent(ItemMovementEvent.of(
                uid, ItemAction.ADMIN_MOVE, itemId, count, adminName, from, to, location));
        AdminToolsMod.getActionLogger().log(adminName, "INVSEE_EDIT_MOVE", targetName,
                count + "x " + itemId + " " + from + " -> " + to + " " + fromSlot + " -> "
                        + toSlot + " uid=" + uid);
    }

    private Map<String, Change> changes(Snapshot before, Snapshot after) {
        Map<String, Change> out = new HashMap<>();
        Set<String> uids = new HashSet<>(before.counts.keySet());
        uids.addAll(after.counts.keySet());
        for (String uid : uids) {
            int delta = after.counts.getOrDefault(uid, 0) - before.counts.getOrDefault(uid, 0);
            if (delta == 0) continue;
            String item = after.items.getOrDefault(uid, before.items.getOrDefault(uid, "minecraft:unknown"));
            out.put(uid, new Change(item, delta,
                    formatLocations(before.locations.get(uid)), formatLocations(after.locations.get(uid))));
        }
        return out;
    }

    private String formatLocations(Set<String> locations) {
        return locations == null || locations.isEmpty() ? "unknown" : String.join("+", locations);
    }

    private static final class Snapshot {
        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, String> items = new HashMap<>();
        final Map<String, Set<String>> locations = new HashMap<>();
    }

    private record Change(String itemId, int delta, String beforeLocation, String afterLocation) {}
}

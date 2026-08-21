package com.echubbuck.admintools.identity;

import com.echubbuck.admintools.container.ContainerAuditTracker;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ItemMovementTracker {
    /** How long a sink-recorded drop/pickup suppresses the diff-side duplicate event. */
    private static final int SINK_MEMORY_TICKS = 3;
    /** How long a container deposit waits for a matching withdrawal before expiring. */
    private static final int CONTAINER_MEMORY_TICKS = 600;

    private final ItemIdentityManager identityManager;
    private final ItemDuplicateDetector duplicateDetector;
    private final ContainerAuditTracker containerAuditTracker;

    private final Map<UUID, Map<String, Integer>> prevCounts = new HashMap<>();
    private final Map<UUID, Map<String, String>> prevItems = new HashMap<>();
    /** Uids the event sink already logged as DROP/PICKUP -> tick stamp, to avoid double logging. */
    private final Map<String, Integer> sinkDrops = new HashMap<>();
    private final Map<String, Integer> sinkPickups = new HashMap<>();
    /** Uids seen disappearing into an open container -> tick stamp, for withdrawal attribution. */
    private final Map<String, Integer> containerDeposits = new HashMap<>();
    private int tickCounter = 0;

    public ItemMovementTracker(ItemIdentityManager identityManager, ItemDuplicateDetector duplicateDetector) {
        this(identityManager, duplicateDetector, null);
    }

    public ItemMovementTracker(ItemIdentityManager identityManager,
                               ItemDuplicateDetector duplicateDetector,
                               ContainerAuditTracker containerAuditTracker) {
        this.identityManager = identityManager;
        this.duplicateDetector = duplicateDetector;
        this.containerAuditTracker = containerAuditTracker;
    }

    public ItemIdentityManager identityManager() {
        return identityManager;
    }

    // --- Hooks from ItemEventSink ---

    /** The sink logged a DROP for this uid; the diff should not log a second one. */
    public void noteSinkDrop(String uid) {
        sinkDrops.put(uid, tickCounter);
    }

    /** The sink logged a PICKUP for this uid; the diff should not log UNKNOWN arrival. */
    public void noteSinkPickup(String uid) {
        sinkPickups.put(uid, tickCounter);
    }

    // --- Lifecycle ---

    /** Seeds the snapshot for a newly-joined player without emitting movement events. */
    public void baselinePlayer(ServerPlayer player) {
        String name = player.getScoreboardName();
        UUID puuid = player.getUUID();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> items = new HashMap<>();
        for (ItemStack s : collectStacks(player)) {
            UUID uid = identityManager.ensureIdentity(s, "UNKNOWN", puuid, name);
            counts.merge(uid.toString(), s.getCount(), Integer::sum);
            items.putIfAbsent(uid.toString(), identityManager.itemId(s));
        }
        prevCounts.put(puuid, counts);
        prevItems.put(puuid, items);
    }

    /**
     * Once-per-tick maintenance. Registered on START_SERVER_TICK (not per-level,
     * which would run the scans once per dimension).
     */
    public void onServerTick(MinecraftServer server) {
        tickCounter++;
        scanAll(server);
        if (tickCounter % 100 == 0) { // every ~5 seconds
            duplicateDetector.scanPlayers(server.getPlayerList().getPlayers());
        }
        if (tickCounter % 600 == 0) { // every ~30 seconds
            identityManager.ledger().saveIfDirty();
        }
    }

    /** Drops all per-player state on logout so the maps stay bounded. */
    public void forgetPlayer(UUID uuid) {
        prevCounts.remove(uuid);
        prevItems.remove(uuid);
    }

    private boolean sinkRecorded(Map<String, Integer> memory, String uid) {
        Integer t = memory.get(uid);
        return t != null && tickCounter - t <= SINK_MEMORY_TICKS;
    }

    private boolean withdrawnFromContainer(String uid) {
        if (containerDeposits.containsKey(uid)) return true;
        var entry = identityManager.ledger().entry(uid);
        return entry != null && "container".equals(entry.currentOwner);
    }

    private static final class PlayerDelta {
        final UUID player;
        final String name;
        final Map<String, Integer> deltas = new HashMap<>(); // uid -> count delta
        final Map<String, String> item = new HashMap<>();    // uid -> itemId
        final Set<String> externallyLoggedLosses = new HashSet<>(); // sink DROP or container deposit

        PlayerDelta(UUID player, String name) {
            this.player = player;
            this.name = name;
        }
    }

    private void scanAll(MinecraftServer server) {
        List<PlayerDelta> deltas = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            deltas.add(scanPlayer(player));
        }
        correlateTransfers(deltas);
        pruneMemories();
    }

    private void pruneMemories() {
        pruneMemory(sinkDrops, SINK_MEMORY_TICKS * 8);
        pruneMemory(sinkPickups, SINK_MEMORY_TICKS * 8);
        pruneMemory(containerDeposits, CONTAINER_MEMORY_TICKS);
    }

    private void pruneMemory(Map<String, Integer> memory, int maxAgeTicks) {
        if (memory.isEmpty()) return;
        Iterator<Map.Entry<String, Integer>> it = memory.entrySet().iterator();
        while (it.hasNext()) {
            if (tickCounter - it.next().getValue() > maxAgeTicks) it.remove();
        }
    }

    private PlayerDelta scanPlayer(ServerPlayer player) {
        String name = player.getScoreboardName();
        UUID puuid = player.getUUID();
        List<ItemStack> stacks = collectStacks(player);

        PlayerDelta pd = new PlayerDelta(puuid, name);

        Map<String, Integer> currentCounts = new HashMap<>();

        // Pass 1: ensure identity + correct transient split duplicates (same uid twice in one player).
        Map<String, ItemStack> firstStackOfUid = new HashMap<>();
        for (ItemStack s : stacks) {
            UUID uid = identityManager.ensureIdentity(s, "UNKNOWN", puuid, name);
            String u = uid.toString();
            ItemStack existing = firstStackOfUid.putIfAbsent(u, s);
            if (existing != null) {
                // A vanilla split copied the component onto a second, independent stack.
                UUID nuid = identityManager.assignIdentity(s, "SPLIT", puuid, name);
                identityManager.ledger().addParent(nuid.toString(), u);
                identityManager.ledger().recordEvent(ItemMovementEvent.of(
                        nuid.toString(), ItemAction.SPLIT, identityManager.itemId(s), s.getCount(),
                        name, u, nuid.toString(), ItemIdentityManager.ownerKey(name)));
                u = nuid.toString();
            }
            currentCounts.merge(u, s.getCount(), Integer::sum);
            pd.item.putIfAbsent(u, identityManager.itemId(s));
        }

        // Pass 2: diff against the previous snapshot.
        Map<String, Integer> prev = prevCounts.getOrDefault(puuid, Map.of());
        Map<String, String> prevItem = prevItems.getOrDefault(puuid, Map.of());
        for (Map.Entry<String, Integer> e : currentCounts.entrySet()) {
            String uid = e.getKey();
            int now = e.getValue();
            int before = prev.getOrDefault(uid, -1);
            if (before == -1) {
                pd.deltas.put(uid, now); // new identity (created by a hook or first observation)
                boolean containerEventLogged = containerAuditTracker != null
                        && containerAuditTracker.consumeRecentlyLogged(uid);
                boolean auditingContainer = containerAuditTracker != null
                        && containerAuditTracker.hasActiveSession(puuid);
                if (containerEventLogged || auditingContainer) {
                    // The open container session will attribute this arrival precisely on close.
                } else if (withdrawnFromContainer(uid)) {
                    // This uid was previously deposited into a container by someone; attribute the return.
                    containerDeposits.remove(uid);
                    identityManager.ledger().setOwnerLocation(uid, name, ItemIdentityManager.ownerKey(name));
                    identityManager.ledger().recordEvent(ItemMovementEvent.of(
                            uid, ItemAction.CONTAINER_WITHDRAWAL, pd.item.get(uid), now,
                            "container", "container", name, ItemIdentityManager.ownerKey(name)));
                } else if (!sinkRecorded(sinkPickups, uid)) {
                    identityManager.ledger().recordEvent(ItemMovementEvent.of(
                            uid, ItemAction.UNKNOWN, pd.item.get(uid), now, name, null, name,
                            ItemIdentityManager.ownerKey(name)));
                }
            } else if (before != now) {
                pd.deltas.put(uid, now - before);
                identityManager.ledger().updateCount(uid, now);
            }
        }
        for (Map.Entry<String, Integer> e : prev.entrySet()) {
            String uid = e.getKey();
            if (currentCounts.containsKey(uid)) continue;
            int lost = e.getValue();
            String item = prevItem.get(uid);
            // A same-item stack in this player gained exactly the lost amount -> the two merged
            // and this identity was absorbed (history preserved in the ledger).
            boolean merged = false;
            if (item != null) {
                for (Map.Entry<String, Integer> c : currentCounts.entrySet()) {
                    if (prev.containsKey(c.getKey())
                            && item.equals(pd.item.get(c.getKey()))
                            && c.getValue() - prev.get(c.getKey()) == lost) {
                        identityManager.ledger().addParent(c.getKey(), uid);
                        identityManager.ledger().setStatus(uid, "MERGED");
                        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                                uid, ItemAction.MERGE, item, lost, name, uid, c.getKey(),
                                ItemIdentityManager.ownerKey(name)));
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged) {
                // Identity left this player (dropped / moved / consumed / transferred).
                pd.deltas.put(uid, -lost);
                pd.item.putIfAbsent(uid, item == null ? "" : item);
                boolean containerEventLogged = containerAuditTracker != null
                        && containerAuditTracker.consumeRecentlyLogged(uid);
                if (sinkRecorded(sinkDrops, uid)) {
                    // Exact drop already recorded by ItemEventSink.onDrop; sync location only.
                    identityManager.ledger().setOwnerLocation(uid, "ground", null);
                    pd.externallyLoggedLosses.add(uid);
                } else if (containerEventLogged) {
                    // Exact container audit already recorded this loss at menu close.
                    pd.externallyLoggedLosses.add(uid);
                } else if (containerAuditTracker != null
                        && containerAuditTracker.hasActiveSession(puuid)) {
                    // The open container session will attribute this loss precisely on close.
                    identityManager.ledger().setOwnerLocation(uid, "container", null);
                    pd.externallyLoggedLosses.add(uid);
                } else if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
                    // Went straight into an open container GUI: a deposit, not a drop.
                    identityManager.ledger().setOwnerLocation(uid, "container", null);
                    containerDeposits.put(uid, tickCounter);
                    identityManager.ledger().recordEvent(ItemMovementEvent.of(
                            uid, ItemAction.CONTAINER_DEPOSIT, item == null ? "" : item, lost,
                            name, name, "container", "container"));
                    pd.externallyLoggedLosses.add(uid);
                } else {
                    identityManager.ledger().setOwnerLocation(uid, "ground", null);
                }
            }
        }

        prevCounts.put(puuid, new HashMap<>(currentCounts));
        prevItems.put(puuid, new HashMap<>(pd.item));
        return pd;
    }

    private void correlateTransfers(List<PlayerDelta> deltas) {
        // Group by uid: collect every (player, delta) observed this tick.
        Map<String, List<PlayerDelta>> byUid = new HashMap<>();
        for (PlayerDelta pd : deltas) {
            for (String uid : pd.deltas.keySet()) {
                byUid.computeIfAbsent(uid, k -> new ArrayList<>()).add(pd);
            }
        }

        for (Map.Entry<String, List<PlayerDelta>> e : byUid.entrySet()) {
            String uid = e.getKey();
            List<PlayerDelta> involved = e.getValue();
            if (involved.size() == 1) {
                PlayerDelta pd = involved.get(0);
                int delta = pd.deltas.get(uid);
                if (delta < 0 && !pd.externallyLoggedLosses.contains(uid)) {
                    identityManager.ledger().recordEvent(ItemMovementEvent.of(
                            uid, ItemAction.DROP, pd.item.get(uid), -delta, pd.name, pd.name, "ground",
                            ItemIdentityManager.ownerKey(pd.name)));
                }
                continue;
            }
            // Pair a negative delta (sender) with a positive delta (receiver) of equal magnitude.
            Set<PlayerDelta> matched = new HashSet<>();
            for (PlayerDelta from : involved) {
                int fromDelta = from.deltas.get(uid);
                if (fromDelta >= 0) continue;
                for (PlayerDelta to : involved) {
                    if (to == from || matched.contains(to)) continue;
                    int toDelta = to.deltas.getOrDefault(uid, 0);
                    if (toDelta > 0 && toDelta == -fromDelta) {
                        matched.add(from);
                        matched.add(to);
                        identityManager.ledger().recordEvent(ItemMovementEvent.of(
                                uid, ItemAction.TRANSFER, from.item.getOrDefault(uid, ""), toDelta,
                                from.name, from.name, to.name, ItemIdentityManager.ownerKey(to.name)));
                        identityManager.ledger().setOwnerLocation(uid, to.name, ItemIdentityManager.ownerKey(to.name));
                        break;
                    }
                }
            }
            // Any unpaired loss is a drop (unless already logged by the sink or a container deposit).
            for (PlayerDelta pd : involved) {
                if (matched.contains(pd)) continue;
                int delta = pd.deltas.get(uid);
                if (delta < 0 && !pd.externallyLoggedLosses.contains(uid)) {
                    identityManager.ledger().recordEvent(ItemMovementEvent.of(
                            uid, ItemAction.DROP, pd.item.getOrDefault(uid, ""), -delta, pd.name, pd.name,
                            "ground", ItemIdentityManager.ownerKey(pd.name)));
                }
            }
        }
    }

    private List<ItemStack> collectStacks(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) out.add(s);
        }
        var ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            ItemStack s = ender.getItem(i);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}

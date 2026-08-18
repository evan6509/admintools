package com.echubbuck.admintools.identity;

import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ItemMovementTracker {
    private final ItemIdentityManager identityManager;
    private final ItemDuplicateDetector duplicateDetector;

    private final Map<UUID, Map<String, Integer>> prevCounts = new HashMap<>();
    private final Map<UUID, Map<String, String>> prevItems = new HashMap<>();
    private int tickCounter = 0;

    public ItemMovementTracker(ItemIdentityManager identityManager, ItemDuplicateDetector duplicateDetector) {
        this.identityManager = identityManager;
        this.duplicateDetector = duplicateDetector;
    }

    public ItemIdentityManager identityManager() {
        return identityManager;
    }

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

    public void onStartTick(ServerLevel world) {
        tickCounter++;
        scanAll(world);
        if (tickCounter % 100 == 0) { // every ~5 seconds
            duplicateDetector.scan(world);
        }
        if (tickCounter % 600 == 0) { // every ~30 seconds
            identityManager.ledger().saveIfDirty();
        }
    }

    private static final class PlayerDelta {
        final UUID player;
        final String name;
        final Map<String, Integer> deltas = new HashMap<>(); // uid -> count delta
        final Map<String, String> item = new HashMap<>();    // uid -> itemId

        PlayerDelta(UUID player, String name) {
            this.player = player;
            this.name = name;
        }
    }

    private void scanAll(ServerLevel world) {
        List<PlayerDelta> deltas = new ArrayList<>();
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            deltas.add(scanPlayer(player));
        }
        correlateTransfers(deltas);
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
                        name, u, nuid.toString(), "player:" + name));
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
                identityManager.ledger().recordEvent(ItemMovementEvent.of(
                        uid, ItemAction.UNKNOWN, pd.item.get(uid), now, name, null, name, "player:" + name));
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
                                uid, ItemAction.MERGE, item, lost, name, uid, c.getKey(), "player:" + name));
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged) {
                // Identity left this player (dropped / moved / consumed / transferred).
                pd.deltas.put(uid, -lost);
                pd.item.putIfAbsent(uid, item == null ? "" : item);
                identityManager.ledger().setOwnerLocation(uid, "ground", null);
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
                if (delta < 0) {
                    identityManager.ledger().recordEvent(ItemMovementEvent.of(
                            uid, ItemAction.DROP, pd.item.get(uid), -delta, pd.name, pd.name, "ground", "player:" + pd.name));
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
                                from.name, from.name, to.name, "player:" + to.name));
                        identityManager.ledger().setOwnerLocation(uid, to.name, "player:" + to.name);
                        break;
                    }
                }
            }
            // Any unpaired loss is a drop.
            for (PlayerDelta pd : involved) {
                if (matched.contains(pd)) continue;
                int delta = pd.deltas.get(uid);
                if (delta < 0) {
                    identityManager.ledger().recordEvent(ItemMovementEvent.of(
                            uid, ItemAction.DROP, pd.item.getOrDefault(uid, ""), -delta, pd.name, pd.name, "ground", "player:" + pd.name));
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
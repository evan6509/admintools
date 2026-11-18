package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemIdentity;
import com.echubbuck.admintools.common.ItemLedger;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paper-safe identity observation. Non-stackable items carry a durable PDC UID;
 * stackable items use persisted slot observations so their vanilla stacking rules remain unchanged.
 */
public final class PaperItemTracker {
    private static final Path STATE_PATH = Path.of("config", "admintools", "paper_item_locations.json");

    private final ItemLedger ledger;
    private final Path statePath;
    private final NamespacedKey uidKey;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Observed> previous = new LinkedHashMap<>();
    private final Map<String, PendingAction> pendingActions = new HashMap<>();
    private final Set<String> reportedDuplicates = new HashSet<>();
    private BukkitTask task;
    private int ticks;
    private boolean detectCreative;

    public PaperItemTracker(Plugin plugin, ItemLedger ledger) {
        this(plugin, ledger, STATE_PATH);
    }

    PaperItemTracker(Plugin plugin, ItemLedger ledger, Path statePath) {
        this.statePath = statePath;
        this.ledger = ledger;
        this.uidKey = new NamespacedKey(plugin, "uid");
        loadState();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 1L, 1L);
    }

    public void setDetectCreative(boolean detectCreative) {
        this.detectCreative = detectCreative;
    }

    public void noteAdminAction(Player player, String itemId, ItemAction action, String actor) {
        pendingActions.put(player.getUniqueId() + "|" + itemId, new PendingAction(action, actor));
    }

    public void scanNow() {
        scan();
    }

    public void close() {
        if (task != null) task.cancel();
        saveState();
        ledger.saveIfDirty();
    }

    private void scan() {
        ticks++;
        Set<UUID> onlinePlayers = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getUniqueId()));
        Map<String, RawStack> raw = collect();
        Map<String, Observed> next = new LinkedHashMap<>();
        Set<String> consumedOldLocations = new HashSet<>();

        // Preserve identities that are still in the same slot with the same item metadata.
        for (var entry : raw.entrySet()) {
            Observed old = previous.get(entry.getKey());
            RawStack current = entry.getValue();
            if (old == null || !old.fingerprint().equals(current.fingerprint())) continue;
            String embedded = embeddedUid(current.stack());
            if (embedded != null && !embedded.equals(old.uid())) continue;
            Observed retained = current.observe(old.uid());
            next.put(entry.getKey(), retained);
            consumedOldLocations.add(entry.getKey());
            ledger.setOwnerLocation(old.uid(), current.owner(), entry.getKey());
            if (old.count() != current.count()) {
                ledger.updateCount(old.uid(), current.count());
                int delta = current.count() - old.count();
                PendingAction pending = pending(current.playerUuid(), current.itemId());
                boolean adminChange = pending != null && (delta > 0 && pending.action() == ItemAction.ADMIN_GIVE
                        || delta < 0 && pending.action() == ItemAction.ADMIN_REMOVE);
                ItemAction action = adminChange ? pending.action() : ItemAction.UNKNOWN;
                ledger.recordEvent(ItemMovementEvent.of(old.uid(), action, current.itemId(), Math.abs(delta),
                        adminChange ? pending.actor() : current.owner(),
                        adminChange && delta > 0 ? null : current.owner(),
                        adminChange && delta < 0 ? "removed" : current.owner(), entry.getKey()));
            }
        }

        // Correlate newly occupied slots with departures, preferring an embedded UID.
        for (var entry : raw.entrySet()) {
            if (next.containsKey(entry.getKey())) continue;
            RawStack current = entry.getValue();
            String uid = embeddedUid(current.stack());
            Map.Entry<String, Observed> moved = findDeparture(current, uid, consumedOldLocations, onlinePlayers);
            if (moved != null) {
                uid = moved.getValue().uid();
                consumedOldLocations.add(moved.getKey());
            }
            if (uid == null) uid = recoverAtLocation(entry.getKey(), current);
            if (uid == null) uid = UUID.randomUUID().toString();

            ensureLedger(uid, current, entry.getKey());
            if (current.stack().getMaxStackSize() == 1 && embeddedUid(current.stack()) == null) {
                setEmbeddedUid(current.stack(), uid);
            }
            next.put(entry.getKey(), current.observe(uid));

            PendingAction pending = pending(current.playerUuid(), current.itemId());
            if (pending != null && pending.action() != ItemAction.ADMIN_GIVE) pending = null;
            ItemAction action = pending != null ? pending.action()
                    : moved != null ? ItemAction.TRANSFER : ItemAction.UNKNOWN;
            String actor = pending != null ? pending.actor() : current.owner();
            String from = moved == null ? null : moved.getKey();
            ledger.recordEvent(ItemMovementEvent.of(uid, action, current.itemId(), current.count(),
                    actor, from, current.owner(), entry.getKey()));
        }

        // Anything left behind disappeared from an observed player inventory.
        for (var entry : previous.entrySet()) {
            if (consumedOldLocations.contains(entry.getKey())) continue;
            Observed old = entry.getValue();
            if (!onlinePlayers.contains(old.playerUuid())) {
                // Absence from the online scan is not an item movement.
                next.put(entry.getKey(), old);
                continue;
            }
            PendingAction pending = pending(old.playerUuid(), old.itemId());
            if (pending != null && pending.action() != ItemAction.ADMIN_REMOVE) pending = null;
            ItemAction action = pending != null ? pending.action() : ItemAction.DROP;
            String actor = pending != null ? pending.actor() : old.owner();
            String destination = action == ItemAction.ADMIN_REMOVE ? "removed" : "ground-or-container";
            ledger.setOwnerLocation(old.uid(), destination, destination);
            if (action == ItemAction.ADMIN_REMOVE) {
                ledger.setStatus(old.uid(), "REMOVED");
                ledger.updateCount(old.uid(), 0);
            }
            ledger.recordEvent(ItemMovementEvent.of(old.uid(), action, old.itemId(), old.count(),
                    actor, old.owner(), destination, entry.getKey()));
        }

        previous.clear();
        previous.putAll(next);
        detectDuplicates(next);
        // Command attribution is consumed by this immediate post-operation scan only.
        pendingActions.clear();
        if (ticks % 600 == 0) {
            saveState();
            ledger.saveIfDirty();
        }
    }

    private Map<String, RawStack> collect() {
        Map<String, RawStack> out = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            collectInventory(out, player, player.getInventory(), "inventory", 36);
            collectInventory(out, player, player.getEnderChest(), "ender", player.getEnderChest().getSize());
        }
        return out;
    }

    private void collectInventory(Map<String, RawStack> out, Player player, Inventory inventory,
                                  String kind, int slots) {
        for (int slot = 0; slot < Math.min(slots, inventory.getSize()); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            String location = "paper:player:" + player.getUniqueId() + ":" + kind + ":" + slot;
            out.put(location, new RawStack(player.getUniqueId(), player.getName(), itemId(stack),
                    stack.getAmount(), fingerprint(stack), stack));
        }
    }

    private Map.Entry<String, Observed> findDeparture(RawStack current, String embedded,
                                                       Set<String> consumed, Set<UUID> onlinePlayers) {
        for (var entry : previous.entrySet()) {
            if (consumed.contains(entry.getKey())) continue;
            Observed old = entry.getValue();
            if (!onlinePlayers.contains(old.playerUuid())) continue;
            if (embedded != null && embedded.equals(old.uid())) return entry;
            if (embedded == null && old.fingerprint().equals(current.fingerprint())
                    && old.count() == current.count()) return entry;
        }
        return null;
    }

    private String recoverAtLocation(String location, RawStack current) {
        for (var entry : ledger.allEntries()) {
            if (location.equals(entry.currentLocation) && current.itemId().equals(entry.itemId)
                    && "ACTIVE".equals(entry.status)) return entry.uid;
        }
        return null;
    }

    private void ensureLedger(String uid, RawStack stack, String location) {
        if (!ledger.has(uid)) {
            ItemIdentity identity = ItemIdentity.create(UUID.fromString(uid), "PAPER_OBSERVE", stack.playerUuid());
            ledger.registerIdentity(identity, stack.itemId(), stack.count(), stack.owner(), location);
            ledger.setCreatorName(uid, stack.owner());
        } else {
            ledger.updateCount(uid, stack.count());
            ledger.setOwnerLocation(uid, stack.owner(), location);
        }
    }

    private PendingAction pending(UUID playerUuid, String itemId) {
        PendingAction action = pendingActions.get(playerUuid + "|" + itemId);
        return action;
    }

    private void detectDuplicates(Map<String, Observed> observations) {
        Map<String, List<String>> locations = new HashMap<>();
        for (var entry : observations.entrySet()) {
            Observed observed = entry.getValue();
            Player player = Bukkit.getPlayer(observed.playerUuid());
            if (player == null) continue;
            if (!detectCreative && player.getGameMode().name().equals("CREATIVE")) continue;
            locations.computeIfAbsent(observed.uid(), ignored -> new ArrayList<>()).add(entry.getKey());
        }
        Set<String> active = new HashSet<>();
        locations.forEach((uid, found) -> {
            if (found.size() < 2) return;
            String key = uid + "|" + String.join("|", found);
            active.add(key);
            if (reportedDuplicates.add(key)) {
                Observed sample = observations.get(found.getFirst());
                ItemMovementEvent event = ItemMovementEvent.of(uid, ItemAction.DUPLICATE_DETECTED,
                        sample.itemId(), sample.count(), null, found.getFirst(), found.getLast(), String.join(",", found));
                ledger.recordEvent(event);
                ledger.recordDuplicate(event);
                Bukkit.getLogger().warning("[AdminTools] Duplicate item UID " + uid + " at " + found);
            }
        });
        reportedDuplicates.retainAll(active);
    }

    private String embeddedUid(ItemStack stack) {
        String value = stack.getPersistentDataContainer().get(uidKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void setEmbeddedUid(ItemStack stack, String uid) {
        stack.editPersistentDataContainer(pdc -> pdc.set(uidKey, PersistentDataType.STRING, uid));
    }

    private static String itemId(ItemStack stack) {
        return stack.getType().getKey().toString();
    }

    private String fingerprint(ItemStack stack) {
        try {
            ItemStack one = stack.clone();
            one.setAmount(1);
            one.editPersistentDataContainer(pdc -> pdc.remove(uidKey));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(one.serializeAsBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            return stack.getType().getKey() + "|" + String.valueOf(stack.getItemMeta());
        }
    }

    private void loadState() {
        if (!Files.exists(statePath)) return;
        try {
            Type type = new TypeToken<Map<String, Observed>>() {}.getType();
            Map<String, Observed> loaded = gson.fromJson(Files.readString(statePath), type);
            if (loaded != null) previous.putAll(loaded);
        } catch (Exception exception) {
            System.err.println("[AdminTools] Failed to load Paper item locations: " + exception.getMessage());
        }
    }

    private void saveState() {
        try {
            Files.createDirectories(statePath.getParent());
            Path temporary = statePath.resolveSibling(statePath.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(previous));
            Files.move(temporary, statePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            System.err.println("[AdminTools] Failed to save Paper item locations: " + exception.getMessage());
        }
    }

    private record RawStack(UUID playerUuid, String owner, String itemId, int count,
                            String fingerprint, ItemStack stack) {
        Observed observe(String uid) {
            return new Observed(uid, playerUuid, owner, itemId, count, fingerprint);
        }
    }

    private record Observed(String uid, UUID playerUuid, String owner, String itemId,
                            int count, String fingerprint) {}

    private record PendingAction(ItemAction action, String actor) {}
}

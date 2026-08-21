package com.echubbuck.admintools.container;

import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemLedger;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.echubbuck.admintools.identity.ItemIdentityManager;
import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks player-opened chest-like containers and writes one JSONL file per location. */
public class ContainerAuditTracker {
    private static final Path LOG_ROOT = Path.of("logs", "admintools", "containers");
    private static final int MAX_RECENT_EVENTS = 500;

    private final ItemIdentityManager identityManager;
    private final ItemLedger ledger;
    private final Gson gson = new Gson();
    private final Map<SessionKey, Session> sessions = new HashMap<>();
    private final Map<String, Long> recentlyLoggedUids = new HashMap<>();
    private final Deque<ContainerAuditEvent> recentEvents = new ArrayDeque<>();
    private boolean enabled = true;

    public ContainerAuditTracker(ItemIdentityManager identityManager) {
        this.identityManager = identityManager;
        this.ledger = identityManager.ledger();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized boolean hasActiveSession(UUID playerUuid) {
        if (!enabled) return false;
        for (SessionKey key : sessions.keySet()) {
            if (key.player().equals(playerUuid)) return true;
        }
        return false;
    }

    /** Suppresses the next player-inventory diff for a change already logged at container close. */
    public synchronized boolean consumeRecentlyLogged(String uid) {
        Long timestamp = recentlyLoggedUids.remove(uid);
        return timestamp != null && System.currentTimeMillis() - timestamp <= 5_000;
    }

    public ContainerKey keyFor(BlockEntity blockEntity) {
        if (!enabled || !(blockEntity instanceof Container)) return null;
        return ContainerKey.from(blockEntity);
    }

    public synchronized void onOpen(ServerPlayer player, BlockEntity blockEntity) {
        if (!enabled || !(blockEntity instanceof Container container)) return;
        ContainerKey key = keyFor(blockEntity);
        if (key == null) return;

        SessionKey sessionKey = new SessionKey(player.getUUID(), key.id());
        if (sessions.containsKey(sessionKey)) return;
        sessions.put(sessionKey, new Session(
                sessionKey,
                key,
                container,
                player.getScoreboardName(),
                snapshot(container, key),
                System.currentTimeMillis()));
    }

    public synchronized void onClose(ServerPlayer player, BlockEntity blockEntity) {
        if (!enabled || !(blockEntity instanceof Container)) return;
        ContainerKey key = keyFor(blockEntity);
        if (key == null) return;
        finish(new SessionKey(player.getUUID(), key.id()));
    }

    /** Flushes sessions when a player disconnects so their last changes are not lost. */
    public synchronized void forgetPlayer(UUID playerUuid) {
        Iterator<Map.Entry<SessionKey, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SessionKey, Session> entry = it.next();
            if (entry.getKey().player().equals(playerUuid)) {
                it.remove();
                finish(entry.getValue());
            }
        }
    }

    /** Flushes all open sessions before the ledger/log writers are closed. */
    public synchronized void close() {
        List<Session> open = new ArrayList<>(sessions.values());
        sessions.clear();
        for (Session session : open) finish(session);
    }

    public synchronized List<ContainerAuditEvent> recentFor(ContainerKey key, int limit) {
        if (limit <= 0) return List.of();
        List<ContainerAuditEvent> out = readEvents(key);
        int from = Math.max(0, out.size() - limit);
        return new ArrayList<>(out.subList(from, out.size()));
    }

    public synchronized List<ContainerAuditEvent> recentEvents() {
        return new ArrayList<>(recentEvents);
    }

    private void finish(SessionKey key) {
        Session session = sessions.remove(key);
        if (session != null) finish(session);
    }

    private void finish(Session session) {
        Map<String, StackSnapshot> current = snapshot(session.container(), session.containerKey());
        Map<String, Integer> added = new LinkedHashMap<>();
        Map<String, Integer> removed = new LinkedHashMap<>();

        Map<String, Integer> allUids = new HashMap<>();
        allUids.putAll(session.baselineCounts());
        for (String uid : current.keySet()) allUids.putIfAbsent(uid, 0);

        for (String uid : allUids.keySet()) {
            int before = session.baselineCounts().getOrDefault(uid, 0);
            int after = current.getOrDefault(uid, new StackSnapshot("", 0)).count();
            int delta = after - before;
            if (delta == 0) continue;
            long now = System.currentTimeMillis();
            recentlyLoggedUids.entrySet().removeIf(entry -> now - entry.getValue() > 5_000);
            recentlyLoggedUids.put(uid, now);

            String itemId = current.containsKey(uid)
                    ? current.get(uid).itemId()
                    : session.baselineItems().getOrDefault(uid, "minecraft:unknown");
            String playerName = session.playerName();
            if (delta > 0) {
                added.merge(itemId, delta, Integer::sum);
                ledger.setOwnerLocation(uid, "container", session.containerKey().location());
                ledger.recordEvent(ItemMovementEvent.of(
                        uid, ItemAction.CONTAINER_DEPOSIT, itemId, delta,
                        playerName, playerName, session.containerKey().location(), session.containerKey().location()));
            } else {
                int count = -delta;
                removed.merge(itemId, count, Integer::sum);
                ledger.setOwnerLocation(uid, playerName, ItemIdentityManager.ownerKey(playerName));
                ledger.recordEvent(ItemMovementEvent.of(
                        uid, ItemAction.CONTAINER_WITHDRAWAL, itemId, count,
                        playerName, session.containerKey().location(), playerName, session.containerKey().location()));
            }
            ledger.updateCount(uid, Math.max(0, after));
        }

        ContainerAuditEvent event = new ContainerAuditEvent(
                session.openedAt(),
                System.currentTimeMillis(),
                session.playerName(),
                session.sessionKey().player().toString(),
                session.containerKey().dimension(),
                session.containerKey().type(),
                session.containerKey().x(),
                session.containerKey().y(),
                session.containerKey().z(),
                added,
                removed);
        synchronized (recentEvents) {
            recentEvents.addLast(event);
            while (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.removeFirst();
        }
        append(event);
    }

    private Map<String, StackSnapshot> snapshot(Container container, ContainerKey key) {
        Map<String, StackSnapshot> out = new HashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            UUID uid = identityManager.ensureIdentity(stack, "CONTAINER_OBSERVE", null, null);
            String uidString = uid.toString();
            String itemId = identityManager.itemId(stack);
            StackSnapshot previous = out.get(uidString);
            int total = (previous == null ? 0 : previous.count()) + stack.getCount();
            out.put(uidString, new StackSnapshot(itemId, total));
            ledger.setOwnerLocation(uidString, "container", key.location());
        }
        return out;
    }

    private void append(ContainerAuditEvent event) {
        Path path = logPath(event.key());
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(event) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[AdminTools] Container audit write failed: " + e.getMessage());
        }
    }

    private List<ContainerAuditEvent> readEvents(ContainerKey key) {
        Path path = logPath(key);
        if (!Files.exists(path)) return List.of();
        List<ContainerAuditEvent> events = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(path)) {
                if (!line.isBlank()) {
                    ContainerAuditEvent event = gson.fromJson(line, ContainerAuditEvent.class);
                    if (event != null) events.add(event);
                }
            }
        } catch (Exception e) {
            System.err.println("[AdminTools] Container audit read failed: " + e.getMessage());
        }
        return events;
    }

    private Path logPath(ContainerKey key) {
        return LOG_ROOT.resolve(safe(key.dimension()))
                .resolve(safe(key.type()) + "_" + key.x() + "_" + key.y() + "_" + key.z() + ".jsonl");
    }

    private static String safe(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record SessionKey(UUID player, String containerId) {}

    private record StackSnapshot(String itemId, int count) {}

    private record Session(
            SessionKey sessionKey,
            ContainerKey containerKey,
            Container container,
            String playerName,
            Map<String, StackSnapshot> baseline,
            long openedAt) {

        Map<String, Integer> baselineCounts() {
            Map<String, Integer> counts = new HashMap<>();
            for (Map.Entry<String, StackSnapshot> entry : baseline.entrySet()) {
                counts.put(entry.getKey(), entry.getValue().count());
            }
            return counts;
        }

        Map<String, String> baselineItems() {
            Map<String, String> items = new HashMap<>();
            for (Map.Entry<String, StackSnapshot> entry : baseline.entrySet()) {
                items.put(entry.getKey(), entry.getValue().itemId());
            }
            return items;
        }
    }
}

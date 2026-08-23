package com.echubbuck.admintools.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ItemLedger {
    private static final Path CONFIG_PATH = Path.of("config", "admintools", "item_ledger.json");
    private static final Path EVENT_LOG = Path.of("logs", "admintools", "item_ledger.jsonl");
    private static final Path DUPLICATE_LOG = Path.of("logs", "admintools", "item_duplicates.jsonl");

    private static final int MAX_EVENTS = 2000;
    private static final int DEFAULT_MAX_ENTRIES = 5000;
    /** Statuses of entries that are safe to evict first when over the cap. */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "REMOVED", "MERGED", "DESTROYED", "CONSUMED", "UNDELIVERED");

    private final Path configPath;
    private final Path eventLog;
    private final Path duplicateLog;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    /** Compact Gson for line-oriented logs; pretty output would break the JSONL format. */
    private final Gson jsonlGson = new Gson();
    private final Map<String, ItemLedgerEntry> entries = new ConcurrentHashMap<>();
    private final Deque<ItemMovementEvent> recentEvents = new ArrayDeque<>();
    private final List<ItemMovementEvent> duplicateAlerts = new ArrayList<>();
    private volatile boolean dirty = false;
    private volatile int maxEntries = DEFAULT_MAX_ENTRIES;

    private BufferedWriter eventWriter;
    private BufferedWriter duplicateWriter;

    public ItemLedger() {
        this(CONFIG_PATH, EVENT_LOG, DUPLICATE_LOG);
    }

    /** Test hook: redirect persistence to custom paths. */
    public ItemLedger(Path configPath, Path eventLog, Path duplicateLog) {
        this.configPath = configPath;
        this.eventLog = eventLog;
        this.duplicateLog = duplicateLog;
        try {
            Files.createDirectories(this.eventLog.getParent());
            Files.createDirectories(this.duplicateLog.getParent());
        } catch (IOException e) {
            System.err.println("[AdminTools] Failed to create ledger dirs: " + e.getMessage());
        }
        load();
    }

    // --- Identity registration ---

    public void registerIdentity(ItemIdentity identity, String itemId, int count, String owner, String location) {
        entries.put(identity.uidString(), new ItemLedgerEntry(identity, itemId, count, owner, location));
        dirty = true;
    }

    public ItemLedgerEntry entry(String uid) {
        return entries.get(uid);
    }

    public boolean has(String uid) {
        return entries.containsKey(uid);
    }

    public void updateCount(String uid, int count) {
        ItemLedgerEntry e = entries.get(uid);
        if (e != null) {
            e.count = count;
            e.lastSeen = System.currentTimeMillis();
            dirty = true;
        }
    }

    public void setOwnerLocation(String uid, String owner, String location) {
        ItemLedgerEntry e = entries.get(uid);
        if (e != null) {
            e.currentOwner = owner;
            e.currentLocation = location;
            e.lastSeen = System.currentTimeMillis();
            dirty = true;
        }
    }

    public void setStatus(String uid, String status) {
        ItemLedgerEntry e = entries.get(uid);
        if (e != null) {
            e.status = status;
            e.lastSeen = System.currentTimeMillis();
            dirty = true;
        }
    }

    public void addParent(String uid, String parentUid) {
        ItemLedgerEntry e = entries.get(uid);
        if (e != null && !e.parents.contains(parentUid)) {
            e.parents.add(parentUid);
            dirty = true;
        }
    }

    public void setCreatorName(String uid, String creatorName) {
        ItemLedgerEntry e = entries.get(uid);
        if (e != null) {
            e.creatorName = creatorName;
            dirty = true;
        }
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(100, maxEntries);
    }

    // --- Events ---

    public void recordEvent(ItemMovementEvent evt) {
        synchronized (recentEvents) {
            recentEvents.addLast(evt);
            if (recentEvents.size() > MAX_EVENTS) recentEvents.removeFirst();
        }
        appendJsonLine(eventLog, evt);
    }

    public void recordDuplicate(ItemMovementEvent evt) {
        synchronized (duplicateAlerts) {
            duplicateAlerts.add(evt);
            if (duplicateAlerts.size() > 500) duplicateAlerts.remove(0);
        }
        appendJsonLine(duplicateLog, evt);
    }

    // --- Queries ---

    public List<ItemLedgerEntry> allEntries() {
        return new ArrayList<>(entries.values());
    }

    public List<ItemMovementEvent> recent(int n) {
        synchronized (recentEvents) {
            int size = recentEvents.size();
            List<ItemMovementEvent> out = new ArrayList<>(Math.min(n, size));
            var it = recentEvents.descendingIterator();
            for (int i = 0; i < n && it.hasNext(); i++) out.add(it.next());
            return out;
        }
    }

    public List<ItemMovementEvent> eventsByOwner(String owner) {
        return filterEvents(e -> (e.to() != null && e.to().equals(owner)) || owner.equals(e.from()));
    }

    public List<ItemMovementEvent> eventsByItem(String itemId) {
        return filterEvents(e -> itemId.equals(e.itemId()));
    }

    public List<ItemMovementEvent> eventsByUid(String uid) {
        return filterEvents(e -> uid.equals(e.uid()));
    }

    /** Reads one newest-first page from the durable JSONL event history. */
    public EventPage eventPageByUid(String uid, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int retain = safePage * safePageSize;
        Deque<ItemMovementEvent> matching = new ArrayDeque<>(retain);
        int total = 0;
        if (Files.exists(eventLog)) {
            try (BufferedReader reader = Files.newBufferedReader(eventLog)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        ItemMovementEvent event = jsonlGson.fromJson(line, ItemMovementEvent.class);
                        if (event == null || !uid.equals(event.uid())) continue;
                        total++;
                        matching.addLast(event);
                        if (matching.size() > retain) matching.removeFirst();
                    } catch (Exception ignored) {
                        // Preserve readable history even if one line was truncated by a crash.
                    }
                }
            } catch (IOException e) {
                System.err.println("[AdminTools] Failed to read item history: " + e.getMessage());
            }
        } else {
            for (ItemMovementEvent event : eventsByUid(uid)) {
                total++;
                matching.addLast(event);
                if (matching.size() > retain) matching.removeFirst();
            }
        }

        List<ItemMovementEvent> retained = new ArrayList<>(matching);
        int end = retained.size() - (safePage - 1) * safePageSize;
        if (end <= 0) return new EventPage(List.of(), total);
        int start = Math.max(0, end - safePageSize);
        List<ItemMovementEvent> newestFirst = new ArrayList<>(end - start);
        for (int i = end - 1; i >= start; i--) newestFirst.add(retained.get(i));
        return new EventPage(newestFirst, total);
    }

    public int duplicateAlertCount(String uid) {
        if (!Files.exists(duplicateLog)) {
            int count = 0;
            for (ItemMovementEvent event : duplicateAlerts()) if (uid.equals(event.uid())) count++;
            return count;
        }
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(duplicateLog)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    ItemMovementEvent event = jsonlGson.fromJson(line, ItemMovementEvent.class);
                    if (event != null && uid.equals(event.uid())) count++;
                } catch (Exception ignored) {
                    // Continue past a malformed final line.
                }
            }
        } catch (IOException e) {
            System.err.println("[AdminTools] Failed to read duplicate history: " + e.getMessage());
        }
        return count;
    }

    public record EventPage(List<ItemMovementEvent> events, int total) {}

    public List<ItemMovementEvent> duplicateAlerts() {
        synchronized (duplicateAlerts) {
            return new ArrayList<>(duplicateAlerts);
        }
    }

    private List<ItemMovementEvent> filterEvents(java.util.function.Predicate<ItemMovementEvent> pred) {
        synchronized (recentEvents) {
            List<ItemMovementEvent> out = new ArrayList<>();
            for (ItemMovementEvent e : recentEvents) {
                if (pred.test(e)) out.add(e);
            }
            return out;
        }
    }

    // --- Persistence ---

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        pruneIfNeeded();
        dirty = false;
        try {
            Files.createDirectories(configPath.getParent());
            Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(entries));
            try {
                Files.move(temporary, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[AdminTools] Failed to save item ledger: " + e.getMessage());
            dirty = true;
        }
    }

    /** Releases the append-log file handles; call on server stop. */
    public void close() {
        try {
            synchronized (this) {
                if (eventWriter != null) eventWriter.close();
                if (duplicateWriter != null) duplicateWriter.close();
                eventWriter = null;
                duplicateWriter = null;
            }
        } catch (IOException e) {
            System.err.println("[AdminTools] Failed to close ledger logs: " + e.getMessage());
        }
    }

    /**
     * Caps the ledger at {@code maxEntries} so memory and the snapshot file stay
     * bounded when possible. Only terminal entries are safe to evict: deleting an
     * active entry would leave a tagged in-world stack permanently untraceable.
     */
    private void pruneIfNeeded() {
        int over = entries.size() - maxEntries;
        if (over <= 0) return;
        List<ItemLedgerEntry> all = new ArrayList<>(entries.values());
        List<ItemLedgerEntry> terminal = new ArrayList<>();
        for (ItemLedgerEntry e : all) {
            if (TERMINAL_STATUSES.contains(e.status)) terminal.add(e);
        }
        Comparator<ItemLedgerEntry> oldestFirst = Comparator.comparingLong(e -> e.lastSeen);
        terminal.sort(oldestFirst);
        int removed = 0;
        for (ItemLedgerEntry e : terminal) {
            if (removed >= over) break;
            entries.remove(e.uid);
            removed++;
        }
        if (removed > 0) {
            System.out.println("[AdminTools] Pruned " + removed + " ledger entries (cap " + maxEntries + ")");
        }
        if (entries.size() > maxEntries) {
            System.err.println("[AdminTools] Ledger has " + entries.size() + " active entries, above soft cap "
                    + maxEntries + "; active identities were preserved");
        }
    }

    private void load() {
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                Type type = new TypeToken<Map<String, ItemLedgerEntry>>() {}.getType();
                Map<String, ItemLedgerEntry> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    entries.clear();
                    entries.putAll(loaded);
                }
            }
        } catch (Exception e) {
            System.err.println("[AdminTools] Failed to load item ledger: " + e.getMessage());
        }
    }

    private void appendJsonLine(Path path, Object obj) {
        // One compact JSON object per line. The writer is kept open between
        // events to avoid re-opening the file (and re-creating directories)
        // on every single movement event.
        synchronized (this) {
            try {
                BufferedWriter w = path.equals(eventLog) ? eventWriter : duplicateWriter;
                if (w == null) {
                    Files.createDirectories(path.getParent());
                    w = Files.newBufferedWriter(path, java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                    if (path.equals(eventLog)) eventWriter = w; else duplicateWriter = w;
                }
                w.write(jsonlGson.toJson(obj));
                w.newLine();
                w.flush();
            } catch (IOException e) {
                System.err.println("[AdminTools] Ledger write failed: " + e.getMessage());
                try {
                    if (path.equals(eventLog)) eventWriter = null; else duplicateWriter = null;
                } catch (Exception ignored) {}
            }
        }
    }
}

package com.echubbuck.admintools.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dependency-free unit tests for the item identity / ledger logic.
 *
 * <p>Run with:
 * <pre>
 *   ./gradlew :version-26-2:compileTestJava
 *   java -cp "version-26-2/build/classes/java/main:version-26-2/build/classes/java/test:<gson-jar>" \
 *        com.echubbuck.admintools.common.ItemLogicTestRunner
 * </pre>
 */
public class ItemLogicTestRunner {

    public static void main(String[] args) throws Exception {
        ItemIdentityTest.run();
        ItemLedgerTest.run();
        PermissionManagerTest.run();
        ConfigManagerTest.run();
        System.out.println("[AdminTools] ALL UNIT TESTS PASSED");
    }

    // ---- assertions ----

    static void eq(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void isTrue(boolean cond, String label) {
        if (!cond) throw new AssertionError(label + ": expected true");
    }

    static void isNull(Object o, String label) {
        if (o != null) throw new AssertionError(label + ": expected null");
    }

    // ---- ItemIdentity ----

    static class ItemIdentityTest {
        static void run() {
            Set<UUID> ids = new HashSet<>();
            UUID creator = UUID.randomUUID();
            for (int i = 0; i < 1000; i++) {
                ids.add(ItemIdentity.create("TEST", creator).uid());
            }
            eq(1000, ids.size(), "unique ids");

            ItemIdentity id = ItemIdentity.create("ADMIN_GIVE", creator);
            eq("ADMIN_GIVE", id.creationSource(), "creationSource");
            eq(creator, id.creatorUuid(), "creatorUuid");
            isTrue(id.creationTime() > 0, "creationTime");

            ItemIdentity noCreator = ItemIdentity.create("BREAK", null);
            isNull(noCreator.creatorUuid(), "null creator allowed");

            UUID suppliedUid = UUID.randomUUID();
            ItemIdentity supplied = ItemIdentity.create(suppliedUid, "TEST", creator);
            eq(suppliedUid, supplied.uid(), "supplied uid preserved");
        }
    }

    // ---- ItemLedger ----

    static class ItemLedgerTest {
        static void run() throws Exception {
            Path tmp = Files.createTempDirectory("admintools-test");
            ItemLedger ledger = new ItemLedger(
                    tmp.resolve("item_ledger.json"),
                    tmp.resolve("item_ledger.jsonl"),
                    tmp.resolve("item_duplicates.jsonl"));

            // register + query
            ItemIdentity id = ItemIdentity.create("ADMIN_GIVE", UUID.randomUUID());
            ledger.registerIdentity(id, "minecraft:diamond", 64, "Alice", "player:Alice");
            ItemLedgerEntry entry = ledger.entry(id.uidString());
            eq("minecraft:diamond", entry.itemId, "entry.itemId");
            eq(64, entry.count, "entry.count");
            eq("Alice", entry.currentOwner, "entry.owner");
            eq("ADMIN_GIVE", entry.creationSource, "entry.source");

            // save / reload round-trip
            ledger.save();
            ItemLedger reloaded = new ItemLedger(
                    tmp.resolve("item_ledger.json"),
                    tmp.resolve("item_ledger.jsonl"),
                    tmp.resolve("item_duplicates.jsonl"));
            ItemLedgerEntry r = reloaded.entry(id.uidString());
            eq("minecraft:diamond", r.itemId, "reloaded.itemId");
            eq("Alice", r.currentOwner, "reloaded.owner");

            // update + lineage
            ledger.updateCount(id.uidString(), 40);
            ledger.setOwnerLocation(id.uidString(), "Alice", "player:Alice");
            ledger.addParent(id.uidString(), "parent-uid");
            ledger.setStatus(id.uidString(), "ACTIVE");
            ItemLedgerEntry e2 = ledger.entry(id.uidString());
            eq(40, e2.count, "updated count");
            isTrue(e2.parents.contains("parent-uid"), "parent added");

            // events + queries
            String uid2 = UUID.randomUUID().toString();
            ledger.recordEvent(ItemMovementEvent.of(uid2, ItemAction.PICKUP, "minecraft:iron_ingot", 1, "Bob", "ground", "Bob", "player:Bob"));
            ledger.recordEvent(ItemMovementEvent.of(uid2, ItemAction.TRANSFER, "minecraft:iron_ingot", 1, "Bob", "Bob", "Carol", "player:Carol"));
            ledger.recordDuplicate(ItemMovementEvent.of(uid2, ItemAction.DUPLICATE_DETECTED, "minecraft:iron_ingot", 2, null, null, null, "Bob slot 3, Carol slot 7"));
            eq(2, ledger.eventsByUid(uid2).size(), "events by uid");
            eq(2, ledger.recent(10).size(), "recent events");
            eq(1, ledger.eventsByOwner("Carol").size(), "events by owner");
            eq(2, ledger.eventsByItem("minecraft:iron_ingot").size(), "events by item");
            eq(1, ledger.duplicateAlerts().size(), "duplicate alerts");

            String pagedUid = UUID.randomUUID().toString();
            for (int i = 1; i <= 15; i++) {
                ledger.recordEvent(ItemMovementEvent.of(pagedUid, ItemAction.UNKNOWN,
                        "minecraft:stone", i, "Alice", "a", "b", "player:Alice"));
            }
            ItemLedger.EventPage firstPage = ledger.eventPageByUid(pagedUid, 1, 10);
            eq(15, firstPage.total(), "persistent history total");
            eq(10, firstPage.events().size(), "persistent first page size");
            eq(15, firstPage.events().getFirst().count(), "persistent history newest first");
            ItemLedger.EventPage secondPage = ledger.eventPageByUid(pagedUid, 2, 10);
            eq(5, secondPage.events().size(), "persistent second page size");
            eq(5, secondPage.events().getFirst().count(), "persistent second page newest");
            eq(1, ledger.duplicateAlertCount(uid2), "persistent duplicate count");

            // missing entry
            isNull(ledger.entry(UUID.randomUUID().toString()), "missing entry is null");
            isTrue(ledger.allEntries().size() >= 1, "allEntries non-empty");

            // The configured cap is soft for active identities, which must remain traceable.
            ItemLedger capped = new ItemLedger(
                    tmp.resolve("capped.json"),
                    tmp.resolve("capped-events.jsonl"),
                    tmp.resolve("capped-duplicates.jsonl"));
            capped.setMaxEntries(100);
            String terminalUid = null;
            for (int i = 0; i < 101; i++) {
                ItemIdentity active = ItemIdentity.create("TEST", null);
                capped.registerIdentity(active, "minecraft:stone", 1, "Alice", "player:Alice");
                if (i == 0) terminalUid = active.uidString();
            }
            capped.save();
            eq(101, capped.allEntries().size(), "active identities survive soft cap");
            capped.setStatus(terminalUid, "REMOVED");
            capped.save();
            eq(100, capped.allEntries().size(), "terminal identity pruned first");
            isNull(capped.entry(terminalUid), "terminal identity evicted");

            ledger.close();
            reloaded.close();
            capped.close();
        }
    }

    // ---- Per-player permissions ----

    static class PermissionManagerTest {
        static void run() throws Exception {
            Path tmp = Files.createTempDirectory("admintools-permissions-test");
            Path permissions = tmp.resolve("permissions.json");
            Path legacy = tmp.resolve("roles.json");
            UUID player = UUID.randomUUID();

            PermissionManager manager = new PermissionManager(permissions, legacy);
            isTrue(manager.grant(player, PermissionNodes.INVSEE_EDIT), "permission grant changes state");
            isTrue(manager.hasPermission(player, PermissionNodes.INVSEE_EDIT), "exact permission");
            isTrue(!manager.hasPermission(player, PermissionNodes.ENDERSEE), "ungranted permission denied");

            manager.grant(player, PermissionNodes.ADMINITEM_ALL);
            isTrue(manager.hasPermission(player, PermissionNodes.ADMINITEM_GIVE), "branch wildcard");
            isTrue(manager.hasPermission(player, PermissionNodes.ADMINITEM_REMOVE), "branch wildcard remove");

            PermissionManager reloaded = new PermissionManager(permissions, legacy);
            isTrue(reloaded.hasPermission(player, PermissionNodes.INVSEE_EDIT), "permission persistence");
            isTrue(reloaded.remove(player, PermissionNodes.INVSEE_EDIT), "permission removal changes state");
            isTrue(!reloaded.hasPermission(player, PermissionNodes.INVSEE_EDIT), "removed permission denied");
            reloaded.grant(player, PermissionNodes.ALL);
            isTrue(reloaded.hasPermission(player, PermissionNodes.ENDERSEE), "global wildcard");
        }
    }

    // ---- Configuration reload ----

    static class ConfigManagerTest {
        static void run() throws Exception {
            Path tmp = Files.createTempDirectory("admintools-config-test");
            Path path = tmp.resolve("config.json");
            ConfigManager manager = new ConfigManager(path);
            isTrue(manager.getBoolean("enable_inventory_viewer", false), "default config created");

            Files.writeString(path, "{\"enable_inventory_viewer\":false}");
            isTrue(manager.load(), "valid config reload");
            isTrue(!manager.getBoolean("enable_inventory_viewer", true), "reloaded config applied");
            eq(5000, manager.getInt("ledger_max_entries", 0), "missing defaults merged");

            Files.writeString(path, "{not valid json");
            isTrue(!manager.load(), "invalid config rejected");
            isTrue(!manager.getBoolean("enable_inventory_viewer", true), "invalid reload preserves settings");
        }
    }
}

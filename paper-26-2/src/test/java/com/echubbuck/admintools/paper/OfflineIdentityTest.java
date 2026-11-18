package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ItemAction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class OfflineIdentityTest {
    @TempDir Path dir;
    @Test void logoutKeepsIdentityAndOnlineItemsCannotStealIt() {
        try (var f = new TrackerFixture(dir)) {
            var alice = f.player("Alice");
            alice.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 5));
            alice.getEnderChest().setItem(2, PaperTestSupport.item(Material.DIAMOND_SWORD, 1));
            f.tracker.scanNow();
            var original = f.ledger.allEntries().stream().filter(e -> e.itemId.equals("minecraft:diamond")).findFirst().orElseThrow();
            int events = f.ledger.recent(100).size();
            f.online.remove(alice); f.tracker.scanNow();
            assertEquals(events, f.ledger.recent(100).size());
            assertTrue(original.currentLocation.contains(alice.getUniqueId().toString()));
            var bob = f.player("Bob");
            bob.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 5)); f.tracker.scanNow();
            var bobEntry = f.ledger.allEntries().stream().filter(e -> "Bob".equals(e.currentOwner)).findFirst().orElseThrow();
            assertNotEquals(original.uid, bobEntry.uid);
            f.online.add(alice); f.tracker.scanNow();
            assertEquals(3, f.ledger.allEntries().size());
            assertEquals("Alice", f.ledger.entry(original.uid).currentOwner);
            assertTrue(f.ledger.recent(100).stream().noneMatch(e -> e.action() == ItemAction.DROP));
        }
    }
    @Test void restartBeforeLoginPreservesInventoryAndEnderObservations() {
        Player alice;
        java.util.Set<String> originalUids;
        try (var f = new TrackerFixture(dir)) {
            alice = f.player("Alice");
            alice.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 5));
            alice.getEnderChest().setItem(2, PaperTestSupport.item(Material.DIAMOND_SWORD, 1));
            f.tracker.scanNow(); f.ledger.save();
            originalUids = f.ledger.allEntries().stream().map(e -> e.uid).collect(java.util.stream.Collectors.toSet());
        }
        try (var f = new TrackerFixture(dir)) {
            f.tracker.scanNow(); f.tracker.scanNow();
            assertTrue(f.ledger.recent(100).isEmpty());
            f.online.add(alice); f.tracker.scanNow();
            assertTrue(f.ledger.recent(100).isEmpty());
            assertEquals(originalUids, f.ledger.allEntries().stream().map(e -> e.uid).collect(java.util.stream.Collectors.toSet()));
        }
    }
}

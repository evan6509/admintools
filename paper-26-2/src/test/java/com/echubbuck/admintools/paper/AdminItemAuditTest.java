package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ItemAction;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AdminItemAuditTest {
    @TempDir Path dir;
    @Test void partialOperationsKeepIdentityAndCorrectActorWithoutLeakingAttribution() {
        try (var f = new TrackerFixture(dir)) {
            var target = f.player("Target");
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 10));
            f.tracker.scanNow(); var uid = f.ledger.recent(1).getFirst().uid();
            f.tracker.noteAdminAction(target, "minecraft:diamond", ItemAction.ADMIN_GIVE, "Admin");
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 15)); f.tracker.scanNow();
            var give = f.ledger.recent(1).getFirst();
            assertEquals(ItemAction.ADMIN_GIVE, give.action()); assertEquals("Admin", give.actor());
            assertEquals(5, give.count()); assertEquals(uid, give.uid()); assertEquals("Target", give.to());
            f.tracker.noteAdminAction(target, "minecraft:diamond", ItemAction.ADMIN_REMOVE, "Admin");
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 12)); f.tracker.scanNow();
            var remove = f.ledger.recent(1).getFirst();
            assertEquals(ItemAction.ADMIN_REMOVE, remove.action()); assertEquals(3, remove.count());
            assertEquals("Admin", remove.actor()); assertEquals("removed", remove.to());
            assertEquals("ACTIVE", f.ledger.entry(uid).status); assertEquals(12, f.ledger.entry(uid).count);
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 11)); f.tracker.scanNow();
            assertEquals(ItemAction.UNKNOWN, f.ledger.recent(1).getFirst().action());
            assertEquals("Target", f.ledger.recent(1).getFirst().actor());
            f.tracker.noteAdminAction(target, "minecraft:diamond", ItemAction.ADMIN_REMOVE, "Admin");
            target.getInventory().setItem(0, null); f.tracker.scanNow();
            assertEquals(ItemAction.ADMIN_REMOVE, f.ledger.recent(1).getFirst().action());
            assertEquals(11, f.ledger.recent(1).getFirst().count()); assertEquals("REMOVED", f.ledger.entry(uid).status);
        }
    }
    @Test void zeroDeliveryConsumesPendingAction() {
        try (var f = new TrackerFixture(dir)) {
            var target = f.player("Target"); f.tracker.scanNow();
            f.tracker.noteAdminAction(target, "minecraft:diamond", ItemAction.ADMIN_GIVE, "Admin");
            f.tracker.scanNow();
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 1)); f.tracker.scanNow();
            assertEquals(ItemAction.UNKNOWN, f.ledger.recent(1).getFirst().action());
        }
    }
}

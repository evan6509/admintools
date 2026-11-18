package com.echubbuck.admintools.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ViewerTransactionTest {
    // Isolate transactions from the separately reviewed session replacement defect.
    private void open(ViewerFixture f, Player admin, Player target, boolean edit) {
        when(admin.openInventory(any(Inventory.class))).thenAnswer(call -> {
            f.views.get(admin).set(f.view(admin, call.getArgument(0)));
            return admin.getOpenInventory();
        });
        var cursor = new AtomicReference<ItemStack>();
        when(admin.getItemOnCursor()).thenAnswer(call -> cursor.get());
        doAnswer(call -> { cursor.set(PaperTestSupport.copy(call.getArgument(0))); return null; })
                .when(admin).setItemOnCursor(nullable(ItemStack.class));
        f.viewer.openInventory(admin, target, edit);
        var view = admin.getOpenInventory();
        when(view.convertSlot(anyInt())).thenAnswer(call -> {
            int raw = call.getArgument(0); int size = view.getTopInventory().getSize();
            return raw < size ? raw : raw - size < 27 ? raw - size + 9 : raw - size - 27;
        });
        when(view.getItem(anyInt())).thenAnswer(call -> {
            int raw = call.getArgument(0);
            return raw < view.getTopInventory().getSize() ? view.getTopInventory().getItem(raw)
                    : admin.getInventory().getItem(view.convertSlot(raw));
        });
    }
    private InventoryClickEvent click(ViewerFixture f, Player admin, int raw, ClickType type, InventoryAction action) {
        var event = new InventoryClickEvent(admin.getOpenInventory(), InventoryType.SlotType.CONTAINER, raw, type, action);
        f.viewer.onClick(event); assertTrue(event.isCancelled()); return event;
    }
    private int count(ItemStack item) { return item == null ? 0 : item.getAmount(); }

    @Test void untouchedClosePreservesDropsAcquisitionsAndUnrelatedSlots() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 5));
            open(f, admin, target, true);
            target.getInventory().setItem(0, null);
            target.getInventory().setItem(10, PaperTestSupport.item(Material.EMERALD, 7));
            f.viewer.onClose(new InventoryCloseEvent(admin.getOpenInventory()));
            assertNull(target.getInventory().getItem(0));
            assertEquals(7, count(target.getInventory().getItem(10)));
        }
    }

    @Test void staleAndConcurrentViewersOnlyTakeLiveItems() {
        try (var f = new ViewerFixture()) {
            var first = f.player("First"); var second = f.player("Second"); var target = f.player("Target");
            target.getInventory().setItem(5, PaperTestSupport.item(Material.DIAMOND, 10));
            open(f, first, target, true); open(f, second, target, true);
            target.getInventory().setItem(5, PaperTestSupport.item(Material.DIAMOND, 4));
            click(f, first, 5, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
            click(f, second, 5, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            assertEquals(2, count(first.getItemOnCursor()));
            assertEquals(2, count(second.getItemOnCursor()));
            assertNull(target.getInventory().getItem(5));
        }
    }

    @Test void shiftHotbarAndCursorPlacementConserveItems() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 5));
            open(f, admin, target, true);
            click(f, admin, 0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
            assertNull(target.getInventory().getItem(0));
            assertEquals(5, count(admin.getInventory().getItem(0)));
            var hotbar = new InventoryClickEvent(admin.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                    8, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
            f.viewer.onClick(hotbar);
            assertTrue(hotbar.isCancelled());
            assertEquals(5, count(target.getInventory().getItem(8)));
            assertNull(admin.getInventory().getItem(0));
            admin.setItemOnCursor(PaperTestSupport.item(Material.DIAMOND, 3));
            click(f, admin, 8, ClickType.RIGHT, InventoryAction.PLACE_ONE);
            assertEquals(6, count(target.getInventory().getItem(8)));
            assertEquals(2, count(admin.getItemOnCursor()));
        }
    }

    private void queueDrag(ViewerFixture f, Player admin, Map<Integer, ItemStack> changes) {
        ItemStack old = PaperTestSupport.item(Material.DIAMOND, 4);
        // Faithfully model Paper: new cursor during the event; old cursor restored after DENY.
        var event = new InventoryDragEvent(admin.getOpenInventory(), null, old, false, changes);
        admin.setItemOnCursor(null);
        f.viewer.onDrag(event);
        assertTrue(event.isCancelled());
        assertNull(admin.getItemOnCursor());
        admin.setItemOnCursor(old);
    }
    private void runQueued(ViewerFixture f) {
        var tasks = java.util.List.copyOf(f.queued); f.queued.clear(); tasks.forEach(Runnable::run);
    }

    @Test void dragCommitsOnlyAfterPaperRestoresCursorAndConservesTopAndBottomItems() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            open(f, admin, target, true);
            queueDrag(f, admin, Map.of(2, PaperTestSupport.item(Material.DIAMOND, 2),
                    36, PaperTestSupport.item(Material.DIAMOND, 2)));
            assertNull(target.getInventory().getItem(2));
            assertEquals(4, count(admin.getItemOnCursor()));
            runQueued(f);
            assertEquals(2, count(target.getInventory().getItem(2)));
            assertEquals(2, count(admin.getInventory().getItem(9)));
            assertNull(admin.getItemOnCursor());
        }
    }

    @Test void dragRejectsStaleSlotsAtomicallyBeforeAndAfterScheduling() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            open(f, admin, target, true);
            var changes = Map.of(2, PaperTestSupport.item(Material.DIAMOND, 2),
                    3, PaperTestSupport.item(Material.DIAMOND, 2));
            target.getInventory().setItem(3, PaperTestSupport.item(Material.EMERALD, 1));
            queueDrag(f, admin, changes); runQueued(f);
            assertNull(target.getInventory().getItem(2)); assertEquals(4, count(admin.getItemOnCursor()));
            target.getInventory().setItem(3, null); f.tick();
            queueDrag(f, admin, changes);
            target.getInventory().setItem(3, PaperTestSupport.item(Material.EMERALD, 1)); runQueued(f);
            assertNull(target.getInventory().getItem(2)); assertEquals(4, count(admin.getItemOnCursor()));
            assertEquals(Material.EMERALD, target.getInventory().getItem(3).getType());
        }
    }

    @Test void deferredDragAbortsAfterCursorChangeCloseReplacementOrTargetDeparture() {
        for (String change : new String[]{"cursor", "close", "replace", "offline"}) {
            try (var f = new ViewerFixture()) {
                var admin = f.player("Admin"); var target = f.player("Target");
                open(f, admin, target, true);
                queueDrag(f, admin, Map.of(2, PaperTestSupport.item(Material.DIAMOND, 4)));
                switch (change) {
                    case "cursor" -> admin.setItemOnCursor(PaperTestSupport.item(Material.EMERALD, 1));
                    case "close" -> admin.closeInventory();
                    case "replace" -> f.viewer.openEnderChest(admin, target);
                    case "offline" -> when(target.isOnline()).thenReturn(false);
                }
                runQueued(f);
                assertNull(target.getInventory().getItem(2), change);
                assertEquals(change.equals("cursor") ? 1 : 4, count(admin.getItemOnCursor()), change);
            }
        }
    }

    @Test void readOnlyRefreshSelfTargetAndUnsupportedActionsAreSafe() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            open(f, admin, target, false);
            target.getInventory().setItem(0, PaperTestSupport.item(Material.DIAMOND, 3)); f.tick();
            assertEquals(3, count(admin.getOpenInventory().getTopInventory().getItem(0)));
            for (InventoryAction action : InventoryAction.values()) click(f, admin, 0, ClickType.LEFT, action);
            assertEquals(3, count(target.getInventory().getItem(0)));
            var beforeSelf = admin.getOpenInventory();
            f.viewer.openInventory(admin, admin, true);
            assertSame(beforeSelf, admin.getOpenInventory());
            open(f, admin, target, true);
            for (var action : new InventoryAction[]{InventoryAction.COLLECT_TO_CURSOR, InventoryAction.CLONE_STACK,
                    InventoryAction.DROP_ALL_SLOT, InventoryAction.PICKUP_FROM_BUNDLE})
                click(f, admin, 0, ClickType.LEFT, action);
            assertEquals(3, count(target.getInventory().getItem(0)));
            assertNull(admin.getItemOnCursor());
            clearInvocations(admin);
            when(target.isOnline()).thenReturn(false); f.tick();
            verify(admin).closeInventory();
        }
    }
}

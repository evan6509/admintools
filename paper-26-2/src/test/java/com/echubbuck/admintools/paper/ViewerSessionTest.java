package com.echubbuck.admintools.paper;

import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewerSessionTest {
    private static InventoryClickEvent click(org.bukkit.entity.Player admin) {
        return new InventoryClickEvent(admin.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test void normalAndReplacedViewsStayProtected() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            f.viewer.openInventory(admin, target, false);
            var first = admin.getOpenInventory();
            var initial = click(admin); f.viewer.onClick(initial); assertTrue(initial.isCancelled());
            f.viewer.openEnderChest(admin, target);
            f.viewer.onClose(new InventoryCloseEvent(first));
            var replacement = click(admin); f.viewer.onClick(replacement); assertTrue(replacement.isCancelled());
            var drag = new InventoryDragEvent(admin.getOpenInventory(), null, null, false, java.util.Map.of());
            f.viewer.onDrag(drag); assertTrue(drag.isCancelled());
        }
    }

    @Test void oldQueuedWorkCannotWriteAfterReplacement() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            f.viewer.openInventory(admin, target, true);
            f.viewer.onClick(click(admin));
            f.viewer.openEnderChest(admin, target);
            clearInvocations(target.getInventory());
            f.queued.forEach(Runnable::run);
            verify(target.getInventory(), never()).setStorageContents(any());
        }
    }

    @Test void canceledOpenAndShutdownLeaveNoProtectedCopiesOpen() {
        try (var f = new ViewerFixture()) {
            var admin = f.player("Admin"); var target = f.player("Target");
            f.cancelOpen = true;
            f.viewer.openInventory(admin, target, false);
            var normal = click(admin); f.viewer.onClick(normal); assertFalse(normal.isCancelled());
            f.cancelOpen = false;
            f.viewer.openInventory(admin, target, false);
            var protectedView = admin.getOpenInventory();
            f.viewer.close();
            assertNotSame(protectedView, admin.getOpenInventory());
        }
    }
}

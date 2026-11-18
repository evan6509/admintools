package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ConfigManager;
import com.echubbuck.admintools.common.PermissionNodes;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EditPermissionTest {
    @Test void configuredEditingRequiresAnEditGrant() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var plugin = mock(PaperAdminToolsPlugin.class);
            var permissions = mock(PaperPermissionManager.class);
            var config = mock(ConfigManager.class);
            var viewer = mock(InventoryViewer.class);
            var admin = PaperTestSupport.player("Admin"); var target = PaperTestSupport.player("Target");
            when(plugin.permissions()).thenReturn(permissions); when(plugin.config()).thenReturn(config);
            when(plugin.inventoryViewer()).thenReturn(viewer);
            when(config.getBoolean(anyString(), anyBoolean())).thenReturn(true);
            when(permissions.canUse(admin, PermissionNodes.INVSEE)).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayerExact("Target")).thenReturn(target);
            var command = mock(Command.class); when(command.getName()).thenReturn("invsee");
            var router = new AdminCommandRouter(plugin);
            router.onCommand(admin, command, "invsee", new String[]{"Target"});
            verify(viewer).openInventory(admin, target, false);
            clearInvocations(viewer);
            router.onCommand(admin, command, "invsee", new String[]{"Target", "edit"});
            verifyNoInteractions(viewer);
            when(permissions.canUse(admin, PermissionNodes.INVSEE_EDIT)).thenReturn(true);
            when(permissions.canUse(admin, PermissionNodes.INVSEE)).thenReturn(false);
            router.onCommand(admin, command, "invsee", new String[]{"Target"});
            verify(viewer).openInventory(admin, target, true);
            when(config.getBoolean("invsee_edit_mode", false)).thenReturn(false);
            router.onCommand(admin, command, "invsee", new String[]{"Target", "edit"});
            verify(viewer, times(2)).openInventory(admin, target, true);
        }
    }
}

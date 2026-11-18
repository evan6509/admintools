package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.PermissionNodes;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ContainerTraceAccessTest {
    @Test void authorizationPrecedesEveryContainerLookup() {
        var plugin = mock(PaperAdminToolsPlugin.class);
        var permissions = mock(PaperPermissionManager.class);
        var auditor = mock(PaperContainerAuditor.class);
        when(plugin.permissions()).thenReturn(permissions); when(plugin.containerAuditor()).thenReturn(auditor);
        var player = PaperTestSupport.player("Player"); var commands = new PaperAuditCommands(plugin);
        String[] args = {"1", "2", "3"};
        commands.execute(player, "containertrace", args);
        verifyNoInteractions(auditor);
        when(permissions.canUse(player, PermissionNodes.CONTAINERTRACE)).thenReturn(true);
        commands.execute(player, "containertrace", args);
        verify(auditor).trace(player, args);
    }
}

package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ItemLedger;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryPageInputTest {
    @Test void invalidPagesAreRejectedBeforeLedgerAccess() {
        var plugin = mock(PaperAdminToolsPlugin.class);
        var permissions = mock(PaperPermissionManager.class); var ledger = mock(ItemLedger.class);
        when(plugin.permissions()).thenReturn(permissions); when(plugin.ledger()).thenReturn(ledger);
        when(permissions.canUse(any(), anyString())).thenReturn(true);
        var sender = PaperTestSupport.player("Admin"); var commands = new PaperAuditCommands(plugin);
        for (String page : new String[]{"0", "-1", "101", "10000000", "2147483647"})
            commands.execute(sender, "itemtrace", new String[]{UUID.randomUUID().toString(), page});
        verifyNoInteractions(ledger);
    }
}

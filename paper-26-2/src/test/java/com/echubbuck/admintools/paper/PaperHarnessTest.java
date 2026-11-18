package com.echubbuck.admintools.paper;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaperHarnessTest {
    @Test void apiDoublesKeepCopiesIndependent() {
        var item = PaperTestSupport.item(Material.DIAMOND, 3);
        var copy = item.clone();
        copy.setAmount(1);
        assertEquals(3, item.getAmount());
        assertEquals(1, copy.getAmount());
        try (var fixture = new ViewerFixture()) {
            assertTrue(fixture.player("Admin").isOnline());
        }
    }
}

package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ItemLedger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class TrackerFixture implements AutoCloseable {
    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final Plugin plugin = mock(Plugin.class);
    final List<Player> online = new ArrayList<>();
    final ItemLedger ledger;
    final PaperItemTracker tracker;
    TrackerFixture(Path dir) {
        when(plugin.getName()).thenReturn("AdminTools");
        when(plugin.namespace()).thenReturn("admintools");
        var scheduler = mock(BukkitScheduler.class); var task = mock(BukkitTask.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> online);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call ->
                online.stream().filter(p -> p.getUniqueId().equals(call.getArgument(0))).findFirst().orElse(null));
        ledger = new ItemLedger(dir.resolve("ledger.json"), dir.resolve("events.jsonl"), dir.resolve("duplicates.jsonl"));
        tracker = new PaperItemTracker(plugin, ledger, dir.resolve("state.json"));
    }
    Player player(String name) {
        var p = PaperTestSupport.player(name); online.add(p); return p;
    }
    @Override public void close() { tracker.close(); ledger.close(); bukkit.close(); }
}

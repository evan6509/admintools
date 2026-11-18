package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ActionLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class ViewerFixture implements AutoCloseable {
    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final Plugin plugin = mock(Plugin.class);
    final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    final BukkitTask task = mock(BukkitTask.class);
    final List<Runnable> queued = new ArrayList<>();
    final List<Runnable> timers = new ArrayList<>();
    final Map<UUID, Player> players = new HashMap<>();
    final Map<Player, AtomicReference<InventoryView>> views = new HashMap<>();
    final InventoryViewer viewer;
    boolean cancelOpen;

    ViewerFixture() {
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> players.values());
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> players.get(call.getArgument(0)));
        bukkit.when(() -> Bukkit.createInventory(nullable(InventoryHolder.class), anyInt(), any(Component.class)))
                .thenAnswer(call -> PaperTestSupport.inventory(Inventory.class, call.getArgument(1)));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            queued.add(call.getArgument(1)); return task;
        });
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            timers.add(call.getArgument(1)); return task;
        });
        viewer = new InventoryViewer(plugin, mock(ActionLogger.class));
    }

    Player player(String name) {
        Player player = PaperTestSupport.player(name);
        players.put(player.getUniqueId(), player);
        AtomicReference<InventoryView> current = new AtomicReference<>(view(player,
                PaperTestSupport.inventory(Inventory.class, 27)));
        views.put(player, current);
        when(player.getOpenInventory()).thenAnswer(call -> current.get());
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            viewer.onClose(new InventoryCloseEvent(current.get()));
            if (cancelOpen) return null;
            InventoryView next = view(player, call.getArgument(0));
            current.set(next);
            return next;
        });
        doAnswer(call -> {
            viewer.onClose(new InventoryCloseEvent(current.get()));
            current.set(view(player, PaperTestSupport.inventory(Inventory.class, 27)));
            return null;
        }).when(player).closeInventory();
        return player;
    }

    InventoryView view(Player player, Inventory top) {
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(top);
        Inventory bottom = player.getInventory();
        when(view.getBottomInventory()).thenReturn(bottom);
        return view;
    }

    void tick() { List.copyOf(timers).forEach(Runnable::run); }
    @Override public void close() { bukkit.close(); }
}

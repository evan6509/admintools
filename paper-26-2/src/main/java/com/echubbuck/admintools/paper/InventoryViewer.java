package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ActionLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Vanilla chest views used for online player inventory and ender-chest inspection. */
public final class InventoryViewer implements Listener {
    private final Plugin plugin;
    private final ActionLogger logger;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public InventoryViewer(Plugin plugin, ActionLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void openInventory(Player admin, Player target, boolean editable) {
        Inventory view = Bukkit.createInventory(null, 36,
                Component.text("Inventory: " + target.getName() + (editable ? " [EDIT]" : "")));
        ItemStack[] storage = target.getInventory().getStorageContents();
        for (int slot = 0; slot < Math.min(storage.length, view.getSize()); slot++) {
            view.setItem(slot, cloneOrNull(storage[slot]));
        }
        sessions.put(admin.getUniqueId(), new Session(target.getUniqueId(), view, editable, false));
        admin.openInventory(view);
        logger.log(admin.getName(), editable ? "INVSEE_EDIT_OPEN" : "INVSEE_OPEN", target.getName(), "success");
    }

    public void openEnderChest(Player admin, Player target) {
        Inventory view = Bukkit.createInventory(null, 27, Component.text("Ender Chest: " + target.getName()));
        ItemStack[] contents = target.getEnderChest().getContents();
        for (int slot = 0; slot < Math.min(contents.length, view.getSize()); slot++) {
            view.setItem(slot, cloneOrNull(contents[slot]));
        }
        sessions.put(admin.getUniqueId(), new Session(target.getUniqueId(), view, false, true));
        admin.openInventory(view);
        logger.log(admin.getName(), "ENDERSEE_OPEN", target.getName(), "success");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Session session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || !session.inventory().equals(event.getView().getTopInventory())) return;
        if (!session.editable()) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> sync(session, (Player) event.getWhoClicked()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Session session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || !session.inventory().equals(event.getView().getTopInventory())) return;
        if (!session.editable()) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> sync(session, (Player) event.getWhoClicked()));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null && session.editable()) sync(session, (Player) event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(viewer.getUniqueId());
            if (session != null && session.target().equals(event.getPlayer().getUniqueId())) viewer.closeInventory();
        }
    }

    private void sync(Session session, Player admin) {
        Player target = Bukkit.getPlayer(session.target());
        if (target == null || !target.isOnline()) {
            admin.closeInventory();
            return;
        }
        target.getInventory().setStorageContents(cloneContents(session.inventory().getContents(), 36));
        logger.log(admin.getName(), "INVSEE_EDIT", target.getName(), "synchronized");
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private static ItemStack[] cloneContents(ItemStack[] contents, int size) {
        ItemStack[] copy = new ItemStack[size];
        for (int slot = 0; slot < Math.min(contents.length, size); slot++) copy[slot] = cloneOrNull(contents[slot]);
        return copy;
    }

    private record Session(UUID target, Inventory inventory, boolean editable, boolean enderChest) {}
}

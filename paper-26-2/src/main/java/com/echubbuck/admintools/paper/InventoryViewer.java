package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ActionLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.Objects;
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
    private final BukkitTask refreshTask;

    public InventoryViewer(Plugin plugin, ActionLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshViews, 1L, 1L);
    }

    public void openInventory(Player admin, Player target, boolean editable) {
        if (admin.getUniqueId().equals(target.getUniqueId())) {
            admin.sendMessage(Component.text("You cannot invsee yourself."));
            return;
        }
        Inventory view = Bukkit.createInventory(null, 36,
                Component.text("Inventory: " + target.getName() + (editable ? " [EDIT]" : "")));
        ItemStack[] storage = target.getInventory().getStorageContents();
        for (int slot = 0; slot < Math.min(storage.length, view.getSize()); slot++) {
            view.setItem(slot, cloneOrNull(storage[slot]));
        }
        open(admin, new Session(target.getUniqueId(), view, editable, false));
        logger.log(admin.getName(), editable ? "INVSEE_EDIT_OPEN" : "INVSEE_OPEN", target.getName(), "success");
    }

    public void openEnderChest(Player admin, Player target) {
        Inventory view = Bukkit.createInventory(null, 27, Component.text("Ender Chest: " + target.getName()));
        ItemStack[] contents = target.getEnderChest().getContents();
        for (int slot = 0; slot < Math.min(contents.length, view.getSize()); slot++) {
            view.setItem(slot, cloneOrNull(contents[slot]));
        }
        open(admin, new Session(target.getUniqueId(), view, false, true));
        logger.log(admin.getName(), "ENDERSEE_OPEN", target.getName(), "success");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Session session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || !session.inventory().equals(event.getView().getTopInventory())) return;
        // The top inventory contains display copies. Vanilla must never move them.
        event.setCancelled(true);
        Player admin = (Player) event.getWhoClicked();
        Player target = Bukkit.getPlayer(session.target());
        if (!session.editable() || !valid(target)) return;
        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < session.inventory().getSize();
        if (raw < 0 || raw >= session.inventory().getSize() + 36) return;
        Inventory source = top ? target.getInventory() : admin.getInventory();
        int slot = top ? raw : event.getSlot();
        if (slot < 0 || slot >= 36) return;
        ItemStack current = cloneOrNull(source.getItem(slot));
        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            Inventory destination = top ? admin.getInventory() : target.getInventory();
            source.setItem(slot, transfer(current, destination));
        } else if (action == InventoryAction.HOTBAR_SWAP) {
            int hotbar = event.getClick() == ClickType.SWAP_OFFHAND ? 40 : event.getHotbarButton();
            if (hotbar < 0 || (hotbar > 8 && hotbar != 40)) return;
            ItemStack other = cloneOrNull(admin.getInventory().getItem(hotbar));
            if (other != null && other.getAmount() > other.getMaxStackSize()) return;
            source.setItem(slot, other);
            admin.getInventory().setItem(hotbar, current);
        } else if (switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                    PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> true;
            default -> false;
        }) {
            ItemStack cursor = cloneOrNull(admin.getItemOnCursor());
            boolean right = event.isRightClick();
            if (cursor == null) {
                if (current == null) return;
                int take = right ? (current.getAmount() + 1) / 2 : current.getAmount();
                admin.setItemOnCursor(withAmount(current, take));
                source.setItem(slot, withAmount(current, current.getAmount() - take));
            } else if (current == null || current.isSimilar(cursor)) {
                int before = current == null ? 0 : current.getAmount();
                int put = Math.min(right ? 1 : cursor.getAmount(), Math.max(0,
                        Math.min(cursor.getMaxStackSize(), source.getMaxStackSize()) - before));
                source.setItem(slot, withAmount(cursor, before + put));
                admin.setItemOnCursor(withAmount(cursor, cursor.getAmount() - put));
            } else if (cursor.getAmount() <= Math.min(cursor.getMaxStackSize(), source.getMaxStackSize())) {
                source.setItem(slot, cursor);
                admin.setItemOnCursor(current);
            }
        } else {
            // Drop, creative clone, collect-to-cursor and bundle actions are deliberately disabled.
            return;
        }
        logger.log(admin.getName(), "INVSEE_EDIT", target.getName(), "live " + action + " slot=" + slot);
        refreshViews();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Session session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || !session.inventory().equals(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        Player admin = (Player) event.getWhoClicked();
        Player target = Bukkit.getPlayer(session.target());
        if (!session.editable() || !valid(target)) return;
        ItemStack oldCursor = cloneOrNull(event.getOldCursor());
        ItemStack newCursor = cloneOrNull(event.getCursor());
        if (oldCursor == null || !Objects.equals(oldCursor, cloneOrNull(admin.getItemOnCursor()))
                || (newCursor != null && !oldCursor.isSimilar(newCursor))) return;
        int placed = 0;
        var writes = new ArrayList<Runnable>();
        for (var entry : event.getNewItems().entrySet()) {
            int raw = entry.getKey();
            int topSize = session.inventory().getSize();
            if (raw < 0 || raw >= topSize + 36) return;
            Inventory inventory = raw < topSize ? target.getInventory() : admin.getInventory();
            int slot = raw < topSize ? raw : event.getView().convertSlot(raw);
            if (slot < 0 || slot >= 36) return;
            ItemStack before = cloneOrNull(inventory.getItem(slot));
            ItemStack after = cloneOrNull(entry.getValue());
            // Drag proposals were computed from the displayed inventory. Reject stale proposals atomically.
            if (!Objects.equals(before, cloneOrNull(event.getView().getItem(raw))) || after == null
                    || !oldCursor.isSimilar(after) || (before != null && !before.isSimilar(after))
                    || after.getAmount() > Math.min(after.getMaxStackSize(), inventory.getMaxStackSize())) {
                refreshViews();
                return;
            }
            int delta = after.getAmount() - (before == null ? 0 : before.getAmount());
            if (delta <= 0) return;
            placed += delta;
            writes.add(() -> inventory.setItem(slot, after));
        }
        if (placed != oldCursor.getAmount() - (newCursor == null ? 0 : newCursor.getAmount())) return;
        writes.forEach(Runnable::run);
        admin.setItemOnCursor(newCursor);
        logger.log(admin.getName(), "INVSEE_EDIT", target.getName(), "live drag count=" + placed);
        refreshViews();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.inventory().equals(event.getView().getTopInventory())) return;
        sessions.remove(event.getPlayer().getUniqueId(), session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(viewer.getUniqueId());
            if (session != null && session.target().equals(event.getPlayer().getUniqueId())) viewer.closeInventory();
        }
    }

    private static boolean valid(Player target) {
        return target != null && target.isOnline() && !target.isDead();
    }

    private void refreshViews() {
        for (var entry : java.util.List.copyOf(sessions.entrySet())) {
            Player admin = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(entry.getValue().target());
            if (admin == null) { sessions.remove(entry.getKey()); continue; }
            if (!valid(target)) { admin.closeInventory(); continue; }
            Session session = entry.getValue();
            Inventory inventory = session.enderChest() ? target.getEnderChest() : target.getInventory();
            for (int slot = 0; slot < session.inventory().getSize(); slot++) {
                session.inventory().setItem(slot, cloneOrNull(inventory.getItem(slot)));
            }
        }
    }

    public void stopRefreshing() { refreshTask.cancel(); }

    private static ItemStack transfer(ItemStack item, Inventory destination) {
        if (item == null) return null;
        int remaining = item.getAmount();
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                ItemStack existing = cloneOrNull(destination.getItem(slot));
                if (pass == 0 ? existing == null || !existing.isSimilar(item) : existing != null) continue;
                int before = existing == null ? 0 : existing.getAmount();
                int put = Math.min(remaining, Math.max(0,
                        Math.min(item.getMaxStackSize(), destination.getMaxStackSize()) - before));
                destination.setItem(slot, withAmount(item, before + put));
                remaining -= put;
            }
        }
        return withAmount(item, remaining);
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (amount <= 0) return null;
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    private void open(Player admin, Session session) {
        // Closing the prior view may synchronously fire InventoryCloseEvent.
        admin.closeInventory();
        sessions.put(admin.getUniqueId(), session);
        admin.openInventory(session.inventory());
        if (!session.inventory().equals(admin.getOpenInventory().getTopInventory())) {
            sessions.remove(admin.getUniqueId(), session);
        }
    }

    /** Close protected copies before the plugin's listeners are removed. */
    public void close() {
        for (UUID uuid : java.util.List.copyOf(sessions.keySet())) {
            Player viewer = Bukkit.getPlayer(uuid);
            Session session = sessions.get(uuid);
            if (viewer != null && session != null
                    && session.inventory().equals(viewer.getOpenInventory().getTopInventory())) viewer.closeInventory();
        }
        sessions.clear();
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private record Session(UUID target, Inventory inventory, boolean editable, boolean enderChest) {}
}

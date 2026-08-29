package com.echubbuck.admintools.paper;

import com.google.gson.Gson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared-session audit for chest, barrel, and shulker-box inventories. */
public final class PaperContainerAuditor implements Listener {
    private static final Path LOG_ROOT = Path.of("logs", "admintools", "containers");
    private static final SimpleDateFormat TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Gson gson = new Gson();
    private final Map<String, Session> sessions = new HashMap<>();
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        if (this.enabled && !enabled) close();
        this.enabled = enabled;
    }

    @EventHandler
    public synchronized void onOpen(InventoryOpenEvent event) {
        if (!enabled || !supported(event.getInventory())) return;
        ContainerAddress address = address(event.getInventory());
        if (address == null) return;
        HumanEntity player = event.getPlayer();
        Session session = sessions.get(address.id());
        if (session == null) {
            Map<UUID, String> participants = new LinkedHashMap<>();
            participants.put(player.getUniqueId(), player.getName());
            Set<UUID> active = new HashSet<>();
            active.add(player.getUniqueId());
            sessions.put(address.id(), new Session(address, event.getInventory(), participants, active,
                    snapshot(event.getInventory()), System.currentTimeMillis()));
        } else {
            session.participants().putIfAbsent(player.getUniqueId(), player.getName());
            session.active().add(player.getUniqueId());
        }
    }

    @EventHandler
    public synchronized void onClose(InventoryCloseEvent event) {
        if (!supported(event.getInventory())) return;
        ContainerAddress address = address(event.getInventory());
        if (address == null) return;
        Session session = sessions.get(address.id());
        if (session == null) return;
        session.active().remove(event.getPlayer().getUniqueId());
        if (session.active().isEmpty()) finish(sessions.remove(address.id()));
    }

    public synchronized void close() {
        List<Session> open = new ArrayList<>(sessions.values());
        sessions.clear();
        open.forEach(this::finish);
    }

    public boolean trace(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) return false;
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(args[0]);
            y = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            return fail(sender, "Coordinates must be integers.");
        }
        World world = args.length == 4 ? Bukkit.getWorld(args[3])
                : sender instanceof org.bukkit.entity.Player player ? player.getWorld() : null;
        if (world == null) return fail(sender, "Specify a valid world when using this command from console.");
        Inventory inventory = inventoryAt(world, x, y, z);
        if (inventory == null || !supported(inventory)) return fail(sender, "No supported container exists there.");
        ContainerAddress address = address(inventory);
        List<AuditEvent> events = read(address, 10);
        sender.sendMessage(Component.text("--- Container Trace: " + address.display() + " ---", NamedTextColor.GOLD));
        if (events.isEmpty()) {
            sender.sendMessage(Component.text("No audit sessions have been recorded.", NamedTextColor.GRAY));
            return true;
        }
        for (AuditEvent event : events.reversed()) {
            sender.sendMessage(Component.text(TIME.format(new Date(event.closedAt())) + " " + event.players()
                    + " added=" + event.added() + " removed=" + event.removed(), NamedTextColor.GRAY));
        }
        return true;
    }

    private void finish(Session session) {
        if (session == null) return;
        Map<String, Integer> after = snapshot(session.inventory());
        Map<String, Integer> added = new LinkedHashMap<>();
        Map<String, Integer> removed = new LinkedHashMap<>();
        Set<String> items = new HashSet<>(session.baseline().keySet());
        items.addAll(after.keySet());
        for (String item : items) {
            int delta = after.getOrDefault(item, 0) - session.baseline().getOrDefault(item, 0);
            if (delta > 0) added.put(item, delta);
            if (delta < 0) removed.put(item, -delta);
        }
        AuditEvent event = new AuditEvent(session.openedAt(), System.currentTimeMillis(),
                String.join(",", session.participants().values()), session.address().world(),
                session.address().type(), session.address().x(), session.address().y(), session.address().z(),
                added, removed);
        Path path = logPath(session.address());
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(event) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exception) {
            System.err.println("[AdminTools] Container audit write failed: " + exception.getMessage());
        }
    }

    private List<AuditEvent> read(ContainerAddress address, int limit) {
        Path path = logPath(address);
        if (!Files.exists(path)) return List.of();
        Deque<AuditEvent> events = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    AuditEvent event = gson.fromJson(line, AuditEvent.class);
                    if (event != null) {
                        events.addLast(event);
                        if (events.size() > limit) events.removeFirst();
                    }
                } catch (Exception ignored) {
                    // Preserve earlier valid lines after a truncated final write.
                }
            }
        } catch (Exception exception) {
            System.err.println("[AdminTools] Container audit read failed: " + exception.getMessage());
        }
        return new ArrayList<>(events);
    }

    private static Map<String, Integer> snapshot(Inventory inventory) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            totals.merge(stack.getType().getKey().toString(), stack.getAmount(), Integer::sum);
        }
        return totals;
    }

    private static boolean supported(Inventory inventory) {
        InventoryType type = inventory.getType();
        return type == InventoryType.CHEST || type == InventoryType.BARREL || type == InventoryType.SHULKER_BOX;
    }

    private static Inventory inventoryAt(World world, int x, int y, int z) {
        if (world.getBlockAt(x, y, z).getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
            return holder.getInventory();
        }
        return null;
    }

    private static ContainerAddress address(Inventory inventory) {
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) return null;
        return new ContainerAddress(location.getWorld().getName(), inventory.getType().name().toLowerCase(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static Path logPath(ContainerAddress address) {
        return LOG_ROOT.resolve(safe(address.world()))
                .resolve(safe(address.type()) + "_" + address.x() + "_" + address.y() + "_" + address.z() + ".jsonl");
    }

    private static String safe(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static boolean fail(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        return true;
    }

    private record ContainerAddress(String world, String type, int x, int y, int z) {
        String id() { return world + "|" + type + "|" + x + "," + y + "," + z; }
        String display() { return world + " " + x + " " + y + " " + z; }
    }

    private record Session(ContainerAddress address, Inventory inventory, Map<UUID, String> participants,
                           Set<UUID> active, Map<String, Integer> baseline, long openedAt) {}

    private record AuditEvent(long openedAt, long closedAt, String players, String world, String containerType,
                              int x, int y, int z, Map<String, Integer> added, Map<String, Integer> removed) {}
}

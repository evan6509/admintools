package com.echubbuck.admintools.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** UUID-keyed grants shared conceptually with the Fabric implementation. */
public final class PaperPermissionManager {
    private static final Path CONFIG_PATH = Path.of("config", "admintools", "permissions.json");
    private static final Path LEGACY_PATH = Path.of("config", "admintools", "roles.json");

    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PaperPermissionManager() {
        load();
    }

    public synchronized boolean load() {
        Path source = Files.exists(CONFIG_PATH) ? CONFIG_PATH : LEGACY_PATH;
        try {
            Map<UUID, Set<String>> loaded = new HashMap<>();
            if (Files.exists(source)) {
                JsonObject root = gson.fromJson(Files.readString(source), JsonObject.class);
                if (root != null && root.has("players")) {
                    for (var entry : root.getAsJsonObject("players").entrySet()) {
                        Set<String> permissions = ConcurrentHashMap.newKeySet();
                        entry.getValue().getAsJsonArray().forEach(value -> permissions.add(value.getAsString()));
                        loaded.put(UUID.fromString(entry.getKey()), permissions);
                    }
                }
            }
            playerPermissions.clear();
            playerPermissions.putAll(loaded);
            if (!Files.exists(CONFIG_PATH)) save();
            return true;
        } catch (Exception exception) {
            System.err.println("[AdminTools] Failed to load permissions config: " + exception.getMessage());
            return false;
        }
    }

    public boolean canUse(CommandSender sender, String node) {
        if (!(sender instanceof Player player)) return true;
        return player.isOp() || player.hasPermission(node) || hasGrant(player.getUniqueId(), node);
    }

    public boolean hasGrant(UUID playerUuid, String node) {
        Set<String> permissions = playerPermissions.get(playerUuid);
        if (permissions == null) return false;
        if (permissions.contains("*") || permissions.contains("admintools.*") || permissions.contains(node)) {
            return true;
        }
        int separator = node.lastIndexOf('.');
        while (separator > "admintools".length()) {
            if (permissions.contains(node.substring(0, separator) + ".*")) return true;
            separator = node.lastIndexOf('.', separator - 1);
        }
        return false;
    }

    public synchronized boolean grant(UUID playerUuid, String node) {
        boolean changed = playerPermissions.computeIfAbsent(playerUuid, ignored -> ConcurrentHashMap.newKeySet())
                .add(node);
        if (changed) save();
        return changed;
    }

    public synchronized boolean remove(UUID playerUuid, String node) {
        Set<String> permissions = playerPermissions.get(playerUuid);
        if (permissions == null || !permissions.remove(node)) return false;
        if (permissions.isEmpty()) playerPermissions.remove(playerUuid);
        save();
        return true;
    }

    public Set<String> permissionsFor(UUID playerUuid) {
        Set<String> permissions = playerPermissions.get(playerUuid);
        return permissions == null ? Set.of() : new TreeSet<>(permissions);
    }

    private synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject root = new JsonObject();
            JsonObject players = new JsonObject();
            playerPermissions.forEach((uuid, permissions) -> {
                JsonArray values = new JsonArray();
                new TreeSet<>(permissions).forEach(values::add);
                players.add(uuid.toString(), values);
            });
            root.add("players", players);
            Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(root));
            try {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[AdminTools] Failed to save permissions config: " + exception.getMessage());
        }
    }
}

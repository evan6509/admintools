package com.echubbuck.admintools.common;

import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionManager {
    private static final Path DEFAULT_CONFIG_PATH = Path.of("config", "admintools", "permissions.json");
    private static final Path DEFAULT_LEGACY_CONFIG_PATH = Path.of("config", "admintools", "roles.json");

    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private final Path legacyConfigPath;

    public PermissionManager() {
        this(DEFAULT_CONFIG_PATH, DEFAULT_LEGACY_CONFIG_PATH);
    }

    /** Test/migration hook for redirecting permission persistence. */
    public PermissionManager(Path configPath, Path legacyConfigPath) {
        this.configPath = configPath;
        this.legacyConfigPath = legacyConfigPath;
        load();
    }

    public synchronized boolean load() {
        Path source = Files.exists(configPath) ? configPath : legacyConfigPath;
        try {
            Map<UUID, Set<String>> loaded = new HashMap<>();
            if (Files.exists(source)) {
                String json = Files.readString(source);
                JsonObject root = gson.fromJson(json, JsonObject.class);
                if (root != null && root.has("players")) {
                    JsonObject players = root.getAsJsonObject("players");
                    for (var entry : players.entrySet()) {
                        UUID uuid = UUID.fromString(entry.getKey());
                        Set<String> permissions = ConcurrentHashMap.newKeySet();
                        entry.getValue().getAsJsonArray().forEach(e -> permissions.add(e.getAsString()));
                        loaded.put(uuid, permissions);
                    }
                }
            }
            playerPermissions.clear();
            playerPermissions.putAll(loaded);
            if (!Files.exists(configPath)) save();
            return true;
        } catch (Exception e) {
            System.err.println("[AdminTools] Failed to load permissions config: " + e.getMessage());
            return false;
        }
    }

    public boolean hasPermission(UUID playerUuid, String node) {
        Set<String> perms = playerPermissions.get(playerUuid);
        if (perms == null) return false;
        if (perms.contains("*") || perms.contains("admintools.*") || perms.contains(node)) return true;
        int separator = node.lastIndexOf('.');
        while (separator > "admintools".length()) {
            if (perms.contains(node.substring(0, separator) + ".*")) return true;
            separator = node.lastIndexOf('.', separator - 1);
        }
        return false;
    }

    /** OP4 and non-player command sources bypass per-player grants. */
    public boolean canUse(CommandSourceStack source, String node) {
        if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;
        ServerPlayer player = source.getPlayer();
        return player != null && hasPermission(player.getUUID(), node);
    }

    public synchronized boolean grant(UUID playerUuid, String node) {
        boolean changed = playerPermissions.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(node);
        if (changed) save();
        return changed;
    }

    public synchronized boolean remove(UUID playerUuid, String node) {
        Set<String> perms = playerPermissions.get(playerUuid);
        if (perms != null && perms.remove(node)) {
            if (perms.isEmpty()) playerPermissions.remove(playerUuid);
            save();
            return true;
        }
        return false;
    }

    public Set<String> permissionsFor(UUID playerUuid) {
        Set<String> permissions = playerPermissions.get(playerUuid);
        return permissions == null ? Set.of() : new TreeSet<>(permissions);
    }

    private synchronized void save() {
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject root = new JsonObject();
            JsonObject playersJson = new JsonObject();
            playerPermissions.forEach((uuid, perms) -> {
                JsonArray arr = new JsonArray();
                new TreeSet<>(perms).forEach(arr::add);
                playersJson.add(uuid.toString(), arr);
            });
            root.add("players", playersJson);
            Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(root));
            try {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[AdminTools] Failed to save permissions config: " + e.getMessage());
        }
    }
}

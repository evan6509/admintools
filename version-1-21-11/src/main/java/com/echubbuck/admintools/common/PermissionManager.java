package com.echubbuck.admintools.common;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionManager {
    private static final Path CONFIG_PATH = Path.of("config", "admintools", "roles.json");

    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();
    private final Map<String, AdminRole> roles = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PermissionManager() {
        load();
    }

    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                JsonObject root = gson.fromJson(json, JsonObject.class);
                if (root != null) {
                    if (root.has("roles")) {
                        Type mapType = new TypeToken<Map<String, List<String>>>() {}.getType();
                        Map<String, List<String>> raw = gson.fromJson(root.get("roles"), mapType);
                        raw.forEach((name, perms) -> roles.put(name, new AdminRole(name, new HashSet<>(perms))));
                    }
                    if (root.has("players")) {
                        JsonObject players = root.getAsJsonObject("players");
                        for (var entry : players.entrySet()) {
                            UUID uuid = UUID.fromString(entry.getKey());
                            Set<String> perms = new HashSet<>();
                            entry.getValue().getAsJsonArray().forEach(e -> perms.add(e.getAsString()));
                            playerPermissions.put(uuid, perms);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AdminTools] Failed to load roles config: " + e.getMessage());
        }
    }

    public boolean hasPermission(UUID playerUuid, String node) {
        Set<String> perms = playerPermissions.get(playerUuid);
        return perms != null && (perms.contains("*") || perms.contains(node));
    }

    public void grant(UUID playerUuid, String node) {
        playerPermissions.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(node);
        save();
    }

    public void remove(UUID playerUuid, String node) {
        Set<String> perms = playerPermissions.get(playerUuid);
        if (perms != null) {
            perms.remove(node);
            if (perms.isEmpty()) playerPermissions.remove(playerUuid);
            save();
        }
    }

    public void assignRole(UUID playerUuid, String roleName) {
        AdminRole role = roles.get(roleName);
        if (role != null) {
            playerPermissions.put(playerUuid, new HashSet<>(role.permissions()));
            save();
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject root = new JsonObject();
            JsonObject rolesJson = new JsonObject();
            roles.forEach((name, role) -> {
                JsonArray arr = new JsonArray();
                role.permissions().forEach(arr::add);
                rolesJson.add(name, arr);
            });
            root.add("roles", rolesJson);
            JsonObject playersJson = new JsonObject();
            playerPermissions.forEach((uuid, perms) -> {
                JsonArray arr = new JsonArray();
                perms.forEach(arr::add);
                playersJson.add(uuid.toString(), arr);
            });
            root.add("players", playersJson);
            Files.writeString(CONFIG_PATH, gson.toJson(root));
        } catch (Exception e) {
            System.err.println("[AdminTools] Failed to save roles config: " + e.getMessage());
        }
    }
}

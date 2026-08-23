package com.echubbuck.admintools.common;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ConfigManager {
    private static final Path DEFAULT_CONFIG_PATH = Path.of("config", "admintools", "config.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private JsonObject config;

    public ConfigManager() {
        this(DEFAULT_CONFIG_PATH);
    }

    /** Test hook for redirecting configuration persistence. */
    public ConfigManager(Path configPath) {
        this.configPath = configPath;
        config = defaultConfig();
        load();
    }

    public boolean load() {
        try {
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                JsonObject loaded = gson.fromJson(content, JsonObject.class);
                if (loaded == null) throw new JsonParseException("configuration root must be an object");
                JsonObject merged = defaultConfig();
                loaded.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                config = merged;
            } else {
                save();
            }
            return true;
        } catch (Exception e) {
            System.err.println("[AdminTools] Failed to load config: " + e.getMessage());
            return false;
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(config));
            try {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[AdminTools] Failed to save config: " + e.getMessage());
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        try { return config.get(key).getAsBoolean(); } catch (Exception e) { return fallback; }
    }

    public int getInt(String key, int fallback) {
        try { return config.get(key).getAsInt(); } catch (Exception e) { return fallback; }
    }

    public double getDouble(String key, double fallback) {
        try { return config.get(key).getAsDouble(); } catch (Exception e) { return fallback; }
    }

    public String getString(String key, String fallback) {
        try { return config.get(key).getAsString(); } catch (Exception e) { return fallback; }
    }

    private JsonObject defaultConfig() {
        JsonObject obj = new JsonObject();
        obj.addProperty("enable_inventory_viewer", true);
        obj.addProperty("enable_ender_chest_viewer", true);
        obj.addProperty("log_actions_to_file", true);
        obj.addProperty("invsee_edit_mode", false);
        obj.addProperty("detect_creative_duplicates", false);
        obj.addProperty("ledger_max_entries", 5000);
        obj.addProperty("enable_container_audit", true);
        return obj;
    }
}

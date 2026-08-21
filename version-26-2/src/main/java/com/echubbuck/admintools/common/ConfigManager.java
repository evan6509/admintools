package com.echubbuck.admintools.common;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ConfigManager {
    private static final Path CONFIG_PATH = Path.of("config", "admintools", "config.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private JsonObject config;

    public ConfigManager() {
        config = defaultConfig();
        load();
    }

    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String content = Files.readString(CONFIG_PATH);
                config = gson.fromJson(content, JsonObject.class);
                if (config == null) config = defaultConfig();
            } else {
                save();
            }
        } catch (Exception e) {
            config = defaultConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, gson.toJson(config));
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
        obj.addProperty("enable_xray_audit", true);
        obj.addProperty("max_command_rate", 10);
        obj.addProperty("log_actions_to_file", true);
        obj.addProperty("invsee_edit_mode", false);
        obj.addProperty("detect_creative_duplicates", false);
        obj.addProperty("ledger_max_entries", 5000);
        return obj;
    }
}

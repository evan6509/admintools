package com.echubbuck.admintools.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActionLogger {
    private static final Path LOG_DIR = Path.of("logs", "admintools");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    /** Compact Gson for line-oriented JSON; pretty output would break line parsing. */
    private final Gson jsonGson = new Gson();
    private volatile boolean writeToFile = true;

    public ActionLogger() {
        try {
            Files.createDirectories(LOG_DIR);
        } catch (IOException e) {
            System.err.println("[AdminTools] Failed to create log directory: " + e.getMessage());
        }
    }

    public void log(String adminName, String action, String target, String result) {
        String timestamp = LocalDateTime.now().format(FMT);
        String line = String.format("[%s] %s | %s | %s | %s", timestamp, adminName, action, target, result);
        System.out.println(line);
        if (writeToFile) appendToFile("actions.log", line);
    }

    public void logJson(String adminName, String action, String target, String result) {
        var obj = new java.util.LinkedHashMap<String, String>();
        obj.put("timestamp", LocalDateTime.now().format(FMT));
        obj.put("admin", adminName);
        obj.put("action", action);
        obj.put("target", target);
        obj.put("result", result);
        if (writeToFile) appendToFile("actions.json", jsonGson.toJson(obj));
    }

    public void setWriteToFile(boolean writeToFile) {
        this.writeToFile = writeToFile;
    }

    private void appendToFile(String fileName, String line) {
        try {
            Files.writeString(LOG_DIR.resolve(fileName), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[AdminTools] Log write failed: " + e.getMessage());
        }
    }
}

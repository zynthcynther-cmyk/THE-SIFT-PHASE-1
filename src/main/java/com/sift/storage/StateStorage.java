package com.sift.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists plugin state (state.yml). Serialization to a string happens on the
 * caller's thread; the disk write runs on a single background thread and is
 * atomic (temp file + move). Never touches Bukkit world/entity APIs.
 */
public final class StateStorage {

    private final Path file;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TheSift-IO");
        t.setDaemon(true);
        return t;
    });

    public StateStorage(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("state.yml");
        this.logger = logger;
    }

    /** Blocking read; call during startup only. */
    public YamlConfiguration load() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (Files.exists(file)) {
            try {
                yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not read state.yml; backing it up and starting fresh", e);
                try {
                    Files.move(file, file.resolveSibling("state.yml.corrupt-" + System.currentTimeMillis()));
                } catch (IOException ignored) {
                    // best effort
                }
                return new YamlConfiguration();
            }
        }
        return yaml;
    }

    public CompletableFuture<Void> saveAsync(YamlConfiguration yaml) {
        final String data = yaml.saveToString();
        return CompletableFuture.runAsync(() -> write(data), io);
    }

    /** Synchronous save, used on shutdown. */
    public void saveNow(YamlConfiguration yaml) {
        write(yaml.saveToString());
    }

    private void write(String data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("state.yml.tmp");
            Files.writeString(tmp, data, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save state.yml", e);
        }
    }

    public void shutdown() {
        io.shutdown();
    }
}

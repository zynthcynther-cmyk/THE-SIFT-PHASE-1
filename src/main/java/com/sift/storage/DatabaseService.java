package com.sift.storage;

import com.sift.core.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite persistence for player progression and death-recovery records.
 * All JDBC access runs on one dedicated thread; callers get CompletableFutures and must hop
 * back to the main thread before touching Bukkit world/entity APIs.
 *
 * UNVERIFIED: relies on the SQLite JDBC driver being on Paper's classpath (it has historically
 * been bundled). If it is missing at runtime, declare org.xerial:sqlite-jdbc as a plugin library.
 */
public final class DatabaseService implements Service {

    private final Path file;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TheSift-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public DatabaseService(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("sift.db");
        this.logger = logger;
    }

    @Override
    public String name() {
        return "Database";
    }

    @Override
    public void start() {
        try {
            Files.createDirectories(file.getParent());
            // Startup-only blocking work: acceptable before the server is accepting players.
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS schema_info (
                            key TEXT PRIMARY KEY,
                            value TEXT NOT NULL)""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS players (
                            uuid TEXT PRIMARY KEY,
                            first_sift_visit INTEGER,
                            pack_version TEXT,
                            pack_status TEXT)""");
                // Death-recovery records (used by the Mini Rift system in a later phase).
                // claimed flips 0 -> 1 in the same transaction that hands the item out.
                st.execute("""
                        CREATE TABLE IF NOT EXISTS recovery_items (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            owner TEXT NOT NULL,
                            item_data BLOB NOT NULL,
                            created_at INTEGER NOT NULL,
                            claimed INTEGER NOT NULL DEFAULT 0)""");
                st.execute("INSERT OR IGNORE INTO schema_info(key, value) VALUES ('version', '1')");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Database initialisation failed; progression data will not persist", e);
            connection = null;
        }
    }

    @Override
    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing database", e);
            }
        }
    }

    public boolean available() {
        return connection != null;
    }

    /** Runs an INSERT/UPDATE/DELETE off the main thread. */
    public CompletableFuture<Integer> updateAsync(String sql, Object... params) {
        return submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                return ps.executeUpdate();
            }
        });
    }

    /** Runs arbitrary JDBC work on the database thread. */
    public <T> CompletableFuture<T> submit(Callable<T> work) {
        if (connection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database unavailable"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Database operation failed", e);
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }
}

package com.sift.dimension;

import com.sift.config.SiftConfig;
import com.sift.core.Service;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Installs the dimension datapack and resolves the Sift world by its namespaced key.
 * The world itself is created by the server from the datapack, not by this plugin.
 */
public final class DimensionService implements Service {

    private final Plugin plugin;
    private final SiftConfig config;
    private final DatapackInstaller installer;
    private final NamespacedKey key;

    public DimensionService(Plugin plugin, SiftConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.installer = new DatapackInstaller(plugin);
        NamespacedKey parsed = NamespacedKey.fromString(config.dimensionKey());
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid dimension.key: " + config.dimensionKey());
        }
        this.key = parsed;
    }

    @Override
    public String name() {
        return "Dimension";
    }

    @Override
    public void start() {
        if (config.installDatapack() && installer.install(config.levelName())) {
            plugin.getLogger().warning("The Sift datapack was installed or updated. "
                    + "Restart the server once so the dimension can load.");
        }
        // The server loads datapack dimensions during world startup. Check after startup completes.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (world() == null) {
                plugin.getLogger().warning("Sift world '" + key + "' is not loaded. "
                        + "If the datapack was just installed, restart the server.");
            } else {
                plugin.getLogger().info("Sift world found: " + key);
            }
        });
    }

    @Override
    public void stop() {
        // World saving is handled by the server.
    }

    public NamespacedKey key() {
        return key;
    }

    /** UNVERIFIED against 26.3 javadocs: relies on Bukkit#getWorld(NamespacedKey). */
    /** @return the Sift world, or null if it is not loaded. */
    public World world() {
        return Bukkit.getWorld(key);
    }
}

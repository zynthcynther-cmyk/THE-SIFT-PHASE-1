package com.sift.tides;

import com.sift.config.SiftConfig;
import com.sift.core.Service;
import com.sift.dimension.DimensionService;
import com.sift.storage.StateStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Drives the tide cycle. Runs once per second on the main thread (cheap counter math only)
 * and persists state periodically through {@link StateStorage}.
 */
public final class TideService implements Service {

    private final Plugin plugin;
    private final SiftConfig config;
    private final StateStorage storage;
    private final DimensionService dimension;
    private final YamlConfiguration state;

    private Tide current = Tide.FLOW;
    private int elapsedSeconds = 0;
    private boolean warned = false;
    private int secondsSinceSave = 0;
    private BukkitTask task;

    public TideService(Plugin plugin, SiftConfig config, StateStorage storage,
                       DimensionService dimension, YamlConfiguration state) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.dimension = dimension;
        this.state = state;
    }

    @Override
    public String name() {
        return "Tides";
    }

    @Override
    public void start() {
        try {
            current = Tide.valueOf(state.getString("tide.current", "FLOW"));
        } catch (IllegalArgumentException e) {
            current = Tide.FLOW;
        }
        elapsedSeconds = Math.max(0, state.getInt("tide.elapsed-seconds", 0));
        if (elapsedSeconds >= durationOf(current)) {
            elapsedSeconds = 0;
        }
        if (!config.tidesEnabled()) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        writeState();
        storage.saveNow(state);
    }

    public Tide current() {
        return current;
    }

    public int secondsRemaining() {
        return Math.max(0, durationOf(current) - elapsedSeconds);
    }

    /** Admin override; resets the timer for the new tide. */
    public void setTide(Tide tide) {
        change(tide);
    }

    private void tick() {
        elapsedSeconds++;
        int remaining = secondsRemaining();

        if (!warned && config.warningSeconds() > 0 && remaining <= config.warningSeconds()
                && current.next() == Tide.ENDURE) {
            warned = true;
            broadcast(Component.text("The Endure Tide is approaching...", NamedTextColor.DARK_RED));
        }
        if (remaining <= 0) {
            change(current.next());
        }
        if (++secondsSinceSave >= config.autosaveSeconds()) {
            secondsSinceSave = 0;
            writeState();
            storage.saveAsync(state);
        }
    }

    private void change(Tide next) {
        current = next;
        elapsedSeconds = 0;
        warned = false;
        NamedTextColor color = switch (next) {
            case FLOW -> NamedTextColor.AQUA;
            case THRIVE -> NamedTextColor.LIGHT_PURPLE;
            case ENDURE -> NamedTextColor.DARK_RED;
        };
        broadcast(Component.text("The tide shifts: " + next.name().charAt(0)
                + next.name().substring(1).toLowerCase() + " Tide", color));
    }

    private void broadcast(Component message) {
        World world = dimension.world();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    private int durationOf(Tide tide) {
        return switch (tide) {
            case FLOW -> config.flowSeconds();
            case THRIVE -> config.thriveSeconds();
            case ENDURE -> config.endureSeconds();
        };
    }

    private void writeState() {
        state.set("tide.current", current.name());
        state.set("tide.elapsed-seconds", elapsedSeconds);
    }
}

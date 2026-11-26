package com.sift.resourcepack;

import com.sift.config.SiftConfig;
import com.sift.core.Service;
import com.sift.storage.DatabaseService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the resource pack from a URL the admin hosts (this plugin does NOT host files).
 * Tracks per-player acceptance. The pack UUID is derived from the configured version, so bumping
 * resourcepack.version makes clients treat it as a new pack and re-download.
 *
 * UNVERIFIED against 26.3 javadocs: Player#setResourcePack(UUID, String, byte[], Component, boolean)
 * and PlayerResourcePackStatusEvent#getID()/getStatus().
 */
public final class ResourcePackService implements Service, Listener {

    private final Plugin plugin;
    private final SiftConfig config;
    private final DatabaseService database;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> statuses = new ConcurrentHashMap<>();

    private UUID packId;
    private byte[] sha1;
    private boolean active;

    public ResourcePackService(Plugin plugin, SiftConfig config, DatabaseService database) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
    }

    @Override
    public String name() {
        return "ResourcePack";
    }

    @Override
    public void start() {
        if (!config.packEnabled()) {
            return;
        }
        if (!config.packUrl().startsWith("https://") && !config.packUrl().startsWith("http://")) {
            plugin.getLogger().warning("resourcepack.enabled is true but resourcepack.url is not a valid URL; "
                    + "resource pack delivery is disabled.");
            return;
        }
        if (!config.packSha1().matches("[0-9a-f]{40}")) {
            plugin.getLogger().warning("resourcepack.sha1 must be 40 hex characters; "
                    + "resource pack delivery is disabled.");
            return;
        }
        sha1 = HexFormat.of().parseHex(config.packSha1());
        packId = UUID.nameUUIDFromBytes(("thesift:" + config.packVersion()).getBytes(StandardCharsets.UTF_8));
        active = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void stop() {
        active = false;
        statuses.clear();
    }

    public boolean active() {
        return active;
    }

    public PlayerResourcePackStatusEvent.Status statusOf(UUID player) {
        return statuses.get(player);
    }

    /** Sends the pack to a player; safe to call again (e.g. /sift resourcepack). */
    public boolean send(Player player) {
        if (!active) {
            return false;
        }
        player.setResourcePack(packId, config.packUrl(), sha1,
                Component.text(config.packPrompt()), config.packRequired());
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!packId.equals(event.getID())) {
            return; // another plugin's pack
        }
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        statuses.put(player.getUniqueId(), status);

        switch (status) {
            case SUCCESSFULLY_LOADED -> { /* nothing to say */ }
            case DECLINED -> player.sendMessage(Component.text(
                    "Without The Sift resource pack, custom blocks and creatures will look wrong. "
                            + "Use /sift resourcepack to try again.", NamedTextColor.YELLOW));
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> player.sendMessage(Component.text(
                    "The Sift resource pack failed to load (" + status + "). Tell an administrator.",
                    NamedTextColor.RED));
            default -> { /* ACCEPTED, DOWNLOADED: transitional */ }
        }

        if (database.available() && status != PlayerResourcePackStatusEvent.Status.ACCEPTED
                && status != PlayerResourcePackStatusEvent.Status.DOWNLOADED) {
            database.updateAsync("""
                    INSERT INTO players(uuid, pack_version, pack_status) VALUES (?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET pack_version = excluded.pack_version,
                                                    pack_status = excluded.pack_status""",
                    player.getUniqueId().toString(), config.packVersion(), status.name());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        statuses.remove(event.getPlayer().getUniqueId());
    }
}

package com.sift.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of config.yml values. */
public record SiftConfig(
        String dimensionKey,
        String levelName,
        boolean installDatapack,
        boolean tidesEnabled,
        int flowSeconds,
        int thriveSeconds,
        int endureSeconds,
        int warningSeconds,
        boolean allowSleepDuringEndure,
        int autosaveSeconds,
        boolean packEnabled,
        String packUrl,
        String packSha1,
        boolean packRequired,
        String packPrompt,
        String packVersion,
        boolean debug) {

    public static SiftConfig from(FileConfiguration c) {
        return new SiftConfig(
                c.getString("dimension.key", "sift:the_sift"),
                c.getString("dimension.level-name", "world"),
                c.getBoolean("dimension.install-datapack", true),
                c.getBoolean("tides.enabled", true),
                Math.max(10, c.getInt("tides.duration-seconds.flow", 900)),
                Math.max(10, c.getInt("tides.duration-seconds.thrive", 600)),
                Math.max(10, c.getInt("tides.duration-seconds.endure", 480)),
                Math.max(0, c.getInt("tides.warning-seconds", 30)),
                c.getBoolean("tides.allow-sleep-during-endure", true),
                Math.max(10, c.getInt("storage.autosave-seconds", 60)),
                c.getBoolean("resourcepack.enabled", false),
                c.getString("resourcepack.url", "").trim(),
                c.getString("resourcepack.sha1", "").trim().toLowerCase(),
                c.getBoolean("resourcepack.required", false),
                c.getString("resourcepack.prompt", "The Sift resource pack"),
                c.getString("resourcepack.version", "1"),
                c.getBoolean("debug", false));
    }
}

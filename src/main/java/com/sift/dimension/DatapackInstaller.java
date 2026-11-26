package com.sift.dimension;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;

/**
 * Installs the bundled datapack into &lt;level-name&gt;/datapacks/. A datapack that adds a dimension
 * only takes effect when the world loads, so a change here means the server must be restarted once.
 * Existing world data is never touched; an older installed copy is moved to a backup first.
 */
public final class DatapackInstaller {

    public static final String PACK_DIR = "the_sift_dimension";

    /** Resource paths inside the jar (relative to /datapack/) that make up the pack. */
    private static final List<String> FILES = List.of(
            "pack.mcmeta",
            "data/sift/dimension/the_sift.json",
            "data/sift/dimension_type/the_sift.json");

    private final Plugin plugin;

    public DatapackInstaller(Plugin plugin) {
        this.plugin = plugin;
    }

    /** @return true if files were written (restart required). */
    public boolean install(String levelName) {
        Path worldFolder = plugin.getServer().getWorldContainer().toPath().resolve(levelName);
        Path target = worldFolder.resolve("datapacks").resolve(PACK_DIR);
        try {
            boolean changed = false;
            for (String file : FILES) {
                byte[] bundled = read("datapack/" + file);
                Path out = target.resolve(file);
                if (Files.exists(out) && java.util.Arrays.equals(Files.readAllBytes(out), bundled)) {
                    continue;
                }
                if (Files.exists(out)) {
                    Path backup = out.resolveSibling(out.getFileName() + ".bak");
                    Files.copy(out, backup, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.createDirectories(out.getParent());
                Files.write(out, bundled);
                changed = true;
            }
            return changed;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Datapack installation failed", e);
            return false;
        }
    }

    private byte[] read(String resource) throws IOException {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                throw new IOException("Missing bundled resource: " + resource);
            }
            return in.readAllBytes();
        }
    }
}

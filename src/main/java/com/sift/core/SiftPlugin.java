package com.sift.core;

import com.sift.commands.SiftCommand;
import com.sift.config.SiftConfig;
import com.sift.dimension.DimensionService;
import com.sift.resourcepack.ResourcePackService;
import com.sift.storage.DatabaseService;
import com.sift.storage.StateStorage;
import com.sift.tides.TideService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SiftPlugin extends JavaPlugin {

    private ServiceRegistry services;
    private StateStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        SiftConfig config = SiftConfig.from(getConfig());

        storage = new StateStorage(getDataFolder().toPath(), getLogger());
        YamlConfiguration state = storage.load();

        services = new ServiceRegistry(getLogger());
        DatabaseService database = services.register(DatabaseService.class,
                new DatabaseService(getDataFolder().toPath(), getLogger()));
        DimensionService dimension = services.register(DimensionService.class,
                new DimensionService(this, config));
        services.register(ResourcePackService.class,
                new ResourcePackService(this, config, database));
        services.register(TideService.class,
                new TideService(this, config, storage, dimension, state));

        services.startAll();

        final ServiceRegistry registry = services;
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new SiftCommand(registry, config).build(),
                        "The Sift dimension commands"));
    }

    @Override
    public void onDisable() {
        if (services != null) {
            services.stopAll();
        }
        if (storage != null) {
            storage.shutdown();
        }
    }
}

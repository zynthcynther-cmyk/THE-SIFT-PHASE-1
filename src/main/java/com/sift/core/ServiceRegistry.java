package com.sift.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Simple service container: ordered start, reverse-ordered stop, typed lookup. */
public final class ServiceRegistry {

    private final Logger logger;
    private final Map<Class<? extends Service>, Service> services = new LinkedHashMap<>();

    public ServiceRegistry(Logger logger) {
        this.logger = logger;
    }

    public <T extends Service> T register(Class<T> type, T service) {
        if (services.putIfAbsent(type, service) != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
        return service;
    }

    public <T extends Service> T get(Class<T> type) {
        Service service = services.get(type);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return type.cast(service);
    }

    public void startAll() {
        for (Service service : services.values()) {
            logger.info("Starting service: " + service.name());
            service.start();
        }
    }

    public void stopAll() {
        List<Service> reversed = new ArrayList<>(services.values());
        java.util.Collections.reverse(reversed);
        for (Service service : reversed) {
            try {
                service.stop();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error stopping service " + service.name(), t);
            }
        }
        services.clear();
    }
}

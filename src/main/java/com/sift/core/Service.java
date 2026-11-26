package com.sift.core;

/** A lifecycle-managed subsystem of the plugin. */
public interface Service {

    /** Human-readable name used in logs. */
    String name();

    /** Called once, in registration order, after configuration is loaded. */
    void start();

    /** Called once on shutdown, in reverse registration order. Must not throw. */
    void stop();
}

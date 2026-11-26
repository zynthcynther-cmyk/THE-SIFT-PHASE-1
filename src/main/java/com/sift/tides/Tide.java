package com.sift.tides;

/**
 * The three tide states. Names (Flow/Thrive/Endure) follow the documented Sift tide concept;
 * the cycle order and durations are this plugin's own design and are configurable.
 */
public enum Tide {
    FLOW,
    THRIVE,
    ENDURE;

    public Tide next() {
        return values()[(ordinal() + 1) % values().length];
    }
}

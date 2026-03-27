package com.hades.client.platform;

import com.hades.client.event.EventBus;

/**
 * Interface for platform-specific adapters.
 * Each adapter bridges a specific MC platform's events → Hades events.
 * 
 * Only ONE adapter is active at a time (platform-specific takes priority).
 * If that adapter's hooks fail, PlatformManager can fall back to another.
 */
public interface PlatformAdapter {

    /**
     * Human-readable name for logging.
     */
    String getName();

    /**
     * Which platform this adapter supports.
     */
    ClientPlatform getPlatform();

    /**
     * Initialize the adapter and start hooking into the platform's event system.
     * The adapter should fire Hades events on the provided EventBus.
     * 
     * @param hadesEventBus The Hades EventBus to post events to
     * @return true if initialization succeeded
     */
    boolean initialize(EventBus hadesEventBus);

    /**
     * Check if this adapter is currently active and functioning.
     */
    boolean isActive();

    /**
     * Cleanly shut down the adapter, unhooking from platform events.
     */
    /**
     * Cleanly shut down the adapter, unhooking from platform events.
     */
    void shutdown();

    /**
     * Display the ClickGUI using the platform's native wrapping mechanism.
     */
    void displayClickGUI();

    /**
     * Close the ClickGUI via the platform's native wrapping mechanism.
     */
    void closeClickGUI();

    /**
     * Forces the platform to delegate scoreboard rendering to Vanilla.
     */
    void forceVanillaScoreboard(boolean enable);
}

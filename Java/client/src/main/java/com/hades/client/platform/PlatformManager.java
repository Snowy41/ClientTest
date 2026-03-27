package com.hades.client.platform;

import com.hades.client.event.EventBus;
import com.hades.client.platform.adapters.LabyModAdapter;
import com.hades.client.platform.adapters.VanillaAdapter;
import com.hades.client.util.HadesLogger;

/**
 * Manages platform detection and adapter lifecycle.
 * 
 * Strategy: detect the platform, try the platform-specific adapter first.
 * If it fails, fall back to VanillaAdapter.
 * Only ONE adapter is active at a time.
 */
public final class PlatformManager {
    private static final HadesLogger LOG = HadesLogger.get();

    private static ClientPlatform detectedPlatform;
    private static PlatformAdapter activeAdapter;

    private PlatformManager() {
    }

    /**
     * Detect the platform and create the appropriate adapter without starting listeners.
     */
    public static void prepare() {
        // 1. Detect platform
        detectedPlatform = PlatformDetector.detect();
        LOG.info("Platform: " + detectedPlatform.getDisplayName());

        // 2. Try platform-specific adapter first
        PlatformAdapter adapter = createAdapter(detectedPlatform);
        if (adapter != null && adapter.getPlatform() != ClientPlatform.VANILLA) {
            activeAdapter = adapter;
            LOG.info("Prepared adapter: " + adapter.getName());
        } else {
            activeAdapter = new VanillaAdapter();
            LOG.info("Prepared adapter: " + activeAdapter.getName() + " (fallback)");
        }
    }

    /**
     * Initialize the adapters and start their event listeners.
     * 
     * @param eventBus The Hades EventBus to bridge events to
     */
    public static void startListeners(EventBus eventBus) {
        if (!tryInitialize(activeAdapter, eventBus)) {
            LOG.error("Failed to initialize active adapter!");
            if (activeAdapter.getPlatform() != ClientPlatform.VANILLA) {
                activeAdapter = new VanillaAdapter();
                tryInitialize(activeAdapter, eventBus);
                LOG.info("Fell back to VanillaAdapter due to initialization failure.");
            }
        }
        
        // 3. Always initialize VanillaAdapter in the background for core events if active is not Vanilla
        if (activeAdapter.getPlatform() != ClientPlatform.VANILLA) {
            VanillaAdapter vanilla = new VanillaAdapter();
            if (tryInitialize(vanilla, eventBus)) {
                LOG.info("Background adapter running: " + vanilla.getName());
            }
        }
    }

    /**
     * Create the correct adapter for the detected platform.
     */
    private static PlatformAdapter createAdapter(ClientPlatform platform) {
        switch (platform) {
            case LABYMOD:
                return new LabyModAdapter();
            case FORGE:
                // TODO: ForgeAdapter when needed
                LOG.warn("Forge adapter not yet implemented, falling back to Vanilla");
                return new VanillaAdapter();
            case VANILLA:
                return new VanillaAdapter();
            default:
                return new VanillaAdapter();
        }
    }

    /**
     * Try to initialize an adapter, catching any errors.
     */
    private static boolean tryInitialize(PlatformAdapter adapter, EventBus eventBus) {
        try {
            return adapter.initialize(eventBus);
        } catch (Throwable t) {
            LOG.error("Adapter " + adapter.getName() + " failed to initialize", t);
            return false;
        }
    }

    /**
     * Shut down the active adapter.
     */
    public static void shutdown() {
        if (activeAdapter != null) {
            try {
                activeAdapter.shutdown();
                LOG.info("Adapter " + activeAdapter.getName() + " shut down");
            } catch (Exception e) {
                LOG.error("Error shutting down adapter", e);
            }
            activeAdapter = null;
        }
    }

    public static ClientPlatform getDetectedPlatform() {
        return detectedPlatform;
    }

    public static PlatformAdapter getActiveAdapter() {
        return activeAdapter;
    }
}

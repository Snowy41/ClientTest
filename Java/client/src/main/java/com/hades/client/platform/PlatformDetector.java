package com.hades.client.platform;

import com.hades.client.util.HadesLogger;

/**
 * Detects which Minecraft client platform is running.
 * Uses Class.forName() — works with both DLL injection and -javaagent.
 * 
 * Detection order: most-specific (LabyMod) → least-specific (Vanilla fallback).
 */
public final class PlatformDetector {
    private static final HadesLogger LOG = HadesLogger.get();

    private PlatformDetector() {
    }

    /**
     * Detect the current client platform.
     * Tries each platform's marker class in priority order.
     */
    public static ClientPlatform detect() {
        return detect(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Detect the current client platform using a specific classloader.
     */
    public static ClientPlatform detect(ClassLoader classLoader) {
        // Check platforms in order of specificity (most-specific first)
        for (ClientPlatform platform : ClientPlatform.values()) {
            if (platform.getMarkerClass() == null) {
                continue; // Skip VANILLA — it's the fallback
            }

            if (classExists(platform.getMarkerClass(), classLoader)) {
                LOG.info("Detected platform: " + platform.getDisplayName()
                        + " (found " + platform.getMarkerClass() + ")");
                return platform;
            }
        }

        LOG.info("Detected platform: Vanilla (no platform markers found)");
        return ClientPlatform.VANILLA;
    }

    /**
     * Check if a class exists without loading it eagerly.
     */
    private static boolean classExists(String className, ClassLoader cl) {
        try {
            Class.forName(className, false, cl);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            LOG.warn("Error checking for class " + className + ": " + e.getMessage());
            return false;
        }
    }
}

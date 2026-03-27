package com.hades.client.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Modular manager for Outline ESP.
 * Currently a stub — stencil-based outline ESP requires FBO modification
 * which is incompatible with LabyMod's rendering pipeline.
 * Box ESP (in RenderUtil) is used instead.
 */
public class OutlineESPManager {

    private static final Map<Object, Integer> outlineEntities = new HashMap<>();

    public static void clear() {
        outlineEntities.clear();
    }

    public static void registerEntity(Object entity, int color) {
        if (entity != null) {
            outlineEntities.put(entity, color);
        }
    }

    /**
     * Stub — Outline mode is currently disabled due to FBO compatibility issues.
     * Use Box ESP mode instead.
     */
    public static void renderOutlines(float partialTicks) {
        // No-op: stencil-based rendering corrupts LabyMod's FBO
        clear();
    }
}

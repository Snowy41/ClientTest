package com.hades.preview;

import com.hades.preview.modules.PlayerESPPreview;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of all available module preview renderers.
 * Adding a new module requires ONLY:
 * 1. Create a class implementing ModulePreviewRenderer
 * 2. Call register() in the static block below
 */
public class PreviewRegistry {

    private static final Map<String, ModulePreviewRenderer> REGISTRY = new HashMap<>();

    static {
        register(new PlayerESPPreview());
        // register(new ChestESPPreview());
        // register(new ItemESPPreview());
        // register(new HUDPreview());
    }

    public static void register(ModulePreviewRenderer renderer) {
        REGISTRY.put(renderer.getModuleId(), renderer);
    }

    public static ModulePreviewRenderer get(String moduleId) {
        return REGISTRY.get(moduleId);
    }

    public static Map<String, ModulePreviewRenderer> getAll() {
        return REGISTRY;
    }
}

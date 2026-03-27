package com.hades.preview;

import java.util.List;
import java.util.Map;

/**
 * Interface for module overlay renderers in the preview SDK.
 * Each module (PlayerESP, ChestESP, etc.) implements this to draw
 * its overlay onto the headless framebuffer.
 *
 * Adding a new module preview requires ONLY:
 * 1. Create a class implementing this interface
 * 2. Register it in PreviewRegistry's static block
 */
public interface ModulePreviewRenderer {

    /** Unique module identifier matching the ScenePack moduleId. */
    String getModuleId();

    /**
     * Render the module's overlay to the current GL framebuffer.
     * @param ctx    The headless GL context (framebuffer is already bound)
     * @param config Module config values from the launcher's config panel
     * @param entities Projected screen-space entities from Three.js
     */
    void render(HeadlessGLContext ctx, Map<String, Object> config, List<ScreenEntity> entities);

    /** Return the config schema so the launcher can generate the config panel UI. */
    ConfigSchema getSchema();
}

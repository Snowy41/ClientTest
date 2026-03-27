package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

/**
 * Fired at the END of each render frame (from ScreenRenderEvent POST).
 * LabyMod has finished compositing at this point.
 * This is the correct place to draw overlays (ClickGUI, HUD, etc.)
 * directly to framebuffer 0.
 */
public class PostRenderEvent extends HadesEvent {
    private final float partialTicks;
    private final int scaledWidth;
    private final int scaledHeight;

    public PostRenderEvent(float partialTicks, int scaledWidth, int scaledHeight) {
        this.partialTicks = partialTicks;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public int getScaledWidth() {
        return scaledWidth;
    }

    public int getScaledHeight() {
        return scaledHeight;
    }
}

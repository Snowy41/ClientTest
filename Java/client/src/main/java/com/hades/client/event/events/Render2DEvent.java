package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

/**
 * Fired every frame for 2D overlay rendering (HUD, ClickGUI).
 * Fired after MC's GUI rendering in the correct GL state.
 */
public class Render2DEvent extends HadesEvent {
    private final float partialTicks;
    private final int scaledWidth;
    private final int scaledHeight;

    public Render2DEvent(float partialTicks, int scaledWidth, int scaledHeight) {
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

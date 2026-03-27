package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

/**
 * Fired at the START of each render frame (from GameRenderEvent).
 * Use this for operations that must happen before screen compositing.
 */
public class PreRenderEvent extends HadesEvent {
    private final float partialTicks;

    public PreRenderEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}

package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

/**
 * Fired during world rendering for 3D drawing (tracers, ESP boxes).
 * The modelview matrix is already set up for world-space rendering.
 */
public class Render3DEvent extends HadesEvent {
    private final float partialTicks;

    public Render3DEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}

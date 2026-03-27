package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

public class RenderEvent extends HadesEvent {
    private final float partialTicks;

    public RenderEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}
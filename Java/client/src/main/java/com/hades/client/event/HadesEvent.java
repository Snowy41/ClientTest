package com.hades.client.event;

/**
 * Base class for all Hades events.
 * Supports cancellation for events that can be prevented from propagating.
 */
public abstract class HadesEvent {
    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

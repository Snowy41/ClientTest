package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

/**
 * Fired when a packet is sent or received. Cancellable.
 */
public class PacketEvent extends HadesEvent {
    private final Object packet;
    private final Direction direction;
    private boolean cancelled;

    public PacketEvent(Object packet, Direction direction) {
        this.packet = packet;
        this.direction = direction;
    }

    public Object getPacket() {
        return packet;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isSend() {
        return direction == Direction.SEND;
    }

    public boolean isReceive() {
        return direction == Direction.RECEIVE;
    }

    public enum Direction {
        SEND, RECEIVE
    }

    /** Convenience: fired when the client sends a packet to the server */
    public static class Send extends PacketEvent {
        public Send(Object packet) {
            super(packet, Direction.SEND);
        }
    }

    /** Convenience: fired when the client receives a packet from the server */
    public static class Receive extends PacketEvent {
        public Receive(Object packet) {
            super(packet, Direction.RECEIVE);
        }
    }
}
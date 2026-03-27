package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

public class MotionEvent extends HadesEvent {
    public enum State { PRE, POST }

    private final State state;
    private double x, y, z;
    private float yaw, pitch;
    private boolean onGround;

    public MotionEvent(State state, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.state = state;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    public boolean isPre() { return state == State.PRE; }
    public boolean isPost() { return state == State.POST; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean onGround) { this.onGround = onGround; }
}

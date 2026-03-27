package com.hades.client.combat;

/**
 * RotationManager — pure data store for server-side rotations.
 * 
 * Modules (KillAura, AimAssist) set event.setYaw()/setPitch() DIRECTLY
 * in their own MotionEvent handlers. RotationManager only stores the
 * active rotation state so the RotationRenderHook can read it for
 * 3rd-person model rendering.
 * 
 * It does NOT have its own EventHandler — this prevents race conditions
 * between multiple handlers setting event rotations.
 */
public class RotationManager {

    private static RotationManager instance;

    private boolean active;
    private float serverYaw;
    private float serverPitch;
    private float prevServerYaw;
    private float prevServerPitch;

    private RotationManager() {
        // No EventBus registration — pure data store
    }

    public static RotationManager getInstance() {
        if (instance == null) {
            instance = new RotationManager();
        }
        return instance;
    }

    /**
     * Store the current server-side rotations.
     * Called by combat modules after they set event.setYaw()/setPitch().
     */
    public void setRotations(float yaw, float pitch) {
        if (!this.active) {
            this.prevServerYaw = yaw;
            this.prevServerPitch = pitch;
        } else {
            this.prevServerYaw = this.serverYaw;
            this.prevServerPitch = this.serverPitch;
        }
        this.serverYaw = yaw;
        this.serverPitch = pitch;
        this.active = true;
    }

    public void stopRotations() {
        this.active = false;
        // Keep the last valid rotations so smooth decay/hooks don't violently snap when stopped
    }

    public boolean isActive() {
        return active;
    }

    public float getServerYaw() {
        return serverYaw;
    }

    public float getServerPitch() {
        return serverPitch;
    }

    public float getPrevServerYaw() {
        return prevServerYaw;
    }

    public float getPrevServerPitch() {
        return prevServerPitch;
    }
}


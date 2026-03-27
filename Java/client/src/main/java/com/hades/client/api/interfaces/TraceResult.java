package com.hades.client.api.interfaces;

/**
 * Simple data class holding the result of a world ray trace.
 */
public class TraceResult {
    public final boolean didHit;
    public final double hitX, hitY, hitZ;
    public final String sideHit; // "UP", "DOWN", "NORTH", "SOUTH", "EAST", "WEST", or "NONE"
    public final boolean isEntityHit;

    public TraceResult(boolean didHit, double hitX, double hitY, double hitZ, String sideHit, boolean isEntityHit) {
        this.didHit = didHit;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.sideHit = sideHit != null ? sideHit : "NONE";
        this.isEntityHit = isEntityHit;
    }

    public static TraceResult miss() {
        return new TraceResult(false, 0, 0, 0, "NONE", false);
    }
}

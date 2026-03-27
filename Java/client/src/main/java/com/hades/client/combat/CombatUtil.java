package com.hades.client.combat;

import com.hades.client.api.HadesAPI;

/**
 * CombatUtil contains centralized, high-performance math and logic wrappers 
 * for combat modules (AABB calculations, GCD fixing, FOV checking).
 */
public class CombatUtil {

    /**
     * Calculates the minimum difference between two angles in degrees (-180 to 180).
     */
    public static float getAngleDifference(float a, float b) {
        float diff = Math.abs(a - b) % 360f;
        return diff > 180f ? 360f - diff : diff;
    }

    /**
     * Checks if an entity is within the specified FOV cone of the player's current gaze.
     * Uses yaw only for standard horizontal FOV.
     */
    public static boolean isWithinFOV(com.hades.client.api.interfaces.IEntity entity, float maxFov) {
        if (entity == null || HadesAPI.player == null) return false;
        
        double pX = HadesAPI.player.getX();
        double pZ = HadesAPI.player.getZ();
        double eX = entity.getX();
        double eZ = entity.getZ();

        float yawToTarget = (float) Math.toDegrees(Math.atan2(eZ - pZ, eX - pX)) - 90f;
        float playerYaw = HadesAPI.Player.getYaw();

        return getAngleDifference(playerYaw, yawToTarget) <= maxFov;
    }

    /**
     * Calculates the closest point on the target's bounding box to the given eye coordinates.
     * Prevents AimAssist from snapping to the exact center.
     * 
     * Standard 1.8.9 player AABB: width=0.6, height=1.8 (relative to entity posX/Y/Z).
     * The AABB is centered horizontally on the entity X/Z.
     */
    public static double[] getClosestPointOnAABB(double eyeX, double eyeY, double eyeZ, 
                                                 double eX, double eY, double eZ, 
                                                 float width, float height) {
        double halfWidth = width / 2.0;

        double minX = eX - halfWidth;
        double maxX = eX + halfWidth;
        double minY = eY;
        double maxY = eY + height;
        double minZ = eZ - halfWidth;
        double maxZ = eZ + halfWidth;

        // Clamp eye position to the bounds of the AABB
        double closestX = Math.max(minX, Math.min(maxX, eyeX));
        double closestY = Math.max(minY, Math.min(maxY, eyeY));
        double closestZ = Math.max(minZ, Math.min(maxZ, eyeZ));

        // Adjust Y slightly upward (e.g. 85%) to target chest/head instead of feet
        closestY = Math.min(closestY + (height * 0.85), maxY);

        return new double[]{closestX, closestY, closestZ};
    }

    /**
     * Calculates the true Euclidean distance from the player's eye height to the nearest
     * edge of the target's bounding box (expanded by the 0.1F collision border).
     * This perfectly mirrors Vanilla Minecraft's strict combat reach mechanics.
     */
    public static double getDistanceToBox(com.hades.client.api.interfaces.IEntity player, com.hades.client.api.interfaces.IEntity target) {
        if (player == null || target == null) return 0.0;
        
        double eyeX = player.getX();
        // Vanilla player eye height is 1.62. 
        double eyeY = player.getY() + 1.62;
        double eyeZ = player.getZ();
        
        // Typical vanilla bounding box expansion for raytraces/combat is 0.1F
        float border = 0.1f;
        double width = target.getWidth() / 2.0;

        double minX = target.getX() - width - border;
        double maxX = target.getX() + width + border;
        double minY = target.getY() - border;
        double maxY = target.getY() + target.getHeight() + border;
        double minZ = target.getZ() - width - border;
        double maxZ = target.getZ() + width + border;
        
        // Clamp eye vector to the bounds of the AABB to find closest edge point
        double closestX = Math.max(minX, Math.min(maxX, eyeX));
        double closestY = Math.max(minY, Math.min(maxY, eyeY));
        double closestZ = Math.max(minZ, Math.min(maxZ, eyeZ));
        
        double dX = eyeX - closestX;
        double dY = eyeY - closestY;
        double dZ = eyeZ - closestZ;
        
        return Math.sqrt(dX * dX + dY * dY + dZ * dZ);
    }

    /**
     * Simulates Minecraft's mouse sensitivity GCD (Greatest Common Divisor) rounding.
     * 
     * Formula:
     * f = sensitivity * 0.6 + 0.2
     * gcd = f * f * f * 1.2
     */
    public static float getMouseGCD(float sensitivity) {
        float f = sensitivity * 0.6f + 0.2f;
        return f * f * f * 1.2f;
    }
}

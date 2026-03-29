package com.hades.client.util;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;

public class RotationUtil {

    // Heuristic analysis variables
    public static double ACCURATE_ROTATION_YAW_LEVEL;
    public static double ACCURATE_ROTATION_YAW_VL;
    public static double ACCURATE_ROTATION_PITCH_LEVEL;
    public static double ACCURATE_ROTATION_PITCH_VL;

    /**
     * Berechnet die benötigten Rotationen zu einer Position.
     * 
     * @return float[] {yaw, pitch}
     */
    public static float[] getRotations(double targetX, double targetY, double targetZ) {
        double dx = targetX - HadesAPI.player.getX();
        double dy = targetY - (HadesAPI.player.getY() + 1.62); // eye height
        double dz = targetZ - HadesAPI.player.getZ();

        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, dist) * 180.0 / Math.PI);

        return new float[] { yaw, pitch };
    }

    /**
     * Berechnet die Rotationen zu einer Entity.
     */
    public static float[] getRotationsToEntity(com.hades.client.api.interfaces.IEntity entity) {
        return getRotations(
                entity.getX(),
                entity.getY() + getEntityEyeHeight(entity),
                entity.getZ());
    }

    /**
     * Sanfte Rotation mit max speed limit.
     */
    public static float[] smoothRotation(float currentYaw, float currentPitch,
            float targetYaw, float targetPitch,
            float speed) {
        float deltaYaw = wrapAngle(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        // Proportional easing for human-like movement
        float easeYaw = deltaYaw * 0.45f;
        if (Math.abs(easeYaw) > speed)
            easeYaw = (deltaYaw > 0 ? speed : -speed);
        if (Math.abs(deltaYaw) > 0.5f && Math.abs(easeYaw) < 2.5f)
            easeYaw = (deltaYaw > 0 ? 2.5f : -2.5f);
        if (Math.abs(deltaYaw) <= 0.5f)
            easeYaw = deltaYaw;

        float easePitch = deltaPitch * 0.45f;
        if (Math.abs(easePitch) > speed)
            easePitch = (deltaPitch > 0 ? speed : -speed);
        if (Math.abs(deltaPitch) > 0.5f && Math.abs(easePitch) < 2.5f)
            easePitch = (deltaPitch > 0 ? 2.5f : -2.5f);
        if (Math.abs(deltaPitch) <= 0.5f)
            easePitch = deltaPitch;

        return new float[] {
                currentYaw + easeYaw,
                Math.max(-90f, Math.min(90f, currentPitch + easePitch))
        };
    }

    /**
     * GCD (Greatest Common Divisor) fix for mouse sensitivity.
     * Makes rotations look like real mouse movement.
     * Rewritten to perfectly identically match Vanilla 1.8.9 Entity.setAngles
     * memory
     * accumulation using intermediary double casts to bypass GrimAC Simulation
     * heuristics.
     */
    public static float[] applyGCD(float yaw, float pitch, float prevYaw, float prevPitch) {
        float sensitivity = HadesAPI.Player.getMouseSensitivity();
        float f = sensitivity * 0.6f + 0.2f;
        float gcdBase = f * f * f * 8.0f; // Vanilla 8.0 multiplier

        float deltaYaw = yaw - prevYaw;
        float deltaPitch = pitch - prevPitch;

        // Calculate the raw integer mouse pixel deltas required to reach this rotation
        int mouseDeltaX = Math.round(deltaYaw / (gcdBase * 0.15f));
        int mouseDeltaY = Math.round(deltaPitch / (gcdBase * 0.15f));

        // Re-accumulate EXACTLY like Vanilla 1.8.9 EntityRenderer -> Entity.setAngles
        float f2 = (float) mouseDeltaX * gcdBase;
        float f3 = (float) mouseDeltaY * gcdBase;

        float finalYaw = (float) ((double) prevYaw + (double) f2 * 0.15D);
        float finalPitch = (float) ((double) prevPitch + (double) f3 * 0.15D);

        return new float[] { finalYaw, finalPitch };
    }

    public static float wrapAngle(float angle) {
        angle %= 360f;
        if (angle >= 180f)
            angle -= 360f;
        if (angle < -180f)
            angle += 360f;
        return angle;
    }

    public static float getAngleDifference(float a, float b) {
        return Math.abs(wrapAngle(a - b));
    }

    private static java.lang.reflect.Method cachedEyeHeightMethod;
    private static boolean eyeHeightMethodCached = false;

    private static double getEntityEyeHeight(IEntity entity) {
        try {
            if (!eyeHeightMethodCached) {
                eyeHeightMethodCached = true;
                try {
                    cachedEyeHeightMethod = entity.getRaw().getClass().getMethod("getEyeHeight");
                    cachedEyeHeightMethod.setAccessible(true);
                } catch (Exception ignored) {}
            }
            if (cachedEyeHeightMethod != null) {
                return (float) cachedEyeHeightMethod.invoke(entity.getRaw());
            }
        } catch (Exception e) {
        }
        return 1.62; // default player eye height
    }

    /**
     * Perfectly mimics Vanilla Minecraft's objectMouseOver RayTrace against an
     * AABB.
     * Ensures we only click when the mathematical ray physically passes through
     * the target's expanded collision boundary, completely defeating Intave
     * crypta/hitbox checks.
     */
    public static boolean isRayIntersectingAABB(double eyeX, double eyeY, double eyeZ,
            float yaw, float pitch,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double reach) {
        com.hades.client.event.EventBus.startSection("RotationUtil");
        try {
            float f1 = (float) Math.cos(-yaw * 0.017453292F - (float) Math.PI);
            float f2 = (float) Math.sin(-yaw * 0.017453292F - (float) Math.PI);
            float f3 = (float) -Math.cos(-pitch * 0.017453292F);
            float f4 = (float) Math.sin(-pitch * 0.017453292F);

            double dirX = f2 * f3;
            double dirY = f4;
            double dirZ = f1 * f3;

            // Ensure division by zero doesn't cause infinity issues
            if (dirX == 0)
                dirX = 1e-5;
            if (dirY == 0)
                dirY = 1e-5;
            if (dirZ == 0)
                dirZ = 1e-5;

            double tmin = (minX - eyeX) / dirX;
            double tmax = (maxX - eyeX) / dirX;
            if (tmin > tmax) {
                double tmp = tmin;
                tmin = tmax;
                tmax = tmp;
            }

            double tymin = (minY - eyeY) / dirY;
            double tymax = (maxY - eyeY) / dirY;
            if (tymin > tymax) {
                double tmp = tymin;
                tymin = tymax;
                tymax = tmp;
            }

            if ((tmin > tymax) || (tymin > tmax))
                return false;

            if (tymin > tmin)
                tmin = tymin;
            if (tymax < tmax)
                tmax = tymax;

            double tzmin = (minZ - eyeZ) / dirZ;
            double tzmax = (maxZ - eyeZ) / dirZ;
            if (tzmin > tzmax) {
                double tmp = tzmin;
                tzmin = tzmax;
                tzmax = tmp;
            }

            if ((tmin > tzmax) || (tzmin > tmax))
                return false;
            if (tzmin > tmin)
                tmin = tzmin;

            return tmin >= 0 && tmin <= reach;
        } finally {
            com.hades.client.event.EventBus.endSection("RotationUtil");
        }
    }

    /**
     * Advanced rotation calculation using heuristics, randomization, GCD
     * sensitivity, and Prediction.
     * Ported from Augustus/Whiteout.
     */
    public static float[] faceEntityCustom(IEntity entity, float yawSpeed, float pitchSpeed,
            float currentYaw, float currentPitch,
            boolean intave) {
        com.hades.client.event.EventBus.startSection("RotationUtil");
        try {
            double ex = entity.getX();
            double ey = entity.getY() + getEntityEyeHeight(entity);
            double ez = entity.getZ();

            // Intentionally no target prediction interpolation here.
            // GrimAC uses strict lag-compensated backtracking based on transaction pings.
            // The entity's current visual getX() perfectly aligns with the server's tracked
            // bounding box for our tick. Predicting into the future causes the ray to fall
            // outside the bounding box on the server, causing Hitbox flags.
            // BestHit Vec (Clamp to expanded AABB constraints)
            double actualEntityY = ey - getEntityEyeHeight(entity);
            double[] best = getBestHitVec(entity, ex, actualEntityY, ez);
            ex = best[0];
            ey = best[1];
            ez = best[2];

            // RayTrace Heuristics Offset
            double[] xyz = applyHeuristics(entity, new double[] { ex, ey, ez });
            ex = xyz[0];
            ey = xyz[1];
            ez = xyz[2];

            double diffX = ex - HadesAPI.player.getX();
            double diffY = ey - (HadesAPI.player.getY() + 1.62); // 1.62 = eyeHeight
            double diffZ = ez - HadesAPI.player.getZ();

            float calcYaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI - 90.0);
            float calcPitch = (float) -(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ)) * 180.0 / Math.PI);

            float yaw = updateRotationStatic(currentYaw, calcYaw, yawSpeed);
            float pitch = updateRotationStatic(currentPitch, calcPitch, pitchSpeed);

            // Mathematical Jitter Matrix (Polar ML / Intave Crypta Engine Bypass)
            float randomStrength = 0.08f;
            yaw += (float) (intave ? (MathUtil.nextSecureFloat(1.0, 2.0) * Math.sin(pitch * Math.PI) * randomStrength)
                    : (MathUtil.nextGaussian() * randomStrength));
            pitch += (float) (intave ? (MathUtil.nextSecureFloat(1.0, 2.0) * Math.sin(yaw * Math.PI) * randomStrength)
                    : (MathUtil.nextGaussian() * randomStrength));

            return applyGCD(yaw, pitch, currentYaw, currentPitch);
        } finally {
            com.hades.client.event.EventBus.endSection("RotationUtil");
        }
    }

    /**
     * Clean rotation calculation — same as faceEntityCustom but WITHOUT jitter.
     * Used for smooth visual camera aiming (AimAssist Client/Legit mode).
     */
    public static float[] faceEntityClean(IEntity entity, float yawSpeed, float pitchSpeed,
            float currentYaw, float currentPitch) {
        com.hades.client.event.EventBus.startSection("RotationUtil");
        try {
            double ex = entity.getX();
            double ey = entity.getY() + getEntityEyeHeight(entity);
            double ez = entity.getZ();

            double actualEntityY = ey - getEntityEyeHeight(entity);
            double[] best = getBestHitVec(entity, ex, actualEntityY, ez);
            ex = best[0];
            ey = best[1];
            ez = best[2];

            double[] xyz = applyHeuristics(entity, new double[] { ex, ey, ez });
            ex = xyz[0];
            ey = xyz[1];
            ez = xyz[2];

            double diffX = ex - HadesAPI.player.getX();
            double diffY = ey - (HadesAPI.player.getY() + 1.62);
            double diffZ = ez - HadesAPI.player.getZ();

            float calcYaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI - 90.0);
            float calcPitch = (float) -(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ)) * 180.0 / Math.PI);

            float yaw = updateRotationStatic(currentYaw, calcYaw, yawSpeed);
            float pitch = updateRotationStatic(currentPitch, calcPitch, pitchSpeed);

            // No jitter — clean smooth rotation for the visual camera
            return applyGCD(yaw, pitch, currentYaw, currentPitch);
        } finally {
            com.hades.client.event.EventBus.endSection("RotationUtil");
        }
    }

    public static float updateRotationStatic(float current, float limit, float speedStr) {
        float f = wrapAngle(limit - current);

        // Easing interpolation: Swing fast initially, decelerate smoothly on approach.
        // Prevents mechanical "instant snapping" by taking a fraction of the distance.
        float easeSpeed = f * 0.45f;

        // Clamp to absolute max speed defined by KillAura settings
        if (Math.abs(easeSpeed) > speedStr) {
            easeSpeed = (f > 0 ? speedStr : -speedStr);
        }

        // Prevent Zeno's paradox (getting infinitely closer but never reaching)
        float minSpd = 2.5f;
        if (Math.abs(f) > 0.5f && Math.abs(easeSpeed) < minSpd) {
            easeSpeed = (f > 0 ? minSpd : -minSpd);
        }

        // Maintain the infinite accumulator bounds. Returning 'limit' directly produces
        // massive 360-degree snaps to the GrimAC C03 stream because 'limit' is derived
        // from atan2 (which is strictly bounded between -180 and 180).
        if (Math.abs(f) <= 0.5f) {
            return current + f;
        }

        return current + easeSpeed;
    }

    private static double[] applyHeuristics(IEntity entity, double[] xyz) {
        double boxSize = 0.2;
        double width = entity.getWidth() / 2.0;

        // Vanilla 1.8.9 EntityRenderer.getMouseOver() natively expands the AABB
        // by the entity's collision border size (typically 0.1F).
        // Without this expansion, Intave's Crypta engine will eventually flag
        // because we are swinging "through" the very edge of the hitbox bounds.
        // We use 0.05F here so that we target slightly inside the strict 0.1F box,
        // leaving room for our Gaussian jitter without pushing the ray out of bounds.
        double border = 0.05F;

        double eMinX = entity.getX() - width - border;
        double eMaxX = entity.getX() + width + border;
        double eMinY = entity.getY() - border;
        double eMaxY = entity.getY() + entity.getHeight() + border;
        double eMinZ = entity.getZ() - width - border;
        double eMaxZ = entity.getZ() + width + border;

        double minX = MathUtil.clamp(xyz[0] - boxSize, eMinX, eMaxX);
        double minY = MathUtil.clamp(xyz[1] - boxSize, eMinY, eMaxY);
        double minZ = MathUtil.clamp(xyz[2] - boxSize, eMinZ, eMaxZ);
        double maxX = MathUtil.clamp(xyz[0] + boxSize, eMinX, eMaxX);
        double maxY = MathUtil.clamp(xyz[1] + boxSize, eMinY, eMaxY);
        double maxZ = MathUtil.clamp(xyz[2] + boxSize, eMinZ, eMaxZ);

        xyz[0] = MathUtil.clamp(xyz[0] + MathUtil.randomSin(), minX, maxX);
        xyz[1] = MathUtil.clamp(xyz[1] + MathUtil.randomSin(), minY, maxY);
        xyz[2] = MathUtil.clamp(xyz[2] + MathUtil.randomSin(), minZ, maxZ);
        return xyz;
    }

    public static double[] getBestHitVec(IEntity entity, double predictedX, double predictedY, double predictedZ) {
        double eyeX = HadesAPI.player.getX();
        double eyeY = HadesAPI.player.getY() + 1.62;
        double eyeZ = HadesAPI.player.getZ();

        float f11 = 0.05f; // slightly smaller than true collision border (0.1) to leave room for jitter
        double width = entity.getWidth() / 2.0;

        double minX = predictedX - width - f11;
        double maxX = predictedX + width + f11;
        double minY = predictedY - f11;
        double maxY = predictedY + entity.getHeight() + f11;
        double minZ = predictedZ - width - f11;
        double maxZ = predictedZ + width + f11;

        double ex = MathUtil.clamp(eyeX, minX, maxX);
        double ey = MathUtil.clamp(eyeY, minY, maxY);
        double ez = MathUtil.clamp(eyeZ, minZ, maxZ);

        // Prevent calculating aim to exactly our own eyes (causes atan2 noise/snapping)
        double distSq = (ex - eyeX) * (ex - eyeX) + (ey - eyeY) * (ey - eyeY) + (ez - eyeZ) * (ez - eyeZ);
        if (distSq < 0.01) {
            // We are essentially inside the bounding box. Aim at target's visual center.
            return new double[] { predictedX, predictedY + entity.getHeight() * 0.5, predictedZ };
        }

        return new double[] { ex, ey, ez };
    }
}
package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;

/**
 * Intercepts EntityLivingBase.moveEntityWithHeading(float strafe, float
 * forward)
 * to synchronize physical movement with GrimAC's server-sided prediction.
 *
 * When the aura yaw makes the player face sideways (calcForward <= 0), we
 * temporarily drop sprint so getAIMoveSpeed() returns walk speed (0.1 instead
 * of 0.13). This matches what GrimAC predicts when it sees STOP_SPRINTING.
 */
public class MoveEntityWithHeadingHook {

    public static final String[] YAW_NAMES = { "rotationYaw", "y", "field_70177_z" };
    public static java.lang.reflect.Field rotYawField;
    public static float savedYaw = Float.NaN;

    public static final String[] LAND_MOVEMENT_FACTOR_NAMES = { "landMovementFactor", "aI", "field_70747_aH" };
    public static java.lang.reflect.Field landMovementFactorField;

    public static final String[] GET_AI_MOVE_SPEED_NAMES = { "getAIMoveSpeed", "bI", "func_70689_b", "ck" };
    public static java.lang.reflect.Method getAIMoveSpeedMethod;

    public static final String[] JUMP_MOVEMENT_FACTOR_NAMES = { "jumpMovementFactor", "aM", "field_70747_aH", "bQ" };
    public static java.lang.reflect.Field jumpMovementFactorField;

    public static final String[] SET_FLAG_NAMES = { "setFlag", "b", "func_70052_a" };
    public static java.lang.reflect.Method setFlagMethod;

    /** Track whether we dropped sprint so we can restore it */
    public static boolean droppedSprintForSideways = false;
    public static boolean originalSprintState = false;

    /**
     * 8-sector discretization. Returns int[]{calcForward, calcStrafe}.
     * ByteBuddy Advice can safely call public static methods.
     */
    public static int[] discretize(double diff) {
        if (Math.abs(diff) <= 22.5)
            return new int[] { 1, 0 };
        if (Math.abs(diff - 45) <= 22.5)
            return new int[] { 1, -1 };
        if (Math.abs(diff + 45) <= 22.5)
            return new int[] { 1, 1 };
        if (Math.abs(diff - 90) <= 22.5)
            return new int[] { 0, -1 };
        if (Math.abs(diff + 90) <= 22.5)
            return new int[] { 0, 1 };
        if (Math.abs(diff - 135) <= 22.5)
            return new int[] { -1, -1 };
        if (Math.abs(diff + 135) <= 22.5)
            return new int[] { -1, 1 };
        return new int[] { -1, 0 };
    }

    // Exposed for Sprint.java to read
    public static boolean auraSideways = false;

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.This Object entityLivingBase,
            @Advice.Argument(value = 0, readOnly = false) float strafe,
            @Advice.Argument(value = 1, readOnly = false) float forward) {
        try {
            if (entityLivingBase != com.hades.client.api.HadesAPI.player.getRaw())
                return;

            Float activeYaw = com.hades.client.module.impl.combat.KillAura.getActiveAuraYaw();
            if (activeYaw == null)
                activeYaw = com.hades.client.module.impl.movement.Scaffold.getActiveScaffoldYaw();
            if (activeYaw == null)
                activeYaw = com.hades.client.module.impl.combat.BowAimbot.getActiveBowYaw();
            if (activeYaw == null)
                return;

            if (rotYawField == null)
                rotYawField = resolveField(entityLivingBase.getClass(), YAW_NAMES);

            float auraYaw = activeYaw;
            if (rotYawField != null) {
                if (Float.isNaN(auraYaw)) {
                    auraYaw = activeYaw;
                }

                // Swap rotation natively for the physics calculation.
                savedYaw = rotYawField.getFloat(entityLivingBase);
                rotYawField.setFloat(entityLivingBase, auraYaw);
            }
        } catch (Exception ignored) {
        }
    }

    @Advice.OnMethodExit
    public static void onExit(@Advice.This Object entityLivingBase) {
        try {
            if (entityLivingBase != com.hades.client.api.HadesAPI.player.getRaw())
                return;
            if (rotYawField != null && !Float.isNaN(savedYaw)) {
                rotYawField.setFloat(entityLivingBase, savedYaw);
                savedYaw = Float.NaN;
            }
        } catch (Exception ignored) {
        }
    }

    public static java.lang.reflect.Method resolveMethod(Class<?> clazz, String[] names, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null) {
            for (String name : names) {
                try {
                    java.lang.reflect.Method m = current.getDeclaredMethod(name, paramTypes);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static java.lang.reflect.Field resolveField(Class<?> clazz, String[] names) {
        Class<?> current = clazz;
        while (current != null) {
            for (String name : names) {
                try {
                    java.lang.reflect.Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}

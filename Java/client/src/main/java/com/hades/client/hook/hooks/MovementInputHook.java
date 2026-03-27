package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import java.lang.reflect.Field;
import com.hades.client.util.ReflectionUtil;

public class MovementInputHook {

    public static Field forwardField;
    public static Field strafeField;

    @Advice.OnMethodExit
    public static void onUpdatePlayerMoveState(@Advice.This Object movementInput) {
        try {
            if (forwardField == null || strafeField == null) {
                // Initialize fields mapping to net.minecraft.util.MovementInput
                forwardField = ReflectionUtil.findField(movementInput.getClass().getSuperclass(), "moveForward", "field_78900_b", "b");
                strafeField = ReflectionUtil.findField(movementInput.getClass().getSuperclass(), "moveStrafe", "field_78902_a", "a");
                if (forwardField != null) forwardField.setAccessible(true);
                if (strafeField != null) strafeField.setAccessible(true);
            }

            if (forwardField == null || strafeField == null) return;

            float forward = forwardField.getFloat(movementInput);
            float strafe = strafeField.getFloat(movementInput);

            // Bypass early if no inputs are pressed
            if (forward == 0 && strafe == 0) return;

            // Apply Silent Rotation translation if KillAura OR Scaffold is active
            Float activeYaw = com.hades.client.module.impl.combat.KillAura.getActiveAuraYaw();
            if (activeYaw == null) activeYaw = com.hades.client.module.impl.movement.Scaffold.getActiveScaffoldYaw();
            
            if (activeYaw != null && com.hades.client.api.HadesAPI.player != null && com.hades.client.api.HadesAPI.player.getRaw() != null) {
                    
                    float playerYaw = com.hades.client.api.HadesAPI.player.getYaw();
                    
                    // LiquidBounce rotation mapping: 
                    // newStrafe = strafe * cos(delta) - forward * sin(delta)
                    // newForward = forward * cos(delta) + strafe * sin(delta)
                    
                    // Determine visual intended movement angle relative to the CAMERA yaw
                    double visualAngle = Math.toDegrees(Math.atan2(strafe, forward));
                    
                    // The actual world-space angle the player wants to travel towards
                    double worldAngle = playerYaw - visualAngle;
                    
                    // How far that world-space angle is from the active spoofed yaw we are transmitting
                    double angleDiff = (worldAngle - activeYaw) % 360.0;
                    if (angleDiff < -180.0) angleDiff += 360.0;
                    if (angleDiff > 180.0) angleDiff -= 360.0;
                    
                    // Snap to the closest rigid 45-degree interval to perfectly satisfy GrimAC's WASD reverse-engineering
                    int[] discrete = com.hades.client.hook.hooks.MoveEntityWithHeadingHook.discretize(angleDiff);
                    
                    // Sneaking natively applies a 0.3x modifier. Preserve the intended magnitude limits.
                    float magnitude = Math.max(Math.abs(forward), Math.abs(strafe));
                    
                    float finalForward = discrete[0] * magnitude;
                    float finalStrafe = discrete[1] * magnitude;

                    // Expose to MoveEntityWithHeadingHook if we are forcing sideways movement, so sprint can be correctly handled
                    com.hades.client.hook.hooks.MoveEntityWithHeadingHook.auraSideways = (finalForward <= 0.0f);

                    forwardField.setFloat(movementInput, finalForward);
                    strafeField.setFloat(movementInput, finalStrafe);
                }
        } catch (Exception ignored) {}
    }
}

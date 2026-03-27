package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.api.HadesAPI;

/**
 * ByteBuddy hook for EntityLivingBase.setSprinting(boolean)
 * Takes OBF name: d
 * 
 * Prevents the vanilla physics engine from internally setting the player's
 * sprint state to TRUE when KillAura is attacking sideways/backwards.
 * This guarantees that landMovementFactor natively caches the Walk Speed 
 * (plus any active potion effects), eliminating the need for fragile reflection.
 */
public class SetSprintingHook {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.This Object entityLivingBase,
                               @Advice.Argument(value = 0, readOnly = false) boolean sprinting) {
        
        try {
            // Bypass LabyMod and other ToggleSprint mods that cancel setSprinting(false)
            // by directly overwriting the DataWatcher flag. This ensures vanilla sprint drops
            // (hitting blocks, taking knockback) execute correctly without flagging GrimAC!
            if (MoveEntityWithHeadingHook.setFlagMethod == null) {
                MoveEntityWithHeadingHook.setFlagMethod = MoveEntityWithHeadingHook.resolveMethod(entityLivingBase.getClass(), MoveEntityWithHeadingHook.SET_FLAG_NAMES, int.class, boolean.class);
            }
            if (MoveEntityWithHeadingHook.setFlagMethod != null) {
                MoveEntityWithHeadingHook.setFlagMethod.invoke(entityLivingBase, 3, sprinting);
            }
        } catch (Exception ignored) {}
    }
}

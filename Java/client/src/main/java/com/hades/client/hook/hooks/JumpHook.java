package com.hades.client.hook.hooks;

import com.hades.client.api.HadesAPI;
import com.hades.client.util.ReflectionUtil;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Field;

public class JumpHook {

    public static Field rotYawField;
    public static boolean fieldInitialized = false;
    public static float savedYaw = Float.NaN;

    public static void initFields(Class<?> entityClass) {
        if (!fieldInitialized) {
            Class<?> current = entityClass;
            while (current != null) {
                try {
                    if (rotYawField == null) rotYawField = ReflectionUtil.findField(current, "y", "rotationYaw", "field_70177_z");
                } catch (Exception ignored) {}
                current = current.getSuperclass();
            }
            if (rotYawField != null) rotYawField.setAccessible(true);
            fieldInitialized = true;
        }
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.This Object entityLivingBase) {
        try {
            if (HadesAPI.player != null && entityLivingBase == HadesAPI.player.getRaw()) {
                Float activeYaw = com.hades.client.module.impl.combat.KillAura.getActiveAuraYaw();
                if (activeYaw == null) activeYaw = com.hades.client.module.impl.movement.Scaffold.getActiveScaffoldYaw();
                
                if (activeYaw == null) return;
                
                initFields(entityLivingBase.getClass());

                if (rotYawField != null) {
                    savedYaw = rotYawField.getFloat(entityLivingBase);
                    rotYawField.setFloat(entityLivingBase, activeYaw);
                    
                    // ONLY drop sprint when genuinely sideways/backwards relative to aura yaw.
                    // Vanilla handles ALL other sprint drops natively in onLivingUpdate().
                    // No vanillaIllegal, no hardDrop, no attackedThisTick — those were
                    // redundantly dropping sprint and causing GrimAC Simulation cascades.
                    // EDIT: Even this was wrong! GrimAC correctly simulates that vanilla allows
                    // a player to sprint sideways relative to their visual yaw as long as 
                    // the physical W key is held! Dropping sprint artificially causes a 0.026 vs 0.02 desync!
                }
            }
        } catch (Exception ignored) {}
    }

    @Advice.OnMethodExit
    public static void onExit(@Advice.This Object entityLivingBase) {
        try {
            if (HadesAPI.player != null && entityLivingBase == HadesAPI.player.getRaw()) {
                if (rotYawField != null && !Float.isNaN(savedYaw)) {
                    rotYawField.setFloat(entityLivingBase, savedYaw);
                    savedYaw = Float.NaN;
                }
            }
        } catch (Exception ignored) {}
    }
}

package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;

/**
 * Intercepts Entity.applyEntityCollision(Entity entityIn)
 * In Vanilla 1.8.9, this method locally adds velocity (d0, d1) to EntityPlayerSP
 * when intersecting another entity's bounding box.
 * GrimAC's Simulation module does NOT predict this local client-side pushing Math,
 * instead opting for a 0.08 Lenience threshold per tracked entity.
 * 
 * Because KillAura perfectly targets the center of an entity and rams into it,
 * the local client accumulates 0.05 velocity per tick, which at 0.91 friction
 * compounds to ~0.55 additional positional desynchronization.
 * 
 * Cancelling this client-sided visual pushing perfectly aligns our local velocity
 * with GrimAC's 0-push expectation, resolving the 0.098 distance gap.
 */
public class ApplyEntityCollisionHook {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.This Object entity, @Advice.Argument(0) Object entityIn) {
        try {
            if (com.hades.client.api.HadesAPI.player != null && com.hades.client.api.HadesAPI.player.getRaw() != null) {
                if (entity == com.hades.client.api.HadesAPI.player.getRaw() || entityIn == com.hades.client.api.HadesAPI.player.getRaw()) {
                    return true; // Skip original method
                }
            }
        } catch (Throwable ignored) {
        }
        return false; // Proceed with original method
    }
}

package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.HadesClient;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Field;

/**
 * Hooks EntityPlayerSP.onUpdateWalkingPlayer (bew.p).
 *
 * This method runs EVERY tick and is responsible for:
 *   1. Comparing isSprinting() with serverSprintState and sending C0B packets
 *   2. Comparing isSneaking() with serverSneakState and sending C0B packets
 *   3. Sending C03 position/look packets
 *
 * CRITICAL FIX: Before vanilla's C0B sprint comparison runs, we detect if
 * the KillAura's aura yaw makes the player face sideways (forward ≤ 0).
 * If so, we temporarily force setSprinting(false) so vanilla never sees a
 * sprint state change and never sends C0B START_SPRINTING alongside a
 * sideways-yaw C03 packet. GrimAC reads C0B to set player.isSprinting,
 * which constrains its prediction to forward≥1 — causing Simulation flags.
 */
public class UpdateWalkingPlayerHook {

    public static Field posXField, posYField, posZField;
    public static Field yawField, pitchField;
    public static Field onGroundField;
    
    /** EntityPlayerSP.lastReportedYaw / lastReportedPitch — used to force rot=true in C03 */
    public static Field lastReportedYawField, lastReportedPitchField;

    public static float originalYaw, originalPitch;
    public static double originalX, originalY, originalZ;
    public static boolean originalOnGround;

    public static MotionEvent currentEvent;
    
    // Toggle for varying the micro-jitter each tick to natively force C06 rotation packets
    public static boolean jitterToggle = false;

    /**
     * When true, onExit will NOT restore the original yaw/pitch.
     * This allows non-silent rotation modules (KillAura with silent=false)
     * to physically rotate the player's camera.
     * Reset to false each tick in onExit.
     */
    public static boolean skipRotationRestore = false;

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.This Object player) {
        try {
            if (posXField == null) {
                // Initialize fields
                Class<?> entityClass = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");
                posXField = ReflectionUtil.findField(entityClass, "s", "posX", "field_70165_t");
                posYField = ReflectionUtil.findField(entityClass, "t", "posY", "field_70163_u");
                posZField = ReflectionUtil.findField(entityClass, "u", "posZ", "field_70161_v");
                yawField = ReflectionUtil.findField(entityClass, "y", "rotationYaw", "field_70177_z");
                pitchField = ReflectionUtil.findField(entityClass, "z", "rotationPitch", "field_70125_A");
                onGroundField = ReflectionUtil.findField(entityClass, "C", "onGround", "field_70122_E");

                if (posXField != null) posXField.setAccessible(true);
                if (posYField != null) posYField.setAccessible(true);
                if (posZField != null) posZField.setAccessible(true);
                if (yawField != null) yawField.setAccessible(true);
                if (pitchField != null) pitchField.setAccessible(true);
                if (onGroundField != null) onGroundField.setAccessible(true);
            }

            if (posXField == null) return;

            // DON'T restore visual yaw here! Let the entity keep auraYaw
            // that physics just used. This ensures:
            // 1. originalYaw = auraYaw (matches physics)
            // 2. C03 packet generator (Vanilla d3 calculation) reads auraYaw by default
            // 3. MotionEvent captures the correct base yaw

            originalX = posXField.getDouble(player);
            originalY = posYField.getDouble(player);
            originalZ = posZField.getDouble(player);
            originalYaw = yawField.getFloat(player);
            originalPitch = pitchField.getFloat(player);
            originalOnGround = onGroundField.getBoolean(player);

            if (MoveEntityWithHeadingHook.droppedSprintForSideways) {
                // Ensure LabyMod didn't reactivate sprint between physics and packet generation!
                try {
                    if (MoveEntityWithHeadingHook.setFlagMethod != null) {
                        MoveEntityWithHeadingHook.setFlagMethod.invoke(player, 3, false);
                    }
                } catch (Exception ignored) {}
            }

            currentEvent = new MotionEvent(MotionEvent.State.PRE, originalX, originalY, originalZ, originalYaw, originalPitch, originalOnGround);
            HadesClient.getInstance().getEventBus().post(currentEvent);

            // CRITICAL: Force rot=true in Vanilla's C03 packet decision logic.
            // Vanilla sends C04 (no rotation) when rotationYaw == lastReportedYaw && rotationPitch == lastReportedPitch.
            // When rotations are identical across ticks, GrimAC raytraces with stale visual rotation data causing RotationPlace/Simulation flags.
            // Fix: Apply a micro-jitter (±0.001) that alternates per tick. This guarantees d3 != 0 || d4 != 0, forcing C06.
            jitterToggle = !jitterToggle;
            float packetYaw = currentEvent.getYaw();
            float packetPitch = currentEvent.getPitch();

            if (packetYaw != originalYaw || packetPitch != originalPitch) {
                float jitter = jitterToggle ? 0.001f : -0.001f;
                packetYaw += jitter;
                packetPitch += jitter;
            }

            // Apply potentially modified values for the packet
            posXField.setDouble(player, currentEvent.getX());
            posYField.setDouble(player, currentEvent.getY());
            posZField.setDouble(player, currentEvent.getZ());
            yawField.setFloat(player, packetYaw);
            pitchField.setFloat(player, packetPitch);
            onGroundField.setBoolean(player, currentEvent.isOnGround());

        } catch (Exception ignored) {}
    }

    @Advice.OnMethodExit
    public static void onExit(@Advice.This Object player) {
        try {
            if (currentEvent == null || posXField == null) return;

            // Always restore position and onGround
            posXField.setDouble(player, originalX);
            posYField.setDouble(player, originalY);
            posZField.setDouble(player, originalZ);
            onGroundField.setBoolean(player, originalOnGround);

            // Restore rotations
            if (skipRotationRestore) {
                // Non-silent mode: keep the modified yaw/pitch on the player entity
                // so the camera physically rotates. Use the event's final values.
                yawField.setFloat(player, currentEvent.getYaw());
                pitchField.setFloat(player, currentEvent.getPitch());
                skipRotationRestore = false;
            } else {
                // Restore visual yaw (camera) AFTER C03 was sent
                yawField.setFloat(player, originalYaw);
                pitchField.setFloat(player, originalPitch);
            }

            // Fire POST event
            MotionEvent postEvent = new MotionEvent(MotionEvent.State.POST, currentEvent.getX(), currentEvent.getY(), currentEvent.getZ(), currentEvent.getYaw(), currentEvent.getPitch(), currentEvent.isOnGround());
            HadesClient.getInstance().getEventBus().post(postEvent);
            
            // Restore sprint after C0B + C03 packets are sent.
            // MoveEntityWithHeadingHook dropped sprint for walk-speed physics when sideways.
            // The C0B STOP_SPRINTING was sent during this method. Now we restore sprint
            // so the player doesn't visually slow down on the next tick.
            if (MoveEntityWithHeadingHook.droppedSprintForSideways) {
                com.hades.client.api.HadesAPI.player.setSprinting(true);
                MoveEntityWithHeadingHook.droppedSprintForSideways = false;
            }
            
            currentEvent = null;
        } catch (Exception ignored) {}
    }
}

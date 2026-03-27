package com.hades.client.module.impl.combat;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.combat.RotationManager;
import com.hades.client.combat.TargetManager;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.RotationUtil;

import java.util.Random;

/**
 * KillAura — Augustus-derived rotation pipeline.
 *
 * Key design decisions (from Augustus 2.6 analysis):
 * 1. Internal rotation state (lastYaw/lastPitch) is stored on the module and
 *    fed forward each tick. We NEVER read the player's live rotationYaw/Pitch
 *    during calculation because the UpdateWalkingPlayerHook restores original
 *    values after the packet is sent, which caused frame-to-frame jitter.
 * 2. GCD mouse sensitivity rounding is applied to every rotation change to
 *    simulate real mouse movement granularity.
 * 3. getBestHitVec clamps the player's eye position to the target's expanded
 *    AABB for precise aim that doesn't overshoot the hitbox.
 * 4. Non-silent mode sets the player's actual rotationYaw/Pitch directly.
 */
public class KillAura extends Module {

    // ── Static instance for MovementFix hook access ──
    private static KillAura instance;

    // ── Settings ──
    private final NumberSetting reach = new NumberSetting("Reach", 2.9, 2.0, 6.0, 0.1);
    private final NumberSetting fov = new NumberSetting("FOV", 180.0, 10.0, 360.0, 1.0);
    private final NumberSetting minCps = new NumberSetting("Min CPS", 10.0, 1.0, 20.0, 1.0);
    private final NumberSetting maxCps = new NumberSetting("Max CPS", 12.0, 1.0, 20.0, 1.0);
    private final NumberSetting yawSpeed = new NumberSetting("Yaw Speed", 180.0, 10.0, 180.0, 1.0);
    private final NumberSetting pitchSpeed = new NumberSetting("Pitch Speed", 180.0, 10.0, 180.0, 1.0);
    private final BooleanSetting silent = new BooleanSetting("Silent Rotations", true);
    private final BooleanSetting walls = new BooleanSetting("Through Walls", false);
    private final BooleanSetting intave = new BooleanSetting("Intave", false);

    // ── Internal rotation state (Augustus-style) ──
    private float lastYaw, lastPitch;
    private boolean hasRotationState;

    // ── Target & timing ──
    private IEntity currentTarget;
    private long lastAttackTime;
    public static boolean attackedThisTick = false;
    private int currentDelay;
    private final Random random = new Random();
    private long lastDebugLog = 0;
    


    // ── Session Randomization (Polar ML bypass) ──
    // Varied per-enable to defeat behavioral profiling across sessions
    private float sessionYawMod = 1.0f;
    private float sessionPitchMod = 1.0f;

    // ── Micro-Hesitation (Polar ML bypass) ──
    // Pauses rotation briefly every 5-15s like a human attention shift
    private long nextHesitationTime = 0;
    private long hesitationEndTime = 0;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT, 0);
        instance = this;
        register(reach);
        register(fov);
        register(minCps);
        register(maxCps);
        register(yawSpeed);
        register(pitchSpeed);
        register(silent);
        register(walls);
        register(intave);
    }

    /**
     * Called by UpdatePlayerMoveStateHook to get the current Aura target yaw.
     * Returns null if silent rotations are not active (no movement fix needed).
     */
    public static Float getActiveAuraYaw() {
        if (instance == null || !instance.isEnabled()) return null;
        if (!instance.silent.getValue()) return null;
        if (!instance.hasRotationState) return null;
        return instance.lastYaw;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        currentTarget = null;
        hasRotationState = false;


        // ── Session Randomization: vary speeds per-enable to defeat Polar ML ──
        sessionYawMod = 1.0f + (random.nextFloat() * 0.30f - 0.15f);   // ±15%
        sessionPitchMod = 1.0f + (random.nextFloat() * 0.20f - 0.10f); // ±10%

        // Schedule first micro-hesitation 5-15s from now
        nextHesitationTime = System.currentTimeMillis() + 5000 + random.nextInt(10000);
        hesitationEndTime = 0;

        HadesLogger.get().info("[KillAura] Enabled. Reach=" + reach.getValue()
                + " yawMod=" + String.format("%.2f", sessionYawMod)
                + " pitchMod=" + String.format("%.2f", sessionPitchMod));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RotationManager.getInstance().stopRotations();
        currentTarget = null;
        hasRotationState = false;
    }

    // ── Tick: acquire target & execute attacks ──
    // Vanilla 1.8.9 processes mouse clicks during runTick(), which fires TickEvent.
    // Attacking here (instead of MotionEvent.PRE) perfectly mirrors vanilla clickMouse() timing,
    // preventing GrimAC PacketOrderB and Intave Crypta phase-detection flags.

    @EventHandler
    public void onTick(TickEvent event) {
        attackedThisTick = false;
        if (!isEnabled() || HadesAPI.player == null) return;
        
        // Ensure TargetManager uses KillAura's specific FOV and expanded Pre-Aim Reach (e.g. 3.5)
        TargetManager.getInstance().setConfig(reach.getValue() + 0.5, fov.getValue().floatValue(), "CLOSEST");


        currentTarget = TargetManager.getInstance().getTarget();

        // ── Micro-Hesitation: brief pause in rotation tracking (Polar ML bypass) ──
        long now = System.currentTimeMillis();
        
        // Debug logging (max once per 2 seconds)
        if (now - lastDebugLog > 2000) {
            lastDebugLog = now;
            if (currentTarget == null) {
                int entityCount = HadesAPI.world != null ? HadesAPI.world.getLoadedEntities().size() : -1;
                HadesLogger.get().info("[KillAura] No target. Entities: " + entityCount);
            } else {
                HadesLogger.get().info("[KillAura] Target: " + currentTarget.getName()
                        + " dist=" + String.format("%.2f", com.hades.client.combat.CombatUtil.getDistanceToBox(HadesAPI.player, currentTarget))
                        + " hp=" + currentTarget.getHealth());
            }
        }

        if (currentTarget == null) {
            decayRotations(true);
            return;
        }

        // Pre-Aim Tracking Range: Keep target locked and track rotations up to 0.5 blocks OUTSIDE 
        // of our physical attack reach. This guarantees the crosshair is completely settled and 
        // tracking perfectly the instant they step into actual strike range.
        if (com.hades.client.combat.CombatUtil.getDistanceToBox(HadesAPI.player, currentTarget) > reach.getValue() + 0.5) {
            currentTarget = null;
            decayRotations(true);
            return;
        }

        // Raytrace visibility check
        if (!walls.getValue() && !canSee(currentTarget)) {
            currentTarget = null;
            decayRotations(true);
            return;
        }

        // ── FOV Check (Intave HitBox Bypass) ──
        // Skip entirely when FOV is 360 (attack from any direction).
        float fovValue = fov.getValue().floatValue();
        if (fovValue < 360f) {
            float yawToTarget = (float) (Math.atan2(currentTarget.getZ() - HadesAPI.player.getZ(), currentTarget.getX() - HadesAPI.player.getX()) * 180.0 / Math.PI - 90.0);
            float fovDiff = Math.abs(RotationUtil.getAngleDifference(HadesAPI.player.getYaw(), yawToTarget));
            if (fovDiff > fovValue / 2.0f) {
                currentTarget = null;
                decayRotations(true);
                return;
            }
        }

        // Ensure we initialize rotation state immediately for fast targets
        if (!hasRotationState) {
            lastYaw = HadesAPI.player.getYaw();
            lastPitch = HadesAPI.player.getPitch();
            hasRotationState = true;
        }

        // ── UPDATE ROTATIONS (Must happen before physics tick for GrimAC) ──
        if (hesitationEndTime > 0 && now < hesitationEndTime) {
            // During hesitation: freeze rotations, apply last known state
            applyRotations(lastYaw, lastPitch);
        } else {
            if (hesitationEndTime > 0 && now >= hesitationEndTime) {
                hesitationEndTime = 0;
                nextHesitationTime = now + 5000 + random.nextInt(10000);
            }
            if (now >= nextHesitationTime && nextHesitationTime > 0) {
                hesitationEndTime = now + 100 + random.nextInt(200);
                nextHesitationTime = 0;
            }

            float yawSpd = yawSpeed.getValue().floatValue() * sessionYawMod;
            float pitchSpd = pitchSpeed.getValue().floatValue() * sessionPitchMod;

            float[] newRots = RotationUtil.faceEntityCustom(
                    currentTarget, yawSpd, pitchSpd, 
                    lastYaw, lastPitch, 
                    intave.getValue()
            );

            lastYaw = newRots[0];
            lastPitch = Math.max(-90f, Math.min(90f, newRots[1]));
            
            applyRotations(lastYaw, lastPitch);
        }

        // ── Decoupled Clicker (Intave Crypta / Hitbox Bypass) ──
        if (now >= lastAttackTime + currentDelay) {


            // Server-Side Reach Clamping
            double attackDist = com.hades.client.combat.CombatUtil.getDistanceToBox(HadesAPI.player, currentTarget);
            if (attackDist > reach.getValue()) return;

            // Execute attacks natively in TickEvent (Vanilla order: Swing -> Attack -> C03)
            // This bypasses GrimAC PacketOrderB and Vulcan Post ArmAnimation.
            // Only check current rotation — the server validates using the C03 yaw it receives.
            if (hasRotationState && isAuraLookingAtTarget(currentTarget, lastYaw, lastPitch)) {
                attack(currentTarget);
                // Unintended KeepSprint / Anti-Knockback removed.
                // Re-enabling sprint 12 times a second caused 0.6x momentum decay on every single hit,
                // mathematically erasing all incoming velocity packets. 
                // We now allow Vanilla to naturally drop the sprint after the first hit, restoring normal knockback.
            } else {
                // Silently retry next tick without consuming cooldown or swinging
                return;
            }
            
            lastAttackTime = now;
            currentDelay = generateAttackDelay();
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (!isEnabled() || !silent.getValue()) return;

        if (event.isPre()) {
            if (!hasRotationState) return;

            // ALWAYS send the calculated target yaw in the C03 packet
            // Previously, physicsYaw backread caused the C03 packet to send the 
            // visual camera yaw instead if the player was standing still!
            event.setYaw(lastYaw);
            
            // Intave 12 requires Pitch clamping in some cases to prevent impossible C03 values (-90.0 to 90.0)
            event.setPitch(Math.max(-90f, Math.min(90f, lastPitch)));
        } 
    }

    private void applyRotations(float y, float p) {
        if (silent.getValue()) {
            RotationManager.getInstance().setRotations(y, p);
        } else {
            HadesAPI.player.setYaw(y);
            HadesAPI.player.setPitch(p);
            RotationManager.getInstance().stopRotations();
        }
    }



    private boolean isAuraLookingAtTarget(IEntity target, float y, float p) {
        return isAuraLookingAtTargetAtPos(target, y, p, target.getX(), target.getY(), target.getZ());
    }

    /**
     * Ray-traces from the player's eyes along the given yaw/pitch to check if it
     * intersects the target's hitbox at the specified position.
     * Used with getPrevX/Y/Z() for the previousNetworkYaw check on moving targets.
     */
    private boolean isAuraLookingAtTargetAtPos(IEntity target, float y, float p, double tx, double ty, double tz) {
        // Strict border (0.1f) — exactly matching Vanilla's getCollisionBorderSize() 
        // and GrimAC's raytrace bounds.
        float border = 0.1f;
        double width = target.getWidth() / 2.0;
        
        double minX = tx - width - border;
        double maxX = tx + width + border;
        double minY = ty - border;
        double maxY = ty + target.getHeight() + border;
        double minZ = tz - width - border;
        double maxZ = tz + width + border;

        // Expand AABB along the target's velocity vector so the ray check
        // covers both the current and predicted positions. This fixes the
        // mismatch where faceEntityCustom aims at (pos+velocity) but
        // this check was testing against the raw position.
        double velX = target.getX() - target.getPrevX();
        double velY = target.getY() - target.getPrevY();
        double velZ = target.getZ() - target.getPrevZ();
        if (velX < 0) minX += velX; else maxX += velX;
        if (velY < 0) minY += velY; else maxY += velY;
        if (velZ < 0) minZ += velZ; else maxZ += velZ;

        return RotationUtil.isRayIntersectingAABB(
            HadesAPI.player.getX(), 
            HadesAPI.player.getY() + 1.62,
            HadesAPI.player.getZ(),
            y, p, 
            minX, minY, minZ, maxX, maxY, maxZ, reach.getValue()
        );
    }

    /**
     * Smoothly eases the Aura's internal tracking angles back into the real mouse angles
     * instead of snapping or freezing when a target dies or moves out of range.
     */
    private void decayRotations(boolean tick) {
        if (!hasRotationState) return;

        float playerYaw = HadesAPI.player.getYaw();
        float playerPitch = HadesAPI.player.getPitch();

        // Shift physical camera to Aura's coordinate space without breaking native GCD
        // This avoids AimModulo360 delta > 320 trapping.
        float diff = playerYaw - lastYaw;
        while (diff <= -180f) { playerYaw += 360f; diff += 360f; }
        while (diff > 180f) { playerYaw -= 360f; diff -= 360f; }

        HadesAPI.player.setYaw(playerYaw);
        HadesAPI.player.setPrevYaw(playerYaw);

        float yawSpd = yawSpeed.getValue().floatValue();
        // Multiply yawSpd by 1.5 for faster, snappier un-targeting decay that feels responsive
        float[] decayed = com.hades.client.util.RotationUtil.smoothRotation(
                lastYaw, lastPitch, playerYaw, playerPitch, yawSpd * 1.5f);
                
        // CRITICAL FIX: Smooth decay MUST be converted to valid GCD steps to bypass AimModulo360
        decayed = com.hades.client.util.RotationUtil.applyGCD(decayed[0], decayed[1], lastYaw, lastPitch);
                
        lastYaw = decayed[0];
        lastPitch = decayed[1];

        float yawDiff = Math.abs(lastYaw - playerYaw);
        float pitchDiff = Math.abs(lastPitch - playerPitch);

        if (yawDiff <= 2.0f && pitchDiff <= 2.0f) {
            hasRotationState = false;
            RotationManager.getInstance().stopRotations();

            com.hades.client.hook.hooks.MoveEntityWithHeadingHook.auraSideways = false;

            return;
        }

        if (tick) {
            applyRotations(lastYaw, lastPitch);
        }
    }

    private boolean canSee(IEntity target) {
        if (target == null) return false;
        double eyeX = HadesAPI.player.getX();
        double eyeY = HadesAPI.player.getY() + HadesAPI.player.getEyeHeight();
        double eyeZ = HadesAPI.player.getZ();

        double tx = target.getX();
        double ty = target.getY() + target.getHeight() / 2.0;
        double tz = target.getZ();

        com.hades.client.api.interfaces.TraceResult result = HadesAPI.world.rayTraceBlocks(eyeX, eyeY, eyeZ, tx, ty, tz);
        return result == null || !result.didHit;
    }

    // The methods are now centralized in com.hades.client.util.RotationUtil

    private void attack(IEntity target) {
        if (target == null) return;

        boolean wasBlocking = com.hades.client.combat.CombatState.getInstance().isBlocking();
        
        // GrimAC / Intave bypass: We must ALWAYS send C07 (Unblock) BEFORE C0A (Swing). 
        if (wasBlocking) {
            HadesAPI.network.sendUnblockPacket();
            // Optional: visually unblock for half a tick to look legit
            HadesAPI.mc.setVisuallyBlocking(false);
            com.hades.client.combat.CombatState.getInstance().setBlocking(false);
        }


        // VANILLA 1.8.9 PlayerControllerMP perfectly syncs C02 UseEntity, 
        // enchantments, critical hits, and MOST IMPORTANTLY: Sprint knockback slowdown!
        // It drops our local motionX and motionZ by 0.6D when we hit an opponent!
        // This is exactly what the Anticheat simulates.
        // Vanilla 1.8.9 clickMouse() order: C0A (Swing Animation) THEN C02 (UseEntity).
        // Intave Crypta and Vulcan explicitly check for this 1.8.9 packet order!
        HadesAPI.player.swingItem(); // C0A AND visual swing
        HadesAPI.mc.attackEntity(HadesAPI.player, target); // C02 + Local Physics + Sprint Drop
        attackedThisTick = true;

        // Let vanilla's attack naturally drop sprint. The speed desync between
        // cached landMovementFactor and the actual sprint state is handled by
        // MoveEntityWithHeadingHook.syncLandMovementFactor().
    }


    private int generateAttackDelay() {
        int min = minCps.getValue().intValue();
        int max = maxCps.getValue().intValue();
        if (min > max) min = max;

        // Base CPS target for this click
        int cpsTarget = min == max ? min : min + random.nextInt(max - min + 1);
        if (cpsTarget <= 0) return 1000;

        // Base mathematical delay in ms
        double baseDelay = 1000.0 / cpsTarget;

        // Bypassing Crypta Engine: Humans don't have uniform flat random noise.
        // We use a Gaussian (normal) distribution to simulate muscle fatigue and physical jitter.
        // Standard deviation of ~18% creates natural clumping around the target CPS.
        double noiseStdDev = baseDelay * 0.18; 
        double gaussianNoise = random.nextGaussian() * noiseStdDev;

        int finalDelay = (int) Math.round(baseDelay + gaussianNoise);

        // Sanity clamp
        if (finalDelay < 50) finalDelay = 50; 
        if (finalDelay > 500) finalDelay = 500;

        return finalDelay;
    }

}

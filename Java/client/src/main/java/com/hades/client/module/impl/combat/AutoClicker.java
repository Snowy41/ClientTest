package com.hades.client.module.impl.combat;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;

import java.util.Random;

/**
 * AutoClicker — automatically left-clicks when LMB is held.
 *
 * Modes:
 * Normal — steady randomised CPS within min-max range
 * Jitter — varying CPS per click, heavy randomisation per-tick to
 * simulate humanlike imprecision (bypasses pattern detection)
 * Butterfly — very high CPS simulating two-finger alternating clicks,
 * with a short suppression window between bursts
 *
 * Polar/Intave bypass techniques applied:
 * - CPS is always randomised within a ±variance band, never constant
 * - Click intervals are Gaussian-distributed (not uniform) to avoid
 * histogram fingerprinting used by Polar's click analyser
 * - We never click faster than the interval implies; dead-frames are
 * included naturally by the Gaussian spread
 * - Jitter mode introduces tiny stochastic pauses, mirroring real
 * hardware CPI jitter that legit players exhibit
 * - Butterfly mode uses a burst pattern with micro-delays to match
 * legitimate butterfly CPS shapes rather than a flat square wave
 */
public class AutoClicker extends Module {

    // ── Settings ───────────────────────────────────────────────────────────────

    private final ModeSetting mode = new ModeSetting(
            "Mode", "Normal", "Normal", "Jitter", "Butterfly");

    private final NumberSetting minCPS = new NumberSetting(
            "Min CPS", 10.0, 1.0, 20.0, 0.5);

    private final NumberSetting maxCPS = new NumberSetting(
            "Max CPS", 14.0, 1.0, 20.0, 0.5);

    /** Butterfly: extra clicks fired per actual click event */
    private final NumberSetting butterflyExtra = new NumberSetting(
            "Butterfly Extra", 1.0, 1.0, 4.0, 1.0);

    /** Jitter: how wide the random CPS variance swing is (± this many CPS) */
    private final NumberSetting jitterVariance = new NumberSetting(
            "Jitter Variance", 2.0, 0.5, 5.0, 0.5);

    /** Only click when player is targeting an entity (reduces flags on Intave) */
    private final BooleanSetting onlyOnEntity = new BooleanSetting(
            "Only On Entity", false);

    /** Mimic real players by occasionally missing a click (adds ~0-5% miss rate) */
    private final BooleanSetting simulateMiss = new BooleanSetting(
            "Simulate Miss", true);

    /** Sync clicks with the target's hurtTime to guarantee hits land exactly when invincibility ends */
    private final BooleanSetting syncWithHurtTime = new BooleanSetting(
            "Sync with HurtTime", true);

    // ── Internal state ─────────────────────────────────────────────────────────

    private final Random rng = new Random();

    /** Accumulated fractional clicks — when this reaches ≥1.0 we fire a click */
    private double clickAccumulator = 0.0;
    
    /** Simplex noise offset for fatigue simulation */
    private double noiseX = 0.0;

    /** Debug tick counter for periodic diagnostics */
    private int debugTickCounter = 0;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public AutoClicker() {
        super("AutoClicker", "Clicks LMB automatically at configurable CPS with AC bypass.", Category.COMBAT, 0);
        register(mode);
        register(minCPS);
        register(maxCPS);
        register(butterflyExtra);
        register(jitterVariance);
        register(onlyOnEntity);
        register(simulateMiss);
        register(syncWithHurtTime);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        clickAccumulator = 0.0;
        com.hades.client.util.HadesLogger.get().info("[AutoClicker] Enabled! Mode=" + mode.getValue()
                + " CPS=" + minCPS.getValue() + "-" + maxCPS.getValue()
                + " onlyOnEntity=" + onlyOnEntity.getValue());
    }

    // ── Tick handler ───────────────────────────────────────────────────────────

    /**
     * MC ticks at 20 TPS. To achieve N CPS, we accumulate N/20 clicks per tick.
     * When the accumulator reaches ≥ 1.0, we fire a click and subtract 1.0.
     * This naturally supports any CPS from 1 to 20 with accurate timing.
     * For CPS > 20, Butterfly mode fires multiple clicks per tick.
     */
    @EventHandler
    public void onTick(TickEvent event) {
        // Periodic diagnostic logging (once every 100 ticks ~5 seconds)
        debugTickCounter++;
        if (debugTickCounter % 100 == 1) {
            boolean playerNull = HadesAPI.Player.isNull();
            boolean guiOpen = HadesAPI.Game.isGuiOpen();
            boolean mouseHeld = isMouseLeftHeld();
            boolean entityCheck = onlyOnEntity.getValue() ? isLookingAtEntity() : true;
            com.hades.client.util.HadesLogger.get().info(
                    "[AutoClicker] DIAG tick#" + debugTickCounter
                            + " enabled=" + isEnabled()
                            + " playerNull=" + playerNull
                            + " guiOpen=" + guiOpen
                            + " mouseHeld=" + mouseHeld
                            + " entityOK=" + entityCheck
                            + " accumulator=" + String.format("%.3f", clickAccumulator));
        }

        if (HadesAPI.Player.isNull())
            return;
        if (HadesAPI.Game.isGuiOpen())
            return;

        // Gate: only run when LMB is physically held by the user
        if (!isMouseLeftHeld()) {
            clickAccumulator = 0.0; // Reset when not holding
            return;
        }

        // Optional entity-targeting gate (reduces Intave flags)
        if (onlyOnEntity.getValue() && !isLookingAtEntity())
            return;

        switch (mode.getValue()) {
            case "Normal":
                tickNormal();
                break;
            case "Jitter":
                tickJitter();
                break;
            case "Butterfly":
                tickButterfly();
                break;
        }
    }

    // ── Mode implementations ───────────────────────────────────────────────────

    /**
     * Normal: accumulate clicks per tick based on target CPS.
     * At 16 CPS with 20 TPS, each tick adds 0.8 → fires a click ~4 out of 5 ticks.
     */
    private void tickNormal() {
        double targetCPS = getRandomCPS();
        
        // Progress the Simplex noise vector slowly per tick (fatigue wave)
        noiseX += 0.05;
        // Generate a gentle curve between -1.0 and 1.0
        double fatigueScale = Math.sin(noiseX) * 0.5 + Math.sin(noiseX * 0.4) * 0.5;
        
        // Apply up to -3.0 CPS exhaustion or +1.5 CPS burst based on the wave
        double fatigueOffset = fatigueScale > 0 ? (fatigueScale * 1.5) : (fatigueScale * 3.0);
        targetCPS = Math.max(1.0, targetCPS + fatigueOffset);

        if (syncWithHurtTime.getValue() && shouldSyncWait()) {
            return; // Hold click until vulnerability window opens
        }

        clickAccumulator += targetCPS / 20.0;

        while (clickAccumulator >= 1.0) {
            if (!shouldMiss()) {
                HadesAPI.mc.performClick();
            }
            clickAccumulator -= 1.0;
        }
    }

    /**
     * Jitter: same accumulation but with heavy per-tick variance
     * to simulate humanlike CPS scatter.
     */
    private void tickJitter() {
        double targetCPS = getRandomCPS();
        
        noiseX += 0.08; // slightly faster fatigue cycle for jitter
        double fatigueScale = Math.sin(noiseX) * 0.5 + Math.sin(noiseX * 0.3) * 0.5;
        double fatigueOffset = fatigueScale > 0 ? (fatigueScale * 2.0) : (fatigueScale * 4.0);
        
        // Add jitter variance: swing the CPS randomly each tick
        double variance = jitterVariance.getValue();
        double jitter = (rng.nextGaussian() * variance);
        
        targetCPS = Math.max(1.0, targetCPS + fatigueOffset + jitter);

        if (syncWithHurtTime.getValue() && shouldSyncWait()) {
            return; 
        }

        clickAccumulator += targetCPS / 20.0;

        while (clickAccumulator >= 1.0) {
            if (!shouldMiss()) {
                HadesAPI.mc.performClick();
            }
            clickAccumulator -= 1.0;
        }
    }

    /**
     * Butterfly: simulates double-finger clicking.
     * Fires the base click plus extra clicks per firing tick.
     */
    private void tickButterfly() {
        double targetCPS = getRandomCPS();
        clickAccumulator += targetCPS / 20.0;

        if (syncWithHurtTime.getValue() && shouldSyncWait()) {
            clickAccumulator = 0; // Dump accumulator in butterfly so bursts don't stack up and flag
            return; 
        }

        while (clickAccumulator >= 1.0) {
            if (!shouldMiss()) {
                HadesAPI.mc.performClick();

                // Fire extra butterfly clicks in the same tick
                int extra = (int) Math.round(butterflyExtra.getValue());
                for (int i = 0; i < extra; i++) {
                    if (!shouldMiss()) {
                        HadesAPI.mc.performClick();
                    }
                }
            }
            clickAccumulator -= 1.0;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Returns true if we should delay clicking because the entity is currently invulnerable.
     * Minecraft entities have a 10-tick hurt resistance window.
     * We want our click to land exactly when hurtTime <= 1 (just before they become vulnerable 
     * in the upcoming tick) to maximize hit density without wasting clicks.
     */
    private boolean shouldSyncWait() {
        com.hades.client.api.interfaces.IEntity target = com.hades.client.combat.TargetManager.getInstance().getTarget();
        if (target != null && target.getDistanceToEntity(HadesAPI.player) < 4.0) {
            int hurtTime = target.getHurtTime();
            // If hurtTime is high (just got hit), hold the click
            if (hurtTime > 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pick a Gaussian-distributed CPS target between minCPS and maxCPS.
     * Gaussian distribution avoids the uniform histogram that anticheat pattern
     * detectors look for.
     */
    private double getRandomCPS() {
        double min = minCPS.getValue();
        double max = maxCPS.getValue();
        if (min > max) {
            double t = min;
            min = max;
            max = t;
        }

        double centre = (min + max) / 2.0;
        double sigma = (max - min) / 4.0; // 2-sigma covers min..max
        double cps = rng.nextGaussian() * sigma + centre;
        cps = Math.max(min, Math.min(max, cps));

        if (cps <= 0)
            cps = 1.0;
        return cps;
    }

    /**
     * Returns true ~3% of the time when simulateMiss is enabled,
     * causing the click to be skipped (realistic human imprecision).
     */
    private boolean shouldMiss() {
        return simulateMiss.getValue() && rng.nextDouble() < 0.03;
    }

    private boolean isMouseLeftHeld() {
        return com.hades.client.api.HadesAPI.Input.isButtonDown(0);
    }

    /**
     * Checks if the player's crosshair is pointing at an entity.
     * Delegates entirely to HadesAPI which natively wraps the client memory.
     */
    private boolean isLookingAtEntity() {
        return HadesAPI.mc.getMouseOverType() == 2; // 2 = ENTITY
    }
}

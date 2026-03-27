package com.hades.client.module.impl.combat;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import com.hades.client.combat.TargetManager;


/**
 * AimAssist — smoothly rotates the player toward the nearest valid target.
 *
 * Three aim modes:
 * • Client — directly sets player yaw/pitch (camera moves visually)
 * • Silent — intercepts outgoing C03 packets and overwrites rotation
 *            fields without moving the client camera
 * • Legit  — human-like interpolation with GCD correction to simulate
 *            real mouse movement granularity
 *
 * Anti-detection techniques:
 * - Never snaps: all rotations smoothly interpolate via configurable speed
 * - Per-tick micro-randomization in both yaw and pitch axes
 * - Maximum rotation delta capped per tick to avoid inhuman flags
 * - GCD (Greatest Common Divisor) sensitivity compensation
 * - FOV cone filtering to avoid suspicious 180° snaps
 * - Optional miss simulation through small target lock delays
 */
public class AimAssist extends Module {

    // ── Settings ───────────────────────────────────────────────────────────────

    private final NumberSetting horizontalSpeed = new NumberSetting(
            "Horizontal Speed", "Yaw rotation speed", 3.0, 0.1, 10.0, 0.1);

    private final NumberSetting verticalSpeed = new NumberSetting(
            "Vertical Speed", "Pitch rotation speed", 2.5, 0.1, 10.0, 0.1);

    private final NumberSetting fov = new NumberSetting(
            "FOV", "Field of view cone in degrees", 90.0, 10.0, 360.0, 5.0);

    private final NumberSetting targetRange = new NumberSetting(
            "Target Range", "Maximum distance to consider targets", 4.0, 1.0, 6.0, 0.1);


    private final ModeSetting targetPriority = new ModeSetting(
            "Priority", "Closest", "Closest", "Lowest Health", "Crosshair");

    private final ModeSetting aimMode = new ModeSetting(
            "Aim Mode", "Legit", "Client", "Silent", "Legit");

    private final BooleanSetting randomizationEnabled = new BooleanSetting(
            "Randomization", true);


    private final BooleanSetting onlyOnClick = new BooleanSetting(
            "Only On Click", false);

    private final BooleanSetting weaponOnly = new BooleanSetting(
            "Weapon Only", false);

    private final BooleanSetting ignoreInvisible = new BooleanSetting(
            "Ignore Invisible", true);

    private final BooleanSetting throughWalls = new BooleanSetting(
            "Through Walls", false);

    private final BooleanSetting lockTarget = new BooleanSetting(
            "Lock Target", true);


    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public AimAssist() {
        super("AimAssist", "Smoothly aims toward targets with AC bypass.", Category.COMBAT, 0);
        register(aimMode);
        register(targetPriority);
        register(horizontalSpeed);
        register(verticalSpeed);
        register(fov);
        register(targetRange);
        register(randomizationEnabled);
        register(onlyOnClick);
        register(weaponOnly);
        register(ignoreInvisible);
        register(throughWalls);
        register(lockTarget);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        HadesLogger.get().info("[AimAssist] Enabled. Mode=" + aimMode.getValue()
                + " Priority=" + targetPriority.getValue());
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        com.hades.client.combat.RotationManager.getInstance().stopRotations();
    }

    // ── Tick handler ───────────────────────────────────────────────────────────

    @EventHandler
    public void onPreRender(com.hades.client.event.events.PreRenderEvent event) {
        if (HadesAPI.Player.isNull()) return;
        if (HadesAPI.Game.isGuiOpen()) return;

        // Don't aim when dead
        if (HadesAPI.Player.getHealth() <= 0f) return;

        // Only-on-click gate
        if (onlyOnClick.getValue() && !isMouseLeftHeld()) {
            return;
        }

        // Weapon-only gate
        if (weaponOnly.getValue() && !isHoldingWeapon()) {
            return;
        }

        // ─── Target acquisition ───────────────────────────────────────
        TargetManager tm = TargetManager.getInstance();
        
        // Push local settings to TargetManager priority system
        tm.setConfig(targetRange.getValue(), fov.getValue().floatValue(), targetPriority.getValue());
        
        com.hades.client.api.interfaces.IEntity target = tm.getTarget();

        if (target == null) {
            com.hades.client.combat.RotationManager.getInstance().stopRotations();
            return;
        }

        // ─── Utilize universal Rotation system (proven from KillAura) ─────
        float currentYaw = HadesAPI.Player.getYaw();
        float currentPitch = HadesAPI.Player.getPitch();

        // Speed mapping: AimAssist 1-10 is roughly mapped to KillAura's 10-180 scale.
        float yawSpd = horizontalSpeed.getValue().floatValue() * 18.0f;
        float pitchSpd = verticalSpeed.getValue().floatValue() * 18.0f;

        // Jittered rotations (with AC randomization) — for server-side C03 packets
        float[] serverRots = com.hades.client.util.RotationUtil.faceEntityCustom(
                target, yawSpd, pitchSpd,
                currentYaw, currentPitch,
                false
        );
        float serverYaw = serverRots[0];
        float serverPitch = Math.max(-90f, Math.min(90f, serverRots[1]));

        // Always push jittered rotations to RotationManager → overrides C03 packets
        com.hades.client.combat.RotationManager.getInstance().setRotations(serverYaw, serverPitch);

        switch (aimMode.getValue()) {
            case "Client":
            case "Legit":
                // Clean rotations (no jitter) — smooth visual camera
                float[] cleanRots = com.hades.client.util.RotationUtil.faceEntityClean(
                        target, yawSpd, pitchSpd,
                        currentYaw, currentPitch
                );
                HadesAPI.player.setYaw(cleanRots[0]);
                HadesAPI.player.setPitch(Math.max(-90f, Math.min(90f, cleanRots[1])));
                break;
            case "Silent":
                // Camera untouched — only packets get jittered rotation
                break;
        }
    }


    // ── Target acquisition logic removed ──
    // AimAssist now properly delegates to the global TargetManager.
    
    // ── Helpers ────────────────────────────────────────────────────────────────


    /**
     * Checks if LMB is physically held via LWJGL Mouse.
     */
    private boolean isMouseLeftHeld() {
        return com.hades.client.api.HadesAPI.Input.isButtonDown(0);
    }

    /**
     * Checks if the player is holding a weapon (sword, axe, or bow).
     * Uses the held item's class hierarchy to detect weapon types.
     */
    private boolean isHoldingWeapon() {
        return HadesAPI.player.isHoldingWeapon();
    }
}

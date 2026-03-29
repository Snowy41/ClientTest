package com.hades.client.module.impl.combat;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;

import com.hades.client.combat.ProjectileTracker;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.RotationUtil;

public class BowAimbot extends Module {

    private final BooleanSetting silent = new BooleanSetting("Silent", true);
    private final BooleanSetting predict = new BooleanSetting("Predict", true);
    private final BooleanSetting autoRelease = new BooleanSetting("AutoRelease", false);
    private final NumberSetting fov = new NumberSetting("FOV", 180.0, 10.0, 360.0, 1.0);

    private static Float activeBowYaw = null;
    private static Float activeBowPitch = null;

    private IEntity target;

    public BowAimbot() {
        super("BowAimbot", "Automatically aims your bow.", Category.COMBAT, 0);
        register(silent);
        register(predict);
        register(autoRelease);
        register(fov);
    }

    @Override
    public void onEnable() {
        activeBowYaw = null;
        activeBowPitch = null;
        target = null;
    }

    @Override
    public void onDisable() {
        activeBowYaw = null;
        activeBowPitch = null;
        target = null;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (HadesAPI.player == null || HadesAPI.world == null || HadesAPI.mc == null || !HadesAPI.mc.isInGame()) return;

        // --- Determine held item type (Matched from Trajectories) ---
        int slot = com.hades.client.manager.InventoryManager.getInstance().getHeldItemSlot();
        if (slot == -1) { resetAim(); return; }
        com.hades.client.api.interfaces.IItemStack itemStack = com.hades.client.manager.InventoryManager.getInstance().getSlot(slot);
        if (itemStack == null || itemStack.isNull()) { resetAim(); return; }
        String name = itemStack.getItem().getUnlocalizedName().toLowerCase();

        if (!name.contains("bow")) { resetAim(); return; }

        // --- Bow charge power (Matched from Trajectories) ---
        double shootPower = HadesAPI.player.getItemInUseDuration();
        shootPower /= 20.0;
        shootPower = ((shootPower * shootPower) + (shootPower * 2.0)) / 3.0;
        
        if (shootPower < 0.1) { resetAim(); return; }
        if (shootPower > 1.0) shootPower = 1.0;

        if (target == null || !isValidBowTarget(target)) {
            target = HadesAPI.world.getLoadedEntities().stream()
                    .filter(this::isValidBowTarget)
                    .min(java.util.Comparator.comparingDouble(this::getCrosshairScore))
                    .orElse(null);
        }

        if (target != null) {
            float[] rots = ProjectileTracker.calculateBowAim(target, (float) shootPower, predict.getValue());
            float targetYaw = rots[0];
            float targetPitch = rots[1];

            // Setup current rotation for smoothing
            float currentYaw = activeBowYaw != null ? activeBowYaw : HadesAPI.Player.getYaw();
            float currentPitch = activeBowPitch != null ? activeBowPitch : HadesAPI.Player.getPitch();

            // Smooth track towards the calculated trajectory (Prevents aimbot from instantly snapping and clipping)
            float smoothedYaw = RotationUtil.updateRotationStatic(currentYaw, targetYaw, 120.0f);
            float smoothedPitch = RotationUtil.updateRotationStatic(currentPitch, targetPitch, 120.0f);

            float[] gcdFixed = RotationUtil.applyGCD(smoothedYaw, smoothedPitch, 
                HadesAPI.Player.getYaw(), HadesAPI.Player.getPitch());

            activeBowYaw = gcdFixed[0];
            activeBowPitch = gcdFixed[1];

            if (!silent.getValue()) {
                HadesAPI.Player.setYaw(activeBowYaw);
                HadesAPI.Player.setPitch(activeBowPitch);
            }
            
            // AutoRelease logic
            if (autoRelease.getValue() && HadesAPI.player.getItemInUseDuration() >= 20) {
                // Not cleanly actionable without Left/Right click emulation API being mapped yet. 
            }
        } else {
            resetAim();
        }
    }

    private void resetAim() {
        activeBowYaw = null;
        activeBowPitch = null;
        target = null;
    }

    private boolean isValidBowTarget(IEntity e) {
        if (e.getRaw() == HadesAPI.player.getRaw() || !e.isLiving() || !e.isPlayer()) return false;
        if (e.isInvisible() || e.getHealth() <= 0 || Float.isNaN(e.getHealth())) return false;
        if (com.hades.client.module.impl.misc.Friends.isFriend(e)) return false;
        
        float currentFov = fov.getValue().floatValue();
        if (currentFov < 360f && !com.hades.client.combat.CombatUtil.isWithinFOV(e, currentFov)) return false;
        if (com.hades.client.combat.CombatUtil.getDistanceToBox(HadesAPI.player, e) > 120.0) return false;
        
        return true;
    }

    private double getCrosshairScore(IEntity e) {
        double pX = HadesAPI.player.getX();
        double pZ = HadesAPI.player.getZ();
        float yawToTarget = (float) Math.toDegrees(Math.atan2(e.getZ() - pZ, e.getX() - pX)) - 90f;
        float angleDiff = Math.abs(com.hades.client.combat.CombatUtil.getAngleDifference(HadesAPI.Player.getYaw(), yawToTarget));
        double dist = com.hades.client.combat.CombatUtil.getDistanceToBox(HadesAPI.player, e);
        return angleDiff + (dist * 0.5);
    }

    @EventHandler(priority = -5)
    public void onMotion(MotionEvent event) {
        if (event.isPre() && silent.getValue() && activeBowYaw != null && activeBowPitch != null) {
            event.setYaw(activeBowYaw);
            event.setPitch(activeBowPitch);
        }
    }

    public static Float getActiveBowYaw() {
        return activeBowYaw;
    }
}

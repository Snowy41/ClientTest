package com.hades.client.module.impl.movement;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.MotionEvent;

import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import org.lwjgl.input.Keyboard;

public class Sprint extends Module {

    private final BooleanSetting multiDir = new BooleanSetting("Multi-Directional", false);
    private final BooleanSetting onlyInAir = new BooleanSetting("Only In Air", false);
    private final BooleanSetting silent = new BooleanSetting("Silent", true);
    private int onGroundTicks;

    public Sprint() {
        super("Sprint", "Sprints for you", Category.MOVEMENT, 0);
        register(multiDir);
        register(onlyInAir);
        // Silent is inherently incompatible with GrimAC Simulation checks, so we remove the logic
        // but keep the setting registered so old configs don't crash.
        register(silent);
    }



    @EventHandler
    public void onTick(TickEvent event) {
        if (HadesAPI.Player.isNull()) return;

        // Failsafe OS-level physical state detection
        boolean holdingW = Keyboard.isKeyDown(Keyboard.KEY_W) || HadesAPI.Game.isKeyForwardDown();
        boolean holdingA = Keyboard.isKeyDown(Keyboard.KEY_A);
        boolean holdingS = Keyboard.isKeyDown(Keyboard.KEY_S);
        boolean holdingD = Keyboard.isKeyDown(Keyboard.KEY_D);
        boolean holdingShift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);

        // Vanilla accurately scales motionX / motionZ by 0.6 upon attacking mathematically BEFORE packet generation, effectively avoiding network desyncs.
        // We do NOT enforce multi-tick cooldowns here, as keeping sprint pressed bypasses the C0B transmission natively preserving visual FOV and W-Tap smoothness.

        boolean allowMultiDirSprint = multiDir.getValue()
                && (holdingW || holdingA || holdingS || holdingD)
                && (!onlyInAir.getValue() || onGroundTicks < 2);

        if (holdingW || allowMultiDirSprint) {
            HadesAPI.Game.setKeySprintPressed(true);
        }
        
        if (holdingShift || HadesAPI.Player.isSneaking()) {
            return; 
        }

        // Reverting back to native setSprinting(true) instead of setKeySprintPressed
        // because setKeySprint pressed only works if the keys are physically held and 
        // food allows it, which completely broke Sprint for toggle-sprint users!
        // The ONLY rule GrimAC cares about is that you CANNOT override the 0.6x hit-slowdown
        // natively applied on the exact tick of an attack.
        // So we just skip setting sprint to true on the exact tick we attack!
        if (allowMultiDirSprint || holdingW) {
            // DO NOT force sprint if the Silent rotation mathematically forces a speed drop!
            if (!com.hades.client.hook.hooks.MoveEntityWithHeadingHook.auraSideways) {
                if (!com.hades.client.module.impl.combat.KillAura.attackedThisTick) {
                    HadesAPI.Player.setSprinting(true);
                }
            }
        }
    }

    @EventHandler
    public void onMotion(final MotionEvent event) {
        if (!event.isPre() || HadesAPI.Player.isNull()) return;

        if (event.isOnGround()) {
            onGroundTicks++;
        } else {
            onGroundTicks = 0;
        }
    }
    
    // onPacket removed to prevent silent START_SPRINTING drops which caused GrimAC Simulation flags
}

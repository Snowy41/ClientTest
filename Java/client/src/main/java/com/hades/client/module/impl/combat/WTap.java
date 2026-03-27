package com.hades.client.module.impl.combat;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.api.HadesAPI;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * Advanced WTap — Manipulates physical engine deceleration alongside sprint logic 
 * to provide maximum comboing and flawless Anticheat evasion.
 */
public class WTap extends Module {

    private final NumberSetting chance = new NumberSetting("Chance", "Chance to W-Tap per hit (%)", 100.0, 0.0, 100.0, 1.0);
    private final NumberSetting delay = new NumberSetting("Delay", "Ticks to unsprint (1-3)", 1.0, 1.0, 3.0, 1.0);
    private final NumberSetting minRange = new NumberSetting("Min Range", "Minimum distance to WTap", 2.0, 0.0, 6.0, 0.1);
    private final NumberSetting maxRange = new NumberSetting("Max Range", "Maximum distance to WTap", 6.0, 0.0, 6.0, 0.1);

    private boolean resetting = false;
    private int resetTicks = 0;
    private final Random random = new Random();

    public WTap() {
        super("WTap", "Resets momentum via physical engine keys for max knockback.", Category.COMBAT, 0);
        register(chance);
        register(delay);
        register(minRange);
        register(maxRange);
    }

    @Override
    public void onEnable() {
        resetting = false;
        resetTicks = 0;
    }

    @Override
    public void onDisable() {
        if (resetting) {
            HadesAPI.mc.setKeyForwardPressed(Keyboard.isKeyDown(Keyboard.KEY_W));
            if (!HadesAPI.Player.isSneaking()) {
                sendSprintPacket(true);
            }
            resetting = false;
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;

        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player == null) return;

        if (resetting) {
            // Legit Physics Engine Manipulation: Force momentum decay in vanilla movement
            HadesAPI.mc.setKeyForwardPressed(false);
            
            resetTicks--;
            if (resetTicks <= 0) {
                // Restore physical input naturally 
                HadesAPI.mc.setKeyForwardPressed(Keyboard.isKeyDown(Keyboard.KEY_W));

                // Re-sprint ONLY if legitimately allowed
                if (!HadesAPI.Player.isSneaking() && !HadesAPI.Player.isCollidedHorizontally()) {
                    sendSprintPacket(true);
                }
                resetting = false;
            }
        }
    }

    @EventHandler
    public void onPacketSend(com.hades.client.event.events.PacketEvent.Send event) {
        if (!isEnabled() || event.isCancelled()) return;

        Object packet = event.getPacket();
        if (packet == null) return;

        String name = com.hades.client.util.PacketMapper.getPacketName(packet);
        if (name.equals("C02PacketUseEntity")) {
            com.hades.client.combat.CombatState state = com.hades.client.combat.CombatState.getInstance();
            
            // Block natively handles knockback resets
            if (state.isBlocking()) return;
            
            // Combo WTap Scaling
            double dist = 0.0;
            com.hades.client.api.interfaces.IEntity target = com.hades.client.combat.TargetManager.getInstance().getTarget();
            if (target != null) {
                dist = target.getDistanceToEntity(HadesAPI.player);
            } else {
                int entityId = HadesAPI.network.getPacketEntityId(packet);
                for (com.hades.client.api.interfaces.IEntity e : HadesAPI.world.getLoadedEntities()) {
                    if (e.getEntityId() == entityId) {
                        dist = e.getDistanceToEntity(HadesAPI.player);
                        break;
                    }
                }
            }

            boolean inRange = true;
            if (dist > 0.0) {
                inRange = dist >= minRange.getValue().doubleValue() && dist <= maxRange.getValue().doubleValue();
            }
            
            if (inRange && HadesAPI.player.isSprinting() && random.nextInt(100) < chance.getValue().intValue()) {
                resetting = true;
                resetTicks = delay.getValue().intValue();
                HadesAPI.mc.setKeyForwardPressed(false);
            }
        }
    }
    
    private void sendSprintPacket(boolean start) {
        com.hades.client.api.HadesAPI.Player.setSprinting(start);
    }
}

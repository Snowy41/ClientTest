package com.hades.client.module.impl.combat;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.api.HadesAPI;
import com.hades.client.combat.TargetManager;

import java.util.Random;

/**
 * Velocity — Reduces or modifies knockback received from other players.
 *
 * Modes:
 * • Blatant   — directly scales or cancels S12/S27 velocity packets
 * • Jump      — cancels horizontal KB and forces a jump instead
 * • Legit     — applies post-hit friction to reduce effective KB
 * • Reverse   — inverts horizontal velocity to move INTO the attacker,
 *               effectively negating knockback distance
 */
public class Velocity extends Module {
    
    private final ModeSetting mode = new ModeSetting("Mode", "Blatant", "Blatant", "Jump", "Legit", "Reverse", "GrimAC");
    private final NumberSetting horizontal = new NumberSetting("Horizontal %", 0.0, 0.0, 100.0, 1.0);
    private final NumberSetting vertical = new NumberSetting("Vertical %", 0.0, 0.0, 100.0, 1.0);
    private final NumberSetting chance = new NumberSetting("Chance %", 100.0, 0.0, 100.0, 1.0);
    private final BooleanSetting targetOnly = new BooleanSetting("Target Only", false);

    // Reverse mode settings
    private final NumberSetting reverseStrength = new NumberSetting("Reverse %", "Strength of reverse motion", 100.0, 50.0, 200.0, 5.0);
    private final NumberSetting reverseTicks = new NumberSetting("Reverse Ticks", "How many ticks to apply reverse", 3.0, 1.0, 10.0, 1.0);

    private final Random random = new Random();
    
    // Reverse mode state
    private int reverseTickCounter = 0;
    private boolean reversing = false;

    public Velocity() {
        super("Velocity", "Reduces or handles knockback to prevent being thrown around.", Category.COMBAT, 0);
        
        // Show horizontal/vertical only for Blatant and Jump
        horizontal.setVisibility(() -> {
            String m = mode.getValue();
            return m.equals("Blatant") || m.equals("Legit");
        });
        vertical.setVisibility(() -> {
            String m = mode.getValue();
            return m.equals("Blatant") || m.equals("Jump");
        });
        // Show reverse settings only in Reverse mode
        reverseStrength.setVisibility(() -> mode.getValue().equals("Reverse"));
        reverseTicks.setVisibility(() -> mode.getValue().equals("Reverse"));
        
        register(mode);
        register(horizontal);
        register(vertical);
        register(chance);
        register(targetOnly);
        register(reverseStrength);
        register(reverseTicks);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reversing = false;
        reverseTickCounter = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reversing = false;
        reverseTickCounter = 0;
    }

    private boolean shouldApplyVelocity() {
        if (chance.getValue().intValue() < 100) {
            if (random.nextInt(100) >= chance.getValue().intValue()) {
                return false;
            }
        }
        
        if (targetOnly.getValue()) {
            if (TargetManager.getInstance().getTarget() == null) {
                return false;
            }
        }
        
        return true;
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        Object packet = event.getPacket();
        if (packet == null) return;

        String modeName = mode.getValue();

        boolean isS12 = HadesAPI.network.isS12Packet(packet);
        boolean isS27 = HadesAPI.network.isS27Packet(packet);

        if (!isS12 && !isS27) return;

        // For S12, verify this velocity packet is for OUR player
        if (isS12) {
            com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
            if (player == null) return;

            int packetEntityId = HadesAPI.network.getS12EntityId(packet);
            int playerId = player.getEntityId();
            
            if (playerId == -1 || packetEntityId != playerId) {
                return; // Not our velocity packet, or couldn't resolve ID
            }
        }

        if (!shouldApplyVelocity()) return;

        switch (modeName) {
            case "Blatant": {
                double hScale = horizontal.getValue() / 100.0;
                double vScale = vertical.getValue() / 100.0;
                
                if (hScale == 0.0 && vScale == 0.0) {
                    // Full cancel — don't let the packet through at all
                    event.setCancelled(true);
                } else {
                    if (isS12) HadesAPI.network.scaleS12Velocity(packet, hScale, vScale);
                    if (isS27) HadesAPI.network.scaleS27Velocity(packet, hScale, vScale);
                }
                break;
            }
            case "Jump": {
                if (isS12 && HadesAPI.Player.isOnGround()) {
                    double vScale = vertical.getValue() / 100.0;
                    // Cancel horizontal knockback entirely
                    HadesAPI.network.scaleS12Velocity(packet, 0.0, vScale);
                    // Force jump to absorb remaining vertical
                    HadesAPI.Player.setMotionY(0.42f);
                }
                break;
            }
            case "Reverse": {
                if (isS12) {
                    // Let vertical through at reduced rate, reverse horizontal
                    HadesAPI.network.scaleS12Velocity(packet, -1.0 * (reverseStrength.getValue() / 100.0), 1.0);
                    reversing = true;
                    reverseTickCounter = reverseTicks.getValue().intValue();
                }
                break;
            }
            case "GrimAC": {
                // GrimAC mathematically simulates all Vanilla 1.8.9 logic.
                // 1. If we are grounded, and we JUMP, the exact tick simulation overwrites motionY = 0.42.
                // 2. If we hit an entity while sprinting, Vanilla natively forces motionX *= 0.6 and motionZ *= 0.6.
                if (isS12 || isS27) {
                    com.hades.client.api.interfaces.IEntity target = TargetManager.getInstance().getTarget();
                    if (target != null && HadesAPI.player.getDistanceToEntity(target) <= 6.0) {
                        try {
                            // Modern GrimAC: 1:1 Simulation prevents S12 scalar modification.
                            // Exploit mechanisms using C0B/C02 desync no longer work natively because S12 is processed independently.
                            // We must apply 1.0x explicitly to avoid "Simulation" flags.
                            if (isS12) HadesAPI.network.scaleS12Velocity(packet, 1.0, 1.0);
                            if (isS27) HadesAPI.network.scaleS27Velocity(packet, 1.0, 1.0);

                            // Send Attack Packet (C02) to sync latency, but DO NOT modify sprint indiscriminately
                            // as spoofing Sprint directly forces GrimAC to evaluate forward=1 inputs.
                            HadesAPI.network.sendAttackPacket(target.getRaw());
                        } catch (Exception ignored) {}
                    }
                }
                break;
            }
            // Legit mode doesn't modify S12 — it works via TickEvent
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        
        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player == null) return;
        
        String modeName = mode.getValue();

        if (modeName.equals("Legit")) {
            int hurtTime = player.getHurtTime();
            if (hurtTime == 9 || hurtTime == 10) {
                if (!shouldApplyVelocity()) return;
                
                double hScale = horizontal.getValue() / 100.0;
                
                if (hScale < 1.0) {
                    double currentMotionX = HadesAPI.Player.getMotionX();
                    double currentMotionZ = HadesAPI.Player.getMotionZ();
                    
                    // Apply friction proportional to setting
                    double friction = 0.6 * hScale;
                    if (friction == 0) friction = 0.2;
                    
                    HadesAPI.Player.setMotionX(currentMotionX * friction);
                    HadesAPI.Player.setMotionZ(currentMotionZ * friction);
                }
            }
        }
        
        // Reverse mode: apply inward motion for a few ticks after hit
        if (modeName.equals("Reverse") && reversing) {
            reverseTickCounter--;
            if (reverseTickCounter <= 0) {
                reversing = false;
            }
        }
    }
}

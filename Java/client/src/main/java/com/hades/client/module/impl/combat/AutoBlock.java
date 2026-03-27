package com.hades.client.module.impl.combat;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.api.HadesAPI;
import com.hades.client.combat.TargetManager;
import com.hades.client.api.interfaces.IEntity;

/**
 * AutoBlock — Advanced block synchronization and prediction pipeline.
 * Features separate modes for manual (Legit) fighting and automated (KillAura) fighting.
 */
public class AutoBlock extends Module {

    private final ModeSetting integration = new ModeSetting("Integration", "KillAura", "KillAura", "Legit");
    private final ModeSetting blockMethod = new ModeSetting("Method", "Interact", "Fake", "Interact", "Post");
    private final NumberSetting range = new NumberSetting("Legit Range", 4.0, 1.0, 6.0, 0.1);

    private boolean reblockNextTick = false;

    public AutoBlock() {
        super("AutoBlock", "Automatically blocks with your sword to mitigate damage.", Category.COMBAT, 0);
        register(integration);
        register(blockMethod);
        register(range);
    }

    @Override
    public void onEnable() {
        reblockNextTick = false;
    }

    @Override
    public void onDisable() {
        com.hades.client.api.interfaces.IPlayer player = HadesAPI.player;
        unblock(player);
        reblockNextTick = false;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        com.hades.client.api.interfaces.IPlayer player = HadesAPI.player;
        if (player == null) return;

        if (!player.isHoldingSword()) {
            unblock(player);
            return;
        }

        boolean shouldBlock = false;

        IEntity target = null;
        if (integration.getValue().equals("KillAura")) {
            target = TargetManager.getInstance().getTarget();
        } else {
            // Legit Mode Integration
            for (IEntity entity : HadesAPI.world.getLoadedEntities()) {
                if (entity == player || !entity.isPlayer() || entity.isInvisible() || entity.getHealth() <= 0) continue;

                double dist = entity.getDistanceToEntity(player);
                if (dist > range.getValue().doubleValue()) continue;

                target = entity;
                break;
            }
        }

        if (target != null) {
            // Chasing / Hunting edge case: If the target is facing roughly the same direction as us (difference < 90),
            // it means we are behind them / chasing them. In this scenario, they cannot hit us, so we never block.
            float yawDiff = Math.abs(com.hades.client.util.RotationUtil.getAngleDifference(target.getYaw(), player.getYaw()));
            boolean isHunting = yawDiff < 90;

            if (!isHunting) {
                // "Smallest amount of time possible": We only block IF they are manifesting an attack (swinging)
                // or if we are actively predicting a combinatorial hit (our hurtTime is about to expire).
                boolean isSwinging = target.isSwingInProgress() || target.getSwingProgress() > 0.0f;
                
                // If we recently took damage and hurtTime is expiring, they can hit us again on the next tick
                boolean predictingComboHit = player.getHurtTime() > 0 && player.getHurtTime() <= 3;
                
                if (isSwinging || predictingComboHit) {
                    shouldBlock = true;
                }
            }
        }

        if (shouldBlock) {
            block(player);
        } else {
            unblock(player);
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        Object packet = event.getPacket();
        if (HadesAPI.network.isC02Packet(packet)) {
            String action = HadesAPI.network.getC02Action(packet);
            if ("ATTACK".equals(action)) {
                if (HadesAPI.player != null && HadesAPI.player.isHoldingSword()) {

                    // LEGIT INTEGRATION CHECK
                    // Vanilla Minecraft (Manual Clicking) does NOT unblock before attacking. This causes native packet flags!
                    // If we are blocking, we must cancel the manual attack, unblock, and inject the corrected sequence.
                    if (integration.getValue().equals("Legit") && com.hades.client.combat.CombatState.getInstance().isBlocking()) {
                        event.setCancelled(true);
                        
                        // 1. Force Unblock (Bypasses Netty Pipeline)
                        HadesAPI.network.sendUnblockPacket();
                        com.hades.client.combat.CombatState.getInstance().setBlocking(false);
                        
                        // 2. Resend Attack (Bypasses Netty Pipeline to avoid recurse)
                        HadesAPI.network.sendPacketDirect(packet);
                        
                        // 3. Re-establish Block State
                        int entityId = HadesAPI.network.getPacketEntityId(packet);
                        doReblock(entityId);
                        return;
                    }

                    // KILLAURA INTEGRATION
                    // KillAura legitimately sends Unblock natively BEFORE the attack sequence.
                    if (integration.getValue().equals("KillAura")) {
                        int entityId = HadesAPI.network.getPacketEntityId(packet);
                        doReblock(entityId);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (!isEnabled()) return;

        // Execute Post-Mode block
        if (event.isPost() && reblockNextTick) {
            if (HadesAPI.player != null && HadesAPI.player.isHoldingSword()) {
                HadesAPI.network.sendBlockPacket(HadesAPI.player.getRaw());
                HadesAPI.mc.setVisuallyBlocking(true);
                com.hades.client.combat.CombatState.getInstance().setBlocking(true);
            }
            reblockNextTick = false;
        }
    }

    private void doReblock(int targetEntityId) {
        String method = blockMethod.getValue();
        if ("Interact".equals(method)) {
            // Interact (Polar/NCP): C02(INTERACT) -> C08(BLOCK)
            IEntity matchedTarget = null;
            for (IEntity e : HadesAPI.world.getLoadedEntities()) {
                if (e.getEntityId() == targetEntityId) {
                    matchedTarget = e;
                    break;
                }
            }

            if (matchedTarget != null) {
                HadesAPI.network.sendInteractPacket(matchedTarget.getRaw());
            }

            HadesAPI.network.sendBlockPacket(HadesAPI.player.getRaw());
            HadesAPI.mc.setVisuallyBlocking(true);
            com.hades.client.combat.CombatState.getInstance().setBlocking(true);

        } else if ("Post".equals(method)) {
            // Post (Intave/GrimAC): C08(BLOCK) at the end of the tick
            reblockNextTick = true;

        } else if ("Fake".equals(method)) {
            // Pure client-side visual
            HadesAPI.mc.setVisuallyBlocking(true);
        }
    }

    private void block(com.hades.client.api.interfaces.IPlayer player) {
        if (!com.hades.client.combat.CombatState.getInstance().isBlocking()) {
            
            // Initial engagement Interact injection
            if (blockMethod.getValue().equals("Interact")) {
                IEntity target = TargetManager.getInstance().getTarget();
                if (target != null) {
                    HadesAPI.network.sendInteractPacket(target.getRaw());
                }
            }

            if (!blockMethod.getValue().equals("Fake")) {
                HadesAPI.network.sendBlockPacket(player.getRaw());
            }
            HadesAPI.mc.setVisuallyBlocking(true);
            com.hades.client.combat.CombatState.getInstance().setBlocking(true);
        }
    }

    private void unblock(com.hades.client.api.interfaces.IPlayer player) {
        if (com.hades.client.combat.CombatState.getInstance().isBlocking()) {
            if (!blockMethod.getValue().equals("Fake")) {
                HadesAPI.network.sendUnblockPacket();
            }
            HadesAPI.mc.setVisuallyBlocking(false);
            com.hades.client.combat.CombatState.getInstance().setBlocking(false);
        }
    }
}

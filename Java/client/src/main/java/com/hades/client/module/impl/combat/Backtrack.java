package com.hades.client.module.impl.combat;

import com.hades.client.api.HadesAPI;
import com.hades.client.combat.TargetManager;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.event.events.Render3DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.PacketMapper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Backtrack — lags other players when they are about to move out of range,
 * allowing you to get additional hits on them.
 *
 * Intercepts incoming entity movement packets (S14/S18) and holds them.
 * The target appears to stay at their old position on your client, while their
 * real (server) position is tracked for the RPI indicator.
 * When conditions no longer apply (out of range, timeout, hit), all held
 * packets are released at once via processPacket (fireChannelRead).
 *
 * Based on Augustus BackTrack logic, adapted to Hades API.
 */
public class Backtrack extends Module {

    private final NumberSetting hitRange = new NumberSetting(
            "Hit Range", "Maximum range to backtrack targets", 4.0, 3.0, 6.0, 0.1);
    private final NumberSetting timerDelay = new NumberSetting(
            "Timer Delay (ms)", "Maximum time to hold packets", 4000.0, 0.0, 10000.0, 100.0);
    private final BooleanSetting esp = new BooleanSetting(
            "ESP", true);
    private final BooleanSetting pingSpoof = new BooleanSetting(
            "Ping Spoof", true);

    // Packet queues
    private final List<Object> packets = new ArrayList<>();
    private final List<DelayedPacket> transactionQueue = new ArrayList<>();

    private static class DelayedPacket {
        public Object packet;
        public long timestamp;

        public DelayedPacket(Object packet) {
            this.packet = packet;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // State
    private boolean blockPackets = false;
    private com.hades.client.api.interfaces.IEntity target = null;
    private long blockStartTime = 0;

    // RPI tracking — real server position of the target
    private double realX, realY, realZ;
    private boolean hasRealPos = false;

    // Slow flush state
    private boolean isFlushing = false;

    public Backtrack() {
        super("Backtrack", "Lags entities when they move out of range to get more hits.", Category.COMBAT, 0);
        register(hitRange);
        register(timerDelay);
        register(esp);
        register(pingSpoof);
    }

    private com.hades.client.combat.CombatState getState() {
        return com.hades.client.combat.CombatState.getInstance();
    }

    @Override
    public void onEnable() {
        blockPackets = false;
        packets.clear();
        transactionQueue.clear();
        target = null;
        hasRealPos = false;
        isFlushing = false;
        blockStartTime = 0;

        // Initialize realPos for all entities to their current serverPos
        for (com.hades.client.api.interfaces.IEntity entity : HadesAPI.world.getLoadedEntities()) {
            if (entity.isLiving()) {
                // No custom fields — we track only our target's real pos
            }
        }
    }

    @Override
    public void onDisable() {
        // Flush ALL held packets immediately
        resetPackets();
        for (DelayedPacket dp : transactionQueue) {
            HadesAPI.network.sendPacketDirect(dp.packet);
        }
        packets.clear();
        transactionQueue.clear();
        blockPackets = false;
        getState().setBacktracking(false);
        isFlushing = false;
        target = null;
        hasRealPos = false;
    }

    // ── Tick: decide whether to block or release ──

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled())
            return;
        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player == null) {
            resetPackets();
            return;
        }

        // Find target
        com.hades.client.api.interfaces.IEntity newTarget = (com.hades.client.api.interfaces.IEntity) TargetManager.getInstance().getTarget();
        if (newTarget != target) {
            target = newTarget;
            if (target != null) {
                realX = target.getX() * 32.0;
                realY = target.getY() * 32.0;
                realZ = target.getZ() * 32.0;
                hasRealPos = true;
            } else {
                hasRealPos = false;
            }
        }

        if (target != null && hasRealPos && !isFlushing) {
            double dist = target.getDistanceToEntity(HadesAPI.player);

            // Should we block?
            // Block if: target is within hit range AND we haven't exceeded timer delay AND LagRange isn't holding
            boolean withinRange = dist < hitRange.getValue().doubleValue();
            boolean withinTime = blockStartTime == 0 ||
                    (System.currentTimeMillis() - blockStartTime) < (long) timerDelay.getValue().doubleValue();

            if (withinRange && withinTime && getState().canDelayPackets(com.hades.client.combat.CombatState.ModuleType.BACKTRACK)) {
                if (!blockPackets) {
                    blockStartTime = System.currentTimeMillis();
                }
                blockPackets = true;
                getState().setBacktracking(true);
            } else {
                // Release — target out of range, time exceeded, or LagRange took over
                blockPackets = false;
                getState().setBacktracking(false);
                fastResetPackets();
                blockStartTime = 0;
            }
        } else if (!isFlushing) {
            // No target — release everything
            blockPackets = false;
            getState().setBacktracking(false);
            fastResetPackets();
            blockStartTime = 0;
        }

        // Handle slow pulsing when flushing (after attack)
        if (isFlushing) {
            pulsePackets(2); // release 2 incoming packets per tick for smooth visuals
            if (packets.isEmpty()) {
                isFlushing = false;
            }
        }

        // Gradual Transaction Drip: Release held C00/C0F responses at a natural rate
        // Capped at 2 per tick to prevent burst detection (which flags Polar as ping spoofing)
        if (pingSpoof.getValue() && !transactionQueue.isEmpty()) {
            List<DelayedPacket> toSend = new ArrayList<>();
            long now = System.currentTimeMillis();
            int sent = 0;
            int maxPerTick = 2; // Mimics gradual connection recovery
            
            for (DelayedPacket dp : transactionQueue) {
                if (sent >= maxPerTick) break;
                
                if (blockPackets) {
                    // While actively tracking, delay by exactly timerDelay for consistent ping emulation
                    long targetDelay = (long) timerDelay.getValue().doubleValue();
                    if (now - dp.timestamp >= targetDelay) {
                        HadesAPI.network.sendPacketDirect(dp.packet);
                        toSend.add(dp);
                        sent++;
                    }
                } else {
                    // Tracking stopped: bleed out transactions gradually (oldest first)
                    HadesAPI.network.sendPacketDirect(dp.packet);
                    toSend.add(dp);
                    sent++;
                }
            }
            transactionQueue.removeAll(toSend);
        }
    }

    // ── Incoming packet handling ──

    @EventHandler
    public synchronized void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || event.isCancelled())
            return;

        Object packet = event.getPacket();
        if (packet == null)
            return;
        String name = PacketMapper.getPacketName(packet);

        // Clear incoming packets on teleport, but let transactions resolve naturally
        if (name.equals("S08PacketPlayerPosLook")) {
            fastResetPackets();
            blockPackets = false;
            getState().setBacktracking(false);
            isFlushing = false;
            return;
        }

        // Track real position from S14 (relative move) and S18 (teleport)
        if (name.startsWith("S14PacketEntity")) {
            trackRealPosFromS14(packet);
        } else if (name.equals("S18PacketEntityTeleport")) {
            trackRealPosFromS18(packet);
        }

        if (blockPackets && target != null && HadesAPI.player != null) {
            if (shouldDelayPacket(packet, name)) {
                synchronized (packets) {
                    packets.add(packet);
                    event.setCancelled(true);
                }
            }
        }
    }

    // ── Outgoing packet handling (ping spoof) ──

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || !pingSpoof.getValue() || event.isCancelled())
            return;

        Object packet = event.getPacket();
        if (packet == null)
            return;
        String name = PacketMapper.getPacketName(packet);

        if (name.equals("C02PacketUseEntity")) {
            if (blockPackets) {
                // We attacked! Trigger slow-flush the incoming packets for smooth visual snap.
                isFlushing = true;
                blockPackets = false;
                getState().setBacktracking(false);
                // DO NOT FLUSH TRANSACTIONS HERE! 
                // The server uses pending transactions to calculate backtrack.
                // If we flush them, GrimAC updates the enemy pos and voids the hit + flags PingSpoof burst!
            }
            return;
        }

        if (!blockPackets && !isFlushing)
            return; // Spoof only when actively tracking

        if (name.equals("C00PacketKeepAlive") || name.equals("C0FPacketConfirmTransaction")) {
            transactionQueue.add(new DelayedPacket(packet));
            event.setCancelled(true);
        }
    }

    // ── Rendering ──

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !esp.getValue())
            return;
        if (!HadesAPI.mc.isInGame())
            return;
        if (target == null || !blockPackets || !hasRealPos)
            return;

        try {
            double renderPosX = HadesAPI.renderer.getRenderPosX();
            double renderPosY = HadesAPI.renderer.getRenderPosY();
            double renderPosZ = HadesAPI.renderer.getRenderPosZ();

            double renderX = (realX / 32.0) - renderPosX;
            double renderY = (realY / 32.0) - renderPosY;
            double renderZ = (realZ / 32.0) - renderPosZ;

            float width = target.getWidth();
            float height = target.getHeight();
            if (width <= 0)
                width = 0.6f;
            if (height <= 0)
                height = 1.8f;

            int color = new java.awt.Color(0, 255, 0, 80).getRGB();
            com.hades.client.util.RenderUtil.drawOutlinedEntityESP(renderX, renderY, renderZ, width, height, color);
        } catch (Exception e) {
            HadesLogger.get().error("[Backtrack] Render error", e);
        }
    }

    // ── Core: flush held packets ──

    private void resetPackets() {
        fastResetPackets(); // default instantly clears. changed name to clarify
    }

    private void fastResetPackets() {
        if (packets.isEmpty())
            return;

        Channel channel = HadesAPI.Game.getNettyChannel();
        if (channel == null) {
            packets.clear();
            return;
        }
        ChannelHandlerContext ctx = channel.pipeline().context("hades_packet_handler");
        if (ctx == null) {
            packets.clear();
            return;
        }

        synchronized (packets) {
            for (Object packet : packets) {
                try {
                    ctx.fireChannelRead(packet);
                } catch (Exception e) {
                    HadesLogger.get().error("[Backtrack] Error processing packet", e);
                }
            }
            packets.clear();
        }
        hasRealPos = false;
    }

    private void pulsePackets(int count) {
        if (packets.isEmpty())
            return;

        Channel channel = HadesAPI.Game.getNettyChannel();
        if (channel == null) {
            packets.clear();
            return;
        }
        ChannelHandlerContext ctx = channel.pipeline().context("hades_packet_handler");
        if (ctx == null) {
            packets.clear();
            return;
        }

        synchronized (packets) {
            int released = 0;
            while (!packets.isEmpty() && released < count) {
                Object p = packets.remove(0);
                try {
                    ctx.fireChannelRead(p);
                    released++;
                } catch (Exception e) {
                }
            }
            if (packets.isEmpty()) {
                hasRealPos = false;
            }
        }
    }



    // ── Packet classification ──

    private boolean shouldDelayPacket(Object packet, String name) {
        // NOTE: We do NOT delay incoming S00PacketKeepAlive or S32PacketConfirmTransaction here.
        // These must reach the vanilla handler immediately to prevent KeepAlive timeouts.
        // Ping spoofing is handled by holding the OUTGOING responses (C00/C0F) in transactionQueue,
        // not by blocking the incoming triggers. This prevents the double-hold blackout that
        // Polar detects as artificial ping manipulation.

        // Entity packets: ONLY delay if they belong to our target
        boolean isEntityPacket = name.startsWith("S14PacketEntity") ||
                                 name.equals("S18PacketEntityTeleport") ||
                                 name.equals("S12PacketEntityVelocity") ||
                                 name.equals("S19PacketEntityStatus") ||
                                 name.equals("S04PacketEntityEquipment") ||
                                 name.equals("S20PacketEntityProperties"); // Removed spawn packets to fix ghost entities

        if (isEntityPacket) {
            if (target == null) return false;
            int packetEntityId = HadesAPI.network.getPacketEntityId(packet);
            if (packetEntityId != -1) {
                return packetEntityId == target.getEntityId();
            }
        }
        
        return false;
    }

    // ── Position tracking ──

    private void trackRealPosFromS14(Object packet) {
        if (target == null) return;
        int entityId = HadesAPI.network.getPacketEntityId(packet);
        if (entityId == -1 || entityId != target.getEntityId()) return;

        if (!hasRealPos) {
            // Initialize from current entity position (in server pos units = * 32)
            realX = target.getX() * 32.0;
            realY = target.getY() * 32.0;
            realZ = target.getZ() * 32.0;
            hasRealPos = true;
        }

        double[] delta = HadesAPI.network.getS14EntityMoveDelta(packet);
        if (delta != null) {
            realX += delta[0];
            realY += delta[1];
            realZ += delta[2];
        }
    }

    private void trackRealPosFromS18(Object packet) {
        if (target == null) return;
        int entityId = HadesAPI.network.getPacketEntityId(packet);
        if (entityId == -1 || entityId != target.getEntityId()) return;

        double[] pos = HadesAPI.network.getS18EntityPos(packet);
        if (pos != null) {
            realX = pos[0];
            realY = pos[1];
            realZ = pos[2];
            hasRealPos = true;
        }
    }

}

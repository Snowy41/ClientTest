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

import java.util.Queue;
import java.util.LinkedList;

/**
 * LagRange — delays ALL outgoing packets to keep you out of your opponent's range,
 * allowing for easy first hits.
 *
 * How it works:
 * 1. When enabled, outgoing packets are held in a queue — the server sees your old position.
 * 2. Opponents cannot hit you because to the server, you are still far away.
 * 3. On your client, you walk toward the opponent normally.
 * 4. When you attack (C02PacketUseEntity), ALL queued packets flush instantly.
 *    You teleport to your real position right next to them and land the first hit.
 * 5. After flushing, packets continue to flow normally until you move out of close range,
 *    at which point delay re-arms automatically.
 *
 * On disable: ALL packets are flushed immediately, restoring normal ping.
 *
 * Based on Augustus FakeLag combat logic, adapted to Hades API.
 */
public class LagRange extends Module {

    private final NumberSetting lagPackets = new NumberSetting(
            "Lag Packets", "Number of C03 packets to queue before auto-flushing", 9.0, 1.0, 9.0, 1.0);
    private final NumberSetting minLagDistance = new NumberSetting(
            "Min Lag Distance", "Distance to stop lagging (hit range)", 3.1, 3.0, 5.0, 0.1);
    private final NumberSetting maxLagDistance = new NumberSetting(
            "Max Lag Distance", "Distance to stop lagging (too far)", 6.0, 4.0, 8.0, 0.1);
    private final BooleanSetting onlyMove = new BooleanSetting(
            "Only Movement", false); // true = only queue C03, false = queue ALL packets
    private final BooleanSetting realPositionIndicator = new BooleanSetting(
            "Real Position Indicator", true);
    private final BooleanSetting disableOnHit = new BooleanSetting(
            "Disable on Hit", "Disables LagRange when you are hit", true);

    private final Queue<DelayedPacket> packets = new LinkedList<>();
    private final Queue<Object> transactionDrip = new LinkedList<>(); // Gradual C00/C0F release queue
    private boolean shouldBlockPackets = false;
    private boolean isDripping = false;
    private int damageCooldown = 0;
    private int comboHits = 0;
    private int lastHurtTime = 0;

    private static class DelayedPacket {
        public Object packet;
        
        public DelayedPacket(Object packet) {
            this.packet = packet;
        }
    }
    
    // Where the server thinks we are (last flushed position)
    private double serverX, serverY, serverZ;
    private boolean hasServerPos = false;

    public LagRange() {
        super("LagRange", "Delays outgoing packets to keep opponents further away for easy first hits.", Category.COMBAT, 0);
        register(lagPackets);
        register(minLagDistance);
        register(maxLagDistance);
        register(onlyMove);
        register(realPositionIndicator);
        register(disableOnHit);
    }
    
    private com.hades.client.combat.CombatState getState() {
        return com.hades.client.combat.CombatState.getInstance();
    }

    @Override
    public void onEnable() {
        shouldBlockPackets = false;
        damageCooldown = 0;
        comboHits = 0;
        lastHurtTime = 0;
        packets.clear();
        transactionDrip.clear();
        isDripping = false;
        hasServerPos = false;
        
        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player != null) {
            serverX = player.getX();
            serverY = player.getY();
            serverZ = player.getZ();
            hasServerPos = true;
        }
    }

    @Override
    public void onDisable() {
        // CRITICAL: flush ALL queued packets to restore normal ping
        resetPackets();
        // Flush remaining drip queue immediately on disable
        while (!transactionDrip.isEmpty()) {
            Object pkt = transactionDrip.poll();
            if (pkt != null) {
                HadesAPI.network.sendPacketDirect(pkt);
            }
        }
        isDripping = false;
    }

    // ── Tick: manage blocking state ──

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player == null) { resetPackets(); return; }

        // Count queued C03 packets
        int c03Count = 0;
        synchronized (packets) {
            for (DelayedPacket dp : packets) {
                if (PacketMapper.getPacketName(dp.packet).startsWith("C03PacketPlayer")) {
                    c03Count++;
                }
            }
        }

        // If packet count exceeds limit → instant flush to prevent massive anticheat balance violation
        if (c03Count > lagPackets.getValue().intValue()) {
            shouldBlockPackets = false;
            resetPackets();
        }

        if (damageCooldown > 0) {
            damageCooldown--;
        }

        int currentHurtTime = player.getHurtTime();
        if (currentHurtTime > 0 && lastHurtTime < currentHurtTime) {
            comboHits++;
            if (disableOnHit.getValue() || comboHits >= 2) {
                damageCooldown = 20; // Disable for 1 second if hit/comboed
                comboHits = 0;
            }
        }
        lastHurtTime = currentHurtTime;

        // Do not lag if we are on damage cooldown (prevents knockback manipulation flags)
        if (damageCooldown > 0 || !getState().canDelayPackets(com.hades.client.combat.CombatState.ModuleType.LAGRANGE)) {
            shouldBlockPackets = false;
            resetPackets();
            return;
        }

        com.hades.client.api.interfaces.IEntity nearest = TargetManager.getInstance().getTarget();
        if (nearest == null) {
            shouldBlockPackets = false;
            resetPackets();
            return;
        } else {
            // Flush immediately if the enemy is taking damage
            if (nearest.getHurtTime() > 0) {
                shouldBlockPackets = false;
                resetPackets();
            } else {
                double dist = nearest.getDistanceToEntity(HadesAPI.player);
                double min = minLagDistance.getValue().doubleValue();
                double max = maxLagDistance.getValue().doubleValue();

                // Track delay state
                if (dist < min || dist > max) {
                    shouldBlockPackets = false;
                    resetPackets();
                } else if (c03Count <= lagPackets.getValue().intValue()) {
                    shouldBlockPackets = true;
                } else {
                    shouldBlockPackets = false;
                    resetPackets();
                }
            }
        }

        // Gradual Transaction Drip — releases held C00/C0F at a natural rate
        // Prevents burst detection by Polar (which flags simultaneous transaction responses as ping spoofing)
        if (isDripping && !transactionDrip.isEmpty()) {
            int dripCount = Math.min(2, transactionDrip.size());
            for (int i = 0; i < dripCount; i++) {
                Object pkt = transactionDrip.poll();
                if (pkt != null) {
                    HadesAPI.network.sendPacketDirect(pkt);
                }
            }
            if (transactionDrip.isEmpty()) {
                isDripping = false;
            }
        }
    }

    // ── Outgoing packet handling ──

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || event.isCancelled()) return;

        Object packet = event.getPacket();
        if (packet == null) return;
        String name = PacketMapper.getPacketName(packet);

        // Attack (C02PacketUseEntity) → instant flush for the hit to register
        if (name.equals("C02PacketUseEntity")) {
            comboHits = 0; // Reset combo counter when we hit back
            shouldBlockPackets = false;
            resetPackets();
            return; // Let the C02 through normally
        }

        // Record server position from C03 before we queue it
        synchronized (packets) {
            if (name.startsWith("C03PacketPlayer") && !hasServerPos && packets.isEmpty()) {
                com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
                if (player != null) {
                    serverX = player.getX();
                    serverY = player.getY();
                    serverZ = player.getZ();
                    hasServerPos = true;
                }
            }

            // Queue packets when blocking
            if (shouldBlockPackets) {
                getState().setLagging(true);
                boolean contains = false;
                for (DelayedPacket dp : packets) {
                    if (dp.packet == packet) { contains = true; break; }
                }
                
                // If we have an active target, we MUST queue all packets to prevent C0A/C0B desyncs (BadPacketsF)
                boolean forceQueueAll = (TargetManager.getInstance().getTarget() != null);

                // ALWAYS queue transactions and keepalives chronologically to emulate real ping spikes/chokes.
                if (name.equals("C00PacketKeepAlive") || name.equals("C0FPacketConfirmTransaction")) {
                    if (!contains) {
                        packets.add(new DelayedPacket(packet));
                        event.setCancelled(true);
                    }
                } else if (name.equals("C0APacketAnimation") || name.equals("C0BPacketEntityAction") ||
                           name.equals("C08PacketPlayerBlockPlacement") || name.equals("C07PacketPlayerDigging")) {
                    // Combat action packets pass through immediately — holding them creates
                    // impossible sequences (swings/blocks with 0ms timing) when flushed,
                    // which Polar detects as packet manipulation.
                    // These are safe to send because they don't reveal position.
                } else if (onlyMove.getValue() && !forceQueueAll) {
                    // Only queue movement packets (Safe when just running around, no combat)
                    if (name.startsWith("C03PacketPlayer") && !contains) {
                        packets.add(new DelayedPacket(packet));
                        event.setCancelled(true);
                    }
                } else {
                    // Queue ALL outgoing packets (genuine lag simulation, preserves chronological combat order)
                    if (!contains) {
                        packets.add(new DelayedPacket(packet));
                        event.setCancelled(true);
                    }
                }
            } else {
                getState().setLagging(false);
            }
        }
    }

    // ── Incoming packet handling ──

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || event.isCancelled()) return;

        Object packet = event.getPacket();
        if (packet == null) return;

        // Server teleport → flush immediately
        if (PacketMapper.getPacketName(packet).equals("S08PacketPlayerPosLook")) {
            shouldBlockPackets = false;
            resetPackets();
        }
    }

    // ── Rendering (RPI) ──

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !realPositionIndicator.getValue()) return;
        if (!HadesAPI.mc.isInGame()) return;
        if (!hasServerPos || packets.isEmpty()) return;

        try {
            double renderPosX = HadesAPI.renderer.getRenderPosX();
            double renderPosY = HadesAPI.renderer.getRenderPosY();
            double renderPosZ = HadesAPI.renderer.getRenderPosZ();

            double renderX = serverX - renderPosX;
            double renderY = serverY - renderPosY;
            double renderZ = serverZ - renderPosZ;

            int color = new java.awt.Color(0, 255, 0, 80).getRGB();
            com.hades.client.util.RenderUtil.drawOutlinedEntityESP(renderX, renderY, renderZ, 0.6, 1.8, color);
        } catch (Exception e) {
            HadesLogger.get().error("[LagRange] Render error", e);
        }
    }

    // ── Core: flush packets ──

    private void resetPackets() {
        if (HadesAPI.player == null) {
            synchronized (packets) { packets.clear(); }
            hasServerPos = false;
            return;
        }

        synchronized (packets) {
            if (!packets.isEmpty()) {
                for (DelayedPacket dp : packets) {
                    String pktName = PacketMapper.getPacketName(dp.packet);
                    
                    // Transaction/KeepAlive responses → drip queue for gradual release
                    // This prevents burst detection by Polar which flags simultaneous transaction responses
                    if (pktName.equals("C00PacketKeepAlive") || pktName.equals("C0FPacketConfirmTransaction")) {
                        transactionDrip.add(dp.packet);
                    } else {
                        // Position + combat packets flush immediately (needed for hit registration)
                        HadesAPI.network.sendPacketDirect(dp.packet);
                        
                        if (pktName.startsWith("C03PacketPlayer")) {
                            com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
                            if (player != null) {
                                serverX = player.getX();
                                serverY = player.getY();
                                serverZ = player.getZ();
                            }
                        }
                    }
                }
                packets.clear();
            }
        }
        getState().setLagging(false);
        hasServerPos = false;
        
        if (!transactionDrip.isEmpty()) {
            isDripping = true;
        }
    }

}

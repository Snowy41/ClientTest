package com.hades.client.module.impl.misc;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.util.PacketMapper;
import com.hades.client.util.HadesLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class TelemetryLogger extends Module {

    private final List<TelemetryEntry> sessionData = new ArrayList<>();
    private long startTime = 0;
    private int currentTick = 0;

    // Packets we care about for movement & combat analysis
    private static final List<String> WHITELIST = Arrays.asList(
            "C03PacketPlayer", "C04PacketPlayerPosition", "C05PacketPlayerLook", "C06PacketPlayerPosLook",
            "C07PacketPlayerDigging", "C08PacketPlayerBlockPlacement", "C09PacketHeldItemChange",
            "C0APacketAnimation", "C0BPacketEntityAction", "C02PacketUseEntity",
            "S08PacketPlayerPosLook", "S12PacketEntityVelocity", "S32PacketConfirmTransaction"
    );

    public TelemetryLogger() {
        super("TelemetryLogger", "Dumps tick-by-tick packet and physics metrics for AC bypass analysis", Category.MISC, 0);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        sessionData.clear();
        startTime = System.currentTimeMillis();
        currentTick = 0;
        HadesLogger.get().info("[Telemetry] Started recording session.");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        HadesLogger.get().info("[Telemetry] Stopped recording. Saving to file...");
        saveSessionData();
        sessionData.clear();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.player == null) return;
        currentTick++;

        // Only record state once per tick
        StateEntry entry = new StateEntry();
        entry.timestamp = System.currentTimeMillis() - startTime;
        entry.tick = currentTick;
        entry.x = HadesAPI.player.getX();
        entry.y = HadesAPI.player.getY();
        entry.z = HadesAPI.player.getZ();
        entry.yaw = HadesAPI.player.getYaw();
        entry.pitch = HadesAPI.player.getPitch();
        entry.onGround = HadesAPI.player.isOnGround();
        entry.sprinting = HadesAPI.player.isSprinting();
        
        // entry.sneaking = HadesAPI.mc.isKeySneakPressed(); // Requires API addition
        entry.sneaking = false;

        sessionData.add(entry);
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;

        Object packet = event.getPacket();
        if (packet == null) return;

        String packetName = PacketMapper.getPacketName(packet);

        // Filter out spam packets not in whitelist
        boolean allowed = false;
        for (String white : WHITELIST) {
            if (packetName.contains(white)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) return;

        PacketEntry entry = new PacketEntry();
        entry.timestamp = System.currentTimeMillis() - startTime;
        entry.tick = currentTick;
        entry.direction = event.isSend() ? "SEND" : "RECV";
        entry.packetName = packetName;
        entry.details = dumpPacketFields(packet);

        sessionData.add(entry);
    }

    private String dumpPacketFields(Object packet) {
        StringBuilder sb = new StringBuilder("{");
        Class<?> clazz = packet.getClass();
        
        while (clazz != Object.class && clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                try {
                    Object value = field.get(packet);
                    sb.append(field.getType().getSimpleName()).append("=").append(value).append(", ");
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }

    private void saveSessionData() {
        if (sessionData.isEmpty()) return;

        // Ensure directory exists
        File dir = new File(com.hades.client.HadesClient.getInstance().getConfigManager().getConfigDirectory().getParentFile(), "telemetry");
        if (!dir.exists()) dir.mkdirs();

        String dateStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File file = new File(dir, "session_" + dateStr + ".log");

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("--- Hades Telemetry Log ---");
            writer.println("Date: " + dateStr);
            writer.println("Total Ticks Recorded: " + currentTick);
            writer.println("Total Events Logged: " + sessionData.size());
            writer.println("---------------------------\n");

            for (TelemetryEntry e : sessionData) {
                writer.println(e.format());
            }

            HadesLogger.get().info("[Telemetry] Session saved to: " + file.getAbsolutePath());
        } catch (IOException e) {
            HadesLogger.get().error("[Telemetry] Failed to save session data", e);
        }
    }

    private abstract static class TelemetryEntry {
        long timestamp;
        int tick;

        abstract String format();

        protected String getPrefix() {
            String timeStr = String.format("%06d", timestamp);
            return "[" + timeStr + "ms] [TICK " + String.format("%04d", tick) + "] ";
        }
    }

    private static class StateEntry extends TelemetryEntry {
        double x, y, z;
        float yaw, pitch;
        boolean onGround, sprinting, sneaking;

        @Override
        String format() {
            return getPrefix() + "[STATE] X: " + String.format("%.3f", x) + " | Y: " + String.format("%.3f", y) + 
                   " | Z: " + String.format("%.3f", z) + " | Yaw: " + String.format("%.2f", yaw) + 
                   " | Pitch: " + String.format("%.2f", pitch) + " | Ground: " + onGround + 
                   " | Sprint: " + sprinting + " | Sneak: " + sneaking;
        }
    }

    private static class PacketEntry extends TelemetryEntry {
        String direction;
        String packetName;
        String details;

        @Override
        String format() {
            return getPrefix() + "[" + direction + "] " + packetName + " " + details;
        }
    }
}

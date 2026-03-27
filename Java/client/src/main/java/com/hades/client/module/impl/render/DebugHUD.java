package com.hades.client.module.impl.render;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.api.HadesAPI;
import com.hades.client.util.PacketMapper;
import com.hades.client.util.PacketInspector;

import java.awt.Color;
import java.util.LinkedList;
import java.util.List;

public class DebugHUD extends Module {

    private final BooleanSetting showPackets = new BooleanSetting("Show Packets", true);
    private final BooleanSetting showRotation = new BooleanSetting("Show Rotation", true);
    private final BooleanSetting showPosition = new BooleanSetting("Show Position", true);
    private final BooleanSetting showCompass = new BooleanSetting("Show Compass", true);
    private final BooleanSetting showPitch = new BooleanSetting("Show Pitch Slider", true);
    private final BooleanSetting showSpeed = new BooleanSetting("Show Speed Graph", true);
    private final BooleanSetting showExtraDebug = new BooleanSetting("Show Extra Debug Info", false);
    private final BooleanSetting showPacketInspector = new BooleanSetting("Show Packet Inspector", false);

    /** Represents one captured packet entry for display. */
    private static class PacketLogEntry {
        final boolean outbound;
        final String name;
        final List<String> fields;

        PacketLogEntry(boolean outbound, String name, List<String> fields) {
            this.outbound = outbound;
            this.name = name;
            this.fields = fields;
        }
    }

    private static final int MAX_PACKET_LOG = 8;
    private final LinkedList<PacketLogEntry> packetLog = new LinkedList<>();

    // Packet graph tracking
    private int inPacketsSec = 0;
    private int outPacketsSec = 0;
    private long lastSecond = 0;

    private final LinkedList<Integer> inHistory = new LinkedList<>();
    private final LinkedList<Integer> outHistory = new LinkedList<>();
    private final LinkedList<Float> speedHistory = new LinkedList<>();
    private final int MAX_HISTORY = 40; // 40 seconds of graph
    private final int MAX_SPEED_HISTORY = 40; // 40 ticks of speed or 40 frames

    // Speed tracking
    private double lastX = 0, lastZ = 0;
    private float currentSpeed = 0f;

    // Last known server yaw/pitch from C03/C05/C06
    private float serverYaw = 0f;
    private float serverPitch = 0f;

    public DebugHUD() {
        super("DebugHUD", "Displays debug information and packet graphs on screen.", Category.RENDER, 0);
        register(showPackets);
        register(showRotation);
        register(showPosition);
        register(showCompass);
        register(showPitch);
        register(showSpeed);
        register(showExtraDebug);
        register(showPacketInspector);

        for (int i = 0; i < MAX_HISTORY; i++) {
            inHistory.add(0);
            outHistory.add(0);
        }
        for (int i = 0; i < MAX_SPEED_HISTORY; i++) {
            speedHistory.add(0f);
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        inPacketsSec = 0;
        outPacketsSec = 0;
        lastSecond = System.currentTimeMillis();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled())
            return;
        inPacketsSec++;
        if (showPacketInspector.getValue()) {
            Object pkt = event.getPacket();
            String name = PacketMapper.getPacketName(pkt);
            PacketLogEntry entry = new PacketLogEntry(false, name, PacketInspector.inspect(pkt));
            synchronized (packetLog) {
                packetLog.addFirst(entry);
                if (packetLog.size() > MAX_PACKET_LOG)
                    packetLog.removeLast();
            }
        }
    }

    @EventHandler
    public void onTick(com.hades.client.event.events.TickEvent event) {
        if (!isEnabled() || HadesAPI.Player.isNull())
            return;

        // Calculate speed per second (20 ticks per second) to avoid frame-jitter
        double dx = HadesAPI.Player.getX() - lastX;
        double dz = HadesAPI.Player.getZ() - lastZ;
        currentSpeed = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
        lastX = HadesAPI.Player.getX();
        lastZ = HadesAPI.Player.getZ();

        speedHistory.addLast(currentSpeed);
        if (speedHistory.size() > MAX_SPEED_HISTORY)
            speedHistory.removeFirst();
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled())
            return;
        outPacketsSec++;

        Object packet = event.getPacket();
        if (showPacketInspector.getValue()) {
            String name = PacketMapper.getPacketName(packet);
            PacketLogEntry entry = new PacketLogEntry(true, name, PacketInspector.inspect(packet));
            synchronized (packetLog) {
                packetLog.addFirst(entry);
                if (packetLog.size() > MAX_PACKET_LOG)
                    packetLog.removeLast();
            }
        }
        if (packet != null) {
            String name = PacketMapper.getPacketName(packet);
            if (name.startsWith("C03PacketPlayer")) {
                com.hades.client.wrapper.packet.C03PacketPlayerWrapper wrapper = new com.hades.client.wrapper.packet.C03PacketPlayerWrapper(
                        packet);
                if (wrapper.isRotating()) {
                    serverYaw = wrapper.getYaw();
                    serverPitch = wrapper.getPitch();
                }
            }
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || HadesAPI.Player.isNull())
            return;

        long now = System.currentTimeMillis();
        if (now - lastSecond >= 1000) {
            inHistory.addLast(inPacketsSec);
            outHistory.addLast(outPacketsSec);
            if (inHistory.size() > MAX_HISTORY)
                inHistory.removeFirst();
            if (outHistory.size() > MAX_HISTORY)
                outHistory.removeFirst();

            inPacketsSec = 0;
            outPacketsSec = 0;
            lastSecond = now;
        }

        // Speed history and dx/dz calculation is now handled in onTick

        float yOffset = 10f;
        float xOffset = 10f;

        // Draw Packet Graph
        if (showPackets.getValue()) {
            float graphWidth = 120f;
            float graphHeight = 40f;

            // Background
            HadesAPI.Render.drawRect(xOffset, yOffset, graphWidth, graphHeight, new Color(20, 20, 20, 180).getRGB());
            HadesAPI.Render.drawRect(xOffset, yOffset, graphWidth, 1f, new Color(255, 255, 255, 100).getRGB());
            HadesAPI.Render.drawString("Packet Output/Sec", xOffset + 4, yOffset + 4, -1, 0.8f);

            // Find Max for scaling
            int maxP = 10;
            for (int i : outHistory)
                if (i > maxP)
                    maxP = i;
            if (outPacketsSec > maxP)
                maxP = outPacketsSec; // include ongoing second

            // Draw Output Graph (Red)
            float step = graphWidth / MAX_HISTORY;
            int i = 0;
            for (int val : outHistory) {
                float h = (val / (float) maxP) * (graphHeight - 12);
                float x = xOffset + (i * step);
                float y = yOffset + graphHeight - h;
                HadesAPI.Render.drawRect(x, y, step - 0.5f, h, new Color(255, 80, 80, 200).getRGB());
                i++;
            }
            // Draw current incomplete second
            float currentH = (outPacketsSec / (float) maxP) * (graphHeight - 12);
            HadesAPI.Render.drawRect(xOffset + (i * step), yOffset + graphHeight - currentH, step - 0.5f, currentH,
                    new Color(255, 150, 150, 200).getRGB());

            yOffset += graphHeight + 5;

            // Background for IN
            HadesAPI.Render.drawRect(xOffset, yOffset, graphWidth, graphHeight, new Color(20, 20, 20, 180).getRGB());
            HadesAPI.Render.drawRect(xOffset, yOffset, graphWidth, 1f, new Color(255, 255, 255, 100).getRGB());
            HadesAPI.Render.drawString("Packet Input/Sec", xOffset + 4, yOffset + 4, -1, 0.8f);

            maxP = 10;
            for (int v : inHistory)
                if (v > maxP)
                    maxP = v;
            if (inPacketsSec > maxP)
                maxP = inPacketsSec;

            // Draw Input Graph (Green)
            i = 0;
            for (int val : inHistory) {
                float h = (val / (float) maxP) * (graphHeight - 12);
                float x = xOffset + (i * step);
                float y = yOffset + graphHeight - h;
                HadesAPI.Render.drawRect(x, y, step - 0.5f, h, new Color(80, 255, 80, 200).getRGB());
                i++;
            }
            // Draw current incomplete second
            currentH = (inPacketsSec / (float) maxP) * (graphHeight - 12);
            HadesAPI.Render.drawRect(xOffset + (i * step), yOffset + graphHeight - currentH, step - 0.5f, currentH,
                    new Color(150, 255, 150, 200).getRGB());

            yOffset += graphHeight + 10;
        }

        // We already gathered Player Data above

        // Draw Rotations
        if (showRotation.getValue()) {
            HadesAPI.Render.drawRect(xOffset, yOffset, 120f, 30f, new Color(20, 20, 20, 180).getRGB());
            HadesAPI.Render.drawString("Rotation Data", xOffset + 4, yOffset + 4, new Color(0, 200, 255).getRGB(),
                    0.9f);

            float cYaw = HadesAPI.Player.getYaw() % 360f;
            if (cYaw < 0)
                cYaw += 360f;
            float cPitch = HadesAPI.Player.getPitch();

            float sYaw = serverYaw % 360f;
            if (sYaw < 0)
                sYaw += 360f;

            String cText = String.format("Client: %.1f, %.1f", cYaw, cPitch);
            String sText = String.format("Server: %.1f, %.1f", sYaw, serverPitch);

            HadesAPI.Render.drawString(cText, xOffset + 4, yOffset + 14, -1, 0.7f);

            // Color server text red if spoofed/desynced, green if synced
            int sColor = (Math.abs(cYaw - sYaw) > 1.0f
                    || Math.abs(cPitch - serverPitch) > 1.0f)
                            ? new Color(255, 80, 80).getRGB()
                            : new Color(80, 255, 80).getRGB();
            HadesAPI.Render.drawString(sText, xOffset + 4, yOffset + 22, sColor, 0.7f);

            yOffset += 35;
        }

        // Draw Position
        if (showPosition.getValue()) {
            HadesAPI.Render.drawRect(xOffset, yOffset, 120f, 30f, new Color(20, 20, 20, 180).getRGB());
            HadesAPI.Render.drawString("Position", xOffset + 4, yOffset + 4, new Color(0, 200, 255).getRGB(), 0.9f);

            String xyz = String.format("XYZ: %.2f, %.2f, %.2f", HadesAPI.Player.getX(), HadesAPI.Player.getY(),
                    HadesAPI.Player.getZ());
            String onGround = String.format("OnGround: %b", HadesAPI.Player.isOnGround());

            HadesAPI.Render.drawString(xyz, xOffset + 4, yOffset + 14, -1, 0.7f);
            HadesAPI.Render.drawString(onGround, xOffset + 4, yOffset + 22,
                    HadesAPI.Player.isOnGround() ? new Color(80, 255, 80).getRGB() : new Color(255, 80, 80).getRGB(),
                    0.7f);

            yOffset += 35;
        }

        if (showExtraDebug.getValue()) {
            HadesAPI.Render.drawRect(xOffset, yOffset, 120f, 25f, new Color(20, 20, 20, 180).getRGB());
            HadesAPI.Render.drawString("Extra Info", xOffset + 4, yOffset + 4, new Color(0, 200, 255).getRGB(), 0.9f);

            // Safe RAM calculation
            long ramUsage = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024L / 1024L;
            long maxRam = Runtime.getRuntime().maxMemory() / 1024L / 1024L;
            HadesAPI.Render.drawString("RAM: " + ramUsage + "MB / " + maxRam + "MB", xOffset + 4, yOffset + 14, -1,
                    0.7f);

            yOffset += 30;
        }

        // Draw Speed Graph
        if (showSpeed.getValue()) {
            float graphWidth = 120f;
            float graphHeight = 35f;

            HadesAPI.Render.drawRect(xOffset, yOffset, graphWidth, graphHeight, new Color(20, 20, 20, 180).getRGB());
            HadesAPI.Render.drawString("Speed (Blocks/Sec)", xOffset + 4, yOffset + 4, new Color(255, 200, 0).getRGB(),
                    0.8f);

            float maxS = 10f; // min scale (10 b/s = normal sprint jump)
            for (float s : speedHistory)
                if (s > maxS)
                    maxS = s;

            float step = graphWidth / MAX_SPEED_HISTORY;
            int i = 0;
            for (float val : speedHistory) {
                float h = (val / maxS) * (graphHeight - 14);
                float x = xOffset + (i * step);
                float y = yOffset + graphHeight - h;
                HadesAPI.Render.drawRect(x, y, step - 0.2f, h, new Color(255, 200, 0, 200).getRGB());
                i++;
            }

            HadesAPI.Render.drawString(String.format("%.2f b/s", currentSpeed), xOffset + graphWidth - 30, yOffset + 4,
                    -1,
                    0.7f);
            yOffset += graphHeight + 5;
        }

        // Draw Pitch Slider
        if (showPitch.getValue()) {
            float boxW = 20f;
            float boxH = 90f;
            // Draw on the right side of the main stack
            float pxOffset = xOffset + 125f;
            float pyOffset = 10f;

            HadesAPI.Render.drawRect(pxOffset, pyOffset, boxW, boxH, new Color(20, 20, 20, 180).getRGB());
            HadesAPI.Render.drawRect(pxOffset + boxW / 2 - 0.5f, pyOffset + 5, 1f, boxH - 10,
                    new Color(100, 100, 100, 200).getRGB());

            // Middle Line (0 Pitch)
            HadesAPI.Render.drawRect(pxOffset + 2, pyOffset + boxH / 2 - 0.5f, boxW - 4, 1f,
                    new Color(255, 255, 255, 150).getRGB());

            // Client Pitch (White Horizontal Bar)
            // Pitch goes from -90 (up) to 90 (down). We map it so -90 is top, 90 is bottom.
            float mappedCPitch = ((HadesAPI.Player.getPitch() + 90f) / 180f) * (boxH - 10);
            HadesAPI.Render.drawRect(pxOffset + 2, pyOffset + 5 + mappedCPitch - 1, boxW - 4, 2f,
                    new Color(255, 255, 255, 255).getRGB());

            // Server Pitch (Red Dot/Small Rect)
            float mappedSPitch = ((serverPitch + 90f) / 180f) * (boxH - 10);
            HadesAPI.Render.drawRect(pxOffset + boxW / 2 - 2, pyOffset + 5 + mappedSPitch - 2, 4f, 4f,
                    new Color(255, 80, 80, 255).getRGB());

            HadesAPI.Render.drawString("P", pxOffset + 7, pyOffset + boxH + 2, -1, 0.7f);
        }

        // Draw Compass Strip
        if (showCompass.getValue()) {
            // Draw in top center
            float compW = 150f;
            float compH = 20f;
            float cx = (event.getScaledWidth() / 2f) - (compW / 2f);
            float cy = 5f;

            // Background
            HadesAPI.Render.drawRect(cx, cy, compW, compH, new Color(20, 20, 20, 180).getRGB());

            // Normalize current yaw
            float currentYaw = HadesAPI.Player.getYaw() % 360f;
            if (currentYaw < 0)
                currentYaw += 360f;

            // Scissor the compass area so text doesn't bleed out
            HadesAPI.Render.enableScissor(cx, cy, compW, compH);

            // Draw ticks for N, E, S, W
            drawCompassTick(cx, cy, compW, compH, currentYaw, 180f, "N", new Color(255, 100, 100).getRGB());
            drawCompassTick(cx, cy, compW, compH, currentYaw, 270f, "E", -1);
            drawCompassTick(cx, cy, compW, compH, currentYaw, 0f, "S", -1);
            drawCompassTick(cx, cy, compW, compH, currentYaw, 90f, "W", -1);
            drawCompassTick(cx, cy, compW, compH, currentYaw, 360f, "S", -1);

            HadesAPI.Render.disableScissor();

            // Highlight Center (Crosshair view) - Red marker to show EXACTLY what we are
            // looking at
            HadesAPI.Render.drawRect(cx + compW / 2f - 1f, cy, 2f, compH, new Color(255, 50, 50, 200).getRGB());
        }

        // ── Packet Inspector Panel (right side) ──────────────────────────────────────
        if (showPacketInspector.getValue()) {
            float panelW = 220f;
            float lineH = 8f;
            float padding = 3f;
            float pxRight = event.getScaledWidth() - panelW - 5f;
            float pyTop = 30f;

            List<PacketLogEntry> snapshot;
            synchronized (packetLog) {
                snapshot = new java.util.ArrayList<>(packetLog);
            }

            float py = pyTop;
            for (PacketLogEntry entry : snapshot) {
                // Header row: packet name, coloured by direction
                int nameColor = entry.outbound
                        ? new Color(255, 180, 60).getRGB() // orange = outbound
                        : new Color(60, 200, 255).getRGB(); // cyan = inbound

                String direction = entry.outbound ? "▲ " : "▼ ";
                int headerLines = 1 + (entry.fields.size() + 1); // name + fields
                float blockH = padding + headerLines * lineH + padding;

                HadesAPI.Render.drawRect(pxRight, py, panelW, blockH,
                        new Color(15, 15, 15, 190).getRGB());
                HadesAPI.Render.drawRect(pxRight, py, 2f, blockH, nameColor);

                HadesAPI.Render.drawString(direction + entry.name,
                        pxRight + 5, py + padding, nameColor, 0.7f);

                float fy = py + padding + lineH;
                for (String field : entry.fields) {
                    // Split "name=value" → dim white name, bright white value
                    int eq = field.indexOf('=');
                    if (eq > 0) {
                        String fname = field.substring(0, eq + 1);
                        String fval = field.substring(eq + 1);
                        float fw = HadesAPI.Render.getStringWidth(fname, 0.65f);
                        HadesAPI.Render.drawString(fname, pxRight + 5, fy,
                                new Color(160, 160, 160).getRGB(), 0.65f);
                        HadesAPI.Render.drawString(fval, pxRight + 5 + fw, fy,
                                new Color(220, 220, 220).getRGB(), 0.65f);
                    } else {
                        HadesAPI.Render.drawString(field, pxRight + 5, fy,
                                new Color(180, 180, 180).getRGB(), 0.65f);
                    }
                    fy += lineH;
                }

                py += blockH + 2f;
                if (py > event.getScaledHeight() - 20)
                    break; // clamp to screen
            }
        }
    }

    private void drawCompassTick(float cx, float cy, float compW, float compH, float currentYaw, float targetYaw,
            String text, int color) {
        float diff = targetYaw - currentYaw;
        if (diff > 180)
            diff -= 360;
        if (diff < -180)
            diff += 360;

        // Spread points out across the compass width
        // If diff is 0, it's dead center. If diff is 90, it's at the far edge.
        float pixelX = (cx + compW / 2f) + (diff / 90f) * (compW / 2f);

        // Draw slightly outside the box so items slide in
        if (pixelX >= cx - 20 && pixelX <= cx + compW + 20) {
            HadesAPI.Render.drawString(text, pixelX - HadesAPI.Render.getStringWidth(text, 0.8f) / 2f, cy + 4, color,
                    0.8f);
            HadesAPI.Render.drawRect(pixelX - 0.5f, cy + 14, 1f, 6f, new Color(150, 150, 150, 200).getRGB());
        }
    }
}

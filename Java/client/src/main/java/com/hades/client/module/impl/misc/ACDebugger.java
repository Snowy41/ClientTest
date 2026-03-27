package com.hades.client.module.impl.misc;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.combat.TargetManager;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.PacketMapper;
import com.hades.client.util.ReflectionUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ACDebugger — Isolated Anticheat Combat Diagnostic Tool.
 *
 * Runs for a specified duration, listens to combat module activity, captures a
 * ring buffer
 * of packet sequencing, and correlates any anticheat flags caught in chat.
 *
 * When the test finishes, it outputs ONE comprehensive report detailing the
 * active modules
 * and exactly what flagged, with no console spam.
 */
public class ACDebugger extends Module {

    // ── Settings ──
    private final ModeSetting targetAC = new ModeSetting("Target AC", "GrimAC", "GrimAC", "Intave", "Vulcan", "Polar",
            "Matrix", "NCP");
    private final NumberSetting testDuration = new NumberSetting("Test Duration (s)",
            "How long to run the diagnostic test", 30.0, 5.0, 120.0, 5.0);
    private final NumberSetting contextSize = new NumberSetting("Context Buffer", "Events before flag to capture", 30.0,
            10.0, 100.0, 5.0);

    // Internal toggles for deep debugging
    private final BooleanSetting captureOutgoing = new BooleanSetting("Capture Outgoing", "Log C03/C02/C0A/C0B/C08",
            true);
    private final BooleanSetting captureIncoming = new BooleanSetting("Capture Incoming", "Log S12/S32/S14", true);
    private final BooleanSetting captureState = new BooleanSetting("Capture State", "Snapshot player state each tick",
            true);

    // ── Ring buffer & Flag data ──
    private static final int MAX_TIMELINE = 10000;
    private final List<TimelineEvent> timeline = new CopyOnWriteArrayList<>();
    private final List<FlagCapture> flags = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> flagTypeCounts = new LinkedHashMap<>();

    // ── Timing ──
    private long startMs = 0;
    private int tickCount = 0;
    private int tickPacketIndex = 0;
    private int currentTickId = 0;

    // ── Reflection cache ──
    private Method getUnformattedTextMethod = null;
    private boolean chatReflectionFailed = false;

    // ── AC patterns ──
    private static final String[] AC_PATTERNS = {
            "[GrimAC]", "[Grim]", "[Intave", "[Vulcan]", "[AGC]",
            "[Polar]", "[Matrix]", "[NCP]", "[NoCheatPlus]", "[AAC]",
            "[Spartan]", "[Verus]", "[Watchdog]", "failed"
    };

    public ACDebugger() {
        super("ACDebugger", "Diagnostic combat test logger. Run while fighting to capture AC flags.", Category.MISC, 0);
        register(targetAC);
        register(testDuration);
        register(contextSize);
        register(captureOutgoing);
        register(captureIncoming);
        register(captureState);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        timeline.clear();
        flags.clear();
        flagTypeCounts.clear();
        tickCount = 0;
        tickPacketIndex = 0;
        currentTickId = 0;
        startMs = System.currentTimeMillis();
        chatReflectionFailed = false;
        getUnformattedTextMethod = null;

        String activeCombat = getActiveCombatModules();
        HadesLogger.get().info("[ACDebugger] Started test against " + targetAC.getValue() + " for "
                + testDuration.getValue().intValue() + "s.");
        HadesLogger.get().info("[ACDebugger] Active Combat Modules: " + activeCombat);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        int secondsRan = tickCount / 20;
        HadesLogger.get()
                .info("[ACDebugger] Test finished after " + secondsRan + "s. Caught " + flags.size() + " flags.");

        if (!timeline.isEmpty()) {
            exportReport();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TICK: execution + state logic
    // ═══════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled())
            return;
        tickCount++;
        currentTickId = tickCount;
        tickPacketIndex = 0;

        if (captureState.getValue() && HadesAPI.player != null) {
            capturePlayerState();
        }

        int maxTicks = (int) (testDuration.getValue() * 20);
        if (tickCount >= maxTicks) {
            setEnabled(false); // Auto-end test
        }
    }

    private void capturePlayerState() {
        IEntity player = HadesAPI.player;
        if (player == null)
            return;

        StringBuilder sb = new StringBuilder("STATE ");
        sb.append("pos=(").append(fmt(player.getX())).append(",").append(fmt(player.getY())).append(",")
                .append(fmt(player.getZ())).append(") ");
        sb.append("yaw=").append(fmt(player.getYaw())).append(" pitch=").append(fmt(player.getPitch())).append(" ");
        sb.append("ground=").append(player.isOnGround()).append(" ");
        try {
            sb.append("sprint=").append(HadesAPI.player.isSprinting()).append(" ");
        } catch (Exception ignored) {
        }

        try {
            sb.append("motX=").append(fmt(HadesAPI.Player.getMotionX())).append(" ");
            sb.append("motY=").append(fmt(HadesAPI.Player.getMotionY())).append(" ");
            sb.append("motZ=").append(fmt(HadesAPI.Player.getMotionZ())).append(" ");
        } catch (Exception ignored) {
        }

        try {
            sb.append("fwd=").append(fmt(HadesAPI.player.getMoveForward())).append(" ");
            sb.append("str=").append(fmt(HadesAPI.player.getMoveStrafing())).append(" ");
            sb.append("sideways=").append(com.hades.client.hook.hooks.MoveEntityWithHeadingHook.auraSideways)
                    .append(" ");
            boolean hookFail = com.hades.client.api.provider.Vanilla189Player.setSprintingMethod == null;
            sb.append("hookFail=").append(hookFail).append(" ");

            Object rawPlayer = player.getRaw();
            Method isSneak = ReflectionUtil.findMethod(rawPlayer.getClass(),
                    new String[] { "av", "isSneaking", "func_70093_af" });
            if (isSneak != null)
                sb.append("snk=").append(isSneak.invoke(rawPlayer)).append(" ");

            Method isUsing = ReflectionUtil.findMethod(rawPlayer.getClass(),
                    new String[] { "bS", "isUsingItem", "func_71039_bw" });
            if (isUsing != null)
                sb.append("use=").append(isUsing.invoke(rawPlayer)).append(" ");

            Field jumpTicksField = ReflectionUtil.findField(rawPlayer.getClass(), "aT", "jumpTicks", "field_70773_bE");
            if (jumpTicksField != null)
                sb.append("jTks=").append(jumpTicksField.getInt(rawPlayer)).append(" ");

            Field fallDistanceField = ReflectionUtil.findField(rawPlayer.getClass(), "O", "fallDistance",
                    "field_70143_R");
            if (fallDistanceField != null)
                sb.append("fall=").append(fmt(fallDistanceField.getFloat(rawPlayer))).append(" ");
        } catch (Exception ignored) {
        }

        IEntity target = TargetManager.getInstance().getTarget();
        if (target != null) {
            sb.append("tgt_dist=").append(fmt(target.getDistanceToEntity(player))).append(" ");
            sb.append("tgt_hurt=").append(target.getHurtTime()).append(" ");
        } else {
            sb.append("tgt=NONE ");
        }

        try {
            sb.append("hp=").append(fmt(HadesAPI.Player.getHealth()));
        } catch (Exception ignored) {
        }

        addEvent("STATE", sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OUTGOING PACKETS
    // ═══════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || !captureOutgoing.getValue())
            return;
        Object packet = event.getPacket();
        if (packet == null)
            return;

        String name = PacketMapper.getPacketName(packet);
        tickPacketIndex++;

        if (name.startsWith("C03PacketPlayer")) {
            StringBuilder sb = new StringBuilder("OUT ").append(name).append(" #").append(tickPacketIndex).append(" ");
            try {
                Field fYaw = ReflectionUtil.findField(packet.getClass(), "d", "yaw");
                Field fPitch = ReflectionUtil.findField(packet.getClass(), "e", "pitch");
                Field fX = ReflectionUtil.findField(packet.getClass(), "a", "x");
                Field fY = ReflectionUtil.findField(packet.getClass(), "b", "y");
                Field fZ = ReflectionUtil.findField(packet.getClass(), "c", "z");
                Field fGround = ReflectionUtil.findField(packet.getClass(), "f", "onGround");
                Field fMoving = ReflectionUtil.findField(packet.getClass(), "g", "moving");
                Field fRotating = ReflectionUtil.findField(packet.getClass(), "h", "rotating");

                if (fMoving != null) {
                    fMoving.setAccessible(true);
                    sb.append("mov=").append(fMoving.get(packet)).append(" ");
                }
                if (fRotating != null) {
                    fRotating.setAccessible(true);
                    sb.append("rot=").append(fRotating.get(packet)).append(" ");
                }
                if (fX != null) {
                    fX.setAccessible(true);
                    sb.append("x=").append(fmt((double) fX.get(packet))).append(" ");
                }
                if (fY != null) {
                    fY.setAccessible(true);
                    sb.append("y=").append(fmt((double) fY.get(packet))).append(" ");
                }
                if (fZ != null) {
                    fZ.setAccessible(true);
                    sb.append("z=").append(fmt((double) fZ.get(packet))).append(" ");
                }
                if (fYaw != null) {
                    fYaw.setAccessible(true);
                    sb.append("yaw=").append(fmt((float) fYaw.get(packet))).append(" ");
                }
                if (fPitch != null) {
                    fPitch.setAccessible(true);
                    sb.append("pitch=").append(fmt((float) fPitch.get(packet))).append(" ");
                }
                if (fGround != null) {
                    fGround.setAccessible(true);
                    sb.append("ground=").append(fGround.get(packet));
                }
            } catch (Exception e) {
                sb.append("(field read error)");
            }
            addEvent("OUT_C03", sb.toString());
        } else if (name.equals("C02PacketUseEntity")) {
            StringBuilder sb = new StringBuilder("OUT C02_ATTACK #").append(tickPacketIndex).append(" ");
            try {
                Field fAction = ReflectionUtil.findField(packet.getClass(), "b", "action");
                if (fAction != null) {
                    fAction.setAccessible(true);
                    sb.append("action=").append(fAction.get(packet)).append(" ");
                }
                Field fId = ReflectionUtil.findField(packet.getClass(), "a", "entityId");
                if (fId != null) {
                    fId.setAccessible(true);
                    sb.append("entityId=").append(fId.get(packet));
                }
            } catch (Exception e) {
                sb.append("(field read error)");
            }
            addEvent("OUT_C02", sb.toString());
        } else if (name.equals("C0APacketAnimation")) {
            addEvent("OUT_C0A", "OUT C0A_SWING #" + tickPacketIndex);
        } else if (name.equals("C0BPacketEntityAction")) {
            StringBuilder sb = new StringBuilder("OUT C0B_ACTION #").append(tickPacketIndex).append(" ");
            try {
                Field fAction = ReflectionUtil.findField(packet.getClass(), "b", "action");
                if (fAction != null) {
                    fAction.setAccessible(true);
                    sb.append("action=").append(fAction.get(packet));
                }
            } catch (Exception e) {
                sb.append("(field read error)");
            }
            addEvent("OUT_C0B", sb.toString());
        } else if (name.equals("C08PacketPlayerBlockPlacement")) {
            addEvent("OUT_C08", "OUT C08_BLOCK_PLACE #" + tickPacketIndex);
        } else if (name.equals("C07PacketPlayerDigging")) {
            StringBuilder sb = new StringBuilder("OUT C07_DIG #").append(tickPacketIndex).append(" ");
            try {
                Field fStatus = ReflectionUtil.findField(packet.getClass(), "a", "status");
                if (fStatus != null) {
                    fStatus.setAccessible(true);
                    sb.append("status=").append(fStatus.get(packet));
                }
            } catch (Exception e) {
                sb.append("(field read error)");
            }
            addEvent("OUT_C07", sb.toString());
        } else if (name.equals("C00PacketKeepAlive")) {
            addEvent("OUT_C00", "OUT C00_KEEPALIVE #" + tickPacketIndex);
        } else if (name.equals("C0FPacketConfirmTransaction")) {
            addEvent("OUT_C0F", "OUT C0F_TRANSACTION #" + tickPacketIndex);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INCOMING PACKETS
    // ═══════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled())
            return;
        Object packet = event.getPacket();
        if (packet == null)
            return;

        String name = PacketMapper.getPacketName(packet);

        if ("S02PacketChat".equals(name)) {
            String chatText = extractChatText(packet);
            if (chatText != null && !chatText.isEmpty()) {
                checkForFlag(chatText);
            }
            return;
        }

        if (!captureIncoming.getValue())
            return;

        if ("S12PacketEntityVelocity".equals(name)) {
            StringBuilder sb = new StringBuilder("IN S12_VELOCITY ");
            try {
                Field fId = ReflectionUtil.findField(packet.getClass(), "a", "entityId");
                Field fMX = ReflectionUtil.findField(packet.getClass(), "b", "motionX");
                Field fMY = ReflectionUtil.findField(packet.getClass(), "c", "motionY");
                Field fMZ = ReflectionUtil.findField(packet.getClass(), "d", "motionZ");
                if (fId != null) {
                    fId.setAccessible(true);
                    sb.append("eid=").append(fId.get(packet)).append(" ");
                }
                if (fMX != null) {
                    fMX.setAccessible(true);
                    sb.append("vx=").append(fMX.get(packet)).append(" ");
                }
                if (fMY != null) {
                    fMY.setAccessible(true);
                    sb.append("vy=").append(fMY.get(packet)).append(" ");
                }
                if (fMZ != null) {
                    fMZ.setAccessible(true);
                    sb.append("vz=").append(fMZ.get(packet));
                }
            } catch (Exception ignored) {
            }
            addEvent("IN_S12", sb.toString());
        } else if ("S32PacketConfirmTransaction".equals(name)) {
            addEvent("IN_S32", "IN S32_TRANSACTION");
        } else if ("S00PacketKeepAlive".equals(name)) {
            addEvent("IN_S00", "IN S00_KEEPALIVE");
        } else if (name.startsWith("S14PacketEntity")) {
            addEvent("IN_S14", "IN " + name);
        } else if ("S08PacketPlayerPosLook".equals(name)) {
            addEvent("IN_S08", "IN S08_TELEPORT (server position correction)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FLAG DETECTION & CONTEXT CAPTURE
    // ═══════════════════════════════════════════════════════════════════════

    private void checkForFlag(String chatText) {
        boolean isFlag = false;
        for (String pattern : AC_PATTERNS) {
            if (chatText.contains(pattern)) {
                isFlag = true;
                break;
            }
        }
        if (!isFlag)
            return;

        long elapsed = System.currentTimeMillis() - startMs;
        addEvent("FLAG", "FLAG @" + elapsed + "ms: " + chatText.trim());

        String flagType = categorizeFlag(chatText);
        flagTypeCounts.merge(flagType, 1, Integer::sum);

        int ctxSize = contextSize.getValue().intValue();
        int startIdx = Math.max(0, timeline.size() - 1 - ctxSize);
        List<TimelineEvent> context = new ArrayList<>();
        for (int i = startIdx; i < timeline.size(); i++) {
            context.add(timeline.get(i));
        }

        String activeCombat = getActiveCombatModules();
        FlagCapture fc = new FlagCapture(elapsed, tickCount, flagType, chatText.trim(), activeCombat, context);
        flags.add(fc);
    }

    private String categorizeFlag(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("simulation"))
            return "GrimAC:Simulation";
        if (lower.contains("packetorder"))
            return "GrimAC:PacketOrder";
        if (lower.contains("badpacket"))
            return "GrimAC:BadPackets";
        if (lower.contains("timer"))
            return "GrimAC:Timer";
        if (lower.contains("crypta") || (lower.contains("killaura") && lower.contains("intave")))
            return "Intave:Crypta";
        if (lower.contains("hitbox"))
            return "Intave:HitBox";
        if (lower.contains("autoblocker"))
            return "Intave:AutoBlock";
        if (lower.contains("scaffold"))
            return "Scaffold";
        if (lower.contains("aim"))
            return "Vulcan:Aim";
        if (lower.contains("reach"))
            return "Reach";
        if (lower.contains("velocity") || lower.contains("knockback"))
            return "Velocity";
        if (lower.contains("click") || lower.contains("cps") || lower.contains("autoclicker"))
            return "AutoClicker";
        if (lower.contains("sprint"))
            return "Sprint";
        if (lower.contains("combat") && lower.contains("killaura"))
            return "KillAura";
        if (lower.contains("[grimac]") || lower.contains("[grim]"))
            return "GrimAC:Other";
        if (lower.contains("[intave"))
            return "Intave:Other";
        if (lower.contains("[vulcan]"))
            return "Vulcan:Other";
        if (lower.contains("[polar]"))
            return "Polar";
        return "Unknown";
    }

    private void addEvent(String type, String data) {
        long elapsed = System.currentTimeMillis() - startMs;
        TimelineEvent te = new TimelineEvent(elapsed, currentTickId, tickPacketIndex, type, data);
        timeline.add(te);
        while (timeline.size() > MAX_TIMELINE)
            timeline.remove(0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REPORTING & EXPORT
    // ═══════════════════════════════════════════════════════════════════════

    private String getActiveCombatModules() {
        StringBuilder sb = new StringBuilder();
        try {
            for (Module m : HadesClient.getInstance().getModuleManager().getModulesByCategory(Category.COMBAT)) {
                if (m.isEnabled()) {
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append(m.getName());
                }
            }
        } catch (Exception ignored) {
        }
        return sb.length() > 0 ? sb.toString() : "None";
    }

    private void exportReport() {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String acName = targetAC.getValue();
            String activeCombatStr = getActiveCombatModules();

            File flagFile = new File(System.getProperty("user.home"), "testlog_" + acName + "_" + timestamp + ".txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(flagFile))) {
                pw.println("=== Anticheat Diagnostic Test ===");
                pw.println("Target AC: " + acName);
                pw.println("Test Duration: " + (tickCount / 20) + " seconds");
                pw.println("Active Modules during test: " + activeCombatStr);

                StringBuilder eqLine = new StringBuilder();
                for (int ei = 0; ei < 50; ei++)
                    eqLine.append('=');
                pw.println(eqLine.toString());

                if (flags.isEmpty()) {
                    pw.println("\nSUCCESS! No flags detected during this combat test.");
                } else {
                    pw.println("\n--- FLAGS DETECTED ---");

                    for (int fi = 0; fi < flags.size(); fi++) {
                        FlagCapture fc = flags.get(fi);

                        pw.println("\n[" + formatTime(fc.elapsedMs) + "] " + fc.flagText);
                        pw.println("Caused while: " + fc.activeModules + " were enabled");
                        pw.println("Context sequence prior to flag:");

                        for (TimelineEvent te : fc.context) {
                            pw.println("  - " + te.type + " " + te.shortData().replace(te.type + " ", ""));
                        }
                    }
                }
            }
            HadesLogger.get().info("[ACDebugger] Clean report saved locally to: " + flagFile.getAbsolutePath());
        } catch (Exception e) {
            HadesLogger.get().error("[ACDebugger] Export failed", e);
        }
    }

    private String formatTime(long ms) {
        long s = ms / 1000;
        long dec = ms % 1000;
        return String.format("%02d:%02d.%03d", s / 60, s % 60, dec);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEXT EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════

    private String extractChatText(Object packet) {
        if (chatReflectionFailed)
            return null;
        try {
            Field chatField = ReflectionUtil.findField(packet.getClass(), "a", "chatComponent", "field_148919_a");
            if (chatField == null) {
                chatReflectionFailed = true;
                return null;
            }
            chatField.setAccessible(true);
            Object chatComponent = chatField.get(packet);
            if (chatComponent == null)
                return null;

            if (getUnformattedTextMethod == null) {
                getUnformattedTextMethod = ReflectionUtil.findMethod(chatComponent.getClass(),
                        new String[] { "c", "getUnformattedText", "func_150260_c" });
                if (getUnformattedTextMethod == null) {
                    getUnformattedTextMethod = ReflectionUtil.findMethod(chatComponent.getClass(),
                            new String[] { "e", "getUnformattedTextForChat", "func_150254_d" });
                }
            }
            if (getUnformattedTextMethod != null) {
                Object result = getUnformattedTextMethod.invoke(chatComponent);
                return result != null ? result.toString() : null;
            }
        } catch (Exception e) {
            if (!chatReflectionFailed) {
                chatReflectionFailed = true;
            }
        }
        return null;
    }

    private String fmt(double v) {
        return String.format("%.4f", v);
    }

    private String fmt(float v) {
        return String.format("%.3f", v);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA STRUCTURES
    // ═══════════════════════════════════════════════════════════════════════

    private static class TimelineEvent {
        final long elapsedMs;
        final int tickId;
        final int packetIdx;
        final String type;
        final String data;

        TimelineEvent(long elapsedMs, int tickId, int packetIdx, String type, String data) {
            this.elapsedMs = elapsedMs;
            this.tickId = tickId;
            this.packetIdx = packetIdx;
            this.type = type;
            this.data = data;
        }

        String shortData() {
            return data.length() > 250 ? data.substring(0, 250) + "..." : data;
        }
    }

    private static class FlagCapture {
        final long elapsedMs;
        final int tickId;
        final String flagType;
        final String flagText;
        final String activeModules;
        final List<TimelineEvent> context;

        FlagCapture(long elapsedMs, int tickId, String flagType, String flagText, String activeModules,
                List<TimelineEvent> context) {
            this.elapsedMs = elapsedMs;
            this.tickId = tickId;
            this.flagType = flagType;
            this.flagText = flagText;
            this.activeModules = activeModules;
            this.context = context;
        }
    }
}

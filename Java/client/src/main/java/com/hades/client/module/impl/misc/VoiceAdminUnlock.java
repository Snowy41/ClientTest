package com.hades.client.module.impl.misc;

import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Voice Chat Admin Unlock — LabyMod Exploit
 *
 * Forces the VoiceClient.isAdmin flag to true, unlocking the full staff
 * moderation panel in voice chat. This includes:
 *   - Mute/Unmute any player
 *   - Warn players
 *   - Kick players from voice
 *   - Move players between channels
 *   - View player metadata and staff notes
 *   - Create/Delete/Update voice channels
 *
 * The voice server WILL REJECT moderation commands from non-staff, but
 * the UI is unlocked and can be explored.
 *
 * Source refs:
 *   - VoiceClient.isAdmin (boolean field, set from HandshakeResponsePacket.isStaff)
 *   - VoiceClient.handleHandshakeResponse() line 205: this.isAdmin = packet.isStaff()
 *   - DefaultVoiceConnector.isStaff() delegates to voiceClient.isAdmin()
 */
public class VoiceAdminUnlock extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final BooleanSetting forceAdmin = register(
            new BooleanSetting("Force Admin", "Force voice chat admin/staff status", true));
    private final BooleanSetting showStaffUI = register(
            new BooleanSetting("Staff UI", "Unlock the full voice moderation UI", true));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    // Cached reflection refs
    private Field isAdminField;
    private Object voiceClientRef;

    public VoiceAdminUnlock() {
        super("VoiceAdminUnlock", "Unlock voice chat staff/admin panel & moderation tools",
                Category.MISC, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Hades-VoiceAdminUnlock");
            t.setDaemon(true);
            return t;
        });

        // Periodically force isAdmin=true (in case of reconnect/handshake reset)
        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (forceAdmin.getValue()) {
                    forceAdminFlag();
                }
            } catch (Throwable t) {
                // Voice chat might not be connected
            }
        }, 1000, 2000, TimeUnit.MILLISECONDS);

        // Try immediately
        try {
            forceAdminFlag();
        } catch (Throwable ignored) {}

        LOG.info("[VoiceAdminUnlock] Enabled");
    }

    @Override
    protected void onDisable() {
        // Restore the original admin state
        try {
            if (isAdminField != null && voiceClientRef != null) {
                isAdminField.set(voiceClientRef, false);
                LOG.info("[VoiceAdminUnlock] Restored isAdmin = false");
            }
        } catch (Exception ignored) {}

        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        task = null;
        scheduler = null;
        isAdminField = null;
        voiceClientRef = null;
        LOG.info("[VoiceAdminUnlock] Disabled");
    }

    /**
     * Locate the VoiceClient instance and force its isAdmin field to true.
     * Path: VoiceConnector -> DefaultVoiceConnector.voiceClient (VoiceClient) -> isAdmin
     */
    private void forceAdminFlag() throws Exception {
        if (!isLabyModPresent()) return;

        if (voiceClientRef != null && isAdminField != null) {
            // Fast path: already resolved
            isAdminField.set(voiceClientRef, true);
            return;
        }

        // Resolve VoiceClient via the VoiceConnector
        Object voiceClient = resolveVoiceClient();
        if (voiceClient == null) return;

        voiceClientRef = voiceClient;

        // Find the isAdmin boolean field in VoiceClient
        Class<?> vcClass = voiceClient.getClass();
        for (Field f : vcClass.getDeclaredFields()) {
            if (f.getType() == boolean.class) {
                String name = f.getName().toLowerCase();
                if (name.contains("admin") || name.equals("isadmin")) {
                    f.setAccessible(true);
                    isAdminField = f;
                    break;
                }
            }
        }

        // Fallback: try the isAdmin() method to identify the field
        if (isAdminField == null) {
            try {
                Method isAdminMethod = vcClass.getMethod("isAdmin");
                boolean beforeVal = (boolean) isAdminMethod.invoke(voiceClient);

                // Toggle all boolean fields to find which one isAdmin() reads from
                for (Field f : vcClass.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        f.setAccessible(true);
                        boolean orig = f.getBoolean(voiceClient);
                        f.set(voiceClient, !orig);
                        boolean afterVal = (boolean) isAdminMethod.invoke(voiceClient);
                        if (afterVal != beforeVal) {
                            // Found it!
                            isAdminField = f;
                            f.set(voiceClient, true);
                            LOG.info("[VoiceAdminUnlock] Identified admin field: " + f.getName());
                            return;
                        }
                        f.set(voiceClient, orig); // Restore
                    }
                }
            } catch (Exception e) {
                LOG.error("[VoiceAdminUnlock] Could not identify admin field via method probe", e);
            }
        }

        if (isAdminField != null) {
            isAdminField.set(voiceClient, true);
            LOG.info("[VoiceAdminUnlock] Forced isAdmin = true");
        }
    }

    private Object resolveVoiceClient() {
        try {
            Class<?> addonClass = Class.forName("net.labymod.addons.voicechat.core.VoiceChatAddon");
            Object addonInstance = addonClass.getField("INSTANCE").get(null);
            if (addonInstance == null) return null;

            Object refStorage = addonInstance.getClass().getMethod("referenceStorage").invoke(addonInstance);
            if (refStorage == null) return null;

            Object connector = refStorage.getClass().getMethod("voiceConnector").invoke(refStorage);
            if (connector == null) return null;

            Class<?> voiceClientClass = Class.forName("net.labymod.voice.client.VoiceClient");
            Object voiceClient = findFieldOfType(connector, voiceClientClass);
            if (voiceClient != null) {
                LOG.info("[VoiceAdminUnlock] Found VoiceClient instance");
                return voiceClient;
            }
        } catch (ClassNotFoundException e) {
            LOG.info("[VoiceAdminUnlock] VoiceChat addon not present");
        } catch (Exception e) {
            LOG.error("[VoiceAdminUnlock] Failed to resolve VoiceClient", e);
        }
        return null;
    }

    /**
     * Helper: find a field of a specific type (or subtype) on an object via reflection.
     */
    private Object findFieldOfType(Object obj, Class<?> targetType) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null && targetType.isAssignableFrom(val.getClass())) {
                        return val;
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private boolean isLabyModPresent() {
        try {
            Class.forName("net.labymod.api.Laby");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

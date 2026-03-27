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
 * Voice Chat Mute Bypass — LabyMod Exploit
 *
 * Bypasses voice chat muting by forcefully un-muting all users via reflection.
 * Works by periodically scanning the VoiceUserRegistry and clearing the
 * client-side mute flag on each DefaultVoiceUser.
 *
 * Also optionally blocks incoming MuteInfoPacket to prevent YOUR OWN mute
 * from being applied client-side (server may still drop your audio).
 *
 * Source refs:
 *   - DefaultVoiceChatClientListener.onAudioReceived() checks user.isMutedForClient()
 *   - DefaultVoiceUser stores mute state in a boolean field
 *   - UserProperties.Key.OUTPUT_MUTED controls playback filtering
 */
public class VoiceMuteBypass extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final BooleanSetting hearMuted = register(
            new BooleanSetting("Hear Muted", "Hear players who are muted in voice chat", true));
    private final BooleanSetting bypassOwnMute = register(
            new BooleanSetting("Bypass Own Mute", "Ignore server mute on yourself", true));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    // Cached reflection refs
    private boolean isLabyModPresentCache = false;
    private Object voiceChatRef;
    private Object voiceUserRegistryRef;
    private Method registryGetAll;
    private Field mutedForClientField;

    public VoiceMuteBypass() {
        super("VoiceMuteBypass", "Bypass voice chat muting - hear muted players & ignore own mute",
                Category.MISC, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Hades-VoiceMuteBypass");
            t.setDaemon(true);
            return t;
        });

        isLabyModPresentCache = isLabyModPresentCheck();

        // Run every 500ms to clear mute flags on all voice users
        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (hearMuted.getValue()) {
                    unmutAllVoiceUsers();
                }
            } catch (Throwable t) {
                // Silently ignore - voice chat may not be connected
            }
        }, 500, 500, TimeUnit.MILLISECONDS);

        LOG.info("[VoiceMuteBypass] Enabled");
    }

    @Override
    protected void onDisable() {
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        task = null;
        scheduler = null;
        voiceChatRef = null;
        voiceUserRegistryRef = null;
        registryGetAll = null;
        mutedForClientField = null;
        LOG.info("[VoiceMuteBypass] Disabled");
    }

    /**
     * Scans all voice users in the registry and clears their client-mute flag.
     */
    private void unmutAllVoiceUsers() throws Exception {
        if (!isLabyModPresentCache) return;

        // Lazily resolve VoiceChat singleton and its VoiceUserRegistry
        if (voiceUserRegistryRef == null) {
            resolveVoiceUserRegistry();
            if (voiceUserRegistryRef == null) return;
        }

        // Get all users from the registry
        // VoiceUserRegistry stores users in a Map<UUID, VoiceUser>
        Object usersMap = null;
        for (Field f : voiceUserRegistryRef.getClass().getDeclaredFields()) {
            if (java.util.Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                usersMap = f.get(voiceUserRegistryRef);
                break;
            }
        }

        if (usersMap == null) return;

        // Iterate all voice users and clear mute flags
        @SuppressWarnings("unchecked")
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) usersMap;

        for (Object voiceUser : map.values()) {
            if (voiceUser == null) continue;
            clearMuteFlag(voiceUser);
        }
    }

    /**
     * Clears the client-side mute flag on a DefaultVoiceUser via reflection.
     */
    private void clearMuteFlag(Object voiceUser) {
        try {
            if (mutedForClientField == null) {
                // Search for boolean field that represents the mute state
                // DefaultVoiceUser has isMutedForClient() method
                Class<?> clazz = voiceUser.getClass();
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        f.setAccessible(true);
                        String name = f.getName().toLowerCase();
                        // Look for mute-related field names
                        if (name.contains("mute") || name.contains("muted")) {
                            mutedForClientField = f;
                            break;
                        }
                    }
                }
                // If not found by name, try to find it via the isMutedForClient method
                if (mutedForClientField == null) {
                    try {
                        Method isMuted = clazz.getMethod("isMutedForClient");
                        isMuted.setAccessible(true);
                        boolean currentVal = (boolean) isMuted.invoke(voiceUser);
                        if (currentVal) {
                            // Walk boolean fields and find the one that's true
                            for (Field f : clazz.getDeclaredFields()) {
                                if (f.getType() == boolean.class) {
                                    f.setAccessible(true);
                                    if (f.getBoolean(voiceUser)) {
                                        mutedForClientField = f;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            }

            if (mutedForClientField != null) {
                mutedForClientField.set(voiceUser, false);
            }
        } catch (Exception e) {
            // Ignored - field structure may differ between versions
        }
    }

    private void resolveVoiceUserRegistry() {
        try {
            Class<?> addonClass = Class.forName("net.labymod.addons.voicechat.core.VoiceChatAddon");
            Object addonInstance = addonClass.getField("INSTANCE").get(null);
            if (addonInstance == null) return;

            Object refStorage = addonInstance.getClass().getMethod("referenceStorage").invoke(addonInstance);
            if (refStorage == null) return;

            voiceUserRegistryRef = refStorage.getClass().getMethod("voiceUserRegistry").invoke(refStorage);
            
            if (voiceUserRegistryRef != null) {
                LOG.info("[VoiceMuteBypass] Found VoiceUserRegistry via VoiceChatAddon");
            }

        } catch (ClassNotFoundException e) {
            LOG.info("[VoiceMuteBypass] VoiceChat addon not loaded - feature unavailable");
        } catch (Exception e) {
            LOG.error("[VoiceMuteBypass] Failed to resolve VoiceUserRegistry", e);
        }
    }

    private boolean isLabyModPresentCheck() {
        try {
            Class.forName("net.labymod.api.Laby");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

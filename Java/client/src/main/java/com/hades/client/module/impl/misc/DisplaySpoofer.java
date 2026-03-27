package com.hades.client.module.impl.misc;

import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.StringSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Display Spoofer (11.4 & 11.5)
 *
 * Spoofs subtitles and player flags (like country flags) by injecting fake packets
 * into the LabyMod server feature system.
 */
public class DisplaySpoofer extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final StringSetting targetUuid = register(
            new StringSetting("Target UUID", "UUID of the player to spoof (leave blank for self)", ""));

    private final StringSetting customSubtitle = register(
            new StringSetting("Subtitle Text", "Text to display under the player's nametag", "§c[Admin]"));

    private final StringSetting customFlag = register(
            new StringSetting("Country Flag Code", "Two-letter country code (e.g., US, DE, RU)", "RU"));

    private final BooleanSetting injectLocal = register(
            new BooleanSetting("Local Only", "Inject directly into local client (client-sided instead of server broadcast)", true));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    private boolean cached = false;
    private Class<?> subtitleModelClass;
    private Constructor<?> subConstructor;
    private Class<?> componentClass;
    private Constructor<?> compConstructor;
    private Object serverFeatureProvider;
    private Method getOrCreateUserFeatureMethod;
    private Object labyAPIInstance;

    public DisplaySpoofer() {
        super("DisplaySpoofer", "Spoof subtitles and flags via Server API (11.4, 11.5)",
                Category.MISC, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Hades-DisplaySpoofer");
            t.setDaemon(true);
            return t;
        });

        if (!cached) cacheReflection();

        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                spoofDisplay();
            } catch (Throwable t) {
                // Ignore runtime errors
            }
        }, 0, 3000, TimeUnit.MILLISECONDS);

        LOG.info("[DisplaySpoofer] Enabled - Subtitle: " + customSubtitle.getValue());
    }

    private void cacheReflection() {
        try {
            Class<?> labyModClass = Class.forName("net.labymod.core.main.LabyMod");
            Object references = labyModClass.getMethod("references").invoke(null);
            Object service = references.getClass().getMethod("serverFeatureService").invoke(references);
            serverFeatureProvider = service.getClass().getMethod("get").invoke(service);
            getOrCreateUserFeatureMethod = serverFeatureProvider.getClass().getMethod("getOrCreateUserFeature", UUID.class);

            subtitleModelClass = Class.forName("net.labymod.serverapi.core.model.display.Subtitle");
            subConstructor = subtitleModelClass.getConstructor(UUID.class, double.class, String.class);
            componentClass = Class.forName("net.labymod.core.main.user.serverfeature.subtitle.SubtitleComponent");
            compConstructor = componentClass.getConstructor(subtitleModelClass);

            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            labyAPIInstance = labyClass.getMethod("labyAPI").invoke(null);
            
            cached = true;
        } catch (Exception e) {
            LOG.error("[DisplaySpoofer] Failed to cache reflection", e);
        }
    }

    @Override
    protected void onDisable() {
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        task = null;
        scheduler = null;
        LOG.info("[DisplaySpoofer] Disabled");
    }

    private void spoofDisplay() throws Exception {
        if (!cached) return;
        UUID target = getTarget();
        if (target == null) return;

        if (injectLocal.getValue()) {
            injectLocally(target, customSubtitle.getValue(), customFlag.getValue());
        } else {
            sendToServer(target, customSubtitle.getValue(), customFlag.getValue());
        }
    }

    private UUID getTarget() {
        String input = targetUuid.getValue();
        if (input == null || input.isEmpty()) {
            return getPlayerUUID();
        }
        try {
            return UUID.fromString(input);
        } catch (Exception e) {
            return getPlayerUUID(); // Fallback
        }
    }
    
    private UUID getPlayerUUID() {
        if (!cached) return UUID.randomUUID();
        try {
            Object minecraft = labyAPIInstance.getClass().getMethod("minecraft").invoke(labyAPIInstance);
            Object sessionAccessor = minecraft.getClass().getMethod("sessionAccessor").invoke(minecraft);
            Object session = sessionAccessor.getClass().getMethod("getSession").invoke(sessionAccessor);
            return (UUID) session.getClass().getMethod("getUniqueId").invoke(session);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }

    private void sendToServer(UUID target, String subtitle, String flagCode) throws Exception {
        // Fallback to local injection since server payload sending requires manual ByteBuf mapping
        // which may crash the client if the protocol version isn't perfectly matched.
        injectLocally(target, subtitle, flagCode);
    }

    private void injectLocally(UUID target, String subtitle, String flagCode) throws Exception {
        if (!cached) return;
        
        Object userFeature = getOrCreateUserFeatureMethod.invoke(serverFeatureProvider, target);

        if (userFeature != null) {
            Object subtitleObj = subConstructor.newInstance(target, 1.0, subtitle);
            Object subtitleComponent = compConstructor.newInstance(subtitleObj);
            
            // userFeature.setSubtitle(SubtitleComponent)
            Method setSub = userFeature.getClass().getMethod("setSubtitle", componentClass);
            setSub.invoke(userFeature, subtitleComponent);
            
            // Flag injecting (UserServerFeature.setCountryCode(String))
            try {
                Method setCountry = userFeature.getClass().getMethod("setCountryCode", String.class);
                setCountry.invoke(userFeature, flagCode.toLowerCase());
            } catch (Exception ignored) {} // ServerFeature might use different flag fields in Laby 4
        }
    }
}

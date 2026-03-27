package com.hades.client.module.impl.misc;

import com.hades.client.api.HadesAPI;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.MultiSelectSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CrossClientSprays extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final MultiSelectSetting spraysToPlay = register(
            new MultiSelectSetting("Sprays", "Select sprays to play", this::pullSprays)
    );

    private final BooleanSetting continuous = register(
            new BooleanSetting("Spam Mode", "Continuously spam the action", false));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public CrossClientSprays() {
        super("CrossClientSprays", "Spoof sprays visible to ALL nearby players",
                Category.MISC, Keyboard.KEY_NONE);
    }

    private List<MultiSelectSetting.Option> pullSprays() {
        List<MultiSelectSetting.Option> opts = new ArrayList<>();
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object references = labyClass.getMethod("references").invoke(null);
            Object sprayService = references.getClass().getMethod("sprayService").invoke(references);
            Object sprayStorage = sprayService.getClass().getMethod("sprayStorage").invoke(sprayService);
            List<?> packs = (List<?>) sprayStorage.getClass().getMethod("getPacks").invoke(sprayStorage);
            
            Class<?> iconClass = Class.forName("net.labymod.api.client.gui.icon.Icon");
            Method urlMethod = iconClass.getMethod("url", String.class);

            for (Object pack : packs) {
                List<?> sprays = (List<?>) pack.getClass().getMethod("getSprays").invoke(pack);
                for (Object sprayObj : sprays) {
                    Method getId = sprayObj.getClass().getMethod("getId");
                    Method getName = sprayObj.getClass().getMethod("getName");
                    Method getUuid = sprayObj.getClass().getMethod("getUuid");
                    int id = (int) getId.invoke(sprayObj);
                    String name = (String) getName.invoke(sprayObj);
                    java.util.UUID uuid = (java.util.UUID) getUuid.invoke(sprayObj);
                    
                    Object icon = null;
                    try {
                        String url = "https://dl.labymod.net/sticker/cut/" + uuid.toString() + ".webp";
                        icon = urlMethod.invoke(null, url);
                    } catch (Exception e) {}

                    opts.add(new MultiSelectSetting.Option(name, id, icon));
                }
            }
        } catch (Exception ignore) {}
        return opts;
    }

    @Override
    protected void onEnable() {
        if (continuous.getValue()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Hades-CrossClientSprays");
                t.setDaemon(true);
                return t;
            });

            task = scheduler.scheduleAtFixedRate(() -> {
                try {
                    triggerAction();
                } catch (Throwable t) {
                }
            }, 0, 1000, TimeUnit.MILLISECONDS);
            LOG.info("[CrossClientSprays] Enabled - Spamming");
        } else {
            LOG.info("[CrossClientSprays] Triggering once.");
            triggerAction();
            this.toggle();
        }
    }

    @Override
    protected void onDisable() {
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        task = null;
        scheduler = null;
    }

    @Override
    public String getDisplaySuffix() {
        int s = spraysToPlay.getValue() != null ? spraysToPlay.getValue().size() : 0;
        return s + " Options";
    }

    private void triggerAction() {
        List<Integer> sprays = spraysToPlay.getValue() != null ? spraysToPlay.getValue() : new ArrayList<>();

        if (!sprays.isEmpty()) {
            int randomSpray = sprays.get((int) (Math.random() * sprays.size()));
            playSpray((short) randomSpray);
        }
    }

    private void playSpray(short id) {
        Object session = getSession();
        if (session == null) {
            LOG.error("[CrossClientSprays] PlaySpray failed: Session is null");
            return;
        }

        try {
            Class<?> dirClass = Class.forName("net.labymod.api.util.math.Direction");
            Object[] dirs = dirClass.getEnumConstants();
            Object direction = dirs[0]; // DEFAULT (NORTH)

            Method spray = session.getClass().getMethod("spray", short.class, int.class, double.class, double.class, double.class, dirClass, float.class);
            
            double x = HadesAPI.Player.getX();
            double y = HadesAPI.Player.getY();
            double z = HadesAPI.Player.getZ();

            spray.invoke(session, id, 47, x, y, z, direction, 0.0f);
            
            // Local playback fallback: we broadcasted the spray. To see it on the client,
            // we should also call the sprayService.sprayClient method if possible, or
            // just let the user see it via the broadcast if LabyMod natively handles our spoofed SprayEvent correctly.
            // LabyMod's SprayCooldownTracker.sprayClient() usually fires the SprayEvent.
            // But since LabyMod triggers local sprays upon creating the packet, it might be fine.
        } catch (Exception e) {
            LOG.error("[CrossClientSprays] Failed to play spray", e);
        }
    }

    private Object getSession() {
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object labyAPI = labyClass.getMethod("labyAPI").invoke(null);
            Method labyConnectMethod = labyAPI.getClass().getMethod("labyConnect");
            Object labyConnect = labyConnectMethod.invoke(labyAPI);
            Method getSession = labyConnect.getClass().getMethod("getSession");
            return getSession.invoke(labyConnect);
        } catch (Exception e) {
            LOG.error("[CrossClientSprays] Failed to get session", e);
            return null;
        }
    }
}

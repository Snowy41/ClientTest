package com.hades.client.module.impl.misc;

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

public class CrossClientEmotes extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final MultiSelectSetting emotesToPlay = register(
            new MultiSelectSetting("Emotes", "Select emotes to play", this::pullEmotes)
    );


    private final BooleanSetting continuous = register(
            new BooleanSetting("Spam Mode", "Continuously spam the action", false));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public CrossClientEmotes() {
        super("CrossClientEmotes", "Spoof emotes and sprays visible to ALL nearby players (10.1 & 10.2)",
                Category.MISC, Keyboard.KEY_NONE);
    }

    private List<MultiSelectSetting.Option> pullEmotes() {
        List<MultiSelectSetting.Option> opts = new ArrayList<>();
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object references = labyClass.getMethod("references").invoke(null);
            Object emoteService = references.getClass().getMethod("emoteService").invoke(references);
            Object emotesMap = emoteService.getClass().getMethod("getEmotes").invoke(emoteService); // Int2ObjectOpenHashMap
            
            Object values = emotesMap.getClass().getMethod("values").invoke(emotesMap); // Collection
            Object[] emoteArray = (Object[]) values.getClass().getMethod("toArray").invoke(values);
            
            Class<?> iconClass = Class.forName("net.labymod.api.client.gui.icon.Icon");
            Method urlMethod = iconClass.getMethod("url", String.class);

            for (Object emoteObj : emoteArray) {
                if (emoteObj != null) {
                    Method getId = emoteObj.getClass().getMethod("getId");
                    Method getName = emoteObj.getClass().getMethod("getName");
                    int id = (int) getId.invoke(emoteObj);
                    String name = (String) getName.invoke(emoteObj);
                    
                    Object icon = null;
                    try {
                        String url = "https://www.labymod.net/page/tpl/assets/images/shop/products/" + name.toLowerCase().replace(" ", "-") + "_0.png";
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
                Thread t = new Thread(r, "Hades-CrossClientEmotes");
                t.setDaemon(true);
                return t;
            });

            task = scheduler.scheduleAtFixedRate(() -> {
                try {
                    triggerAction();
                } catch (Throwable t) {
                    // Fail silently on spam
                }
            }, 0, 1000, TimeUnit.MILLISECONDS);
            LOG.info("[CrossClientEmotes] Enabled - Spamming actions");
        } else {
            // Trigger once and automatically disable
            LOG.info("[CrossClientEmotes] Triggering once.");
            triggerAction();
            this.toggle(); // Turn module off
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
        int e = emotesToPlay.getValue() != null ? emotesToPlay.getValue().size() : 0;
        return e + " Options";
    }

    private void triggerAction() {
        List<Integer> emotes = emotesToPlay.getValue() != null ? emotesToPlay.getValue() : new ArrayList<>();

        if (!emotes.isEmpty()) {
            int randomEmote = emotes.get((int) (Math.random() * emotes.size()));
            playEmote(randomEmote);
        }
    }

    private void playEmote(int id) {
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object references = labyClass.getMethod("references").invoke(null);
            Object emoteService = references.getClass().getMethod("emoteService").invoke(references);
            Object emotesMap = emoteService.getClass().getMethod("getEmotes").invoke(emoteService);
            
            Object emoteItem = emotesMap.getClass().getMethod("get", int.class).invoke(emotesMap, id);
            
            if (emoteItem != null) {
                // 1. Broadcast to other players via LabyConnect session
                try {
                    Object labyAPI = labyClass.getMethod("labyAPI").invoke(null);
                    Object labyConnect = labyAPI.getClass().getMethod("labyConnect").invoke(labyAPI);
                    
                    // Check if LabyConnect is actually authenticated (PLAY state)
                    Object state = labyConnect.getClass().getMethod("state").invoke(labyConnect);
                    String stateName = state.toString();
                    LOG.info("[CrossClientEmotes] LabyConnect state: " + stateName);
                    
                    boolean isAuth = false;
                    try {
                        isAuth = (boolean) labyConnect.getClass().getMethod("isAuthenticated").invoke(labyConnect);
                    } catch (Exception ignored) {}
                    
                    if (!isAuth) {
                        LOG.error("[CrossClientEmotes] LabyConnect NOT authenticated (state=" + stateName + "). Broadcast will fail silently.");
                    }
                    
                    Object session = labyConnect.getClass().getMethod("getSession").invoke(labyConnect);
                    if (session != null) {
                        Method playEmoteMethod = session.getClass().getMethod("playEmote", short.class);
                        playEmoteMethod.invoke(session, (short) id);
                        LOG.info("[CrossClientEmotes] Broadcasted Emote " + id + " via session (auth=" + isAuth + ")");
                    } else {
                        LOG.error("[CrossClientEmotes] Session is null! LabyConnect not connected.");
                    }
                } catch (Exception sessionEx) {
                    LOG.error("[CrossClientEmotes] Session broadcast failed", sessionEx);
                }
                
                // 2. Force local playback by calling playClientEmote(UUID, EmoteItem) directly
                //    This bypasses the emotesConfig check that blocks playClientEmote(EmoteItem)
                try {
                    Object labyAPI = labyClass.getMethod("labyAPI").invoke(null);
                    Object minecraft = labyAPI.getClass().getMethod("minecraft").invoke(labyAPI);
                    Object clientPlayer = minecraft.getClass().getMethod("getClientPlayer").invoke(minecraft);
                    if (clientPlayer != null) {
                        java.util.UUID uuid = (java.util.UUID) clientPlayer.getClass().getMethod("getUniqueId").invoke(clientPlayer);
                        Class<?> emoteItemClass = Class.forName("net.labymod.core.main.user.shop.emote.model.EmoteItem");
                        Method playLocal = emoteService.getClass().getMethod("playClientEmote", java.util.UUID.class, emoteItemClass);
                        playLocal.invoke(emoteService, uuid, emoteItem);
                        LOG.info("[CrossClientEmotes] Forced local emote " + id + " via playClientEmote(UUID, EmoteItem)");
                    }
                } catch (Exception localEx) {
                    LOG.error("[CrossClientEmotes] Local playback failed", localEx);
                }
            }
        } catch (Exception e) {
            LOG.error("[CrossClientEmotes] Failed to play emote", e);
        }
    }
}

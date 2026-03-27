package com.hades.client.backend;

import com.hades.client.util.HadesLogger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

public class BackendConnection {

    private static BackendConnection instance;
    private WebSocketClient webSocket;
    private Gson gson;

    // Supabase project constants extracted from hades-core-hub-main/.env
    private static final String SUPABASE_PROJECT_URL = "szxxwxwityixqzzmarlq.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN6eHh3eHdpdHlpeHF6em1hcmxxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzA4NTkyNDQsImV4cCI6MjA4NjQzNTI0NH0.5XSYOM1VZrKOeQJSErdI-J2PcvWNo2YLHrCfQ5MNxRs";
    
    private String jwtToken;
    private boolean connected = false;

    // Profile data fetched from launcher-profile edge function
    private String profileUsername;
    private String profileAvatarUrl;
    private String profileDescription;
    private int profileHadesCoins;
    private String profileCreatedAt;
    private List<String> profileRoles = new ArrayList<>();
    private List<String> profileBadgeNames = new ArrayList<>();
    private boolean profileSubscriptionActive = false;
    private boolean profileFetched = false;

    // Cloud configs fetched from launcher-profile
    private final List<CloudConfig> cloudConfigs = new ArrayList<>();
    private boolean configsFetched = false;

    private BackendConnection() {
        this.gson = new Gson();
    }

    public static BackendConnection getInstance() {
        if (instance == null) {
            instance = new BackendConnection();
        }
        return instance;
    }

    public void connect(String sessionToken) {
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            HadesLogger.get().warn("BackendConnection: Session token is empty! Running in offline mode.");
            return;
        }

        this.jwtToken = sessionToken;
        HadesLogger.get().info("BackendConnection: Starting Supabase Realtime WebSocket Connection...");

        // Fetch profile data in background
        fetchProfile();

        try {
            // Phoenix Channels v1 WebSocket structure
            String wsUrl = "wss://" + SUPABASE_PROJECT_URL + "/realtime/v1/websocket?apikey=" + SUPABASE_ANON_KEY + "&vsn=1.0.0";
            URI uri = new URI(wsUrl);

            webSocket = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    connected = true;
                    HadesLogger.get().info("BackendConnection: Connected to Web Hub Supabase Realtime Engine.");
                    authenticateAndJoin();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    HadesLogger.get().info("BackendConnection: Connection closed. Code: " + code + ", Reason: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    HadesLogger.get().error("BackendConnection: Protocol exception occurred.", ex);
                }
            };

            webSocket.connect();

        } catch (URISyntaxException e) {
            HadesLogger.get().error("BackendConnection: Invalid URI syntax", e);
        }
    }

    /**
     * Fetch profile data from the launcher-profile edge function.
     * Runs in a background thread to avoid blocking.
     */
    private void fetchProfile() {
        new Thread(() -> {
            try {
                String urlStr = "https://" + SUPABASE_PROJECT_URL + "/functions/v1/launcher-profile";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + jwtToken);
                conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JsonObject response = gson.fromJson(sb.toString(), JsonObject.class);

                    // Parse profile
                    if (response.has("profile")) {
                        JsonObject profile = response.getAsJsonObject("profile");
                        profileUsername = profile.has("username") && !profile.get("username").isJsonNull() 
                                ? profile.get("username").getAsString() : null;
                        profileAvatarUrl = profile.has("avatar_url") && !profile.get("avatar_url").isJsonNull() 
                                ? profile.get("avatar_url").getAsString() : null;
                        profileDescription = profile.has("description") && !profile.get("description").isJsonNull() 
                                ? profile.get("description").getAsString() : "";
                        profileHadesCoins = profile.has("hades_coins") && !profile.get("hades_coins").isJsonNull() 
                                ? profile.get("hades_coins").getAsInt() : 0;
                        profileCreatedAt = profile.has("created_at") && !profile.get("created_at").isJsonNull() 
                                ? profile.get("created_at").getAsString() : "";
                    }

                    // Parse roles
                    if (response.has("roles") && response.get("roles").isJsonArray()) {
                        profileRoles.clear();
                        for (JsonElement el : response.getAsJsonArray("roles")) {
                            profileRoles.add(el.getAsString());
                        }
                    }

                    // Parse badges
                    if (response.has("badges") && response.get("badges").isJsonArray()) {
                        profileBadgeNames.clear();
                        for (JsonElement el : response.getAsJsonArray("badges")) {
                            if (el.isJsonObject() && el.getAsJsonObject().has("badge_name")) {
                                profileBadgeNames.add(el.getAsJsonObject().get("badge_name").getAsString());
                            }
                        }
                    }

                    // Parse subscription
                    if (response.has("subscription") && response.get("subscription").isJsonObject()) {
                        JsonObject sub = response.getAsJsonObject("subscription");
                        profileSubscriptionActive = sub.has("active") && sub.get("active").getAsBoolean();
                    }

                    // Parse configs (own + purchased)
                    parseConfigs(response);

                    profileFetched = true;

                    // Update HadesClient session username from DB if available
                    if (profileUsername != null && !profileUsername.isEmpty()) {
                        com.hades.client.HadesClient.getInstance().setSessionUsername(profileUsername);
                    }

                    HadesLogger.get().info("BackendConnection: Profile fetched - " + profileUsername 
                            + " | Coins: " + profileHadesCoins 
                            + " | Configs: " + cloudConfigs.size()
                            + " | Roles: " + profileRoles 
                            + " | Sub: " + profileSubscriptionActive);
                } else {
                    HadesLogger.get().warn("BackendConnection: launcher-profile returned HTTP " + code);
                }
            } catch (Exception e) {
                HadesLogger.get().error("BackendConnection: Failed to fetch profile", e);
            }
        }, "Hades-ProfileFetch").start();
    }

    private void parseConfigs(JsonObject response) {
        if (response.has("configs") && response.get("configs").isJsonArray()) {
            synchronized (cloudConfigs) {
                cloudConfigs.clear();
                for (JsonElement el : response.getAsJsonArray("configs")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject c = el.getAsJsonObject();
                    cloudConfigs.add(new CloudConfig(
                            c.has("id") ? c.get("id").getAsString() : "",
                            c.has("name") && !c.get("name").isJsonNull() ? c.get("name").getAsString() : "Unnamed",
                            c.has("description") && !c.get("description").isJsonNull() ? c.get("description").getAsString() : "",
                            c.has("category") && !c.get("category").isJsonNull() ? c.get("category").getAsString() : "PvP",
                            c.has("file_path") && !c.get("file_path").isJsonNull() ? c.get("file_path").getAsString() : "",
                            c.has("is_official") && c.get("is_official").getAsBoolean(),
                            c.has("downloads") ? c.get("downloads").getAsInt() : 0,
                            c.has("rating") ? c.get("rating").getAsFloat() : 0f
                    ));
                }
                configsFetched = true;
            }
        }
    }

    private void authenticateAndJoin() {
        // Join the general public channel with broadcast enabled
        Map<String, Object> broadcastConfig = new HashMap<>();
        broadcastConfig.put("ack", false);
        broadcastConfig.put("self", false); // Don't echo own broadcasts back

        Map<String, Object> config = new HashMap<>();
        config.put("broadcast", broadcastConfig);

        Map<String, Object> payload = new HashMap<>();
        payload.put("access_token", jwtToken);
        payload.put("config", config);

        Map<String, Object> message = new HashMap<>();
        message.put("topic", "realtime:public");
        message.put("event", "phx_join");
        message.put("payload", payload);
        message.put("ref", "1");

        webSocket.send(gson.toJson(message));
        HadesLogger.get().info("BackendConnection: Sent Auth/Join message into Phoenix Channel (broadcast enabled).");

        // Also subscribe specifically to configs table changes
        Map<String, Object> configPayload = new HashMap<>();
        configPayload.put("access_token", jwtToken);

        Map<String, Object> configMsg = new HashMap<>();
        configMsg.put("topic", "realtime:public:configs");
        configMsg.put("event", "phx_join");
        configMsg.put("payload", configPayload);
        configMsg.put("ref", "2");

        webSocket.send(gson.toJson(configMsg));
        HadesLogger.get().info("BackendConnection: Subscribed to configs table changes.");

        // Start heartbeat to keep the connection alive
        startHeartbeat();
    }

    private void startHeartbeat() {
        Thread heartbeat = new Thread(() -> {
            while (connected && webSocket != null && webSocket.isOpen()) {
                try {
                    Thread.sleep(30000); // 30 second heartbeat
                    Map<String, Object> hb = new HashMap<>();
                    hb.put("topic", "phoenix");
                    hb.put("event", "heartbeat");
                    hb.put("payload", new HashMap<>());
                    hb.put("ref", String.valueOf(System.currentTimeMillis()));
                    webSocket.send(gson.toJson(hb));
                } catch (Exception e) {
                    break;
                }
            }
        }, "Hades-WS-Heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    private void handleMessage(String rawJson) {
        try {
            JsonObject msg = gson.fromJson(rawJson, JsonObject.class);
            String topic = msg.has("topic") ? msg.get("topic").getAsString() : "";
            String event = msg.has("event") ? msg.get("event").getAsString() : "";

            // Check for config table changes
            if (topic.contains("configs") && (event.equals("INSERT") || event.equals("UPDATE") || event.equals("DELETE"))) {
                HadesLogger.get().info("BackendConnection: Config change detected (" + event + "), re-fetching configs...");
                fetchProfile(); // Re-fetch everything to get the updated config list
            }
            
            // Check for role broadcasts from other clients.
            // Supabase Realtime redelivers broadcast messages using the CUSTOM event name
            // we specified ("role_sync"), not "broadcast".
            if (topic.equals("realtime:public") && msg.has("payload")) {
                JsonObject outerPayload = msg.getAsJsonObject("payload");

                // Case 1: Supabase wraps broadcast as event="broadcast", payload={event:"role_sync", payload:{...}}
                if (event.equals("broadcast") && outerPayload.has("event") 
                        && "role_sync".equals(outerPayload.get("event").getAsString())
                        && outerPayload.has("payload")) {
                    JsonObject innerPayload = outerPayload.getAsJsonObject("payload");
                    applyRoleSync(innerPayload);
                }
                // Case 2: Supabase delivers directly as event="role_sync", payload={uuid:..., role:...}
                else if (event.equals("role_sync")) {
                    applyRoleSync(outerPayload);
                }
            }
        } catch (Exception e) {
            HadesLogger.get().error("BackendConnection: Failed to parse incoming socket event.", e);
        }
    }

    private void applyRoleSync(JsonObject payload) {
        String uuidStr = payload.has("uuid") ? payload.get("uuid").getAsString() : null;
        String roleStr = payload.has("role") ? payload.get("role").getAsString() : null;
        
        if (uuidStr != null && roleStr != null) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                com.hades.client.module.impl.render.RoleManager.setRole(uuid, roleStr);
                HadesLogger.get().info("BackendConnection: Role synced - " + uuidStr.substring(0, 8) + "... -> " + roleStr);
            } catch (Exception e) {
                HadesLogger.get().error("BackendConnection: Invalid UUID in role_sync: " + uuidStr);
            }
        }
    }

    /**
     * Broadcasts the current player's role to all connected clients via Supabase Realtime
     */
    public void broadcastRole(String uuid, String role) {
        if (!connected || webSocket == null || !webSocket.isOpen()) return;
        try {
            JsonObject payloadInner = new JsonObject();
            payloadInner.addProperty("type", "role_sync");
            payloadInner.addProperty("uuid", uuid);
            payloadInner.addProperty("role", role);

            JsonObject payload = new JsonObject();
            payload.addProperty("type", "broadcast");
            payload.addProperty("event", "role_sync");
            payload.add("payload", payloadInner);

            JsonObject message = new JsonObject();
            message.addProperty("topic", "realtime:public");
            message.addProperty("event", "broadcast");
            message.add("payload", payload);
            message.addProperty("ref", String.valueOf(System.currentTimeMillis()));

            webSocket.send(gson.toJson(message));
        } catch (Exception e) {
            HadesLogger.get().error("BackendConnection: Failed to construct broadcast payload.", e);
        }
    }

    /**
     * Download a config's JSON content from Supabase Storage.
     * The filePath is relative to the configs bucket (e.g. "userId/configName.json").
     * Returns the raw JSON string, or null on failure.
     */
    public String downloadConfigJson(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        try {
            String urlStr = "https://" + SUPABASE_PROJECT_URL + "/storage/v1/object/configs/" + filePath;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + jwtToken);
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                reader.close();
                return sb.toString();
            } else {
                HadesLogger.get().warn("BackendConnection: Config download returned HTTP " + code + " for " + filePath);
            }
        } catch (Exception e) {
            HadesLogger.get().error("BackendConnection: Failed to download config: " + filePath, e);
        }
        return null;
    }

    public void disconnect() {
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.close();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // ── Profile data getters ──
    public boolean isProfileFetched() { return profileFetched; }
    public String getProfileUsername() { return profileUsername; }
    public String getProfileAvatarUrl() { return profileAvatarUrl; }
    public String getProfileDescription() { return profileDescription; }
    public int getProfileHadesCoins() { return profileHadesCoins; }
    public String getProfileCreatedAt() { return profileCreatedAt; }
    public List<String> getProfileRoles() { return profileRoles; }
    public List<String> getProfileBadgeNames() { return profileBadgeNames; }
    public boolean isProfileSubscriptionActive() { return profileSubscriptionActive; }

    // ── Cloud config getters ──
    public boolean isConfigsFetched() { return configsFetched; }
    public List<CloudConfig> getCloudConfigs() {
        synchronized (cloudConfigs) {
            return new ArrayList<>(cloudConfigs);
        }
    }
}

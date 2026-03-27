package com.hades.client;

import com.hades.client.event.EventBus;
import com.hades.client.gui.clickgui.ClickGUI;
import com.hades.client.platform.PlatformManager;
import com.hades.client.module.ModuleManager;
import com.hades.client.config.ConfigManager;
import com.hades.client.render.GLStateManager;
import com.hades.client.util.HadesLogger;
import com.hades.client.api.HadesAPI;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;


public class HadesClient {
    public static final String NAME = "Hades";
    public static final String VERSION = "1.0.0";

    private static HadesClient instance;

    private EventBus eventBus;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private ClickGUI clickGUI;
    private com.hades.client.gui.hud.HudEditorScreen hudEditorScreen;
    private boolean hudEditorOpen;
    private boolean running;
    
    // Auth
    private String sessionToken;
    private String sessionUsername = "Unknown";
    private String sessionEmail = "";

    // Keybind state
    private Class<?> keyboardClass;

    private HadesClient() {
    }

    public static HadesClient getInstance() {
        if (instance == null) {
            instance = new HadesClient();
        }
        return instance;
    }

    public void start(String token) {
        this.sessionToken = token;
        HadesLogger.get().info("Hades Client starting...");
        
        if (token != null && !token.isEmpty()) {
            HadesLogger.get().info("Received backend session token!");

            // Globally parse Identity from JWT for Gui Elements
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
                    String payloadString = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                    
                    com.google.gson.JsonObject payload = new com.google.gson.Gson().fromJson(payloadString, com.google.gson.JsonObject.class);
                    if (payload.has("email")) this.sessionEmail = payload.get("email").getAsString();
                    if (payload.has("user_metadata")) {
                        com.google.gson.JsonObject meta = payload.getAsJsonObject("user_metadata");
                        if (meta.has("username")) this.sessionUsername = meta.get("username").getAsString();
                    }
                }
            } catch (Exception e) {
                HadesLogger.get().error("Failed to parse JWT payload natively", e);
            }

            // Connect to real-time sync with Web Hub
            com.hades.client.backend.BackendConnection.getInstance().connect(token);
        }

        // Initialize MC wrapper
        

        // Initialize GL
        GLStateManager.init();

        // Initialize custom font renderer (TTF atlas from /resources/font/custom.ttf)
        com.hades.client.util.font.FontUtil.init();
        HadesLogger.get().info("FontUtil initialized: " + com.hades.client.util.font.FontUtil.isLoaded());

        // Initialize event bus
        eventBus = new EventBus();

        // Initialize modules
        moduleManager = new ModuleManager();
        configManager = new ConfigManager();
        moduleManager.init();

        // Load default config
        configManager.load("default.json");

        // Crash-Safety: JVM Shutdown Hook to save instantly before the process dies unexpectedly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            HadesLogger.get().info("Emergency shutdown hook triggered - Saving config forcefully!");
            configManager.save(configManager.getActiveConfigName());
        }, "Hades-Shutdown-Hook"));

        // Initialize ClickGUI and HudEditor
        clickGUI = new ClickGUI();
        clickGUI.init(moduleManager);
        hudEditorScreen = new com.hades.client.gui.hud.HudEditorScreen();

        // Initialize platform detection
        PlatformManager.prepare();

        // Initialize Hades API router (needs activeAdapter to determine graphics backend)
        HadesAPI.init();
        
        // Start platform event listeners (safe now because HadesAPI is ready!)
        PlatformManager.startListeners(eventBus);

        // Register ourselves for events
        eventBus.register(this);
        eventBus.register(new com.hades.client.event.PacketPipelineHook());

        // Register TargetManager (centralized target provider for all combat modules)
        eventBus.register(com.hades.client.combat.TargetManager.getInstance());

        // Register Notification System
        eventBus.register(com.hades.client.notification.NotificationManager.getInstance());

        // Register always-on Role Nametags (P2P role broadcasting + 3D rendering)
        // eventBus.register(com.hades.client.module.impl.render.HadesRoleTags.getInstance()); // Disabled for debugging

        running = true;

        // Start keybind polling only if we have no native platform hooks
        // LabyModAdapter handles KeyEvents natively.
        if (com.hades.client.platform.PlatformManager.getActiveAdapter()
                .getPlatform() == com.hades.client.platform.ClientPlatform.VANILLA) {
            startKeybindLoop();
        }

        HadesLogger.get().info("Hades Client v" + VERSION + " started ("
                + PlatformManager.getDetectedPlatform().getDisplayName() + ")");

        // Show startup notification
        com.hades.client.notification.NotificationManager.getInstance().show(
                "Hades Client",
                "Injected successfully. Ready!",
                com.hades.client.notification.Notification.Type.INFO,
                4000L);

        // Run in-game tests after a short delay (lets events start flowing)
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                com.hades.client.test.InGameTestRunner.runAll();
            } catch (Exception e) {
                HadesLogger.get().error("In-game tests failed", e);
            }
        }, "Hades-Tests").start();
    }

    /**
     * Keybind polling loop. Checks LWJGL Keyboard state.
     * Separated from tick events because keybinds should work even when not
     * in-game.
     */
    private void startKeybindLoop() {
        Thread keybindThread = new Thread(() -> {
            while (running) {
                try {
                    checkKeybinds();
                    Thread.sleep(50); // 20Hz
                } catch (Exception e) {
                    if (System.currentTimeMillis() % 5000 < 50) {
                        HadesLogger.get().error("Keybind loop error", e);
                    }
                }
            }
        }, "Hades-Keybinds");
        keybindThread.setDaemon(true);
        keybindThread.start();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        // Only run tick logic on the main client thread to avoid LWJGL call crashes and
        // sync issues.
        // LabyModAdapter fires TickEvent on the main thread.
        // VanillaAdapter fires TickEvent on a background polling thread.
        if (com.hades.client.platform.PlatformManager.getActiveAdapter()
                .getPlatform() == com.hades.client.platform.ClientPlatform.VANILLA) {
            // Vanilla runs its own polling loop.
        } else {
            // Main thread processing for Modules and logic if needed.
        }
    }

    @EventHandler
    public void onKey(com.hades.client.event.events.KeyEvent event) {
        if (!event.isPressed() || event.getKeyCode() == 0)
            return;

        // Do not process keybinds if any GUI (Inventory, Chat, etc.) is open
        if (HadesAPI.mc != null && HadesAPI.mc.isInGui()) {
            return;
        }

        if (event.getKeyCode() == 54) { // RSHIFT
            if (clickGUI != null) {
                HadesLogger.get().info("RSHIFT pressed (via Event), toggling ClickGUI");
                clickGUI.toggle();
            }
        }

        for (com.hades.client.module.Module module : moduleManager.getModules()) {
            if (module.getKeyBind() == event.getKeyCode()) {
                module.toggle();
            }
        }

        // F8 = Scene Capture (dev-only, gated by -Dclient.preview.capture=true)
        if (event.getKeyCode() == com.hades.client.preview.SceneCaptureManager.CAPTURE_KEY) {
            com.hades.client.preview.SceneCaptureManager.onCaptureKey();
        }
    }

    private void checkKeybinds() {
        try {
            if (keyboardClass == null) {
                keyboardClass = Class.forName("org.lwjgl.input.Keyboard", true,
                        com.hades.client.util.ReflectionUtil.findClass("ave") != null ? com.hades.client.util.ReflectionUtil.findClass("ave").getClassLoader()
                                : Thread.currentThread().getContextClassLoader());
            }

            // We only rely on checkKeybinds for continuous polling if needed,
            // but for toggle, the KeyEvent is better!
            // Actually, wait, if we are in LabyMod, the KeyEvent works flawlessly.
            // So we don't need to poll for toggles here anymore.

        } catch (Exception e) {
            // Ignore
        }
    }

    public void stop() {
        running = false;

        // Save cleanly on normal exit
        if (configManager != null) {
            configManager.save(configManager.getActiveConfigName());
        }

        // Close WebSocket
        com.hades.client.backend.BackendConnection.getInstance().disconnect();

        PlatformManager.shutdown();
        HadesLogger.get().info("Hades Client stopped.");
    }

    // ══════════════════════════════════════════
    // ClickGUI integration
    // ══════════════════════════════════════════

    /**
     * Opens the ClickGUI by displaying a HadesScreen.
     */
    public void openClickGUI() {
        if (PlatformManager.getActiveAdapter() != null) {
            PlatformManager.getActiveAdapter().displayClickGUI();
        }
    }

    public void closeClickGUI() {
        if (PlatformManager.getActiveAdapter() != null) {
            PlatformManager.getActiveAdapter().closeClickGUI();
        }
    }

    // ══════════════════════════════════════════
    // Accessors
    // ══════════════════════════════════════════

    public EventBus getEventBus() {
        return eventBus;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ClickGUI getClickGUI() {
        return clickGUI;
    }

    public com.hades.client.gui.hud.HudEditorScreen getHudEditorScreen() {
        return hudEditorScreen;
    }

    public boolean isHudEditorOpen() {
        return hudEditorOpen;
    }

    public void setHudEditorOpen(boolean hudEditorOpen) {
        this.hudEditorOpen = hudEditorOpen;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getSessionUsername() {
        return sessionUsername;
    }

    public void setSessionUsername(String username) {
        this.sessionUsername = username;
    }

    public String getSessionEmail() {
        return sessionEmail;
    }
}
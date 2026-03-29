package com.hades.client.platform.adapters;

import com.hades.client.event.EventBus;
import com.hades.client.api.HadesAPI;

import com.hades.client.platform.ClientPlatform;
import com.hades.client.platform.PlatformAdapter;
import com.hades.client.util.HadesLogger;

// Direct LabyMod imports — compileOnly dependency, already loaded at runtime by LabyMod
import net.labymod.api.Laby;
import net.labymod.api.LabyAPI;

import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.event.client.render.GameRenderEvent;

/**
 * LabyMod 4 platform adapter.
 * Subscribes to LabyMod's EventBus using direct class imports and
 * proxies events to the Hades EventBus.
 * 
 * Uses {@code Laby.labyAPI().eventBus().registerListener()} to hook
 * into LabyMod's event pipeline. Events are fired on MC's main thread.
 */
public class LabyModAdapter implements PlatformAdapter {
    private static final HadesLogger LOG = HadesLogger.get();

    private boolean active = false;
    private EventBus hadesEventBus;
    private java.awt.image.BufferedImage hadesLogo = null;
    private int tickCounter = 0;

    @Override
    public String getName() {
        return "LabyMod 4";
    }

    @Override
    public ClientPlatform getPlatform() {
        return ClientPlatform.LABYMOD;
    }

    @Override
    public boolean initialize(EventBus hadesEventBus) {
        this.hadesEventBus = hadesEventBus;

        try {
            java.io.InputStream stream = null;
            String path = "assets/hades/pictures/logo.png";
            
            // Try ContextClassLoader first
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) stream = cl.getResourceAsStream(path);
            
            // Try base classloader
            if (stream == null) stream = LabyModAdapter.class.getResourceAsStream("/" + path);
            if (stream == null) stream = LabyModAdapter.class.getClassLoader().getResourceAsStream(path);
            
            if (stream != null) {
                hadesLogo = javax.imageio.ImageIO.read(stream);
                LOG.info("Hades logo loaded: " + hadesLogo.getWidth() + "x" + hadesLogo.getHeight());
                stream.close();
            } else {
                LOG.error("Hades logo NOT FOUND at classpath: " + path);
            }
        } catch (Throwable t) {
            LOG.error("Failed to load Hades logo", t);
        }

        try {
            // Check if LabyMod is fully initialized
            if (!Laby.isInitialized()) {
                LOG.warn("LabyMod not yet fully initialized, waiting...");
                // Wait up to 30 seconds for LabyMod to initialize
                for (int i = 0; i < 60; i++) {
                    Thread.sleep(500);
                    if (Laby.isInitialized())
                        break;
                }
                if (!Laby.isInitialized()) {
                    LOG.error("LabyMod did not initialize in time");
                    return false;
                }
            }

            // Get LabyMod API instance
            LabyAPI labyApi = Laby.labyAPI();
            if (labyApi == null) {
                LOG.error("Laby.labyAPI() returned null");
                return false;
            }

            // Get the EventBus
            net.labymod.api.event.EventBus labyEventBus = labyApi.eventBus();
            if (labyEventBus == null) {
                LOG.error("LabyAPI.eventBus() returned null");
                return false;
            }

            // LabyRoleTagRenderer Native Integration disabled as requested

            // Register dynamic listeners manually (Bypassing Annotation Processor)
            registerDynamic(GameRenderEvent.class, "dummyRender", (byte) 32, event -> {
                try {
                    GameRenderEvent gre = (GameRenderEvent) event;
                    net.labymod.api.event.Phase phase = gre.phase();
                    if (phase == net.labymod.api.event.Phase.PRE) {
                        float partialTicks = 0f;
                        try { partialTicks = gre.getPartialTicks(); } catch (Throwable ignored) {}
                        hadesEventBus.post(new com.hades.client.event.events.PreRenderEvent(partialTicks));
                        hadesEventBus.post(new com.hades.client.event.events.RenderEvent(partialTicks));
                    }
                } catch (Throwable ignored) {
                }
            });
            // 3D World Rendering — Essential for local player RoleTag, ESP, and Trajectories
            registerDynamic(net.labymod.api.event.client.render.world.RenderWorldEvent.class, "dummyRenderWorld", (byte) 0, event -> {
                try {
                    net.labymod.api.event.client.render.world.RenderWorldEvent rwe = (net.labymod.api.event.client.render.world.RenderWorldEvent) event;
                    // LabyMod natively configures GL_MODELVIEW correctly for RenderWorldEvent.POST on 1.8.9
                    if (rwe.phase() == net.labymod.api.event.Phase.POST) {
                        float partialTicks = rwe.getPartialTicks();
                        hadesEventBus.post(new com.hades.client.event.events.Render3DEvent(partialTicks));
                    }
                } catch (Throwable ignored) {}
            });
            // Critical Hook: Render2DEvent & Render3DEvent
            // LabyMod's ScreenContextListener natively corrupts Ortho. Hooking at Priority 127 secures
            // the untouched Vanilla GUI framework to overlay correctly above the world. 
            // The user explicitly verified Priority 127 is the only valid deployment window for this architecture.
            registerDynamic(net.labymod.api.event.client.render.GameRenderEvent.class, "postCanvasHudRender", (byte) 127, event -> {
                try {
                    net.labymod.api.event.client.render.GameRenderEvent gre = (net.labymod.api.event.client.render.GameRenderEvent) event;
                    if (gre.phase() == net.labymod.api.event.Phase.POST) {
                        net.labymod.api.client.gui.window.Window window = Laby.labyAPI().minecraft().minecraftWindow();
                        int scaledW = window.getScaledWidth();
                        int scaledH = window.getScaledHeight();
                        float partialTicks = Laby.labyAPI().minecraft().getTickDelta();

                        com.hades.client.api.HadesAPI.Render.setLabyRenderContext(false);

                        // Save the binding that LabyMod ended its render pass on (keeps Vanilla completely synchronized)
                        int previousTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);

                        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_SCISSOR_BIT);

                        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
                        org.lwjgl.opengl.GL11.glPushMatrix();
                        org.lwjgl.opengl.GL11.glLoadIdentity();
                        org.lwjgl.opengl.GL11.glOrtho(0, scaledW, scaledH, 0, 1000, 3000);
                        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                        org.lwjgl.opengl.GL11.glPushMatrix();
                        org.lwjgl.opengl.GL11.glLoadIdentity();
                        org.lwjgl.opengl.GL11.glTranslatef(0.0f, 0.0f, -2000.0f);

                        // Standard Hades HUD states
                        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                        org.lwjgl.opengl.GL11.glDepthMask(false);
                        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
                        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
                        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
                        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);

                        // Post 2D UI for Hades Core Framework
                        hadesEventBus.post(new com.hades.client.event.events.Render2DEvent(partialTicks, scaledW, scaledH));

                        org.lwjgl.opengl.GL20.glUseProgram(0);

                        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
                        org.lwjgl.opengl.GL11.glPopMatrix();
                        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                        org.lwjgl.opengl.GL11.glPopMatrix();

                        // Soft-restore
                        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, previousTexture);
                        org.lwjgl.opengl.GL11.glPopAttrib();
                    }
                } catch (Throwable t) {
                    LOG.error("Hades HUD Render Error (post-canvas)", t);
                }
            });

            registerDynamic(net.labymod.api.event.client.render.world.RenderWorldEvent.class, "dummyRenderWorld",
                    event -> {
                        net.labymod.api.event.client.render.world.RenderWorldEvent e = (net.labymod.api.event.client.render.world.RenderWorldEvent) event;
                        if (e.phase() == net.labymod.api.event.Phase.POST) {
                            try {
                                hadesEventBus
                                        .post(new com.hades.client.event.events.Render3DEvent(e.getPartialTicks()));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            registerDynamic(net.labymod.api.event.client.render.ScreenRenderEvent.class, "dummyScreenRender", event -> {
                net.labymod.api.event.client.render.ScreenRenderEvent e = (net.labymod.api.event.client.render.ScreenRenderEvent) event;
                if (e.phase() == net.labymod.api.event.Phase.POST) {
                    try {
                        net.labymod.api.client.gui.window.Window window = Laby.labyAPI().minecraft()
                                .minecraftWindow();
                        int scaledW = window.getScaledWidth();
                        int scaledH = window.getScaledHeight();
                        float partialTicks = Laby.labyAPI().minecraft().getTickDelta();

                        // Get the Stack and Context from the event's screenContext
                        net.labymod.api.client.render.matrix.Stack stack = null;
                        net.labymod.api.client.gui.screen.ScreenContext screenCtx = null;
                        try {
                            screenCtx = e.screenContext();
                            if (screenCtx != null) {
                                stack = screenCtx.stack();
                            }
                        } catch (Throwable ignored) {
                        }
                        if (stack == null) {
                            stack = net.labymod.api.client.render.matrix.Stack.getDefaultEmptyStack();
                        }

                        // Store the stack and context globally for LabyRenderer
                        com.hades.client.util.LabyRenderer.setCurrentStack(stack);
                        com.hades.client.util.LabyRenderer.setCurrentScreenContext(screenCtx);
                        // Enable LabyMod render context (LabyRenderer works here)
                        com.hades.client.api.HadesAPI.Render.setLabyRenderContext(true);

                        // Fire Render2DEvent during screen rendering too (for HUD behind menus)
                        hadesEventBus
                                .post(new com.hades.client.event.events.Render2DEvent(partialTicks, scaledW, scaledH));

                        com.hades.client.gui.clickgui.ClickGUI clickGUI = com.hades.client.HadesClient.getInstance()
                                .getClickGUI();
                        
                        // Render ClickGUI OR HudEditorScreen
                        if (com.hades.client.HadesClient.getInstance().isHudEditorOpen()) {
                            net.labymod.api.client.gui.mouse.Mouse mouse = Laby.labyAPI().minecraft().mouse();
                            int mouseX = (int) mouse.getX();
                            int mouseY = (int) mouse.getY();
                            
                            // Force Vanilla GL for our custom screens so Scissor works flawlessly
                            com.hades.client.api.HadesAPI.Render.setLabyRenderContext(false);
                            com.hades.client.HadesClient.getInstance().getHudEditorScreen().render(mouseX, mouseY, partialTicks);
                            com.hades.client.api.HadesAPI.Render.setLabyRenderContext(true);
                        } else if (clickGUI != null && clickGUI.isVisible()) {
                            // Get mouse position
                            net.labymod.api.client.gui.mouse.Mouse mouse = Laby.labyAPI().minecraft().mouse();
                            int mouseX = (int) mouse.getX();
                            int mouseY = (int) mouse.getY();

                            // Force Vanilla GL for our custom screens so Scissor works flawlessly
                            com.hades.client.api.HadesAPI.Render.setLabyRenderContext(false);
                            // Render the full ClickGUI
                            clickGUI.render(mouseX, mouseY, partialTicks);
                            com.hades.client.api.HadesAPI.Render.setLabyRenderContext(true);
                        }

                        // Fire PostRenderEvent
                        hadesEventBus.post(new com.hades.client.event.events.PostRenderEvent(
                                partialTicks, scaledW, scaledH));

                        // Clear the stored stack, context and render context to prevent leaking state
                        com.hades.client.api.HadesAPI.Render.setLabyRenderContext(false);
                        com.hades.client.util.LabyRenderer.setCurrentStack(null);
                        com.hades.client.util.LabyRenderer.setCurrentScreenContext(null);
                    } catch (Throwable renderErr) {
                        LOG.error("ScreenRenderEvent error", renderErr);
                    }
                }
            });

            // (Deleted unreliable IngameOverlayRenderEvent listener)
            registerDynamic(GameTickEvent.class, "dummyGameTick", event -> {
                try {
                    tickCounter++;
                    // Node: TickEvent is now fired natively by our ByteBuddy RunTickHook.
                    // We removed hadesEventBus.post(new TickEvent()) from here to prevent double
                    // ticks.

                    // Removed Spotify Hijack to allow standard LabyMod behavior

                    // --- DISCORD HIJACK (Build new DiscordActivity, call displayInternal directly)
                    // ---
                    if (tickCounter % 100 == 0) {
                        try {
                            Object discordApp = Laby.labyAPI().thirdPartyService().discord();
                            if (discordApp != null) {
                                boolean running = (boolean) discordApp.getClass().getMethod("isRunning")
                                        .invoke(discordApp);
                                if (running) {
                                    // Build a brand new DiscordActivity via the Builder
                                    Class<?> activityClass = Class
                                            .forName("net.labymod.api.thirdparty.discord.DiscordActivity");
                                    Class<?> builderClass = Class
                                            .forName("net.labymod.api.thirdparty.discord.DiscordActivity$Builder");

                                    // Use the builder(Object holder) factory — pass discordApp as holder
                                    java.lang.reflect.Method builderFactory = activityClass.getMethod("builder",
                                            Object.class);
                                    Object builder = builderFactory.invoke(null, discordApp);

                                    // Set details and state
                                    builderClass.getMethod("details", String.class).invoke(builder,
                                            "Using Hades Client");
                                    builderClass.getMethod("state", String.class).invoke(builder, "Hades.tf");
                                    builderClass.getMethod("start").invoke(builder);

                                    // Build the activity
                                    Object newActivity = builderClass.getMethod("build").invoke(builder);

                                    // Call displayInternal() directly — this bypasses the equals() guard
                                    java.lang.reflect.Method displayInternal = discordApp.getClass()
                                            .getDeclaredMethod("displayInternal", activityClass);
                                    displayInternal.setAccessible(true);
                                    displayInternal.invoke(discordApp, newActivity);

                                    if (tickCounter == 100) {
                                        LOG.info("Discord RPC hijacked via displayInternal()");
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            if (tickCounter <= 200)
                                LOG.error("Discord hijack failed", t);
                        }
                    }
                } catch (Throwable ignored) {
                }
            });

            registerDynamic(net.labymod.api.event.labymod.discordrpc.DiscordActivityUpdateEvent.class,
                    "dummyDiscordUpdate", event -> {
                        net.labymod.api.event.labymod.discordrpc.DiscordActivityUpdateEvent e = (net.labymod.api.event.labymod.discordrpc.DiscordActivityUpdateEvent) event;
                        try {
                            LOG.info("Discord Update Event received.");
                            Object activity = e.activity(); // net.labymod.api.thirdparty.discord.DiscordActivity
                            if (activity != null) {
                                // Dynamically overwrite string fields in the LabyMod payload using Reflection
                                // to bypass scope
                                for (java.lang.reflect.Field field : activity.getClass().getDeclaredFields()) {
                                    if (field.getType() == String.class) {
                                        field.setAccessible(true);
                                        String name = field.getName().toLowerCase();
                                        if (name.contains("detail") || name.contains("state")) {
                                            LOG.info("Overwriting Discord field: " + field.getName());
                                            field.set(activity, "Using Hades Client");
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            LOG.error("Failed to hijack Discord RPC", t);
                        }
                    });
            registerDynamic(net.labymod.api.event.client.input.KeyEvent.class, "dummyKey", event -> {
                net.labymod.api.event.client.input.KeyEvent e = (net.labymod.api.event.client.input.KeyEvent) event;
                try {
                    boolean pressed = e.state() == net.labymod.api.event.client.input.KeyEvent.State.PRESS;
                    int keyCode = e.key().getId();
                    hadesEventBus.post(new com.hades.client.event.events.KeyEvent(keyCode, pressed));
                } catch (Throwable ignored) {
                }
            });

            active = true;
            LOG.info(
                    "LabyModAdapter: Registered on LabyMod EventBus. Falling back to VanillaAdapter for background hooks, but will provide UI integration natively.");

            return true;

        } catch (

        Throwable t) {
            LOG.error("LabyModAdapter: Failed to initialize", t);
            return false;
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void shutdown() {
        if (active) {
            // Cannot unregister easily without storing all SubscribeMethod instances.
            // But since this is a client, we don't hot-reload the adapter usually.
            for (net.labymod.api.event.method.SubscribeMethod m : registeredMethods) {
                try {
                    Laby.labyAPI().eventBus().registry().unregister(m);
                } catch (Exception ignored) {
                }
            }
            registeredMethods.clear();
        }
        active = false;
        LOG.info("LabyModAdapter: Shut down");
    }

    @Override
    public void displayClickGUI() {
        Object screen = com.hades.client.gui.HadesScreen.create();
        if (screen != null) {
            HadesAPI.mc.displayScreen(screen);
            com.hades.client.HadesClient.getInstance().getClickGUI().setVisible(true);
        }
    }

    @Override
    public void closeClickGUI() {
        HadesAPI.mc.displayScreen(null);
        com.hades.client.HadesClient.getInstance().getClickGUI().setVisible(false);
    }

    @Override
    public void forceVanillaScoreboard(boolean enable) {
        if (!active) return;
        // Best-effort: Try to disable LabyMod's scoreboard HUD widget via reflection.
        // If the API changed (NoSuchMethodException), silently ignore — our ByteBuddy hook
        // skips the native renderScoreboard entirely, so this is just a cleanup step.
        try {
            Object registry = Laby.labyAPI().hudWidgetRegistry();
            // Try multiple method names across LabyMod versions
            Object widget = null;
            for (String methodName : new String[]{"getWidget", "getById", "get"}) {
                try {
                    java.lang.reflect.Method m = registry.getClass().getMethod(methodName, String.class);
                    widget = m.invoke(registry, "scoreboard");
                    if (widget != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (widget != null) {
                java.lang.reflect.Method getConfig = null;
                for (String mName : new String[]{"getConfig", "config"}) {
                    try {
                        getConfig = widget.getClass().getMethod(mName);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
                if (getConfig != null) {
                    Object config = getConfig.invoke(widget);
                    java.lang.reflect.Method setEnabled = config.getClass().getMethod("setEnabled", boolean.class);
                    setEnabled.invoke(config, !enable);
                }
            }
        } catch (Throwable ignored) {
            // Silently ignore — our hook handles everything
        }
    }

    // --- Dynamic Listener Registry Implementation ---

    private final java.util.List<net.labymod.api.event.method.SubscribeMethod> registeredMethods = new java.util.ArrayList<>();

    public void dummyRender(GameRenderEvent e) {
    }

    public void postCanvasHudRender(GameRenderEvent e) {
    }

    public void dummyRenderWorld(net.labymod.api.event.client.render.world.RenderWorldEvent e) {
    }

    public void dummyScreenRender(net.labymod.api.event.client.render.ScreenRenderEvent e) {
    }

    public void dummyGameTick(GameTickEvent e) {
    }

    public void dummyKey(net.labymod.api.event.client.input.KeyEvent e) {
    }

    public void dummyDiscordUpdate(net.labymod.api.event.labymod.discordrpc.DiscordActivityUpdateEvent e) {
    }

    private void registerDynamic(Class<?> eventClass, String methodName,
            java.util.function.Consumer<net.labymod.api.event.Event> consumer) {
        registerDynamic(eventClass, methodName, (byte) 32, consumer);
    }

    private void registerDynamic(Class<?> eventClass, String methodName, byte priority,
            java.util.function.Consumer<net.labymod.api.event.Event> consumer) {
        try {
            java.lang.reflect.Method dummyMethod = LabyModAdapter.class.getMethod(methodName, eventClass);

            net.labymod.api.event.method.SubscribeMethod subMethod = new net.labymod.api.event.method.SubscribeMethod() {
                @Override
                public void invoke(net.labymod.api.event.Event event) {
                    consumer.accept(event);
                }

                @Override
                public ClassLoader getClassLoader() {
                    return LabyModAdapter.class.getClassLoader();
                }

                @Override
                public java.lang.reflect.Method getMethod() {
                    return dummyMethod;
                }

                @Override
                public byte getPriority() {
                    return priority;
                }

                @Override
                public net.labymod.api.event.method.SubscribeMethod copy(Object listener) {
                    return this;
                }

                @Override
                public net.labymod.api.models.addon.info.InstalledAddonInfo getAddon() {
                    return null;
                }

                @Override
                public Object getListener() {
                    return LabyModAdapter.this;
                }

                @Override
                public net.labymod.api.event.LabyEvent getLabyEvent() {
                    // Create dynamic proxy for the annotation
                    return (net.labymod.api.event.LabyEvent) java.lang.reflect.Proxy.newProxyInstance(
                            LabyModAdapter.class.getClassLoader(),
                            new Class<?>[] { net.labymod.api.event.LabyEvent.class },
                            (proxy, method, args) -> {
                                String name = method.getName();
                                if ("background".equals(name))
                                    return false;
                                if ("allowAllExceptions".equals(name))
                                    return true;
                                if ("allowExceptions".equals(name))
                                    return new Class[0];
                                if ("classLoaderExclusive".equals(name))
                                    return false;
                                return null;
                            });
                }

                @Override
                public boolean isInClassLoader(ClassLoader classLoader) {
                    return classLoader == getClassLoader();
                }

                @Override
                public Class<?> getEventType() {
                    return eventClass;
                }
            };

            Laby.labyAPI().eventBus().registry().register(subMethod);
            registeredMethods.add(subMethod);
        } catch (Exception e) {
            LOG.error("Failed to register dynamic listener for " + eventClass.getSimpleName(), e);
        }
    }

    // ── Platform-specific Minecraft instance detection ──

    /**
     * Detect the Minecraft instance using LabyMod's API.
     * Laby.labyAPI().minecraft() returns the real ave instance directly,
     * bypassing Class.forName("ave") which loads an imposter class.
     *
     * @return Object[2] = { minecraftInstance, minecraftClass } or null if not
     *         found
     */
    public static Object[] findMinecraftInstance() {
        try {
            LabyAPI api = Laby.labyAPI();
            if (api == null)
                return null;

            Object mcInstance = api.minecraft();
            if (mcInstance == null)
                return null;

            Class<?> mcClass = mcInstance.getClass();
            HadesLogger.get().info("[MC-Detect] LabyMod API found MC class: " + mcClass.getName()
                    + " (" + mcClass.getDeclaredMethods().length + " methods)");
            return new Object[] { mcInstance, mcClass };
        } catch (Throwable t) {
            HadesLogger.get().info("[MC-Detect] LabyMod API detection failed: " + t.getMessage());
            return null;
        }
    }

    /**
     * Check if LabyMod is present in this JVM.
     */
    public static boolean isLabyModPresent() {
        try {
            Class.forName("net.labymod.api.Laby");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

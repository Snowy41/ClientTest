package com.hades.client.platform.adapters;

import com.hades.client.event.EventBus;
import com.hades.client.event.events.*;
import com.hades.client.platform.ClientPlatform;
import com.hades.client.platform.PlatformAdapter;
import com.hades.client.util.HadesLogger;
import com.hades.client.api.HadesAPI;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Vanilla Minecraft adapter.
 * Uses ByteBuddy to proxy mc.ingameGUI to fire Hades events natively.
 * 
 * This is the fallback adapter when no platform-specific hooks are available.
 * It works on any MC version.
 */
public class VanillaAdapter implements PlatformAdapter {
    private static final HadesLogger LOG = HadesLogger.get();

    public static final String[] CLASS_NAMES = { "net.minecraft.client.Minecraft", "ave" };
    public static final String[] METHOD_NAMES = { "A", "getMinecraft", "func_71410_x" };

    private EventBus hadesEventBus;
    private volatile boolean active = false;

    @Override
    public String getName() {
        return "Vanilla";
    }

    @Override
    public ClientPlatform getPlatform() {
        return ClientPlatform.VANILLA;
    }

    @Override
    public boolean initialize(EventBus hadesEventBus) {
        this.hadesEventBus = hadesEventBus;

        try {
            hookMinecraft();
            active = true;
            LOG.info("VanillaAdapter: Secondary hook established");
            return true;
        } catch (Throwable t) {
            LOG.error("VanillaAdapter: Failed to initialize", t);
            return false;
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void shutdown() {
        active = false;
        LOG.info("VanillaAdapter: Shut down");
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
        // Vanilla naturally uses Vanilla scoreboard, do nothing
    }

    private void hookMinecraft() {
        new Thread(() -> {
            // Wait for MC to be in-game
            while (active && !HadesAPI.mc.isInGame()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }

            if (!active)
                return;

            try {
                Object mc = HadesAPI.mc;
                if (mc == null)
                    return;

                Class<?> mcClass = mc.getClass();
                if (mcClass.getName().contains("ByteBuddy")) {
                    return; // Already hooked
                }

                MinecraftInterceptor.setEventBus(hadesEventBus);

                // We can't easily replace the statically stored `ave` instance globally,
                // but we CAN start a polling thread for RenderEvents just by calling `onRender`
                // from HadesScreen.
                // Since this proxy approach is complex, let's revert to a simple polling thread
                // for ticks,
                // and rely on HadesScreen.drawScreen() + Mixin hooks for render events later.
                startEventLoop();
            } catch (Exception e) {
                LOG.error("Hooking failed", e);
            }
        }, "Hades-VanillaInitHook").start();
    }

    private void startEventLoop() {
        new Thread(() -> {
            long lastTick = System.currentTimeMillis();
            while (active) {
                try {
                    long now = System.currentTimeMillis();
                    if (now - lastTick >= 50) {
                        lastTick = now;
                        if (HadesAPI.mc.isInGame() && hadesEventBus != null) {
                            // Only fire background TickEvents if we are the primary adapter.
                            // Otherwise LabyModAdapter is already firing them safely on the main thread!
                            if (com.hades.client.platform.PlatformManager.getActiveAdapter()
                                    .getPlatform() == ClientPlatform.VANILLA) {
                                try {
                                    hadesEventBus.post(new TickEvent());
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                }
            }
        }, "Hades-VanillaEvents").start();
    }

    public static class MinecraftInterceptor {
        private static EventBus eventBus;

        public static void setEventBus(EventBus bus) {
            eventBus = bus;
        }

        @net.bytebuddy.implementation.bind.annotation.RuntimeType
        public static Object intercept(
                @net.bytebuddy.implementation.bind.annotation.Origin Method method,
                @net.bytebuddy.implementation.bind.annotation.AllArguments Object[] args,
                @net.bytebuddy.implementation.bind.annotation.SuperCall java.util.concurrent.Callable<?> superCall)
                throws Exception {

            String name = method.getName();
            int len = args.length;

            // updateTick() -> e()
            if (len == 0 && (name.equals("e") || name.equals("updateTick") || name.equals("func_73836_a"))) {
                Object ret = superCall.call();
                try {
                    if (eventBus != null)
                        eventBus.post(new TickEvent());
                } catch (Throwable ignored) {
                }
                return ret;
            }

            return superCall.call();
        }
    }

    /**
     * Called externally to dispatch 2D render events.
     * Typically called from HadesScreen.drawScreen().
     */
    public void onRender2D(float partialTicks) {
        if (!active)
            return;
        try {
            int[] sr = new int[]{1920, 1080};
            hadesEventBus.post(new Render2DEvent(partialTicks, sr[0], sr[1]));
        } catch (Exception e) {
            LOG.error("VanillaAdapter: Render2DEvent error", e);
        }
    }

    /**
     * Called externally to dispatch 3D render events.
     */
    public void onRender3D(float partialTicks) {
        if (!active)
            return;
        try {
            hadesEventBus.post(new Render3DEvent(partialTicks));
        } catch (Exception e) {
            LOG.error("VanillaAdapter: Render3DEvent error", e);
        }
    }
}

package com.hades.client.gui;

import com.hades.client.HadesClient;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.render.GLStateManager;
import com.hades.client.util.HadesLogger;

import java.lang.reflect.Method;

/**
 * GuiScreen proxy that delegates rendering to HadesClient's ClickGUI.
 *
 * Since we compile against 1.8.9.jar (obfuscated), we CANNOT directly extend
 * axu (GuiScreen) at compile time — the method signatures use obfuscated
 * parameter types that would create hard class dependencies.
 *
 * Instead, we use ByteBuddy to create a runtime subclass, but with MUCH simpler
 * logic than the old ClickGUIScreenFactory.
 */
public class HadesScreen {

    private static final HadesLogger LOG = HadesLogger.get();
    private static Object screenInstance;
    private static Class<?> guiScreenClass;
    private static Class<?> proxyClass;

    /**
     * Create and return a GuiScreen instance that renders our ClickGUI.
     */
    public static Object create() {
        try {
            if (guiScreenClass == null) {
                guiScreenClass = findGuiScreenClass();
            }

            if (proxyClass == null) {
                proxyClass = createProxyClass();
            }

            if (proxyClass != null) {
                screenInstance = proxyClass.newInstance();
                return screenInstance;
            }
        } catch (Exception e) {
            LOG.error("Failed to create HadesScreen", e);
        }
        return null;
    }

    private static Class<?> findGuiScreenClass() {
        String[] candidates = { "net.minecraft.client.gui.GuiScreen", "axu" };
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        // Try thread classloaders
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            ClassLoader cl = t.getContextClassLoader();
            if (cl == null)
                continue;
            for (String name : candidates) {
                try {
                    return Class.forName(name, false, cl);
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        throw new RuntimeException("GuiScreen class not found");
    }

    /**
     * Creates a GuiScreen subclass at runtime using ByteBuddy.
     * The interceptor dispatches drawScreen/mouseClicked/keyTyped to our ClickGUI.
     */
    private static Class<?> createProxyClass() {
        try {
            // Resolve method names by checking what exists on the class
            String drawScreenName = getMethodName(guiScreenClass, new String[] { "a", "drawScreen", "func_73863_a" },
                    int.class, int.class, float.class);
            String mouseClickedName = getMethodName(guiScreenClass,
                    new String[] { "a", "mouseClicked", "func_73864_a" }, int.class, int.class, int.class);
            String mouseReleasedName = getMethodName(guiScreenClass,
                    new String[] { "b", "mouseReleased", "func_146286_b" }, int.class, int.class, int.class);
            String keyTypedName = getMethodName(guiScreenClass, new String[] { "a", "keyTyped", "func_73869_a" },
                    char.class, int.class);
            String doesGuiPauseName = getMethodName(guiScreenClass,
                    new String[] { "d", "doesGuiPauseGame", "func_73868_f" });
            String handleMouseInputName = getMethodName(guiScreenClass,
                    new String[] { "p", "handleMouseInput", "func_146274_d" });
            String onGuiClosedName = getMethodName(guiScreenClass,
                    new String[] { "m", "onGuiClosed", "func_146281_b" });

            LOG.info("GuiScreen methods: draw=" + drawScreenName + " click=" + mouseClickedName
                    + " release=" + mouseReleasedName + " key=" + keyTypedName
                    + " pause=" + doesGuiPauseName + " mouseInput=" + handleMouseInputName);

            // Store method names for the interceptor
            ScreenInterceptor.drawScreenName = drawScreenName;
            ScreenInterceptor.mouseClickedName = mouseClickedName;
            ScreenInterceptor.mouseReleasedName = mouseReleasedName;
            ScreenInterceptor.keyTypedName = keyTypedName;
            ScreenInterceptor.doesGuiPauseName = doesGuiPauseName;
            ScreenInterceptor.handleMouseInputName = handleMouseInputName;
            ScreenInterceptor.onGuiClosedName = onGuiClosedName;

            Class<?> proxyClass = new net.bytebuddy.ByteBuddy()
                    .subclass(guiScreenClass)
                    .method(net.bytebuddy.matcher.ElementMatchers.any())
                    .intercept(net.bytebuddy.implementation.MethodDelegation.to(ScreenInterceptor.class))
                    .make()
                    .load(HadesScreen.class.getClassLoader(),
                            net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.WRAPPER)
                    .getLoaded();

            LOG.info("HadesScreen proxy class created: " + proxyClass.getName());
            return proxyClass;
        } catch (Exception e) {
            LOG.error("Failed to create proxy class", e);
            return null;
        }
    }

    private static String getMethodName(Class<?> clazz, String[] names, Class<?>... params) {
        for (String name : names) {
            try {
                clazz.getMethod(name, params);
                return name;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (String name : names) {
            try {
                clazz.getDeclaredMethod(name, params);
                return name;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return names[0];
    }

    /**
     * Static interceptor for the ByteBuddy proxy.
     * Delegates all GUI methods to HadesClient's ClickGUI.
     */
    public static class ScreenInterceptor {
        static String drawScreenName, mouseClickedName, mouseReleasedName;
        static String keyTypedName, doesGuiPauseName, handleMouseInputName, onGuiClosedName;

        @net.bytebuddy.implementation.bind.annotation.RuntimeType
        public static Object intercept(
                @net.bytebuddy.implementation.bind.annotation.Origin Method method,
                @net.bytebuddy.implementation.bind.annotation.AllArguments Object[] args,
                @net.bytebuddy.implementation.bind.annotation.SuperCall java.util.concurrent.Callable<?> superCall)
                throws Exception {

            String name = method.getName();
            int len = args.length;

            // drawScreen(int mouseX, int mouseY, float partialTicks)
            if (name.equals(drawScreenName) && len == 3 && args[2] instanceof Float) {
                try {
                    // Prevent double-rendering if we are on LabyMod, 
                    // since LabyModAdapter manually fires Render2D for the ClickGUI in ScreenRenderEvent
                    if (com.hades.client.platform.PlatformManager.getActiveAdapter().getPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD) {
                        return null;
                    }

                    int actualMouseX = com.hades.client.api.HadesAPI.Input.getMouseX();
                    int actualMouseY = com.hades.client.api.HadesAPI.Input.getMouseY();
                    float pt = (float) args[2];

                    if (HadesClient.getInstance().isHudEditorOpen()) {
                        HadesClient.getInstance().getHudEditorScreen().render(actualMouseX, actualMouseY, pt);
                    } else {
                        HadesClient.getInstance().getClickGUI().render(actualMouseX, actualMouseY, pt);
                    }

                } catch (Throwable e) {
                    HadesLogger.get().error("drawScreen render error: " + e.getMessage(), e);
                }
                return null;
            }

            // mouseClicked(int x, int y, int button)
            if (name.equals(mouseClickedName) && len == 3 && args[0] instanceof Integer && args[2] instanceof Integer) {
                try {
                    int actualMouseX = com.hades.client.api.HadesAPI.Input.getMouseX();
                    int actualMouseY = com.hades.client.api.HadesAPI.Input.getMouseY();
                    int button = (int) args[2];

                    if (HadesClient.getInstance().isHudEditorOpen()) {
                        HadesClient.getInstance().getHudEditorScreen().mouseClicked(actualMouseX, actualMouseY, button);
                    } else {
                        HadesClient.getInstance().getClickGUI().mouseClicked(actualMouseX, actualMouseY, button);
                    }
                } catch (Throwable t) {
                    HadesLogger.get().error("mouseClicked error", t);
                }
                return null;
            }

            // mouseReleased(int x, int y, int button)
            if (name.equals(mouseReleasedName) && len == 3 && args[0] instanceof Integer
                    && args[2] instanceof Integer) {
                try {
                    int actualMouseX = com.hades.client.api.HadesAPI.Input.getMouseX();
                    int actualMouseY = com.hades.client.api.HadesAPI.Input.getMouseY();
                    int button = (int) args[2];

                    if (HadesClient.getInstance().isHudEditorOpen()) {
                        HadesClient.getInstance().getHudEditorScreen().mouseReleased(actualMouseX, actualMouseY, button);
                    } else {
                        HadesClient.getInstance().getClickGUI().mouseReleased(actualMouseX, actualMouseY, button);
                    }
                } catch (Throwable t) {
                    HadesLogger.get().error("mouseReleased error", t);
                }
                return null;
            }

            // keyTyped(char c, int keyCode)
            if (name.equals(keyTypedName) && len == 2 && args[0] instanceof Character) {
                try {
                    if (HadesClient.getInstance().isHudEditorOpen()) {
                        HadesClient.getInstance().getHudEditorScreen().keyTyped((char) args[0], (int) args[1]);
                    } else {
                        HadesClient.getInstance().getClickGUI().keyTyped((char) args[0], (int) args[1]);
                    }
                } catch (Throwable t) {
                    HadesLogger.get().error("keyTyped error", t);
                }
                return null;
            }

            // doesGuiPauseGame()
            if (name.equals(doesGuiPauseName) && len == 0) {
                return false;
            }

            // onGuiClosed()
            if (name.equals(onGuiClosedName) && len == 0) {
                if (HadesClient.getInstance().isHudEditorOpen()) {
                    HadesClient.getInstance().getHudEditorScreen().close();
                } else {
                    HadesClient.getInstance().getClickGUI().close();
                }
                return null;
            }

            // setWorldAndResolution - let it through
            if (len == 3 && !(args[0] instanceof Integer)) {
                return superCall.call();
            }

            // Default: call super
            try {
                return superCall.call();
            } catch (Throwable t) {
                return getDefault(method.getReturnType());
            }
        }

        private static Object getDefault(Class<?> r) {
            if (r == boolean.class)
                return false;
            if (r == int.class || r == float.class || r == double.class || r == long.class)
                return 0;
            return null;
        }
    }
}

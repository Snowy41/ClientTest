package com.hades.client;

import com.hades.client.util.HadesLogger;
import com.hades.client.hook.HookManager;

import java.lang.instrument.Instrumentation;

public class HadesAgent {
    private static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
        System.out.println("[Hades] Agent loaded (premain)");
        initialize(args);
    }

    public static void agentmain(String args, Instrumentation inst) {
        instrumentation = inst;
        System.out.println("[Hades] Agent loaded (agentmain)");
        initialize(args);
    }

    public static void initialize() {
        initialize(null);
    }

    public static void initialize(String args) {
        HadesLogger.get().info("Hades Init Thread starting...");

        // Start dynamic attach specifically for our JVM context
        HookManager.install();

        // If we didn't receive instrumentation from premain/agentmain, fallback to the
        // ByteBuddy dynamic instance
        if (instrumentation == null) {
            instrumentation = HookManager.getInstrumentation();
        }

        Thread startThread = new Thread(() -> {
            try {
                HadesLogger.get().info("Waiting for Minecraft to load...");

                // Find MC's classloader BEFORE waiting for MC, since isMinecraftLoaded()
                // needs the right CL to find classes like "ave"
                boolean foundCL = false;
                for (int clAttempt = 0; clAttempt < 120 && !foundCL; clAttempt++) {
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        String tName = t.getName();
                        if (("Client thread".equals(tName) || "Render thread".equals(tName))
                                && t.getContextClassLoader() != null) {
                            Thread.currentThread().setContextClassLoader(t.getContextClassLoader());
                            HadesLogger.get().info("Found and set " + tName + " ClassLoader");
                            foundCL = true;
                            break;
                        }
                    }
                    if (!foundCL) {
                        if (clAttempt % 10 == 0) {
                            HadesLogger.get()
                                    .info("Searching for Client/Render thread CL... (attempt " + clAttempt + ")");
                        }
                        Thread.sleep(500);
                    }
                }
                if (!foundCL) {
                    HadesLogger.get().error("Could not find Client/Render thread ClassLoader!");
                    return;
                }

                // Wait for MC instance to load
                int attempts = 0;
                while (!isMinecraftLoaded()) {
                    if (attempts % 10 == 0) {
                        HadesLogger.get().info("Still waiting for Minecraft... (attempt " + attempts + "/600)");
                    }
                    if (++attempts > 600) {
                        HadesLogger.get().error("Timeout waiting for Minecraft to load!");
                        return;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                HadesLogger.get().info("Minecraft loaded, Instrumentation available: " + (instrumentation != null));
                HadesLogger.get().info("Retransform supported: " +
                        (instrumentation != null && instrumentation.isRetransformClassesSupported()));

                // The 'args' parameter contains the JWT session token passed from the DLL
                HadesClient.getInstance().start(args);
            } catch (Throwable t) {
                HadesLogger.get().error("FATAL ERROR in Hades-Init thread: ", t);
            }
        }, "Hades-Init");
        startThread.setDaemon(true);
        startThread.start();
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    private static boolean isMinecraftLoaded() {
        try {
            Class<?> mcClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
            if (mcClass == null) return false;
            java.lang.reflect.Method getMC = com.hades.client.util.ReflectionUtil.findMethod(mcClass, new String[] { "A", "getMinecraft", "func_71410_x" });
            if (getMC == null) return false;
            return getMC.invoke(null) != null;
        } catch (Exception e) {
            return false;
        }
    }
}

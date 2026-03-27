package com.hades.client.util;

public class InjectionTest {

    private static final HadesLogger LOG = HadesLogger.get();

    public static void runTest() {
        LOG.info("=== Hades Client Injection Test ===");

        // Test 1: ClassLoader
        ClassLoader cl = InjectionTest.class.getClassLoader();
        LOG.info("[TEST 1] ClassLoader: " + cl.getClass().getName());

        // Test 2: Minecraft-Klasse (ave)
        try {
            Class<?> minecraftClass = Class.forName("ave", false, cl);
            LOG.info("[TEST 2] ERFOLG - Minecraft-Klasse 'ave' gefunden: " + minecraftClass);
        } catch (ClassNotFoundException e) {
            LOG.error("[TEST 2] FEHLGESCHLAGEN - Minecraft-Klasse 'ave' nicht gefunden");
        }

        // Test 3: Minecraft-Instanz
        try {
            Class<?> minecraftClass = Class.forName("ave");
            java.lang.reflect.Method[] methods = minecraftClass.getDeclaredMethods();
            LOG.info("[TEST 3] ave hat " + methods.length + " Methoden");

            for (java.lang.reflect.Field field : minecraftClass.getDeclaredFields()) {
                if (field.getType() == minecraftClass
                        && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object mcInstance = field.get(null);
                    LOG.info("[TEST 3] Minecraft-Instanz: " + (mcInstance != null ? "JA" : "NEIN (null)"));
                    break;
                }
            }
        } catch (Exception e) {
            LOG.error("[TEST 3] FEHLGESCHLAGEN", e);
        }

        // Test 4: LabyMod API
        String[] labyClasses = {
                "net.labymod.api.LabyAPI",
                "net.labymod.api.Laby",
                "net.labymod.api.client.Minecraft"
        };
        boolean labyFound = false;
        for (String className : labyClasses) {
            try {
                Class<?> c = Class.forName(className);
                LOG.info("[TEST 4] ERFOLG - " + className + " gefunden");
                labyFound = true;
                break;
            } catch (ClassNotFoundException ignored) {}
        }
        if (!labyFound) {
            LOG.warn("[TEST 4] Keine LabyMod API Klasse gefunden");
        }

        // Test 5: Eigene Klassen
        try {
            Class.forName("com.hades.client.util.PacketUtil");
            LOG.info("[TEST 5] ERFOLG - Eigene Klassen geladen");
        } catch (ClassNotFoundException e) {
            LOG.error("[TEST 5] FEHLGESCHLAGEN - Eigene Klassen nicht gefunden");
        }

        // Test 6: LWJGL prüfen (für Input)
        try {
            Class<?> keyboard = Class.forName("org.lwjgl.input.Keyboard");
            LOG.info("[TEST 6] ERFOLG - LWJGL Keyboard verfügbar");
        } catch (ClassNotFoundException e) {
            LOG.error("[TEST 6] FEHLGESCHLAGEN - LWJGL nicht gefunden");
        }

        LOG.info("=== Injection Test abgeschlossen ===");
        LOG.info("Log-Datei: " + HadesLogger.get().getLogFile().toAbsolutePath());
    }
}
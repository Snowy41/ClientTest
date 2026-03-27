package com.hades.client.util.font;

import java.awt.Font;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance OpenGL TTF Font Manager.
 *
 * Supports:
 *  - Multiple fonts via registerFont() / getRenderer(fontName, size)
 *  - Bold, italic, bold+italic via pre-baked texture atlases in CFontRenderer
 *  - Dynamic font sizes cached on demand
 *  - Fully standalone — no Minecraft or LabyMod dependencies
 *
 * Font file is loaded on any thread via init(), but GL texture atlas creation
 * is deferred to the first getRenderer() call which MUST happen on the render thread.
 */
public class FontUtil {
    /** Registry of named base fonts (e.g. "hades" → the custom.ttf Font object) */
    private static final Map<String, Font> fontRegistry = new ConcurrentHashMap<>();

    /** Cache: "fontName:size" → CFontRenderer */
    private static final Map<String, CFontRenderer> rendererCache = new ConcurrentHashMap<>();

    /** The default font name used by Hades */
    public static final String DEFAULT_FONT = "hades";

    // Default sizes for HadesClient
    public static final int SIZE_SMALL = 11;
    public static final int SIZE_NORMAL = 14;
    public static final int SIZE_LARGE = 20;

    private static volatile boolean fontFileLoaded = false;
    private static volatile boolean glInitialized = false;

    // ── Font Registration ──────────────────────────────────────────

    /**
     * Register a named font. Can be called from any thread.
     * @param name   unique font identifier (e.g. "hades", "monospace")
     * @param font   a java.awt.Font (usually from Font.createFont)
     */
    public static void registerFont(String name, Font font) {
        fontRegistry.put(name, font);
    }

    /**
     * Check if a named font is registered.
     */
    public static boolean hasFont(String name) {
        return fontRegistry.containsKey(name);
    }

    // ── Initialization ─────────────────────────────────────────────

    /**
     * Phase 1: Load the default TTF font file from resources. Safe to call from any thread.
     * Does NOT create any GL resources.
     */
    public static void init() {
        try {
            java.io.InputStream is = null;
            String foundVia = null;

            // Strategy 1: This class's own classloader
            is = FontUtil.class.getResourceAsStream("/font/custom.ttf");
            if (is != null) foundVia = "FontUtil classloader";

            // Strategy 2: Current thread's context classloader
            if (is == null) {
                ClassLoader tcl = Thread.currentThread().getContextClassLoader();
                if (tcl != null) {
                    is = tcl.getResourceAsStream("font/custom.ttf");
                    if (is == null) is = tcl.getResourceAsStream("/font/custom.ttf");
                    if (is != null) foundVia = "thread context classloader";
                }
            }

            // Strategy 3: Scan all thread classloaders
            if (is == null) {
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    ClassLoader cl = t.getContextClassLoader();
                    if (cl == null) continue;
                    is = cl.getResourceAsStream("font/custom.ttf");
                    if (is == null) is = cl.getResourceAsStream("/font/custom.ttf");
                    if (is != null) {
                        foundVia = "thread '" + t.getName() + "' classloader";
                        break;
                    }
                }
            }

            // Strategy 4: Load from disk (~/.hades/font/custom.ttf)
            if (is == null) {
                java.io.File diskFont = new java.io.File(System.getProperty("user.home"), ".hades/font/custom.ttf");
                if (diskFont.exists()) {
                    is = new java.io.FileInputStream(diskFont);
                    foundVia = "disk (" + diskFont.getAbsolutePath() + ")";
                }
            }

            // Strategy 5: Try to extract from our own JAR's codeSource
            if (is == null) {
                try {
                    java.security.CodeSource cs = FontUtil.class.getProtectionDomain().getCodeSource();
                    if (cs != null && cs.getLocation() != null) {
                        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new java.io.File(cs.getLocation().toURI()))) {
                            java.util.jar.JarEntry entry = jar.getJarEntry("font/custom.ttf");
                            if (entry != null) {
                                java.io.InputStream jarIs = jar.getInputStream(entry);
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[4096];
                                int n;
                                while ((n = jarIs.read(buf)) != -1) baos.write(buf, 0, n);
                                jarIs.close();
                                is = new java.io.ByteArrayInputStream(baos.toByteArray());
                                foundVia = "JAR codeSource";
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (is != null) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, is);
                is.close();
                registerFont(DEFAULT_FONT, baseFont);
                fontFileLoaded = true;
                System.out.println("[Hades] Font '" + DEFAULT_FONT + "' loaded via " + foundVia);

                // Attempt to load true bold variant
                try {
                    java.io.InputStream boldIs = FontUtil.class.getResourceAsStream("/font/custom-bold.ttf");
                    if (boldIs == null) {
                        ClassLoader cl = Thread.currentThread().getContextClassLoader();
                        if (cl != null) {
                            boldIs = cl.getResourceAsStream("font/custom-bold.ttf");
                            if (boldIs == null) boldIs = cl.getResourceAsStream("/font/custom-bold.ttf");
                        }
                    }
                    if (boldIs != null) {
                        Font boldBase = Font.createFont(Font.TRUETYPE_FONT, boldIs);
                        boldIs.close();
                        registerFont(DEFAULT_FONT + "-bold", boldBase);
                        System.out.println("[Hades] Font '" + DEFAULT_FONT + "-bold' loaded via " + foundVia);
                    }
                } catch (Exception e) {
                    System.err.println("[Hades] Failed to load custom-bold.ttf");
                }

                // Save to disk for future fallback
                try {
                    java.io.File diskFont = new java.io.File(System.getProperty("user.home"), ".hades/font/custom.ttf");
                    if (!diskFont.exists()) {
                        diskFont.getParentFile().mkdirs();
                        java.io.InputStream copy = FontUtil.class.getResourceAsStream("/font/custom.ttf");
                        if (copy == null) {
                            ClassLoader cl = Thread.currentThread().getContextClassLoader();
                            if (cl != null) copy = cl.getResourceAsStream("font/custom.ttf");
                        }
                        if (copy != null) {
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(diskFont);
                            byte[] buf = new byte[4096];
                            int n;
                            while ((n = copy.read(buf)) != -1) fos.write(buf, 0, n);
                            fos.close();
                            copy.close();
                        }
                    }
                } catch (Exception ignored) {}
            } else {
                System.err.println("[Hades] Failed to find custom.ttf via any classloader strategy");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fontFileLoaded = false;
        }
    }

    // ── State Queries ──────────────────────────────────────────────

    /** Returns true if the default font file was loaded AND GL textures have been created. */
    public static boolean isLoaded() {
        return fontFileLoaded && glInitialized;
    }

    /** Returns true if the default font file was loaded (GL may not be ready yet). */
    public static boolean isFontFileLoaded() {
        return fontFileLoaded;
    }

    // ── GL Initialization ──────────────────────────────────────────

    /**
     * Phase 2: Create GL texture atlases. MUST be called from the render thread.
     * Called lazily on first render, or explicitly after GL context is confirmed.
     */
    public static void initGL() {
        if (!fontFileLoaded || glInitialized) return;
        try {
            // Pre-warm the cache for the default font at common sizes
            getOrCreateRenderer(DEFAULT_FONT, SIZE_NORMAL);
            getOrCreateRenderer(DEFAULT_FONT, SIZE_SMALL);
            getOrCreateRenderer(DEFAULT_FONT, SIZE_LARGE);
            glInitialized = true;
        } catch (Exception e) {
            System.err.println("[Hades] FontUtil GL init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Renderer Access ────────────────────────────────────────────

    /**
     * Get the font renderer for the default font at the given size.
     * MUST be called from the render thread (creates GL textures on first call).
     */
    public static CFontRenderer getRenderer(int size) {
        return getRenderer(DEFAULT_FONT, size);
    }

    /**
     * Get the font renderer for a named font at the given size.
     * MUST be called from the render thread.
     */
    public static CFontRenderer getRenderer(String fontName, int size) {
        if (!fontRegistry.containsKey(fontName)) return null;

        // Lazy GL init on first render-thread access
        if (!glInitialized && fontName.equals(DEFAULT_FONT)) {
            initGL();
        }

        return getOrCreateRenderer(fontName, size);
    }

    private static CFontRenderer getOrCreateRenderer(String fontName, int size) {
        String key = fontName + ":" + size;
        return rendererCache.computeIfAbsent(key, k -> {
            Font baseFont = fontRegistry.get(fontName);
            if (baseFont == null) return null;
            Font font = baseFont.deriveFont(Font.PLAIN, (float) size);
            return new CFontRenderer(font, true, true);
        });
    }

    // ── Proxy Methods (default font, simple API) ───────────────────

    public static void drawString(String text, float x, float y, int color) {
        CFontRenderer r = getRenderer(SIZE_NORMAL);
        if (r != null) r.drawString(text, x, y, color);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color) {
        CFontRenderer r = getRenderer(SIZE_NORMAL);
        if (r != null) r.drawStringWithShadow(text, x, y, color);
    }

    public static void drawCenteredString(String text, float x, float y, int color) {
        CFontRenderer r = getRenderer(SIZE_NORMAL);
        if (r != null) r.drawCenteredString(text, x, y, color);
    }

    public static float getStringWidth(String text) {
        CFontRenderer r = getRenderer(SIZE_NORMAL);
        return r != null ? r.getStringWidth(text) : 0;
    }

    public static float getFontHeight() {
        CFontRenderer r = getRenderer(SIZE_NORMAL);
        return r != null ? r.getHeight() : 0;
    }

    // ── Styled Proxy Methods ───────────────────────────────────────

    public static void drawString(String text, float x, float y, int color, int size, boolean bold, boolean italic, boolean shadow) {
        String fontName = DEFAULT_FONT;
        if (bold && fontRegistry.containsKey(DEFAULT_FONT + "-bold")) {
            fontName = DEFAULT_FONT + "-bold";
            bold = false; // The font is physically bold, so we don't need synthetic bold
        }
        CFontRenderer r = getRenderer(fontName, size);
        
        // Inter font baseline correction: shift UP slightly so text is perfectly vertically centered
        float correctedY = y - 1.5f;
        if (r != null) r.drawString(text, x, correctedY, color, shadow, bold, italic);
    }

    public static float getStringWidth(String text, int size, boolean bold, boolean italic) {
        String fontName = DEFAULT_FONT;
        if (bold && fontRegistry.containsKey(DEFAULT_FONT + "-bold")) {
            fontName = DEFAULT_FONT + "-bold";
            bold = false;
        }
        CFontRenderer r = getRenderer(fontName, size);
        return r != null ? r.getStringWidth(text, bold, italic) : 0;
    }

    public static float getFontHeight(int size, boolean bold, boolean italic) {
        String fontName = DEFAULT_FONT;
        if (bold && fontRegistry.containsKey(DEFAULT_FONT + "-bold")) {
            fontName = DEFAULT_FONT + "-bold";
            bold = false;
        }
        CFontRenderer r = getRenderer(fontName, size);
        return r != null ? r.getHeight(bold, italic) : 0;
    }
}

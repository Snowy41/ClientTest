package com.hades.client.util;

import net.labymod.api.Laby;
import net.labymod.api.client.render.draw.RectangleRenderer;
import net.labymod.api.client.render.matrix.Stack;

/**
 * Native LabyMod renderer that uses RectangleRenderer.renderRectangle()
 * directly.
 * No builder pattern, no fixed-function GL.
 */
public class LabyRenderer {

    private static RectangleRenderer cachedRenderer;
    private static volatile Stack currentStack;
    private static volatile net.labymod.api.client.gui.screen.ScreenContext currentScreenContext;

    public static boolean isAvailable() {
        try {
            return Laby.labyAPI() != null && Laby.labyAPI().renderPipeline() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Set the rendering Stack from the current event context */
    public static void setCurrentStack(Stack stack) {
        currentStack = stack;
    }

    public static void setCurrentScreenContext(net.labymod.api.client.gui.screen.ScreenContext context) {
        currentScreenContext = context;
    }

    public static net.labymod.api.client.gui.screen.ScreenContext getCurrentScreenContext() {
        return currentScreenContext;
    }

    private static RectangleRenderer renderer() {
        if (cachedRenderer == null) {
            cachedRenderer = Laby.labyAPI().renderPipeline().rectangleRenderer();
        }
        return cachedRenderer;
    }

    private static Stack stack() {
        Stack s = currentStack;
        if (s != null)
            return s;
        return Stack.getDefaultEmptyStack();
    }

    // ═══════════════════════════════════════
    // Solid rectangles
    // ═══════════════════════════════════════

    public static void drawRect(float x, float y, float width, float height, int color) {
        try {
            renderer().renderRectangle(stack(), x, y, x + width, y + height, color);
        } catch (Throwable t) {
            logRare("drawRect", t);
        }
    }

    // ═══════════════════════════════════════
    // Gradient rectangles (use builder pattern)
    // ═══════════════════════════════════════

    public static void drawGradientRect(float x, float y, float width, float height,
            int colorTop, int colorBottom) {
        try {
            renderer()
                    .pos(x, y, x + width, y + height)
                    .gradientVertical(colorTop, colorBottom)
                    .render(stack());
        } catch (Throwable t) {
            logRare("drawGradientRect", t);
        }
    }

    public static void drawHorizontalGradient(float x, float y, float width, float height,
            int colorLeft, int colorRight) {
        try {
            renderer()
                    .pos(x, y, x + width, y + height)
                    .gradientHorizontal(colorLeft, colorRight)
                    .render(stack());
        } catch (Throwable t) {
            logRare("drawHorizontalGradient", t);
        }
    }

    // ═══════════════════════════════════════
    // Rounded rectangles
    // ═══════════════════════════════════════

    public static void drawRoundedRect(float x, float y, float width, float height,
            float radius, int color) {
        try {
            renderer()
                    .pos(x, y, x + width, y + height)
                    .color(color)
                    .rounded(radius)
                    .render(stack());
        } catch (Throwable t) {
            logRare("drawRoundedRect", t);
        }
    }

    public static void drawRoundedRect(float x, float y, float width, float height,
            float radTL, float radTR, float radBR, float radBL, int color) {
        try {
            renderer()
                    .pos(x, y, x + width, y + height)
                    .color(color)
                    .rounded(radTL, radTR, radBR, radBL)
                    .render(stack());
        } catch (Throwable t) {
            logRare("drawRoundedRect4", t);
        }
    }

    public static void drawRoundedGradientRect(float x, float y, float width, float height,
            float radius, int colorTop, int colorBottom) {
        try {
            renderer()
                    .pos(x, y, x + width, y + height)
                    .gradientVertical(colorTop, colorBottom)
                    .rounded(radius)
                    .render(stack());
        } catch (Throwable t) {
            logRare("drawRoundedGradientRect", t);
        }
    }

    // ═══════════════════════════════════════
    // Shadow (multi-layer rounded rects)
    // ═══════════════════════════════════════

    public static void drawRoundedShadow(float x, float y, float width, float height,
            float radius, float shadowSize) {
        try {
            // Use multiple expanding, transparent rounded rect layers to simulate a soft shadow.
            // Each layer is slightly larger and more transparent, creating a smooth falloff.
            int layers = 8;
            for (int i = layers; i >= 1; i--) {
                float t = (float) i / layers; // 1.0 → outermost, approaching 0 → innermost
                float expand = shadowSize * t;
                float layerAlpha = (1.0f - t) * 0.35f; // innermost layers are more opaque
                int color = (int)(layerAlpha * 255) << 24; // black with computed alpha

                renderer()
                        .pos(x - expand, y - expand, x + width + expand, y + height + expand)
                        .color(color)
                        .rounded(radius + expand)
                        .render(stack());
            }
        } catch (Throwable t) {
            logRare("drawRoundedShadow", t);
        }
    }

    // ═══════════════════════════════════════
    // Text rendering
    // ═══════════════════════════════════════

    public static void drawStringWithShadow(String text, float x, float y, int color, float scale) {
        // Simple drop shadow by drawing black text slightly offset
        drawString(text, x + 0.5f, y + 0.5f, 0xFF000000, scale);
        drawString(text, x, y, color, scale);
    }

    public static void drawString(String text, float x, float y, int color, float scale) {
        try {
            if (getCurrentScreenContext() != null) {
                Laby.labyAPI().renderPipeline().componentRenderer()
                        .builder()
                        .text(text)
                        .pos(x, y)
                        .color(color)
                        .scale(scale)
                        .render(getCurrentScreenContext());
            } else {
                Laby.labyAPI().renderPipeline().componentRenderer()
                        .builder()
                        .text(text)
                        .pos(x, y)
                        .color(color)
                        .scale(scale)
                        .render(stack());
            }
        } catch (Throwable t) {
            logRare("drawString", t);
        }
    }

    public static float getStringWidth(String text, float scale) {
        try {
            net.labymod.api.client.render.font.ComponentRenderer cr = Laby.labyAPI().renderPipeline()
                    .componentRenderer();
            net.labymod.api.client.component.Component comp = cr.plainSerializer().deserialize(text);
            return cr.width(comp) * scale;
        } catch (Throwable t) {
            return text.length() * 6f * scale;
        }
    }

    public static float getFontHeight(float scale) {
        try {
            return Laby.labyAPI().renderPipeline().componentRenderer().height() * scale;
        } catch (Throwable t) {
            return 9f * scale;
        }
    }

    // ═══════════════════════════════════════
    // Image rendering
    // ═══════════════════════════════════════

    private static final java.util.Map<String, Integer> textureCache = new java.util.HashMap<>();

    public static boolean drawImage(String namespace, String path, float x, float y, float width, float height) {
        try {
            String fullPath = "/assets/" + namespace + "/" + path;
            int textureId = textureCache.getOrDefault(fullPath, -1);

            if (textureId == -1) {
                java.io.InputStream is = null;
                
                // Try multiple classloaders
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl != null) is = cl.getResourceAsStream(fullPath.substring(1)); // without leading slash
                
                if (is == null) is = LabyRenderer.class.getResourceAsStream(fullPath);
                if (is == null) is = LabyRenderer.class.getClassLoader().getResourceAsStream(fullPath.substring(1));

                if (is != null) {
                    try {
                        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(is);
                        textureId = org.lwjgl.opengl.GL11.glGenTextures();
                        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, textureId);

                        // Setup texture parameters
                        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                                org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                                org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);

                        int[] pixels = new int[image.getWidth() * image.getHeight()];
                        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

                        java.nio.ByteBuffer buffer = java.nio.ByteBuffer
                                .allocateDirect(image.getWidth() * image.getHeight() * 4);
                        for (int h = 0; h < image.getHeight(); h++) {
                            for (int w = 0; w < image.getWidth(); w++) {
                                int pixel = pixels[h * image.getWidth() + w];
                                buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
                                buffer.put((byte) ((pixel >> 8) & 0xFF)); // Green component
                                buffer.put((byte) (pixel & 0xFF)); // Blue component
                                buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component
                            }
                        }
                        buffer.flip();
                        org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                                org.lwjgl.opengl.GL11.GL_RGBA8, image.getWidth(), image.getHeight(), 0,
                                org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);

                        textureCache.put(fullPath, textureId);
                    } catch (Exception e) {
                        logRare("drawImage", e);
                    } finally {
                        try { if (is != null) is.close(); } catch (Exception ignored) {}
                    }
                }
            }

            if (textureId != -1) {
                boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
                boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                int oldTex = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);

                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
                org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                        org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
                org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);

                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, textureId);

                org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
                org.lwjgl.opengl.GL11.glTexCoord2f(0f, 0f);
                org.lwjgl.opengl.GL11.glVertex2f(x, y);
                org.lwjgl.opengl.GL11.glTexCoord2f(0f, 1f);
                org.lwjgl.opengl.GL11.glVertex2f(x, y + height);
                org.lwjgl.opengl.GL11.glTexCoord2f(1f, 1f);
                org.lwjgl.opengl.GL11.glVertex2f(x + width, y + height);
                org.lwjgl.opengl.GL11.glTexCoord2f(1f, 0f);
                org.lwjgl.opengl.GL11.glVertex2f(x + width, y);
                org.lwjgl.opengl.GL11.glEnd();

                // Clean up: unbind texture, reset color, restore MC default state
                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, oldTex);
                
                if (!wasTex2D) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
                return true;
            }
            return false;
        } catch (Throwable t) {
            logRare("drawImage", t);
            return false;
        }
    }

    // ═══════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════

    private static long lastLogTime = 0;

    private static void logRare(String method, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 5000) {
            lastLogTime = now;
            HadesLogger.get().error("LabyRenderer." + method + " failed: " + t.getMessage(), t);
        }
    }
}

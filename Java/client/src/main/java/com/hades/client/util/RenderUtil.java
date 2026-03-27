package com.hades.client.util;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import com.hades.client.platform.PlatformManager;
import com.hades.client.platform.ClientPlatform;

/**
 * Modern GL rendering utilities for Minecraft 1.8.9 (LWJGL2 / OpenGL 1.1+).
 * Supports rounded rects, shadows, gradients, and custom font rendering.
 */
public class RenderUtil {

    // ── GL constants (from org.lwjgl.opengl.GL11) ──
    private static final int GL_QUADS = 0x0007;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_RGBA = 6408;
    private static final int GL_UNSIGNED_BYTE = 5121;
    private static final int GL_TEXTURE_MIN_FILTER = 10241;
    private static final int GL_TEXTURE_MAG_FILTER = 10240;
    private static final int GL_LINEAR = 9729;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_SMOOTH = 0x1D01;
    private static final int GL_LINE_SMOOTH = 0x0B20;
    private static final int GL_POLYGON_SMOOTH = 0x0B41;
    private static final int GL_POLYGON = 0x0009;

    // ── GL method cache ──
    private static Class<?> gl11Class;
    private static Method glEnable, glDisable, glBegin, glEnd, glVertex2f, glColor4f;
    private static Method glBlendFunc, glTexCoord2f;
    private static Method glScissor, glLineWidth;
    private static Method glPushMatrix, glPopMatrix, glTranslatef, glShadeModel;

    // ── Tessellator cache ──
    private static Object tessellator;
    private static Object worldRenderer;
    private static Method tessGetWorldRenderer;
    private static Method wrBegin, wrEnd, wrPos, wrColor, wrEndVertex;
    private static boolean usingTessellator = false;

    // ── Custom font ──
    private static Font baseFont;
    private static final Map<String, Font> dynamicFontCache = new HashMap<>();
    private static final Map<String, FontTexture> fontTextureCache = new HashMap<>();
    public static boolean fontLoaded = false;

    // ── Minecraft font fallback ──
    private static Object mcFontRenderer;
    private static Method drawStringMethod;
    private static Method getStringWidthMethod;

    static {
        initGl();
    }

    private static void initGl() {
        try {
            // Try with the thread context classloader first
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null)
                cl = RenderUtil.class.getClassLoader();
            gl11Class = Class.forName("org.lwjgl.opengl.GL11", true, cl);
            glEnable = gl11Class.getMethod("glEnable", int.class);
            glDisable = gl11Class.getMethod("glDisable", int.class);
            glBegin = gl11Class.getMethod("glBegin", int.class);
            glEnd = gl11Class.getMethod("glEnd");
            glVertex2f = gl11Class.getMethod("glVertex2f", float.class, float.class);
            glColor4f = gl11Class.getMethod("glColor4f", float.class, float.class, float.class, float.class);
            glBlendFunc = gl11Class.getMethod("glBlendFunc", int.class, int.class);
            glTexCoord2f = gl11Class.getMethod("glTexCoord2f", float.class, float.class);
            glScissor = gl11Class.getMethod("glScissor", int.class, int.class, int.class, int.class);
            glLineWidth = gl11Class.getMethod("glLineWidth", float.class);
            glPushMatrix = gl11Class.getMethod("glPushMatrix");
            glPopMatrix = gl11Class.getMethod("glPopMatrix");
            glTranslatef = gl11Class.getMethod("glTranslatef", float.class, float.class, float.class);
            glShadeModel = gl11Class.getMethod("glShadeModel", int.class);

            initTessellator(cl);
            loadCustomFont();
            cacheMcFontRenderer();
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("RenderUtil.initGl FAILED during static initialization", e);
        }
    }

    private static void initTessellator(ClassLoader cl) {
        try {
            // Tessellator class in obfuscated 1.8.9 is "bfv" or we try the MCP name
            // WorldRenderer (VertexBuffer in 1.9+) is used with Tessellator.getInstance()
            String[] tessNames = { "bfv", "net.minecraft.client.renderer.Tessellator" };
            String[] wrNames = { "bfw", "net.minecraft.client.renderer.WorldRenderer" };
            String[] vfNames = { "bfr", "net.minecraft.client.renderer.vertex.DefaultVertexFormats" }; // For
                                                                                                       // VertexFormat.POSITION_COLOR

            Class<?> tessClass = null;
            for (String name : tessNames) {
                try {
                    tessClass = Class.forName(name, true, cl);
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (tessClass == null)
                return;

            Class<?> wrClass = null;
            for (String name : wrNames) {
                try {
                    wrClass = Class.forName(name, true, cl);
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (wrClass == null)
                return;

            Class<?> vfClass = null;
            for (String name : vfNames) {
                try {
                    vfClass = Class.forName(name, true, cl);
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            // VertexFormat might not be strictly necessary if we can find a begin method
            // without it,
            // or if we can infer the correct VertexFormat. For now, we'll assume
            // POSITION_COLOR.

            // Tessellator.getInstance()
            Method getInstanceMethod = null;
            for (Method m : tessClass.getMethods()) {
                if (m.getReturnType() == tessClass && m.getParameterCount() == 0) {
                    getInstanceMethod = m;
                    break;
                }
            }
            if (getInstanceMethod == null)
                return;
            tessellator = getInstanceMethod.invoke(null);

            // Tessellator.getWorldRenderer() or equivalent
            for (Method m : tessClass.getMethods()) {
                if (m.getReturnType() == wrClass && m.getParameterCount() == 0) {
                    tessGetWorldRenderer = m;
                    worldRenderer = tessGetWorldRenderer.invoke(tessellator);
                    break;
                }
            }
            if (worldRenderer == null)
                return;

            // Tessellator.draw()
            for (Method m : tessClass.getDeclaredMethods()) {
                if (m.getReturnType() == int.class && m.getParameterCount() == 0) {
                    wrEnd = m;
                    wrEnd.setAccessible(true);
                    break;
                }
            }
            if (wrEnd == null)
                return;

            // WorldRenderer methods
            for (Method m : wrClass.getDeclaredMethods()) {
                m.setAccessible(true);
                Class<?>[] p = m.getParameterTypes();
                // begin(int drawMode, VertexFormat fmt)
                if (wrBegin == null && (m.getName().equals("begin") || m.getName().equals("a")) && p.length == 2
                        && p[0] == int.class && p[1] != null && p[1].getName().contains("VertexFormat")) {
                    wrBegin = m;
                }
                // endVertex()
                if (wrEndVertex == null && (m.getName().equals("endVertex") || m.getName().equals("b")) && p.length == 0
                        && m.getReturnType() == wrClass) {
                    wrEndVertex = m;
                }
                // pos(double x, double y, double z) -> WorldRenderer
                if (wrPos == null && (m.getName().equals("pos") || m.getName().equals("b")) && p.length == 3
                        && p[0] == double.class && m.getReturnType() == wrClass) {
                    wrPos = m;
                }
                // color(float r, float g, float b, float a) -> WorldRenderer (or int r, int g,
                // int b, int a)
                if (wrColor == null && (m.getName().equals("color") || m.getName().equals("c")) && p.length == 4
                        && p[0] == float.class && m.getReturnType() == wrClass) {
                    wrColor = m;
                }
                // Fallback for color(int, int, int, int)
                if (wrColor == null && (m.getName().equals("color") || m.getName().equals("c")) && p.length == 4
                        && p[0] == int.class && m.getReturnType() == wrClass) {
                    wrColor = m;
                }
            }

            // Get VertexFormat.POSITION_COLOR
            Object vertexFormatPositionColor = null;
            if (vfClass != null) {
                for (java.lang.reflect.Field f : vfClass.getDeclaredFields()) {
                    if (f.getType().getName().contains("VertexFormat")
                            && (f.getName().equals("POSITION_COLOR") || f.getName().equals("a"))) {
                        f.setAccessible(true);
                        vertexFormatPositionColor = f.get(null);
                        break;
                    }
                }
            }

            if (wrBegin != null && wrPos != null && wrColor != null && wrEndVertex != null && wrEnd != null
                    && vertexFormatPositionColor != null) {
                // Check if the wrColor method takes floats or ints
                if (wrColor.getParameterTypes()[0] == float.class) {
                    // If it takes floats, we're good.
                } else if (wrColor.getParameterTypes()[0] == int.class) {
                    // If it takes ints, we need to ensure the color values are passed as 0-255
                } else {
                    // Unknown color method signature, disable tessellator
                    usingTessellator = false;
                    com.hades.client.util.HadesLogger.get()
                            .warn("Tessellator color method has unexpected signature. Falling back to GL11.");
                    return;
                }

                // Test begin method to see if it works with POSITION_COLOR
                try {
                    wrBegin.invoke(worldRenderer, GL_QUADS, vertexFormatPositionColor);
                    wrEnd.invoke(tessellator); // End the test draw
                    usingTessellator = true;
                    com.hades.client.util.HadesLogger.get().info("Tessellator-based rendering enabled!");
                } catch (Exception e) {
                    com.hades.client.util.HadesLogger.get()
                            .warn("Tessellator begin/end test failed. Falling back to GL11.");
                    com.hades.client.util.HadesLogger.get().error("Tessellator test error", e);
                    usingTessellator = false;
                }
            } else {
                com.hades.client.util.HadesLogger.get().warn(
                        "Could not find all Tessellator/WorldRenderer methods or VertexFormat. Falling back to GL11.");
            }
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("Failed to init Tessellator", e);
        }
    }

    private static void loadCustomFont() {
        try {
            java.io.InputStream is = RenderUtil.class.getResourceAsStream("/font/custom.ttf");
            if (is == null) {
                ClassLoader tcl = Thread.currentThread().getContextClassLoader();
                if (tcl != null) is = tcl.getResourceAsStream("font/custom.ttf");
            }
            if (is == null) {
                java.io.File diskFont = new java.io.File(System.getProperty("user.home"), ".hades/font/custom.ttf");
                if (diskFont.exists()) is = new java.io.FileInputStream(diskFont);
            }
            if (is == null) {
                java.io.File devFont = new java.io.File("C:/Users/tobia/source/repos/ClientTest/Java/client/src/main/resources/font/custom.ttf");
                if (devFont.exists()) is = new java.io.FileInputStream(devFont);
            }
            if (is != null) {
                baseFont = Font.createFont(Font.TRUETYPE_FONT, is);
                fontLoaded = true;
                is.close();
            } else {
                fontLoaded = false;
            }
        } catch (Exception e) {
            fontLoaded = false;
        }
    }

    private static void cacheMcFontRenderer() {
        try {
            Class<?> mcClass = Class.forName("ave");
            Method getMinecraft = mcClass.getMethod("A");
            Object mc = getMinecraft.invoke(null);
            if (mc != null) {
                java.lang.reflect.Field fontField = com.hades.client.util.ReflectionUtil.findField(mcClass, "k", "fontRendererObj", "field_71466_p");
                if (fontField != null) {
                    fontField.setAccessible(true);
                    mcFontRenderer = fontField.get(mc);
                    if (mcFontRenderer != null) {
                        for (Method m : mcFontRenderer.getClass().getDeclaredMethods()) {
                            if (m.getReturnType() == int.class && m.getParameterCount() == 5) {
                                Class<?>[] p = m.getParameterTypes();
                                if (p[0] == String.class && p[1] == float.class) {
                                    drawStringMethod = m;
                                    drawStringMethod.setAccessible(true);
                                }
                            }
                            if (m.getReturnType() == int.class && m.getParameterCount() == 1
                                    && m.getParameterTypes()[0] == String.class) {
                                getStringWidthMethod = m;
                                getStringWidthMethod.setAccessible(true);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ══════════════════════════════════════════
    // Basic shapes
    // ══════════════════════════════════════════

    private static boolean glInitFailed = false;
    private static boolean glInitFailedLogged = false;

    public static void drawRect(float x, float y, float width, float height, int color) {
        if (color == 0 || width <= 0 || height <= 0)
            return;

        if (!com.hades.client.api.HadesAPI.Render.isForceRenderUtil()
                && com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD && com.hades.client.util.LabyRenderer.isAvailable() && com.hades.client.util.LabyRenderer.getCurrentScreenContext() != null) {
            com.hades.client.util.LabyRenderer.drawRect(x, y, width, height, color);
            return;
        }

        try {
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);

            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glVertex2f(x, y);
            org.lwjgl.opengl.GL11.glVertex2f(x, y + height);
            org.lwjgl.opengl.GL11.glVertex2f(x + width, y + height);
            org.lwjgl.opengl.GL11.glVertex2f(x + width, y);
            org.lwjgl.opengl.GL11.glEnd();

            org.lwjgl.opengl.GL11.glPopAttrib();
            if (wasTex2D) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        } catch (Exception ignored) {
        }
    }

    public static void drawGradientRect(float x, float y, float width, float height,
            int colorTop, int colorBottom) {

        if (!com.hades.client.api.HadesAPI.Render.isForceRenderUtil()
                && com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD
                && com.hades.client.util.LabyRenderer.isAvailable() && com.hades.client.util.LabyRenderer.getCurrentScreenContext() != null) {
            com.hades.client.util.LabyRenderer.drawGradientRect(x, y, width, height, colorTop, colorBottom);
            return;
        }

        try {
            float a1 = ((colorTop >> 24) & 0xFF) / 255f;
            float r1 = ((colorTop >> 16) & 0xFF) / 255f;
            float g1 = ((colorTop >> 8) & 0xFF) / 255f;
            float b1 = (colorTop & 0xFF) / 255f;

            float a2 = ((colorBottom >> 24) & 0xFF) / 255f;
            float r2 = ((colorBottom >> 16) & 0xFF) / 255f;
            float g2 = ((colorBottom >> 8) & 0xFF) / 255f;
            float b2 = (colorBottom & 0xFF) / 255f;

            boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_SMOOTH);
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);

            org.lwjgl.opengl.GL11.glColor4f(r1, g1, b1, a1);
            org.lwjgl.opengl.GL11.glVertex2f(x, y);
            org.lwjgl.opengl.GL11.glVertex2f(x + width, y);

            org.lwjgl.opengl.GL11.glColor4f(r2, g2, b2, a2);
            org.lwjgl.opengl.GL11.glVertex2f(x + width, y + height);
            org.lwjgl.opengl.GL11.glVertex2f(x, y + height);

            org.lwjgl.opengl.GL11.glEnd();
            org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_FLAT);
            
            org.lwjgl.opengl.GL11.glPopAttrib();
            if (wasTex2D) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        } catch (Exception ignored) {
        }
    }

    public static void drawHorizontalGradient(float x, float y, float width, float height,
            int colorLeft, int colorRight) {

        if (!com.hades.client.api.HadesAPI.Render.isForceRenderUtil()
                && com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD
                && com.hades.client.util.LabyRenderer.isAvailable() && com.hades.client.util.LabyRenderer.getCurrentScreenContext() != null) {
            com.hades.client.util.LabyRenderer.drawHorizontalGradient(x, y, width, height, colorLeft, colorRight);
            return;
        }

        try {
            float a1 = ((colorLeft >> 24) & 0xFF) / 255f;
            float r1 = ((colorLeft >> 16) & 0xFF) / 255f;
            float g1 = ((colorLeft >> 8) & 0xFF) / 255f;
            float b1 = (colorLeft & 0xFF) / 255f;

            float a2 = ((colorRight >> 24) & 0xFF) / 255f;
            float r2 = ((colorRight >> 16) & 0xFF) / 255f;
            float g2 = ((colorRight >> 8) & 0xFF) / 255f;
            float b2 = (colorRight & 0xFF) / 255f;

            boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_SMOOTH);
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);

            org.lwjgl.opengl.GL11.glColor4f(r1, g1, b1, a1);
            org.lwjgl.opengl.GL11.glVertex2f(x, y);
            org.lwjgl.opengl.GL11.glColor4f(r2, g2, b2, a2);
            org.lwjgl.opengl.GL11.glVertex2f(x + width, y);
            org.lwjgl.opengl.GL11.glColor4f(r2, g2, b2, a2);
            org.lwjgl.opengl.GL11.glVertex2f(x + width, y + height);
            org.lwjgl.opengl.GL11.glColor4f(r1, g1, b1, a1);
            org.lwjgl.opengl.GL11.glVertex2f(x, y + height);

            org.lwjgl.opengl.GL11.glEnd();
            org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_FLAT);
            
            org.lwjgl.opengl.GL11.glPopAttrib();
            if (wasTex2D) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        } catch (Exception ignored) {
        }
    }

    // ══════════════════════════════════════════
    // Rounded rectangles
    // ══════════════════════════════════════════

    public static void drawRoundedRect(float x, float y, float width, float height,
            float radius, int color) {

        try {
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            boolean wasLineSmooth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
            boolean wasPolySmooth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_POLYGON_SMOOTH);

            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH); // GL_LINE_SMOOTH
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_POLYGON_SMOOTH); // GL_POLYGON_SMOOTH
            org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);

            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_POLYGON); // GL_POLYGON
            int arcStep = 3; 

            // Top-left corner arc (180-270 degrees)
            float cx = x + radius, cy = y + radius;
            for (int i = 180; i <= 270; i += arcStep) {
                double rad = Math.toRadians(i);
                org.lwjgl.opengl.GL11.glVertex2f((float)(cx + Math.cos(rad) * radius), (float)(cy + Math.sin(rad) * radius));
            }

            // Top-right corner arc (270-360 degrees)
            cx = x + width - radius; cy = y + radius;
            for (int i = 270; i <= 360; i += arcStep) {
                double rad = Math.toRadians(i);
                org.lwjgl.opengl.GL11.glVertex2f((float)(cx + Math.cos(rad) * radius), (float)(cy + Math.sin(rad) * radius));
            }

            // Bottom-right corner arc (0-90 degrees)
            cx = x + width - radius; cy = y + height - radius;
            for (int i = 0; i <= 90; i += arcStep) {
                double rad = Math.toRadians(i);
                org.lwjgl.opengl.GL11.glVertex2f((float)(cx + Math.cos(rad) * radius), (float)(cy + Math.sin(rad) * radius));
            }

            // Bottom-left corner arc (90-180 degrees)
            cx = x + radius; cy = y + height - radius;
            for (int i = 90; i <= 180; i += arcStep) {
                double rad = Math.toRadians(i);
                org.lwjgl.opengl.GL11.glVertex2f((float)(cx + Math.cos(rad) * radius), (float)(cy + Math.sin(rad) * radius));
            }

            org.lwjgl.opengl.GL11.glEnd();
            org.lwjgl.opengl.GL11.glPopAttrib();

            if (!wasPolySmooth) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_POLYGON_SMOOTH);
            if (!wasLineSmooth) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
            if (wasTex2D) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("drawRoundedRect failed", e);
        }
    }

    public static void drawRoundedGradientRect(float x, float y, float width, float height,
            float radius, int colorTop, int colorBottom) {

        if (!com.hades.client.api.HadesAPI.Render.isForceRenderUtil()
                && com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD && com.hades.client.util.LabyRenderer.isAvailable() && com.hades.client.util.LabyRenderer.getCurrentScreenContext() != null) {
            com.hades.client.util.LabyRenderer.drawRoundedGradientRect(x, y, width, height, radius, colorTop, colorBottom);
            return;
        }

        // Simplified: draw rounded solid background + overlay gradient
        drawRoundedRect(x, y, width, height, radius, colorTop);
        // Gradient overlay on bottom half
        float halfH = height / 2f;
        drawGradientRect(x + radius, y + halfH, width - radius * 2, halfH,
                colorWithAlpha(colorBottom, 0), colorBottom);
    }

    private static void drawRectInternal(float x, float y, float w, float h) {
        try {
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glVertex2f(x, y);
            org.lwjgl.opengl.GL11.glVertex2f(x + w, y);
            org.lwjgl.opengl.GL11.glVertex2f(x + w, y + h);
            org.lwjgl.opengl.GL11.glVertex2f(x, y + h);
            org.lwjgl.opengl.GL11.glEnd();
        } catch (Exception ignored) {
        }
    }

    private static void drawArc(float cx, float cy, float radius, int startAngle, int endAngle) {
        try {
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN); // GL_TRIANGLE_FAN
            org.lwjgl.opengl.GL11.glVertex2f(cx, cy); // center vertex
            for (int i = startAngle; i <= endAngle; i += 3) {
                double rad = Math.toRadians(i);
                float x1 = (float) (cx + Math.cos(rad) * radius);
                float y1 = (float) (cy + Math.sin(rad) * radius);
                org.lwjgl.opengl.GL11.glVertex2f(x1, y1);
            }
            // Ensure we hit the exact end angle
            if ((endAngle - startAngle) % 3 != 0) {
                double rad = Math.toRadians(endAngle);
                org.lwjgl.opengl.GL11.glVertex2f((float)(cx + Math.cos(rad) * radius), (float)(cy + Math.sin(rad) * radius));
            }
            org.lwjgl.opengl.GL11.glEnd();
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("drawArc failed", e);
        }
    }

    // ══════════════════════════════════════════
    // Shadow
    // ══════════════════════════════════════════

    public static void drawShadow(float x, float y, float width, float height,
            float shadowSize, int shadowColor) {
        int transparent = colorWithAlpha(shadowColor, 0);
        // Bottom shadow
        drawGradientRect(x, y + height, width, shadowSize, shadowColor, transparent);
        // Right shadow
        drawHorizontalGradient(x + width, y, shadowSize, height, shadowColor, transparent);
        // Bottom-right corner
        drawGradientRect(x + width, y + height, shadowSize, shadowSize, shadowColor, transparent);
        // Top shadow (subtle)
        drawGradientRect(x, y - shadowSize, width, shadowSize, transparent, shadowColor);
        // Left shadow
        drawHorizontalGradient(x - shadowSize, y, shadowSize, height, transparent, shadowColor);
    }

    public static void drawRoundedShadow(float x, float y, float width, float height,
            float radius, float shadowSize) {
        int transparent = colorWithAlpha(0, 0); // Need alpha 0 black
        int shadowColor = color(0, 0, 0, 80); // Base shadow color

        // Fill the interior of the element so the base background is properly darkened.
        // We draw this manually to avoid the LabyMod shader leaving a faint square boundary.
        float a = ((shadowColor >> 24) & 0xFF) / 255f;
        float r = ((shadowColor >> 16) & 0xFF) / 255f;
        float g = ((shadowColor >> 8) & 0xFF) / 255f;
        float b = (shadowColor & 0xFF) / 255f;

        try {
            boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
            // Center
            drawRectInternal(x + radius, y, width - radius * 2, height);
            // Left
            drawRectInternal(x, y + radius, radius, height - radius * 2);
            // Right
            drawRectInternal(x + width - radius, y + radius, radius, height - radius * 2);
            // Arcs
            drawArc(x + radius, y + radius, radius, 180, 270);
            drawArc(x + width - radius, y + radius, radius, 270, 360);
            drawArc(x + width - radius, y + height - radius, radius, 0, 90);
            drawArc(x + radius, y + height - radius, radius, 90, 180);

            org.lwjgl.opengl.GL11.glPopAttrib();
            if (wasTex2D) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("drawRoundedShadow interior failed", e);
        }

        // Edges
        // Top edge
        drawGradientRect(x + radius, y - shadowSize, width - radius * 2, shadowSize, transparent, shadowColor);
        // Bottom edge
        drawGradientRect(x + radius, y + height, width - radius * 2, shadowSize, shadowColor, transparent);
        // Left edge
        drawHorizontalGradient(x - shadowSize, y + radius, shadowSize, height - radius * 2, transparent, shadowColor);
        // Right edge
        drawHorizontalGradient(x + width, y + radius, shadowSize, height - radius * 2, shadowColor, transparent);

        // Four corners (The target outerRadius is radius + shadowSize, which is handled INTERNALLY by drawRadialGradient, so we pass shadowSize!)
        drawRadialGradient(x + radius, y + radius, radius, shadowSize, shadowColor, transparent, 180, 270);
        drawRadialGradient(x + width - radius, y + radius, radius, shadowSize, shadowColor, transparent, 270, 360);
        drawRadialGradient(x + width - radius, y + height - radius, radius, shadowSize, shadowColor, transparent, 0, 90);
        drawRadialGradient(x + radius, y + height - radius, radius, shadowSize, shadowColor, transparent, 90, 180);
    }

    private static void drawRadialGradient(float cx, float cy, float radius, float shadowSize, int innerColor, int outerColor, int startAngle, int endAngle) {
        try {
            float a1 = ((innerColor >> 24) & 0xFF) / 255f;
            float r1 = ((innerColor >> 16) & 0xFF) / 255f;
            float g1 = ((innerColor >> 8) & 0xFF) / 255f;
            float b1 = (innerColor & 0xFF) / 255f;

            float a2 = ((outerColor >> 24) & 0xFF) / 255f;
            float r2 = ((outerColor >> 16) & 0xFF) / 255f;
            float g2 = ((outerColor >> 8) & 0xFF) / 255f;
            float b2 = (outerColor & 0xFF) / 255f;

            boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_SMOOTH);
            
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            for (int i = startAngle; i < endAngle; i += 5) {
                int nextI = Math.min(i + 5, endAngle);
                double rad1 = Math.toRadians(i);
                double rad2 = Math.toRadians(nextI);
                
                float cos1 = (float) Math.cos(rad1);
                float sin1 = (float) Math.sin(rad1);
                float cos2 = (float) Math.cos(rad2);
                float sin2 = (float) Math.sin(rad2);

                // Vertex 1: Inner (start angle)
                org.lwjgl.opengl.GL11.glColor4f(r1, g1, b1, a1);
                org.lwjgl.opengl.GL11.glVertex2f(cx + cos1 * radius, cy + sin1 * radius);

                // Vertex 2: Outer (start angle)
                org.lwjgl.opengl.GL11.glColor4f(r2, g2, b2, a2);
                org.lwjgl.opengl.GL11.glVertex2f(cx + cos1 * (radius + shadowSize), cy + sin1 * (radius + shadowSize));

                // Vertex 3: Outer (end angle)
                org.lwjgl.opengl.GL11.glColor4f(r2, g2, b2, a2);
                org.lwjgl.opengl.GL11.glVertex2f(cx + cos2 * (radius + shadowSize), cy + sin2 * (radius + shadowSize));

                // Vertex 4: Inner (end angle)
                org.lwjgl.opengl.GL11.glColor4f(r1, g1, b1, a1);
                org.lwjgl.opengl.GL11.glVertex2f(cx + cos2 * radius, cy + sin2 * radius);
            }
            org.lwjgl.opengl.GL11.glEnd();
            
            org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_FLAT);
            
            org.lwjgl.opengl.GL11.glPopAttrib();
            if (wasTex2D) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("drawRadialGradient failed", e);
        }
    }

    // ══════════════════════════════════════════
    // Scissoring (clipping)
    // ══════════════════════════════════════════

    public static void disableScissor() {
        try {
            glDisable.invoke(null, GL_SCISSOR_TEST);
        } catch (Exception ignored) {
        }
    }

    // ══════════════════════════════════════════
    // Text rendering (Custom Font via texture atlas)
    // ══════════════════════════════════════════

    /**
     * Draw string using custom font rendered to texture, or MC fallback.
     */
    public static void drawString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {

        if (fontLoaded) {
            Font font = getFont(size, bold, italic);
            if (shadow) {
                drawCustomFontString(text, x + 0.5f, y + 0.5f, new java.awt.Color(0, 0, 0, 150).getRGB(), font);
            }
            drawCustomFontString(text, x, y, color, font);
            return;
        }

        if (!com.hades.client.api.HadesAPI.Render.isForceRenderUtil()
                && com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD && com.hades.client.util.LabyRenderer.isAvailable() && com.hades.client.util.LabyRenderer.getCurrentScreenContext() != null) {
            float scale = size / 14.0f;
            if (shadow) com.hades.client.util.LabyRenderer.drawStringWithShadow(text, x, y, color, scale);
            else com.hades.client.util.LabyRenderer.drawString(text, x, y, color, scale);
            return;
        }

        drawMcString(text, x, y, color);
    }

    public static void drawString(String text, float x, float y, int color, FontType fontType) {
        drawString(text, x, y, color, fontType.getScale() * 14f, fontType == FontType.LARGE || fontType == FontType.MEDIUM_BOLD, fontType == FontType.SMALL_ITALIC, false);
    }

    public static void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, FontType.NORMAL);
    }

    public static void drawCenteredString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {
        float w = getStringWidth(text, size, bold, italic);
        drawString(text, x - w / 2f, y, color, size, bold, italic, shadow);
    }

    public static void drawCenteredString(String text, float x, float y, int color, FontType fontType) {
        float w = getStringWidth(text, fontType);
        drawString(text, x - w / 2f, y, color, fontType);
    }

    public static void drawCenteredString(String text, float x, float y, int color) {
        drawCenteredString(text, x, y, color, FontType.NORMAL);
    }

    public static float getStringWidth(String text, float size, boolean bold, boolean italic) {

        if (fontLoaded) {
            Font font = getFont(size, bold, italic);
            BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = temp.createGraphics();
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            float w = fm.stringWidth(text);
            g.dispose();
            return w;
        }

        if (!com.hades.client.api.HadesAPI.Render.isForceRenderUtil()
                && com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD && com.hades.client.util.LabyRenderer.isAvailable() && com.hades.client.util.LabyRenderer.getCurrentScreenContext() != null) {
            return com.hades.client.util.LabyRenderer.getStringWidth(text, size / 14f);
        }

        return getMcStringWidth(text);
    }

    public static float getStringWidth(String text, FontType fontType) {
        return getStringWidth(text, fontType.getScale() * 14f, fontType == FontType.LARGE || fontType == FontType.MEDIUM_BOLD, fontType == FontType.SMALL_ITALIC);
    }

    public static float getFontHeight(float size, boolean bold, boolean italic) {

        if (fontLoaded) {
            Font font = getFont(size, bold, italic);
            BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = temp.createGraphics();
            g.setFont(font);
            float h = g.getFontMetrics().getHeight();
            g.dispose();
            return h;
        }

        if (!com.hades.client.api.HadesAPI.Render.isForceRenderUtil()
                && com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD && com.hades.client.util.LabyRenderer.isAvailable()) {
            return com.hades.client.util.LabyRenderer.getFontHeight(size / 14f);
        }

        return 9; // MC default
    }

    public static float getFontHeight(FontType fontType) {
        return getFontHeight(fontType.getScale() * 14f, fontType == FontType.LARGE || fontType == FontType.MEDIUM_BOLD, fontType == FontType.SMALL_ITALIC);
    }

    public static float getFontHeight() {
        return getFontHeight(FontType.NORMAL);
    }



    private static void drawCustomFontString(String text, float x, float y, int color, Font font) {
        if (text == null || text.isEmpty())
            return;

        String cacheKey = text + "|" + font.getSize() + "|" + font.getStyle();
        FontTexture cached = fontTextureCache.get(cacheKey);
        if (cached == null) {
            cached = createFontTexture(text, font);
            fontTextureCache.put(cacheKey, cached);

            // Limit cache size
            if (fontTextureCache.size() > 500) {
                for (FontTexture ft : fontTextureCache.values()) {
                    if (ft != cached) {
                        org.lwjgl.opengl.GL11.glDeleteTextures(ft.textureId);
                    }
                }
                fontTextureCache.clear();
                fontTextureCache.put(cacheKey, cached);
            }
        }

        drawTexturedRect(cached.textureId, x, y, cached.width, cached.height, color, cached.uMax, cached.vMax);
    }

    private static int nextPowerOfTwo(int value) {
        int pot = 1;
        while (pot < value) pot <<= 1;
        return pot;
    }

    private static FontTexture createFontTexture(String text, Font font) {
        BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = temp.createGraphics();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        // Add more padding to avoid slicing italic characters!
        int textWidth = fm.stringWidth(text) + 8;
        int textHeight = fm.getHeight() + 4;
        g2.dispose();

        // Pad to power-of-two for OpenGL 1.1 compatibility
        int potWidth = nextPowerOfTwo(textWidth);
        int potHeight = nextPowerOfTwo(textHeight);

        BufferedImage image = new BufferedImage(potWidth, potHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(font);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(text, 2, fm.getAscent() + 2);
        g.dispose();

        int texId = uploadTexture(image);
        FontTexture ft = new FontTexture();
        ft.textureId = texId;
        ft.width = textWidth;  // display size = original text size
        ft.height = textHeight;
        ft.uMax = (float) textWidth / potWidth;   // fractional UV
        ft.vMax = (float) textHeight / potHeight;
        return ft;
    }

    private static int uploadTexture(BufferedImage image) {
        try {
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(pixels.length * 4).order(java.nio.ByteOrder.nativeOrder());
            for (int pixel : pixels) {
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF)); // G
                buffer.put((byte) (pixel & 0xFF)); // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
            buffer.flip();

            int oldTex = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            int texId = org.lwjgl.opengl.GL11.glGenTextures();
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texId);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA,
                    image.getWidth(), image.getHeight(), 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
            // Re-bind previous texture to not desync GlStateManager
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, oldTex);
            return texId;
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("Failed to upload font texture natively!", e);
            return -1;
        }
    }

    private static void drawTexturedRect(int texId, float x, float y, float w, float h, int color, float uMax, float vMax) {
        try {
            if (texId == -1) return; // Prevent binding invalid textures causing white box/glError

            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            boolean wasTex2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            int oldTex = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);

            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texId);
            org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);

            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glTexCoord2f(0f, 0f);
            org.lwjgl.opengl.GL11.glVertex2f(x, y);
            org.lwjgl.opengl.GL11.glTexCoord2f(uMax, 0f);
            org.lwjgl.opengl.GL11.glVertex2f(x + w, y);
            org.lwjgl.opengl.GL11.glTexCoord2f(uMax, vMax);
            org.lwjgl.opengl.GL11.glVertex2f(x + w, y + h);
            org.lwjgl.opengl.GL11.glTexCoord2f(0f, vMax);
            org.lwjgl.opengl.GL11.glVertex2f(x, y + h);
            org.lwjgl.opengl.GL11.glEnd();

            // Restore: unbind our texture, reset color, disable blend
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, oldTex);
            org.lwjgl.opengl.GL11.glPopAttrib();
            
            if (!wasBlend) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
            if (!wasTex2D) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        } catch (Exception ignored) {
        }
    }

    public static void drawMcString(String text, float x, float y, int color) {
        try {
            if (drawStringMethod != null && mcFontRenderer != null) {
                drawStringMethod.invoke(mcFontRenderer, text, x, y, color, false);
            }
        } catch (Exception ignored) {
        }
    }

    public static void drawMcStringWithShadow(String text, float x, float y, int color) {
        try {
            if (drawStringMethod != null && mcFontRenderer != null) {
                drawStringMethod.invoke(mcFontRenderer, text, x, y, color, true);
            }
        } catch (Exception ignored) {
        }
    }

    public static float getMcStringWidth(String text) {
        try {
            if (getStringWidthMethod != null && mcFontRenderer != null) {
                return (int) getStringWidthMethod.invoke(mcFontRenderer, text);
            }
        } catch (Exception ignored) {
        }
        return text.length() * 6;
    }

    // ══════════════════════════════════════════
    // Utility
    // ══════════════════════════════════════════

    public static int color(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int color(int r, int g, int b) {
        return color(r, g, b, 255);
    }

    public static int colorWithAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return color(
                (int) (r1 + (r2 - r1) * t),
                (int) (g1 + (g2 - g1) * t),
                (int) (b1 + (b2 - b1) * t),
                (int) (a1 + (a2 - a1) * t));
    }



    public static void enableScissor(float x, float y, float width, float height, int scaledWidth, int scaledHeight) {
        int displayWidth = com.hades.client.api.HadesAPI.mc.displayWidth();
        int displayHeight = com.hades.client.api.HadesAPI.mc.displayHeight();

        // Exact physical-to-logical ratio
        float scaleX = (float) displayWidth / scaledWidth;
        float scaleY = (float) displayHeight / scaledHeight;

        // Ensure width and height are positive
        if (width < 0)
            width = 0;
        if (height < 0)
            height = 0;

        // Convert the logical coordinates to physical pixel coordinates based on exact
        // scale
        int finalX = (int) (x * scaleX);

        // Scissor y-coordinate starts from the bottom of the screen in OpenGL.
        // We calculate the top-left corner in physical pixels, then adjust for OpenGL's
        // bottom-left origin.
        int finalY = (int) (displayHeight - ((y + height) * scaleY));
        int finalWidth = (int) (width * scaleX);
        int finalHeight = (int) (height * scaleY);

        // Clamp the final Y coordinate so we don't pass a negative value to OpenGL
        // (causes GL_INVALID_VALUE)
        if (finalY < 0) {
            finalHeight += finalY; // Reduce the height by the amount it goes below 0.
            finalY = 0;
        }

        // Also prevent negative starting X and width/height.
        if (finalX < 0) {
            finalWidth += finalX;
            finalX = 0;
        }

        if (finalWidth < 0)
            finalWidth = 0;
        if (finalHeight < 0)
            finalHeight = 0;

        try {
            glEnable.invoke(null, GL_SCISSOR_TEST);
            glScissor.invoke(null, finalX, finalY, finalWidth, finalHeight);
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("RenderUtil.enableScissor failed", e);
        }
    }

    public static Font getFont(float size, boolean bold, boolean italic) {
        if (!fontLoaded || baseFont == null) return new Font("SansSerif", Font.PLAIN, (int)size);
        String key = size + "-" + bold + "-" + italic;
        return dynamicFontCache.computeIfAbsent(key, k -> {
            int style = Font.PLAIN;
            if (bold) style |= Font.BOLD;
            if (italic) style |= Font.ITALIC;
            return baseFont.deriveFont(style, size);
        });
    }

    private static Font getFontForType(FontType type) {
        switch (type) {
            case SMALL:
                return getFont(11f, false, false);
            case SMALL_ITALIC:
                return getFont(10.5f, false, true);
            case MEDIUM_BOLD:
                return getFont(16f, true, false);
            case LARGE:
                return getFont(20f, true, false);
            default:
                return getFont(14f, false, false);
        }
    }

    public enum FontType {
        NORMAL(1.0f), LARGE(1.3f), MEDIUM_BOLD(1.15f), SMALL(0.8f), SMALL_ITALIC(0.75f);

        private final float scale;

        FontType(float scale) {
            this.scale = scale;
        }

        public float getScale() {
            return scale;
        }
    }

    private static class FontTexture {
        int textureId;
        int width;
        int height;
        float uMax = 1f;
        float vMax = 1f;
    }

    // ══════════════════════════════════════════
    // 3D Rendering (Box ESP)
    // ══════════════════════════════════════════

    public static void putVertex3DInWorld(double x, double y, double z) {
        org.lwjgl.opengl.GL11.glVertex3d(x, y, z);
    }

    public static void drawOutlinedBoundingBox(double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        // Draw 12 edges of a wireframe box using GL_LINES
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINES);

        // Bottom face edges (4 edges)
        putVertex3DInWorld(minX, minY, minZ);
        putVertex3DInWorld(maxX, minY, minZ);
        putVertex3DInWorld(maxX, minY, minZ);
        putVertex3DInWorld(maxX, minY, maxZ);
        putVertex3DInWorld(maxX, minY, maxZ);
        putVertex3DInWorld(minX, minY, maxZ);
        putVertex3DInWorld(minX, minY, maxZ);
        putVertex3DInWorld(minX, minY, minZ);

        // Top face edges (4 edges)
        putVertex3DInWorld(minX, maxY, minZ);
        putVertex3DInWorld(maxX, maxY, minZ);
        putVertex3DInWorld(maxX, maxY, minZ);
        putVertex3DInWorld(maxX, maxY, maxZ);
        putVertex3DInWorld(maxX, maxY, maxZ);
        putVertex3DInWorld(minX, maxY, maxZ);
        putVertex3DInWorld(minX, maxY, maxZ);
        putVertex3DInWorld(minX, maxY, minZ);

        // Vertical edges connecting top and bottom (4 edges)
        putVertex3DInWorld(minX, minY, minZ);
        putVertex3DInWorld(minX, maxY, minZ);
        putVertex3DInWorld(maxX, minY, minZ);
        putVertex3DInWorld(maxX, maxY, minZ);
        putVertex3DInWorld(maxX, minY, maxZ);
        putVertex3DInWorld(maxX, maxY, maxZ);
        putVertex3DInWorld(minX, minY, maxZ);
        putVertex3DInWorld(minX, maxY, maxZ);

        org.lwjgl.opengl.GL11.glEnd();
    }

    public static void drawOutlinedEntityESP(double x, double y, double z, double width, double height, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // Manually query current GL state (glPushAttrib is broken on LWJGL 3/OpenGL
        // 3.0+)
        boolean wasDepthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean wasTexture2D = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        boolean wasLineSmooth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
        boolean wasDepthMask = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);

        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(770, 771);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDepthMask(false);
            org.lwjgl.opengl.GL11.glLineWidth(2f);
            org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);

            double halfW = width / 2.0;
            drawOutlinedBoundingBox(x - halfW, y, z - halfW, x + halfW, y + height, z + halfW);
        } finally {
            // Restore EXACT previous state
            if (wasDepthTest)
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            else
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);

            if (wasBlend)
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            else
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);

            if (wasTexture2D)
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            else
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

            if (wasLineSmooth)
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
            else
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);

            org.lwjgl.opengl.GL11.glDepthMask(wasDepthMask);

            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }
}

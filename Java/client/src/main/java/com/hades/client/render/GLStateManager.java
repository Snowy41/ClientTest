package com.hades.client.render;

import java.lang.reflect.Method;

/**
 * Manages OpenGL state save/restore using LWJGL GL11 directly.
 * Ensures our rendering doesn't corrupt MC's state and vice versa.
 */
public class GLStateManager {

    // GL constants
    public static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_SRC_ALPHA = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_SCISSOR_TEST = 0x0C11;
    public static final int GL_LINE_SMOOTH = 0x0B20;
    public static final int GL_QUADS = 0x0007;
    public static final int GL_TRIANGLE_FAN = 0x0006;
    public static final int GL_LINES = 0x0001;
    public static final int GL_SMOOTH = 0x1D01;
    public static final int GL_FLAT = 0x1D00;
    public static final int GL_PROJECTION = 0x1701;
    public static final int GL_MODELVIEW = 0x1700;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_LINEAR = 0x2601;

    // Cached GL methods
    private static Class<?> gl11;
    private static Method glPushMatrix, glPopMatrix;
    private static Method glPushAttrib, glPopAttrib;
    private static Method glEnable, glDisable;
    private static Method glBegin, glEnd;
    private static Method glVertex2f, glVertex3d;
    private static Method glColor4f;
    private static Method glBlendFunc;
    private static Method glLineWidth;
    private static Method glShadeModel;
    private static Method glTexCoord2f;
    private static Method glBindTexture;
    private static Method glGenTextures;
    private static Method glTexParameteri;
    private static Method glTexImage2D;
    private static Method glScissor;
    private static Method glMatrixMode, glLoadIdentity, glOrtho;
    private static Method glTranslatef;
    private static Method glColorMask;

    private static boolean initialized = false;

    public static boolean init() {
        if (initialized)
            return true;
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null)
                cl = GLStateManager.class.getClassLoader();
            gl11 = Class.forName("org.lwjgl.opengl.GL11", true, cl);

            glPushMatrix = gl11.getMethod("glPushMatrix");
            glPopMatrix = gl11.getMethod("glPopMatrix");
            glPushAttrib = gl11.getMethod("glPushAttrib", int.class);
            glPopAttrib = gl11.getMethod("glPopAttrib");
            glEnable = gl11.getMethod("glEnable", int.class);
            glDisable = gl11.getMethod("glDisable", int.class);
            glBegin = gl11.getMethod("glBegin", int.class);
            glEnd = gl11.getMethod("glEnd");
            glVertex2f = gl11.getMethod("glVertex2f", float.class, float.class);
            glVertex3d = gl11.getMethod("glVertex3d", double.class, double.class, double.class);
            glColor4f = gl11.getMethod("glColor4f", float.class, float.class, float.class, float.class);
            glBlendFunc = gl11.getMethod("glBlendFunc", int.class, int.class);
            glLineWidth = gl11.getMethod("glLineWidth", float.class);
            glShadeModel = gl11.getMethod("glShadeModel", int.class);
            glTexCoord2f = gl11.getMethod("glTexCoord2f", float.class, float.class);
            glBindTexture = gl11.getMethod("glBindTexture", int.class, int.class);
            glGenTextures = gl11.getMethod("glGenTextures");
            glTexParameteri = gl11.getMethod("glTexParameteri", int.class, int.class, int.class);
            glTexImage2d(gl11);
            glScissor = gl11.getMethod("glScissor", int.class, int.class, int.class, int.class);
            glMatrixMode = gl11.getMethod("glMatrixMode", int.class);
            glLoadIdentity = gl11.getMethod("glLoadIdentity");
            glOrtho = gl11.getMethod("glOrtho", double.class, double.class, double.class, double.class, double.class,
                    double.class);
            glTranslatef = gl11.getMethod("glTranslatef", float.class, float.class, float.class);
            glColorMask = gl11.getMethod("glColorMask", boolean.class, boolean.class, boolean.class, boolean.class);

            initialized = true;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void glTexImage2d(Class<?> gl11) throws NoSuchMethodException {
        glTexImage2D = gl11.getMethod("glTexImage2D", int.class, int.class, int.class,
                int.class, int.class, int.class, int.class, int.class, java.nio.ByteBuffer.class);
    }

    // ══════════════════════════════════════════
    // State save/restore
    // ══════════════════════════════════════════

    /**
     * Save full GL state. Call before any Hades rendering.
     */
    public static void save() {
        if (!initialized && !init())
            return;
        try {
            glPushAttrib.invoke(null, GL_ALL_ATTRIB_BITS);
            glPushMatrix.invoke(null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Restore full GL state. Call after Hades rendering.
     */
    public static void restore() {
        if (!initialized)
            return;
        try {
            glPopMatrix.invoke(null);
            glPopAttrib.invoke(null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Set up a clean 2D ortho projection for overlay rendering.
     */
    public static void setup2D(int width, int height) {
        if (!initialized)
            return;
        try {
            glMatrixMode.invoke(null, GL_PROJECTION);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);
            glOrtho.invoke(null, 0.0, (double) width, (double) height, 0.0, -1.0, 1.0);
            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Restore matrices after 2D rendering.
     */
    public static void restore2D() {
        if (!initialized)
            return;
        try {
            glMatrixMode.invoke(null, GL_PROJECTION);
            glPopMatrix.invoke(null);
            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPopMatrix.invoke(null);
        } catch (Exception ignored) {
        }
    }

    // ══════════════════════════════════════════
    // Direct GL wrappers (much cleaner than RenderUtil's reflection)
    // ══════════════════════════════════════════

    public static void enable(int cap) {
        try {
            glEnable.invoke(null, cap);
        } catch (Exception ignored) {
        }
    }

    public static void disable(int cap) {
        try {
            glDisable.invoke(null, cap);
        } catch (Exception ignored) {
        }
    }

    public static void begin(int mode) {
        try {
            glBegin.invoke(null, mode);
        } catch (Exception ignored) {
        }
    }

    public static void end() {
        try {
            glEnd.invoke(null);
        } catch (Exception ignored) {
        }
    }

    public static void vertex2f(float x, float y) {
        try {
            glVertex2f.invoke(null, x, y);
        } catch (Exception ignored) {
        }
    }

    public static void vertex3d(double x, double y, double z) {
        try {
            glVertex3d.invoke(null, x, y, z);
        } catch (Exception ignored) {
        }
    }

    public static void color4f(float r, float g, float b, float a) {
        try {
            glColor4f.invoke(null, r, g, b, a);
        } catch (Exception ignored) {
        }
    }

    public static void blendFunc(int src, int dst) {
        try {
            glBlendFunc.invoke(null, src, dst);
        } catch (Exception ignored) {
        }
    }

    public static void lineWidth(float w) {
        try {
            glLineWidth.invoke(null, w);
        } catch (Exception ignored) {
        }
    }

    public static void shadeModel(int mode) {
        try {
            glShadeModel.invoke(null, mode);
        } catch (Exception ignored) {
        }
    }

    public static void texCoord2f(float u, float v) {
        try {
            glTexCoord2f.invoke(null, u, v);
        } catch (Exception ignored) {
        }
    }

    public static void bindTexture(int target, int tex) {
        try {
            glBindTexture.invoke(null, target, tex);
        } catch (Exception ignored) {
        }
    }

    public static int genTexture() {
        try {
            return (int) glGenTextures.invoke(null);
        } catch (Exception e) {
            return -1;
        }
    }

    public static void texParameteri(int target, int pname, int param) {
        try {
            glTexParameteri.invoke(null, target, pname, param);
        } catch (Exception ignored) {
        }
    }

    public static void texImage2D(int target, int level, int internal, int w, int h, int border, int fmt, int type,
            java.nio.ByteBuffer data) {
        try {
            glTexImage2D.invoke(null, target, level, internal, w, h, border, fmt, type, data);
        } catch (Exception ignored) {
        }
    }

    public static void scissor(int x, int y, int w, int h) {
        try {
            glScissor.invoke(null, x, y, w, h);
        } catch (Exception ignored) {
        }
    }

    public static void translate(float x, float y, float z) {
        try {
            glTranslatef.invoke(null, x, y, z);
        } catch (Exception ignored) {
        }
    }

    public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
        try {
            glColorMask.invoke(null, r, g, b, a);
        } catch (Exception ignored) {
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}

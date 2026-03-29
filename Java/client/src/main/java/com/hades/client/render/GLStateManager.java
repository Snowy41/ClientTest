package com.hades.client.render;

import org.lwjgl.opengl.GL11;

/**
 * Manages OpenGL state save/restore using LWJGL GL11 directly.
 * Ensures our rendering doesn't corrupt MC's state and vice versa.
 */
public class GLStateManager {

    // GL constants
    public static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    public static final int GL_TEXTURE_2D = GL11.GL_TEXTURE_2D;
    public static final int GL_BLEND = GL11.GL_BLEND;
    public static final int GL_SRC_ALPHA = GL11.GL_SRC_ALPHA;
    public static final int GL_ONE_MINUS_SRC_ALPHA = GL11.GL_ONE_MINUS_SRC_ALPHA;
    public static final int GL_DEPTH_TEST = GL11.GL_DEPTH_TEST;
    public static final int GL_SCISSOR_TEST = GL11.GL_SCISSOR_TEST;
    public static final int GL_LINE_SMOOTH = GL11.GL_LINE_SMOOTH;
    public static final int GL_QUADS = GL11.GL_QUADS;
    public static final int GL_TRIANGLE_FAN = GL11.GL_TRIANGLE_FAN;
    public static final int GL_LINES = GL11.GL_LINES;
    public static final int GL_SMOOTH = GL11.GL_SMOOTH;
    public static final int GL_FLAT = GL11.GL_FLAT;
    public static final int GL_PROJECTION = GL11.GL_PROJECTION;
    public static final int GL_MODELVIEW = GL11.GL_MODELVIEW;
    public static final int GL_RGBA = GL11.GL_RGBA;
    public static final int GL_UNSIGNED_BYTE = GL11.GL_UNSIGNED_BYTE;
    public static final int GL_TEXTURE_MIN_FILTER = GL11.GL_TEXTURE_MIN_FILTER;
    public static final int GL_TEXTURE_MAG_FILTER = GL11.GL_TEXTURE_MAG_FILTER;
    public static final int GL_LINEAR = GL11.GL_LINEAR;

    private static boolean initialized = false;

    public static boolean init() {
        if (initialized) return true;
        initialized = true;
        return true;
    }

    // ══════════════════════════════════════════
    // State save/restore
    // ══════════════════════════════════════════

    /**
     * Save full GL state. Call before any Hades rendering.
     */
    public static void save() {
        if (!initialized && !init()) return;
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
    }

    /**
     * Restore full GL state. Call after Hades rendering.
     */
    public static void restore() {
        if (!initialized) return;
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Set up a clean 2D ortho projection for overlay rendering.
     */
    public static void setup2D(int width, int height) {
        if (!initialized) return;
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, (double) width, (double) height, 0.0, -1.0, 1.0);
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    /**
     * Restore matrices after 2D rendering.
     */
    public static void restore2D() {
        if (!initialized) return;
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    // ══════════════════════════════════════════
    // Direct GL wrappers (eliminating reflection)
    // ══════════════════════════════════════════

    public static void enable(int cap) {
        GL11.glEnable(cap);
    }

    public static void disable(int cap) {
        GL11.glDisable(cap);
    }

    public static void begin(int mode) {
        GL11.glBegin(mode);
    }

    public static void end() {
        GL11.glEnd();
    }

    public static void vertex2f(float x, float y) {
        GL11.glVertex2f(x, y);
    }

    public static void vertex3d(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }

    public static void color4f(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    public static void blendFunc(int src, int dst) {
        GL11.glBlendFunc(src, dst);
    }

    public static void lineWidth(float w) {
        GL11.glLineWidth(w);
    }

    public static void shadeModel(int mode) {
        GL11.glShadeModel(mode);
    }

    public static void texCoord2f(float u, float v) {
        GL11.glTexCoord2f(u, v);
    }

    public static void bindTexture(int target, int tex) {
        GL11.glBindTexture(target, tex);
    }

    public static int genTexture() {
        return GL11.glGenTextures();
    }

    public static void texParameteri(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }

    public static void texImage2D(int target, int level, int internal, int w, int h, int border, int fmt, int type,
            java.nio.ByteBuffer data) {
        GL11.glTexImage2D(target, level, internal, w, h, border, fmt, type, data);
    }

    public static void scissor(int x, int y, int w, int h) {
        GL11.glScissor(x, y, w, h);
    }

    public static void translate(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
    }

    public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
        GL11.glColorMask(r, g, b, a);
    }

    public static boolean isInitialized() {
        return initialized;
    }
}

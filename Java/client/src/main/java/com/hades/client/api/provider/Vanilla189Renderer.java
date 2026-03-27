package com.hades.client.api.provider;

import com.hades.client.api.interfaces.IRenderer;
import com.hades.client.util.RenderUtil;
import com.hades.client.util.font.FontUtil;

public class Vanilla189Renderer implements IRenderer {

    @Override
    public void drawRect(float x, float y, float width, float height, int color) {
        RenderUtil.drawRect(x, y, width, height, color);
    }

    @Override
    public void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        RenderUtil.drawRoundedRect(x, y, width, height, radius, color);
    }

    @Override
    public void drawRoundedRect(float x, float y, float width, float height, float radTL, float radTR, float radBR, float radBL, int color) {
        float maxRad = Math.max(Math.max(radTL, radTR), Math.max(radBR, radBL));
        RenderUtil.drawRoundedRect(x, y, width, height, maxRad, color);
    }

    @Override
    public void drawGradientRect(float x, float y, float width, float height, int colorTop, int colorBottom) {
        RenderUtil.drawGradientRect(x, y, width, height, colorTop, colorBottom);
    }

    @Override
    public void drawHorizontalGradient(float x, float y, float width, float height, int colorLeft, int colorRight) {
        RenderUtil.drawHorizontalGradient(x, y, width, height, colorLeft, colorRight);
    }

    @Override
    public void drawRoundedGradientRect(float x, float y, float width, float height, float radius, int colorTop, int colorBottom) {
        RenderUtil.drawRoundedGradientRect(x, y, width, height, radius, colorTop, colorBottom);
    }

    @Override
    public void drawRoundedShadow(float x, float y, float width, float height, float radius, float shadowSize) {
        // Drop shadows removed per user request.
    }

    @Override
    public void drawString(String text, float x, float y, int color, float scale) {
        if (FontUtil.isFontFileLoaded()) {
            FontUtil.getRenderer(getSizeForScale(scale)).drawString(text, x, y, color);
        } else {
            RenderUtil.drawString(text, x, y, color, getFontTypeForScale(scale));
        }
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, 1.0f);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color, float scale) {
        if (FontUtil.isFontFileLoaded()) {
            FontUtil.getRenderer(getSizeForScale(scale)).drawStringWithShadow(text, x, y, color);
        } else {
            drawString(text, x + 1, y + 1, (color & 0xFCFCFC) >> 2 | (color & 0xFF000000), scale);
            drawString(text, x, y, color, scale);
        }
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        drawStringWithShadow(text, x, y, color, 1.0f);
    }

    @Override
    public void drawCenteredString(String text, float x, float y, int color, float scale) {
        float width = getStringWidth(text, scale);
        drawString(text, x - width / 2f, y, color, scale);
    }

    @Override
    public void drawCenteredString(String text, float x, float y, int color) {
        drawCenteredString(text, x, y, color, 1.0f);
    }

    @Override
    public float getStringWidth(String text, float scale) {
        if (FontUtil.isFontFileLoaded()) {
            return FontUtil.getRenderer(getSizeForScale(scale)).getStringWidth(text);
        } else {
            return RenderUtil.getStringWidth(text, getFontTypeForScale(scale));
        }
    }

    @Override
    public float getStringWidth(String text) {
        return getStringWidth(text, 1.0f);
    }

    @Override
    public float getFontHeight(float scale) {
        if (FontUtil.isFontFileLoaded()) {
            return FontUtil.getRenderer(getSizeForScale(scale)).getHeight();
        } else {
            return RenderUtil.getFontHeight(getFontTypeForScale(scale));
        }
    }

    @Override
    public float getFontHeight() {
        return getFontHeight(1.0f);
    }

    @Override
    public void drawString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {
        if (FontUtil.isFontFileLoaded()) {
            com.hades.client.util.font.CFontRenderer r = FontUtil.getRenderer((int) size);
            if (r != null) {
                r.drawString(text, x, y, color, shadow, bold, italic);
                return;
            }
        }
        RenderUtil.drawString(text, x, y, color, size, bold, italic, shadow);
    }

    @Override
    public void drawCenteredString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {
        if (FontUtil.isFontFileLoaded()) {
            com.hades.client.util.font.CFontRenderer r = FontUtil.getRenderer((int) size);
            if (r != null) {
                float w = r.getStringWidth(text, bold, italic);
                r.drawString(text, x - w / 2f, y, color, shadow, bold, italic);
                return;
            }
        }
        RenderUtil.drawCenteredString(text, x, y, color, size, bold, italic, shadow);
    }

    @Override
    public float getStringWidth(String text, float size, boolean bold, boolean italic) {
        if (FontUtil.isFontFileLoaded()) {
            com.hades.client.util.font.CFontRenderer r = FontUtil.getRenderer((int) size);
            if (r != null) {
                return r.getStringWidth(text, bold, italic);
            }
        }
        return RenderUtil.getStringWidth(text, size, bold, italic);
    }

    @Override
    public float getFontHeight(float size, boolean bold, boolean italic) {
        if (FontUtil.isFontFileLoaded()) {
            com.hades.client.util.font.CFontRenderer r = FontUtil.getRenderer((int) size);
            if (r != null) {
                return r.getHeight(bold, italic);
            }
        }
        return RenderUtil.getFontHeight(size, bold, italic);
    }

    @Override
    public boolean drawImage(String namespace, String path, float x, float y, float width, float height) {
        if (com.hades.client.platform.PlatformManager.getDetectedPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD) {
            return com.hades.client.util.LabyRenderer.drawImage(namespace, path, x, y, width, height);
        }
        return false;
    }

    @Override
    public void enableScissor(float x, float y, float width, float height) {
        int[] sr = com.hades.client.api.HadesAPI.Game.getScaledResolution();
        RenderUtil.enableScissor(x, y, width, height, sr[0], sr[1]);
    }

    @Override
    public void disableScissor() {
        RenderUtil.disableScissor();
    }

    @Override
    public void runWithScissor(float x, float y, float width, float height, Runnable action) {
        enableScissor(x, y, width, height);
        try {
            action.run();
        } finally {
            disableScissor();
        }
    }
    
    // Helpers
    private RenderUtil.FontType getFontTypeForScale(float scale) {
        if (scale == 0.75f) return RenderUtil.FontType.SMALL_ITALIC;
        if (scale == 1.15f) return RenderUtil.FontType.MEDIUM_BOLD;
        if (scale <= 0.85f) return RenderUtil.FontType.SMALL;
        if (scale >= 1.25f) return RenderUtil.FontType.LARGE;
        return RenderUtil.FontType.NORMAL;
    }
    
    private int getSizeForScale(float scale) {
        if (scale <= 0.85f) return FontUtil.SIZE_SMALL;
        if (scale >= 1.25f) return FontUtil.SIZE_LARGE;
        return FontUtil.SIZE_NORMAL;
    }

    // ── Reflection Fields for RenderManager Position ──
    private Object renderManagerInstance;
    private java.lang.reflect.Field renderPosXField;
    private java.lang.reflect.Field renderPosYField;
    private java.lang.reflect.Field renderPosZField;

    private void initRenderManager() {
        if (renderManagerInstance != null) return;
        try {
            Object mc = com.hades.client.api.HadesAPI.mc.getRaw();
            if (mc == null) return;
            java.lang.reflect.Method getRM = com.hades.client.util.ReflectionUtil.findMethod(mc.getClass(), new String[]{"af", "getRenderManager", "func_175598_ae"});
            if (getRM != null) renderManagerInstance = getRM.invoke(mc);

            if (renderManagerInstance != null) {
                renderPosXField = com.hades.client.util.ReflectionUtil.findField(renderManagerInstance.getClass(), "o", "h", "renderPosX", "field_78725_b");
                renderPosYField = com.hades.client.util.ReflectionUtil.findField(renderManagerInstance.getClass(), "p", "i", "renderPosY", "field_78726_c");
                renderPosZField = com.hades.client.util.ReflectionUtil.findField(renderManagerInstance.getClass(), "q", "j", "renderPosZ", "field_78723_d");
            }
        } catch (Exception ignored) {}
    }

    @Override
    public double getRenderPosX() {
        initRenderManager();
        return com.hades.client.util.ReflectionUtil.getDoubleField(renderManagerInstance, renderPosXField);
    }

    @Override
    public double getRenderPosY() {
        initRenderManager();
        return com.hades.client.util.ReflectionUtil.getDoubleField(renderManagerInstance, renderPosYField);
    }

    @Override
    public double getRenderPosZ() {
        initRenderManager();
        return com.hades.client.util.ReflectionUtil.getDoubleField(renderManagerInstance, renderPosZField);
    }
}

package com.hades.client.api.provider;

import com.hades.client.api.interfaces.IRenderer;
import com.hades.client.util.LabyRenderer;

public class LabyRendererProvider implements IRenderer {

    // When true, we're in LabyMod's DrawScreen render phase where its Canvas is available.
    private boolean labyRenderContext = false;
    
    // Fallback to Shader Renderer when context is invalid
    private final ShaderRendererProvider fallbackRenderer = new ShaderRendererProvider();

    public void setLabyRenderContext(boolean active) {
        this.labyRenderContext = active;
    }

    private boolean canUseLaby() {
        return labyRenderContext && net.labymod.api.Laby.labyAPI().minecraft().isOnRenderThread();
    }

    @Override
    public void drawRect(float x, float y, float width, float height, int color) {
        if (canUseLaby()) LabyRenderer.drawRect(x, y, width, height, color);
        else fallbackRenderer.drawRect(x, y, width, height, color);
    }

    @Override
    public void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        fallbackRenderer.drawRoundedRect(x, y, width, height, radius, color);
    }

    @Override
    public void drawRoundedRect(float x, float y, float width, float height, float radTL, float radTR, float radBR, float radBL, int color) {
        fallbackRenderer.drawRoundedRect(x, y, width, height, radTL, radTR, radBR, radBL, color);
    }

    @Override
    public void drawGradientRect(float x, float y, float width, float height, int colorTop, int colorBottom) {
        if (canUseLaby()) LabyRenderer.drawGradientRect(x, y, width, height, colorTop, colorBottom);
        else fallbackRenderer.drawGradientRect(x, y, width, height, colorTop, colorBottom);
    }

    @Override
    public void drawHorizontalGradient(float x, float y, float width, float height, int colorLeft, int colorRight) {
        if (canUseLaby()) LabyRenderer.drawHorizontalGradient(x, y, width, height, colorLeft, colorRight);
        else fallbackRenderer.drawHorizontalGradient(x, y, width, height, colorLeft, colorRight);
    }

    @Override
    public void drawRoundedGradientRect(float x, float y, float width, float height, float radius, int colorTop, int colorBottom) {
        if (canUseLaby()) LabyRenderer.drawRoundedGradientRect(x, y, width, height, radius, colorTop, colorBottom);
        else fallbackRenderer.drawRoundedGradientRect(x, y, width, height, radius, colorTop, colorBottom);
    }

    @Override
    public void drawRoundedShadow(float x, float y, float width, float height, float radius, float shadowSize) {
        fallbackRenderer.drawRoundedShadow(x, y, width, height, radius, shadowSize);
    }

    @Override
    public void drawString(String text, float x, float y, int color, float scale) {
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            fallbackRenderer.drawString(text, x, y, color, scale);
            return;
        }
        if (canUseLaby()) LabyRenderer.drawString(text, x, y, color, scale);
        else fallbackRenderer.drawString(text, x, y, color, scale);
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, 1.0f);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color, float scale) {
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            fallbackRenderer.drawStringWithShadow(text, x, y, color, scale);
            return;
        }
        if (canUseLaby()) {
            // LabyMod component renderer naturally has shadow support if explicitly asked, but we'll fake it precisely:
            drawString(text, x + 1 * scale, y + 1 * scale, (color & 0xFCFCFC) >> 2 | (color & 0xFF000000), scale);
            drawString(text, x, y, color, scale);
        } else fallbackRenderer.drawStringWithShadow(text, x, y, color, scale);
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
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            return fallbackRenderer.getStringWidth(text, scale);
        }
        if (canUseLaby()) return LabyRenderer.getStringWidth(text, scale);
        return fallbackRenderer.getStringWidth(text, scale);
    }

    @Override
    public float getStringWidth(String text) {
        return getStringWidth(text, 1.0f);
    }

    @Override
    public float getFontHeight(float scale) {
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            return fallbackRenderer.getFontHeight(scale);
        }
        if (canUseLaby()) return LabyRenderer.getFontHeight(scale);
        return fallbackRenderer.getFontHeight(scale);
    }

    @Override
    public float getFontHeight() {
        return getFontHeight(1.0f);
    }

    @Override
    public void drawString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            fallbackRenderer.drawString(text, x, y, color, size, bold, italic, shadow);
            return;
        }
        if (canUseLaby()) {
            float scale = size / 14f;
            if (shadow) com.hades.client.util.LabyRenderer.drawStringWithShadow(text, x, y, color, scale);
            else com.hades.client.util.LabyRenderer.drawString(text, x, y, color, scale);
            return;
        }
        fallbackRenderer.drawString(text, x, y, color, size, bold, italic, shadow);
    }

    @Override
    public void drawCenteredString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            fallbackRenderer.drawCenteredString(text, x, y, color, size, bold, italic, shadow);
            return;
        }
        if (canUseLaby()) {
            float scale = size / 14f;
            float w = com.hades.client.util.LabyRenderer.getStringWidth(text, scale);
            if (shadow) com.hades.client.util.LabyRenderer.drawStringWithShadow(text, x - w / 2f, y, color, scale);
            else com.hades.client.util.LabyRenderer.drawString(text, x - w / 2f, y, color, scale);
            return;
        }
        fallbackRenderer.drawCenteredString(text, x, y, color, size, bold, italic, shadow);
    }

    @Override
    public float getStringWidth(String text, float size, boolean bold, boolean italic) {
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            return fallbackRenderer.getStringWidth(text, size, bold, italic);
        }
        if (canUseLaby()) return com.hades.client.util.LabyRenderer.getStringWidth(text, size / 14f);
        return fallbackRenderer.getStringWidth(text, size, bold, italic);
    }

    @Override
    public float getFontHeight(float size, boolean bold, boolean italic) {
        if (com.hades.client.util.font.FontUtil.isFontFileLoaded()) {
            return fallbackRenderer.getFontHeight(size, bold, italic);
        }
        if (canUseLaby()) return com.hades.client.util.LabyRenderer.getFontHeight(size / 14f);
        return fallbackRenderer.getFontHeight(size, bold, italic);
    }

    @Override
    public boolean drawImage(String namespace, String path, float x, float y, float width, float height) {
        if (canUseLaby()) return LabyRenderer.drawImage(namespace, path, x, y, width, height);
        return fallbackRenderer.drawImage(namespace, path, x, y, width, height);
    }

    @Override
    public void enableScissor(float x, float y, float width, float height) {
        // We must do GL Scissor for underlying AWT Fonts / GL calls
        fallbackRenderer.enableScissor(x, y, width, height);

        if (canUseLaby() && LabyRenderer.getCurrentScreenContext() != null) {
            LabyRenderer.getCurrentScreenContext().canvas().scissor().push(x, y, width, height);
        }
    }

    @Override
    public void disableScissor() {
        if (canUseLaby() && LabyRenderer.getCurrentScreenContext() != null) {
            LabyRenderer.getCurrentScreenContext().canvas().scissor().pop();
        }
        fallbackRenderer.disableScissor();
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

    @Override
    public double getRenderPosX() {
        return fallbackRenderer.getRenderPosX();
    }

    @Override
    public double getRenderPosY() {
        return fallbackRenderer.getRenderPosY();
    }

    @Override
    public double getRenderPosZ() {
        return fallbackRenderer.getRenderPosZ();
    }
}

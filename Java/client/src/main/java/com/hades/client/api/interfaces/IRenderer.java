package com.hades.client.api.interfaces;

/**
 * Universal interface for rendering graphics and text.
 */
public interface IRenderer {
    
    // Rectangles
    void drawRect(float x, float y, float width, float height, int color);
    void drawRoundedRect(float x, float y, float width, float height, float radius, int color);
    void drawRoundedRect(float x, float y, float width, float height, float radTL, float radTR, float radBR, float radBL, int color);
    
    // Gradients
    void drawGradientRect(float x, float y, float width, float height, int colorTop, int colorBottom);
    void drawHorizontalGradient(float x, float y, float width, float height, int colorLeft, int colorRight);
    void drawRoundedGradientRect(float x, float y, float width, float height, float radius, int colorTop, int colorBottom);
    
    // Shadows
    void drawRoundedShadow(float x, float y, float width, float height, float radius, float shadowSize);
    
    // Text
    void drawString(String text, float x, float y, int color, float scale);
    void drawString(String text, float x, float y, int color);
    void drawStringWithShadow(String text, float x, float y, int color, float scale);
    void drawStringWithShadow(String text, float x, float y, int color);
    
    void drawCenteredString(String text, float x, float y, int color, float scale);
    void drawCenteredString(String text, float x, float y, int color);
    
    // Text Metrics
    float getStringWidth(String text, float scale);
    float getStringWidth(String text);
    float getFontHeight(float scale);
    float getFontHeight();
    
    // Explicit Styling Text Rendering
    void drawString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow);
    void drawCenteredString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow);
    float getStringWidth(String text, float size, boolean bold, boolean italic);
    float getFontHeight(float size, boolean bold, boolean italic);
    
    // Images
    boolean drawImage(String namespace, String path, float x, float y, float width, float height);
    
    // Scissor / Clipping
    void enableScissor(float x, float y, float width, float height);
    void disableScissor();
    void runWithScissor(float x, float y, float width, float height, Runnable action);

    // RenderManager Position
    double getRenderPosX();
    double getRenderPosY();
    double getRenderPosZ();
}

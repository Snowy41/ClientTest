package com.hades.client.util.render;

public interface IRenderer {

    // Solid primitives
    void drawRect(float x, float y, float width, float height, int color);

    void drawRoundedRect(float x, float y, float width, float height, float radius, int color);

    // Gradients
    void drawGradientRect(float x, float y, float width, float height, int colorTop, int colorBottom);

    void drawHorizontalGradient(float x, float y, float width, float height, int colorLeft, int colorRight);

    void drawRoundedGradientRect(float x, float y, float width, float height, float radius, int colorTop,
            int colorBottom);

    // Shadows
    void drawRoundedShadow(float x, float y, float width, float height, float radius, float shadowSize);

    // Text
    void drawString(String text, float x, float y, int color, float scale);

    float getStringWidth(String text, float scale);

    float getFontHeight(float scale);

    // Scissor / Clipping
    void enableScissor(float x, float y, float width, float height);

    void disableScissor();
}

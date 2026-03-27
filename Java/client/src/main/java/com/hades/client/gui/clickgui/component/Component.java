package com.hades.client.gui.clickgui.component;

/**
 * Base class for all GUI components. Supports animations via smooth
 * interpolation.
 */
public abstract class Component {
    protected float x, y, width, height;
    protected boolean visible = true;

    // Clip bounds for software scroll clipping (set by parent scroll container)
    protected float clipTop = Float.NEGATIVE_INFINITY;
    protected float clipBottom = Float.POSITIVE_INFINITY;

    /**
     * Set the visible clip region. Elements outside this range should not be
     * rendered.
     */
    public void setClipBounds(float clipTop, float clipBottom) {
        this.clipTop = clipTop;
        this.clipBottom = clipBottom;
    }

    /**
     * Check if an element at [elementY, elementY+elementH] has any overlap with the
     * clip region
     */
    protected boolean isInClipBounds(float elementY, float elementH) {
        return (elementY + elementH > clipTop) && (elementY < clipBottom);
    }

    protected static final float ANIMATION_SPEED = 0.15f;

    public abstract void render(int mouseX, int mouseY, float partialTicks);

    public void renderShadow(int mouseX, int mouseY, float partialTicks) {}

    public abstract void mouseClicked(int mouseX, int mouseY, int button);

    public abstract void mouseReleased(int mouseX, int mouseY, int button);

    public abstract void keyTyped(char typedChar, int keyCode);

    public boolean isHovered(int mouseX, int mouseY) {
        return visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Smooth interpolation for animations.
     */
    protected float smooth(float current, float target, float speed) {
        float diff = target - current;
        return current + diff * speed;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
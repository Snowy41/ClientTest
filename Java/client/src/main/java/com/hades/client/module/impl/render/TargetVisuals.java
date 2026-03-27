package com.hades.client.module.impl.render;

import com.hades.client.combat.TargetManager;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render3DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import com.hades.client.api.HadesAPI;

/**
 * TargetVisuals — renders advanced 3D visuals on the current TargetManager target.
 *
 * Implements a dynamic "Jello" style gradient cylinder that sweeps up and down
 * the target's body.
 */
public class TargetVisuals extends Module {

    // ── Settings ──
    private final NumberSetting circleSize = new NumberSetting(
            "Circle Size", "Radius of the ring", 0.4, 0.1, 1.0, 0.05);
    private final NumberSetting speed = new NumberSetting(
            "Speed", "Animation cycle time in ms", 1500.0, 500.0, 3000.0, 100.0);
    private final BooleanSetting depthTest = new BooleanSetting(
            "Depth Test", false);

    public TargetVisuals() {
        super("TargetVisuals", "Renders an animated Jello ring on the current target.", Category.RENDER, 0);
        register(circleSize);
        register(speed);
        register(depthTest);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    // ── Rendering ──

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        if (!HadesAPI.mc.isInGame()) return;

        com.hades.client.api.interfaces.IEntity target = TargetManager.getInstance().getTarget();
        if (target == null) return;

        if (HadesAPI.Player.getHealth() <= 0) return;

        try {
            renderJello(target);
        } catch (Exception e) {
            HadesLogger.get().error("[TargetVisuals] Render error", e);
        }
    }

    private void renderJello(com.hades.client.api.interfaces.IEntity target) {
        // ── Animation timing ──
        double everyTime = speed.getValue().doubleValue();
        double drawTime = (System.currentTimeMillis() % (long) everyTime);
        boolean drawMode = drawTime > (everyTime / 2);
        double drawPercent = drawTime / (everyTime / 2);

        if (!drawMode) {
            drawPercent = 1 - drawPercent;
        } else {
            drawPercent -= 1;
        }

        // EaseInOutQuad
        drawPercent = easeInOutQuad(drawPercent);

        // Calculate interpolated entity position
        float pt = HadesAPI.mc.partialTicks();

        double lastX = target.getLastTickX();
        double lastY = target.getLastTickY();
        double lastZ = target.getLastTickZ();
        double curX = target.getX();
        double curY = target.getY();
        double curZ = target.getZ();

        double renderPosX = HadesAPI.renderer.getRenderPosX();
        double renderPosY = HadesAPI.renderer.getRenderPosY();
        double renderPosZ = HadesAPI.renderer.getRenderPosZ();

        double radius = circleSize.getValue().doubleValue();
        double entityHeight = target.getHeight();

        double x = lastX + (curX - lastX) * pt - renderPosX;
        double y = (lastY + (curY - lastY) * pt - renderPosY) + entityHeight * drawPercent;
        double z = lastZ + (curZ - lastZ) * pt - renderPosZ;

        // Eased height for the gradient tail
        double eased = (entityHeight / 3.0) * ((drawPercent > 0.5) ? 1 - drawPercent : drawPercent) * (drawMode ? -1 : 1);

        // ── GL state setup ──
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        if (depthTest.getValue())
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_SMOOTH); // 7425

        // ── Draw the Jello ring ──
        for (int deg = 0; deg < 360; deg += 5) {
            // Hades orange color
            float[] color = getHadesColor();

            double x1 = x - Math.sin(deg * Math.PI / 180.0) * radius;
            double z1 = z + Math.cos(deg * Math.PI / 180.0) * radius;
            double x2 = x - Math.sin((deg - 5) * Math.PI / 180.0) * radius;
            double z2 = z + Math.cos((deg - 5) * Math.PI / 180.0) * radius;

            // Gradient quad: transparent top → colored bottom
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glColor4f(color[0], color[1], color[2], 0.0f);
            org.lwjgl.opengl.GL11.glVertex3d(x1, y + eased, z1);
            org.lwjgl.opengl.GL11.glVertex3d(x2, y + eased, z2);
            org.lwjgl.opengl.GL11.glColor4f(color[0], color[1], color[2], 1.0f);
            org.lwjgl.opengl.GL11.glVertex3d(x2, y, z2);
            org.lwjgl.opengl.GL11.glVertex3d(x1, y, z1);
            org.lwjgl.opengl.GL11.glEnd();

            // Bottom edge line for crispness
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
            org.lwjgl.opengl.GL11.glVertex3d(x2, y, z2);
            org.lwjgl.opengl.GL11.glVertex3d(x1, y, z1);
            org.lwjgl.opengl.GL11.glEnd();
        }

        // ── Restore GL state ──
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        org.lwjgl.opengl.GL11.glShadeModel(org.lwjgl.opengl.GL11.GL_FLAT); // 7424
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
        if (depthTest.getValue())
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LINE_SMOOTH);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glPopMatrix();
    }

    // ── Helpers ──

    /**
     * EaseInOutQuad interpolation for smooth bouncing.
     */
    private double easeInOutQuad(double t) {
        if (t < 0.5) {
            return 2 * t * t;
        } else {
            return 1 - Math.pow(-2 * t + 2, 2) / 2;
        }
    }

    /**
     * Returns the Hades accent color.
     * Base: Orange (#FF8C00).
     */
    private float[] getHadesColor() {
        float hue = 30f / 360f; // Orange hue
        float saturation = 0.95f;
        float brightness = 1.0f;

        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        return new float[]{r, g, b};
    }
}

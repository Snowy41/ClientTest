package com.hades.preview.modules;

import com.hades.preview.ConfigSchema;
import com.hades.preview.HeadlessGLContext;
import com.hades.preview.ModulePreviewRenderer;
import com.hades.preview.ScreenEntity;

import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

/**
 * Preview renderer for PlayerESP module.
 * Draws colored outlined rectangles around player entities with optional
 * name tags and health bars — exactly matching what the real client renders ingame.
 */
public class PlayerESPPreview implements ModulePreviewRenderer {

    @Override
    public String getModuleId() {
        return "PlayerESP";
    }

    @Override
    public void render(HeadlessGLContext ctx, Map<String, Object> config, List<ScreenEntity> entities) {
        // Parse config values
        int boxColor = parseColor(getConfigString(config, "boxColor", "#FF0000FF"));
        float outlineWidth = getConfigFloat(config, "outlineWidth", 2.0f);
        boolean showName = getConfigBool(config, "showName", true);
        boolean showHealth = getConfigBool(config, "showHealth", true);
        boolean fill = getConfigBool(config, "fill", false);
        float fillOpacity = getConfigFloat(config, "fillOpacity", 0.2f);

        float r = ((boxColor >> 16) & 0xFF) / 255.0f;
        float g = ((boxColor >> 8) & 0xFF) / 255.0f;
        float b = (boxColor & 0xFF) / 255.0f;
        float a = ((boxColor >> 24) & 0xFF) / 255.0f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        for (ScreenEntity entity : entities) {
            if (!"player".equalsIgnoreCase(entity.getType())) continue;

            float x = (float) entity.getX();
            float y = (float) entity.getY();
            float w = (float) entity.getWidth();
            float h = (float) entity.getHeight();

            // Fill (semi-transparent)
            if (fill) {
                GL11.glColor4f(r, g, b, a * fillOpacity);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(x, y);
                GL11.glVertex2f(x + w, y);
                GL11.glVertex2f(x + w, y + h);
                GL11.glVertex2f(x, y + h);
                GL11.glEnd();
            }

            // Outline
            GL11.glColor4f(r, g, b, a);
            GL11.glLineWidth(outlineWidth);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + w, y);
            GL11.glVertex2f(x + w, y + h);
            GL11.glVertex2f(x, y + h);
            GL11.glEnd();

            // Health bar (left side)
            if (showHealth) {
                double maxHealth = 20.0;
                double health = entity.getMetaDouble("health", maxHealth);
                float healthPct = (float) Math.min(1.0, health / maxHealth);

                float barWidth = 3;
                float barX = x - barWidth - 2;
                float barH = h * healthPct;
                float barY = y + h - barH;

                // Background
                GL11.glColor4f(0.2f, 0.2f, 0.2f, 0.7f);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(barX, y);
                GL11.glVertex2f(barX + barWidth, y);
                GL11.glVertex2f(barX + barWidth, y + h);
                GL11.glVertex2f(barX, y + h);
                GL11.glEnd();

                // Health fill (green to red gradient)
                float hR = 1.0f - healthPct;
                float hG = healthPct;
                GL11.glColor4f(hR, hG, 0.0f, 0.9f);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(barX, barY);
                GL11.glVertex2f(barX + barWidth, barY);
                GL11.glVertex2f(barX + barWidth, barY + barH);
                GL11.glVertex2f(barX, barY + barH);
                GL11.glEnd();
            }

            // Name tag (above the box) — simple rectangle placeholder
            // Real text rendering would use STB TrueType; for now draw a colored bar
            if (showName) {
                String name = entity.getMetaString("name", "Player");
                float tagW = name.length() * 6.0f; // approximate width
                float tagH = 12.0f;
                float tagX = x + (w - tagW) / 2.0f;
                float tagY = y - tagH - 4;

                // Tag background
                GL11.glColor4f(0.0f, 0.0f, 0.0f, 0.6f);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(tagX - 2, tagY - 1);
                GL11.glVertex2f(tagX + tagW + 2, tagY - 1);
                GL11.glVertex2f(tagX + tagW + 2, tagY + tagH + 1);
                GL11.glVertex2f(tagX - 2, tagY + tagH + 1);
                GL11.glEnd();

                // Tag accent underline
                GL11.glColor4f(r, g, b, a);
                GL11.glLineWidth(1.0f);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(tagX, tagY + tagH);
                GL11.glVertex2f(tagX + tagW, tagY + tagH);
                GL11.glEnd();
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @Override
    public ConfigSchema getSchema() {
        return new ConfigSchema("PlayerESP")
                .addSection("Appearance")
                .addColor("boxColor", "Box Color", "#FF0000FF")
                .addSlider("outlineWidth", "Outline Width", 2.0, 1.0, 5.0, 0.5)
                .addToggle("fill", "Fill Box", false)
                .addSlider("fillOpacity", "Fill Opacity", 0.2, 0.05, 1.0, 0.05)
                .addSection("Info")
                .addToggle("showName", "Show Name", true)
                .addToggle("showHealth", "Show Health Bar", true);
    }

    // ── Helpers ──

    private static int parseColor(String hex) {
        // "#RRGGBBAA" or "#RRGGBB"
        hex = hex.replace("#", "");
        if (hex.length() == 6) hex = hex + "FF";
        long val = Long.parseLong(hex, 16);
        // RGBA → ARGB for our renderer
        int r = (int) ((val >> 24) & 0xFF);
        int g = (int) ((val >> 16) & 0xFF);
        int b = (int) ((val >> 8) & 0xFF);
        int a = (int) (val & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String getConfigString(Map<String, Object> config, String key, String def) {
        Object v = config.get(key);
        return v != null ? v.toString() : def;
    }

    private static float getConfigFloat(Map<String, Object> config, String key, float def) {
        Object v = config.get(key);
        if (v instanceof Number) return ((Number) v).floatValue();
        return def;
    }

    private static boolean getConfigBool(Map<String, Object> config, String key, boolean def) {
        Object v = config.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return def;
    }
}

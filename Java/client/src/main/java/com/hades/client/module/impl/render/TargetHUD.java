package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.combat.TargetManager;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;

/**
 * Renders a premium TargetHUD panel near the crosshair showing the current
 * combat target's name, health bar (animated), and distance.
 *
 * Uses IRenderer exclusively — works on both LabyMod and Vanilla.
 */
public class TargetHUD extends Module {

    private final ModeSetting style = new ModeSetting("Style", "Modern", "Modern", "Minimal", "Classic");
    private final BooleanSetting showDistance = new BooleanSetting("Distance", true);
    private final BooleanSetting animated = new BooleanSetting("Animated", true);
    public final NumberSetting posX = new NumberSetting("X", 0, -500, 500, 1);
    public final NumberSetting posY = new NumberSetting("Y", 30, -500, 500, 1);

    // Animation state
    private float animatedHealth = 0f;
    private float panelAlpha = 0f;
    private String lastTargetName = "";

    // Panel dimensions
    private static final float PANEL_WIDTH = 150f;
    private static final float PANEL_HEIGHT = 48f;
    private static final float HEALTH_BAR_HEIGHT = 4f;
    private static final float CORNER_RADIUS = 6f;

    public TargetHUD() {
        super("TargetHUD", "Shows information about your target", Module.Category.HUD, 0);
        
        // Hide animation setting unless style is Modern
        animated.setVisibility(() -> "Modern".equals(style.getValue()));
        
        register(style);
        register(showDistance);
        register(animated);

        posX.setHidden(true);
        posY.setHidden(true);
        register(posX);
        register(posY);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (HadesAPI.Player.isNull()) return;

        IEntity target = TargetManager.getInstance().getTarget();
        float screenW = event.getScaledWidth();
        float screenH = event.getScaledHeight();

        // Smooth fade in/out
        float targetAlpha = (target != null) ? 1.0f : 0.0f;
        if (animated.getValue()) {
            panelAlpha += (targetAlpha - panelAlpha) * 0.15f;
            if (panelAlpha < 0.01f) panelAlpha = 0f;
        } else {
            panelAlpha = targetAlpha;
        }

        if (panelAlpha <= 0f) {
            animatedHealth = 0f;
            return;
        }

        // Calculate position (centered horizontally, offset below crosshair)
        float centerX = screenW / 2f + posX.getValue().floatValue();
        float centerY = screenH / 2f + posY.getValue().floatValue();
        float x = centerX - PANEL_WIDTH / 2f;
        float y = centerY;

        // Target data
        String name = target != null ? target.getName() : lastTargetName;
        if (target != null) lastTargetName = name;
        float health = target != null ? target.getHealth() : 0f;
        float maxHealth = target != null ? target.getMaxHealth() : 20f;
        if (maxHealth <= 0f) maxHealth = 20f;

        // Animated health interpolation
        if (animated.getValue()) {
            animatedHealth += (health - animatedHealth) * 0.12f;
        } else {
            animatedHealth = health;
        }

        float healthRatio = Math.max(0f, Math.min(1f, animatedHealth / maxHealth));

        // Alpha for fade
        int alpha = (int) (panelAlpha * 255f);
        if (alpha < 4) return;

        String currentStyle = style.getValue();
        if ("Modern".equals(currentStyle)) {
            renderModern(x, y, name, health, maxHealth, healthRatio, alpha, target);
        } else if ("Minimal".equals(currentStyle)) {
            renderMinimal(x, y, name, health, maxHealth, healthRatio, alpha, target);
        } else {
            renderClassic(x, y, name, health, maxHealth, healthRatio, alpha, target);
        }
    }

    // ═══════════════════════════════════════════════
    // Modern Style — Dark panel with gradient healthbar
    // ═══════════════════════════════════════════════

    private void renderModern(float x, float y, String name, float health, float maxHealth,
                              float healthRatio, int alpha, IEntity target) {

        int bgColor = applyAlpha(Theme.WINDOW_BG, alpha);
        int borderColor = applyAlpha(Theme.ACCENT_PRIMARY, (int)(alpha * 0.6f));

        // Panel background with shadow
        HadesAPI.Render.drawRoundedRect(x - 1, y - 1, PANEL_WIDTH + 2, PANEL_HEIGHT + 2, CORNER_RADIUS + 1, applyAlpha(0xFF000000, (int)(alpha * 0.4f)));
        HadesAPI.Render.drawRoundedRect(x, y, PANEL_WIDTH, PANEL_HEIGHT, CORNER_RADIUS, bgColor);

        // Top accent line
        HadesAPI.Render.drawRoundedRect(x, y, PANEL_WIDTH, 2f, CORNER_RADIUS, CORNER_RADIUS, 0, 0, borderColor);

        // Name
        if (name != null && !name.isEmpty()) {
            String displayName = name.length() > 16 ? name.substring(0, 15) + "…" : name;
            HadesAPI.Render.drawStringWithShadow(displayName, x + 8f, y + 7f,
                    applyAlpha(Theme.TEXT_PRIMARY, alpha), 1.0f);
        }

        // Health text (right-aligned)
        String healthText = String.format("%.1f", health) + " ❤";
        float healthTextWidth = HadesAPI.Render.getStringWidth(healthText, 0.85f);
        HadesAPI.Render.drawString(healthText, x + PANEL_WIDTH - healthTextWidth - 8f, y + 8f,
                applyAlpha(getHealthColor(healthRatio, alpha), alpha), 0.85f);

        // Distance
        if (showDistance.getValue() && target != null) {
            float dist = (float) com.hades.client.combat.CombatUtil.getDistanceToBox(HadesAPI.player, target);
            String distText = String.format("%.1fm", dist);
            HadesAPI.Render.drawString(distText, x + 8f, y + 22f,
                    applyAlpha(Theme.TEXT_MUTED, alpha), 0.85f);
        }

        // Health bar background
        float barY = y + PANEL_HEIGHT - HEALTH_BAR_HEIGHT - 6f;
        float barWidth = PANEL_WIDTH - 16f;
        HadesAPI.Render.drawRoundedRect(x + 8f, barY, barWidth, HEALTH_BAR_HEIGHT, 2f,
                applyAlpha(Theme.SLIDER_BG, alpha));

        // Health bar fill (gradient green → yellow → red)
        float fillWidth = barWidth * healthRatio;
        if (fillWidth > 1f) {
            int barColorStart = applyAlpha(getHealthColor(healthRatio, 255), alpha);
            int barColorEnd = applyAlpha(getHealthColorEnd(healthRatio, 255), alpha);
            HadesAPI.Render.drawRoundedGradientRect(x + 8f, barY, fillWidth, HEALTH_BAR_HEIGHT, 2f,
                    barColorStart, barColorEnd);
        }
    }

    // ═══════════════════════════════════════════════
    // Minimal Style — Clean single-line bar
    // ═══════════════════════════════════════════════

    private void renderMinimal(float x, float y, String name, float health, float maxHealth,
                               float healthRatio, int alpha, IEntity target) {

        float minimalWidth = PANEL_WIDTH;
        float minimalHeight = 22f;

        int bgColor = applyAlpha(0xFF0C0C0E, alpha);
        HadesAPI.Render.drawRoundedRect(x, y, minimalWidth, minimalHeight, 4f, bgColor);

        // Name (left side)
        if (name != null) {
            String displayName = name.length() > 12 ? name.substring(0, 11) + "…" : name;
            HadesAPI.Render.drawString(displayName, x + 6f, y + 4f,
                    applyAlpha(Theme.TEXT_PRIMARY, alpha), 0.85f);
        }

        // Health value (right side)
        String hp = String.format("%.0f", health);
        float hpW = HadesAPI.Render.getStringWidth(hp, 0.85f);
        HadesAPI.Render.drawString(hp, x + minimalWidth - hpW - 6f, y + 4f,
                applyAlpha(getHealthColor(healthRatio, 255), alpha), 0.85f);

        // Thin bar at bottom
        float barY = y + minimalHeight - 3f;
        HadesAPI.Render.drawRect(x, barY, minimalWidth, 3f, applyAlpha(0xFF1A1A1E, alpha));
        float fillWidth = minimalWidth * healthRatio;
        if (fillWidth > 0.5f) {
            HadesAPI.Render.drawRect(x, barY, fillWidth, 3f, applyAlpha(getHealthColor(healthRatio, 255), alpha));
        }
    }

    // ═══════════════════════════════════════════════
    // Classic Style — Traditional box layout
    // ═══════════════════════════════════════════════

    private void renderClassic(float x, float y, String name, float health, float maxHealth,
                               float healthRatio, int alpha, IEntity target) {

        float classicHeight = 38f;
        int bgColor = applyAlpha(0xCC000000, alpha);
        HadesAPI.Render.drawRect(x, y, PANEL_WIDTH, classicHeight, bgColor);

        // Border
        HadesAPI.Render.drawRect(x, y, PANEL_WIDTH, 1f, applyAlpha(Theme.ACCENT_PRIMARY, alpha));

        // Name
        if (name != null) {
            HadesAPI.Render.drawStringWithShadow(name.length() > 16 ? name.substring(0, 15) : name,
                    x + 4f, y + 4f, applyAlpha(0xFFFFFFFF, alpha));
        }

        // Health text
        String healthStr = String.format("%.1f / %.1f", health, maxHealth);
        HadesAPI.Render.drawString(healthStr, x + 4f, y + 16f,
                applyAlpha(getHealthColor(healthRatio, 255), alpha), 0.85f);

        // Bar
        float barY = y + classicHeight - 6f;
        HadesAPI.Render.drawRect(x + 4f, barY, PANEL_WIDTH - 8f, 4f, applyAlpha(0xFF333333, alpha));
        float fillWidth = (PANEL_WIDTH - 8f) * healthRatio;
        if (fillWidth > 0.5f) {
            HadesAPI.Render.drawRect(x + 4f, barY, fillWidth, 4f, applyAlpha(getHealthColor(healthRatio, 255), alpha));
        }
    }

    // ═══════════════════════════════════════════════
    // Color helpers
    // ═══════════════════════════════════════════════

    private int getHealthColor(float ratio, int alpha) {
        if (ratio > 0.66f) return HadesAPI.Render.color(80, 220, 100, alpha);   // Green
        if (ratio > 0.33f) return HadesAPI.Render.color(255, 200, 60, alpha);   // Yellow
        return HadesAPI.Render.color(255, 70, 60, alpha);                       // Red
    }

    private int getHealthColorEnd(float ratio, int alpha) {
        if (ratio > 0.66f) return HadesAPI.Render.color(60, 180, 80, alpha);
        if (ratio > 0.33f) return HadesAPI.Render.color(230, 170, 40, alpha);
        return HadesAPI.Render.color(200, 50, 40, alpha);
    }

    private int applyAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}

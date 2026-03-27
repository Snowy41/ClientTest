package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HudAvoidanceUtil;
import com.hades.client.api.HadesAPI;

import java.lang.reflect.Method;

public class HUD extends Module {

    private final BooleanSetting showWatermark = new BooleanSetting("Watermark", true);
    private final BooleanSetting showFPS = new BooleanSetting("FPS", true);
    private final BooleanSetting showBPS = new BooleanSetting("BPS", true);
    private final BooleanSetting showCoords = new BooleanSetting("Coords", true);

    // Hidden positional settings for the HUD Editor
    public final NumberSetting watermarkX = new NumberSetting("Watermark X", 8, 0, 4000, 1);
    public final NumberSetting watermarkY = new NumberSetting("Watermark Y", 5, 0, 4000, 1);
    public final NumberSetting statsX = new NumberSetting("Stats X", 8, 0, 4000, 1);
    public final NumberSetting statsY = new NumberSetting("Stats Y", 60, 0, 4000, 1);

    private Method getDebugFPSMethod;

    public HUD() {
        super("HUD", "Displays information on screen", Module.Category.RENDER, 0);
        this.register(showWatermark);
        this.register(showFPS);
        this.register(showBPS);
        this.register(showCoords);

        // Register but hide them from ClickGUI since they are edited via dragging
        watermarkX.setHidden(true);
        watermarkY.setHidden(true);
        statsX.setHidden(true);
        statsY.setHidden(true);

        this.register(watermarkX);
        this.register(watermarkY);
        this.register(statsX);
        this.register(statsY);

        this.setEnabled(true);

        try {
            Class<?> mcClass = com.hades.client.util.ReflectionUtil.findClass("ave", "net.minecraft.client.Minecraft");
            if (mcClass != null) {
                try {
                    getDebugFPSMethod = mcClass.getMethod("getDebugFPS");
                } catch (NoSuchMethodException e) {
                    getDebugFPSMethod = mcClass.getMethod("O");
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (HadesAPI.Player.isNull())
            return;

        float screenW = event.getScaledWidth();
        float screenH = event.getScaledHeight();

        // 1. Watermark (HADES Logo Image)
        if (showWatermark.getValue()) {
            float logoSize = 48f;

            float customX = watermarkX.getValue().floatValue();
            float customY = watermarkY.getValue().floatValue();

            // Per-element avoidance checking against absolute screen-mapped LabyMod widgets
            float safeY = HudAvoidanceUtil.findSafeY(customX, customY, logoSize, logoSize, screenW, screenH);

            boolean logoRendered = HadesAPI.Render.drawImage("hades", "pictures/logo.png", customX, safeY, logoSize,
                    logoSize);

            // Fallback rendering standard text if the PNG resource isn't found
            if (!logoRendered) {
                String text = "HADES";
                for (float i = 0.5f; i <= 2f; i += 0.5f) {
                    HadesAPI.Render.drawString(text, customX - i, safeY,
                            HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 30), 1.4f);
                    HadesAPI.Render.drawString(text, customX + i, safeY,
                            HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 30), 1.4f);
                    HadesAPI.Render.drawString(text, customX, safeY - i,
                            HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 30), 1.4f);
                    HadesAPI.Render.drawString(text, customX, safeY + i,
                            HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 30), 1.4f);
                }
                HadesAPI.Render.drawStringWithShadow(text, customX, safeY, Theme.ACCENT_PRIMARY, 1.4f);
            }
        }

        // 2. Statistics Box (Sleek dark pill)
        if (showFPS.getValue() || showBPS.getValue() || showCoords.getValue()) {
            StringBuilder stats = new StringBuilder();

            if (showFPS.getValue()) {
                stats.append("FPS: ").append(getFPS());
            }
            if (showBPS.getValue()) {
                if (stats.length() > 0)
                    stats.append("  |  ");
                double bps = Math.hypot(HadesAPI.Player.getMotionX(), HadesAPI.Player.getMotionZ()) * 20.0;
                stats.append(String.format("BPS: %.1f", bps));
            }
            if (showCoords.getValue()) {
                if (stats.length() > 0)
                    stats.append("  |  ");
                stats.append(String.format("XYZ: %d, %d, %d",
                        (int) HadesAPI.Player.getX(),
                        (int) HadesAPI.Player.getY(),
                        (int) HadesAPI.Player.getZ()));
            }

            String statText = stats.toString().trim();
            if (!statText.isEmpty()) {
                float width = HadesAPI.Render.getStringWidth(statText, 0.95f) + 14f;
                float height = HadesAPI.Render.getFontHeight(0.95f) + 10f;

                float customX = statsX.getValue().floatValue();
                float customY = statsY.getValue().floatValue();

                // Per-element avoidance for stats box
                float safeY = HudAvoidanceUtil.findSafeY(customX, customY, width, height, screenW, screenH);

                // Sleek dark rounded container
                HadesAPI.Render.drawRoundedRect(customX, safeY, width, height, 6f,
                        HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 240));

                // Accent border line on the left
                HadesAPI.Render.drawRoundedRect(customX, safeY, 2f, height, 2f, Theme.ACCENT_PRIMARY);

                HadesAPI.Render.drawString(statText, customX + 8f, safeY + 5f, Theme.TEXT_PRIMARY, 0.95f);
            }
        }
    }

    private int getFPS() {
        if (getDebugFPSMethod != null) {
            try {
                return (int) getDebugFPSMethod.invoke(null);
            } catch (Exception e) {
                // Return 0 if method call fails
            }
        }
        return 0;
    }
}

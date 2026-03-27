package com.hades.client.gui.clickgui.component.settings;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.setting.NumberSetting;

/**
 * Modern slider with rounded track, gradient fill, and floating knob.
 */
public class SliderComponent extends SettingComponent<NumberSetting> {

    private boolean dragging;
    private float animatedPercentage = 0f;
    private float hoverAnimation = 0f;

    public SliderComponent(NumberSetting setting) {
        super(setting, Theme.SETTING_HEIGHT + 14);
    }

    @Override
    public void renderShadow(int mouseX, int mouseY, float partialTicks) {
        if (!isInClipBounds(y, height))
            return;

        float clampedHY = Math.max(y, clipTop);
        float clampedHBottom = Math.min(y + height, clipBottom);
        float clampedHH = clampedHBottom - clampedHY;

        if (clampedHH > 0) {
            // Removed drop shadow
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!isInClipBounds(y, height))
            return;

        if (dragging) {
            float sliderX = x + Theme.SETTING_PADDING;
            float sliderWidth = width - Theme.SETTING_PADDING * 2;
            double percent = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderWidth));
            double value = setting.getMin() + percent * (setting.getMax() - setting.getMin());
            setting.setValue(value);
        }

        boolean hovered = isHovered(mouseX, mouseY);
        hoverAnimation = smooth(hoverAnimation, hovered || dragging ? 1f : 0f, ANIMATION_SPEED);

        // Smooth slide animation
        animatedPercentage = smooth(animatedPercentage, (float) setting.getPercentage(), ANIMATION_SPEED * 1.5f);

        float clampedHY = Math.max(y, clipTop);
        float clampedHBottom = Math.min(y + height, clipBottom);
        float clampedHH = clampedHBottom - clampedHY;

        if (clampedHH > 0) {
            int baseC = HadesAPI.Render.color(18, 18, 22, 255);
            int hoverC = HadesAPI.Render.color(26, 26, 30, 255);
            int bgC = HadesAPI.Render.lerpColor(baseC, hoverC, hoverAnimation);
            HadesAPI.Render.drawRoundedRect(x + 2, clampedHY, width - 4, clampedHH, 4f, bgC);
        }

        // Label + Value
        String label = setting.getName();
        String valueStr = formatValue(setting.getValue(), setting.getIncrement());

        int labelColor = HadesAPI.Render.lerpColor(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, hoverAnimation);
        HadesAPI.Render.drawString(label, x + Theme.SETTING_PADDING, y + 4, labelColor);

        // Brighten value text when interacting
        int valColor = HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_ACCENT, hoverAnimation);
        HadesAPI.Render.drawString(valueStr,
                x + width - Theme.SETTING_PADDING - HadesAPI.Render.getStringWidth(valueStr),
                y + 4, valColor);

        // Slider track
        float elementClipTop = clipTop + 2f;
        float trackY = y + Theme.SETTING_HEIGHT + 2;
        float trackX = x + Theme.SETTING_PADDING;
        float trackWidth = width - Theme.SETTING_PADDING * 2;

        // Background track (slightly lighter when hovered)
        int bgTrackColor = HadesAPI.Render.lerpColor(Theme.SLIDER_BG, HadesAPI.Render.color(45, 45, 50),
                hoverAnimation);

        float clampedTY = Math.max(trackY, elementClipTop);
        float clampedTBottom = Math.min(trackY + Theme.SLIDER_HEIGHT, clipBottom);
        float clampedTH = clampedTBottom - clampedTY;

        if (clampedTH > 0) {
            float radTop = (clampedTY > trackY + 0.1f) ? 0f : Theme.SLIDER_HEIGHT / 2f;
            float radBot = (clampedTBottom < trackY + Theme.SLIDER_HEIGHT - 0.1f) ? 0f : Theme.SLIDER_HEIGHT / 2f;
            HadesAPI.Render.drawRoundedRect(trackX, clampedTY, trackWidth, clampedTH, radTop, radTop, radBot, radBot,
                    bgTrackColor);
        }

        // Fill track (gradient)
        float fillWidth = Math.max(Theme.SLIDER_HEIGHT, trackWidth * animatedPercentage);
        if (clampedTH > 0) {
            float radTop = (clampedTY > trackY + 0.1f) ? 0f : Theme.SLIDER_HEIGHT / 2f;
            float radBot = (clampedTBottom < trackY + Theme.SLIDER_HEIGHT - 0.1f) ? 0f : Theme.SLIDER_HEIGHT / 2f;
            HadesAPI.Render.drawRoundedRect(trackX, clampedTY, fillWidth, clampedTH, radTop, radTop, radBot, radBot,
                    Theme.SLIDER_FILL);
        }

        // Knob is removed for an ultra-sleek, minimalist "fill bar" aesthetic.
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            dragging = true;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;
    }

    private String formatValue(double value, double increment) {
        if (increment >= 1)
            return String.valueOf((int) value);
        if (increment >= 0.1)
            return String.format("%.1f", value);
        return String.format("%.2f", value);
    }
}

package com.hades.client.gui.clickgui.component.settings;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.setting.BooleanSetting;

/**
 * Modern toggle switch (iOS style) for boolean settings.
 */
public class BooleanComponent extends SettingComponent<BooleanSetting> {

    private float toggleAnimation = 0f; // 0 = off, 1 = on
    private float hoverAnimation = 0f;

    public BooleanComponent(BooleanSetting setting) {
        super(setting, Theme.SETTING_HEIGHT);
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

        // Animate toggle
        float target = setting.getValue() ? 1f : 0f;
        toggleAnimation = smooth(toggleAnimation, target, ANIMATION_SPEED);

        // Hover animation
        boolean hovered = isHovered(mouseX, mouseY);
        hoverAnimation = smooth(hoverAnimation, hovered ? 1f : 0f, ANIMATION_SPEED);

        float clampedHY = Math.max(y, clipTop);
        float clampedHBottom = Math.min(y + height, clipBottom);
        float clampedHH = clampedHBottom - clampedHY;

        if (clampedHH > 0) {
            int baseC = HadesAPI.Render.color(18, 18, 22, 255);
            int hoverC = HadesAPI.Render.color(26, 26, 30, 255);
            int bgC = HadesAPI.Render.lerpColor(baseC, hoverC, hoverAnimation);
            HadesAPI.Render.drawRoundedRect(x + 2, clampedHY, width - 4, clampedHH, 4f, bgC);
        }

        // Label
        int labelColor = HadesAPI.Render.lerpColor(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, hoverAnimation);
        HadesAPI.Render.drawString(setting.getName(), x + Theme.SETTING_PADDING,
                y + (height - HadesAPI.Render.getFontHeight()) / 2f, labelColor);

        // Toggle switch
        float toggleX = x + width - Theme.TOGGLE_WIDTH - Theme.SETTING_PADDING;
        float toggleY = y + (height - Theme.TOGGLE_HEIGHT) / 2f;

        // Track
        int trackColor = HadesAPI.Render.lerpColor(Theme.TOGGLE_OFF, Theme.TOGGLE_ON, toggleAnimation);
        float clampedTY = Math.max(toggleY, clipTop);
        float clampedTBottom = Math.min(toggleY + Theme.TOGGLE_HEIGHT, clipBottom);
        float clampedTH = clampedTBottom - clampedTY;

        if (clampedTH > 0) {
            float radTop = (clampedTY > toggleY + 0.1f) ? 0f : Theme.TOGGLE_HEIGHT / 2f;
            float radBot = (clampedTBottom < toggleY + Theme.TOGGLE_HEIGHT - 0.1f) ? 0f : Theme.TOGGLE_HEIGHT / 2f;
            HadesAPI.Render.drawRoundedRect(toggleX, clampedTY, Theme.TOGGLE_WIDTH, clampedTH, radTop, radTop, radBot,
                    radBot, trackColor);
        }

        // Knob
        int darkKnob = HadesAPI.Render.color(20, 20, 24);
        int lightKnob = HadesAPI.Render.color(220, 220, 225);
        int knobColor = HadesAPI.Render.lerpColor(lightKnob, darkKnob, toggleAnimation);

        float knobSize = Theme.TOGGLE_HEIGHT - 4;
        float knobX = toggleX + 2 + toggleAnimation * (Theme.TOGGLE_WIDTH - knobSize - 4);
        float knobY = toggleY + 2;

        float clampedKY = Math.max(knobY, clipTop);
        float clampedKBottom = Math.min(knobY + knobSize, clipBottom);
        float clampedKH = clampedKBottom - clampedKY;

        if (clampedKH > 0) {
            float radTop = (clampedKY > knobY + 0.1f) ? 0f : knobSize / 2f;
            float radBot = (clampedKBottom < knobY + knobSize - 0.1f) ? 0f : knobSize / 2f;
            HadesAPI.Render.drawRoundedRect(knobX, clampedKY, knobSize, clampedKH, radTop, radTop, radBot, radBot,
                    knobColor);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            setting.setValue(!setting.getValue());
        }
    }
}

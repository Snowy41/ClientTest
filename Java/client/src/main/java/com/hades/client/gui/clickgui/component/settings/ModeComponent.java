package com.hades.client.gui.clickgui.component.settings;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.setting.ModeSetting;

/**
 * Mode selector with cycle-on-click and visual indicator.
 */
public class ModeComponent extends SettingComponent<ModeSetting> {

    private float hoverAnimation = 0f;

    public ModeComponent(ModeSetting setting) {
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

        // Current mode (right-aligned, pill-shaped background)
        String mode = setting.getValue();
        float modeWidth = HadesAPI.Render.getStringWidth(mode) + 12;
        float modeX = x + width - modeWidth - Theme.SETTING_PADDING;
        float modeY = y + (height - 16) / 2f;

        // Pill smoothly brightens on hover
        int basePillColor = HadesAPI.Render.color(24, 24, 30);
        int hoverPillColor = HadesAPI.Render.color(35, 35, 45);
        int pillColor = HadesAPI.Render.lerpColor(basePillColor, hoverPillColor, hoverAnimation);

        float clampedPY = Math.max(modeY, clipTop);
        float clampedPBottom = Math.min(modeY + 16, clipBottom);
        float clampedPH = clampedPBottom - clampedPY;

        if (clampedPH > 0) {
            if (clampedPH < 16) {
                HadesAPI.Render.drawRect(modeX, clampedPY, modeWidth, clampedPH, pillColor);
            } else {
                HadesAPI.Render.drawRoundedRect(modeX, clampedPY, modeWidth, clampedPH, 4f, pillColor);
            }
        }

        HadesAPI.Render.drawCenteredString(mode, modeX + modeWidth / 2f,
                modeY + (16 - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                setting.cycle();
            }
        }
    }
}

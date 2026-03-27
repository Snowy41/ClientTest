package com.hades.client.gui.clickgui.component.settings;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.setting.MultiSelectSetting;

public class MultiSelectComponent extends SettingComponent<MultiSelectSetting> {

    private float hoverAnimation = 0f;

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting, Theme.SETTING_HEIGHT);
    }

    @Override
    public void renderShadow(int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!isInClipBounds(y, height)) return;

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

        int labelColor = HadesAPI.Render.lerpColor(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, hoverAnimation);
        HadesAPI.Render.drawString(setting.getName(), x + Theme.SETTING_PADDING, y + (height - HadesAPI.Render.getFontHeight()) / 2f, labelColor);

        String valuePreview;
        if (setting.getValue() == null || setting.getValue().isEmpty()) {
            valuePreview = "None";
        } else if (setting.getValue().size() == 1) {
            valuePreview = "1 Selected";
        } else {
            valuePreview = setting.getValue().size() + " Selected";
        }

        float previewWidth = HadesAPI.Render.getStringWidth(valuePreview) + 12;
        float modeX = x + width - previewWidth - Theme.SETTING_PADDING;
        float modeY = y + (height - 16) / 2f;

        int basePillColor = HadesAPI.Render.color(24, 24, 30);
        int hoverPillColor = HadesAPI.Render.color(35, 35, 45);
        int pillColor = HadesAPI.Render.lerpColor(basePillColor, hoverPillColor, hoverAnimation);

        HadesAPI.Render.drawRoundedRect(modeX, modeY, previewWidth, 16, 4f, pillColor);
        HadesAPI.Render.drawCenteredString(valuePreview, modeX + previewWidth / 2f, modeY + (16 - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            ((com.hades.client.gui.clickgui.ClickGUI) HadesClient.getInstance().getClickGUI()).getMultiSelectScreen().open(setting);
        }
    }
}

package com.hades.client.gui.clickgui.component;

import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.api.HadesAPI;
import java.util.function.Consumer;

public class TabSelector extends Component {
    private Module.Category activeCategory;
    private final Consumer<Module.Category> onSelect;

    public TabSelector(Module.Category initialCategory, Consumer<Module.Category> onSelect) {
        this.activeCategory = initialCategory;
        this.onSelect = onSelect;
    }

    public void setActiveCategory(Module.Category category) {
        this.activeCategory = category;
    }

    private float[] hoverAnimations;

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        Module.Category[] categories = java.util.Arrays.stream(Module.Category.values())
                .filter(c -> c != Module.Category.HUD)
                .toArray(Module.Category[]::new);
        float tabWidth = width / categories.length;

        if (hoverAnimations == null || hoverAnimations.length != categories.length) {
            hoverAnimations = new float[categories.length];
        }

        for (int i = 0; i < categories.length; i++) {
            Module.Category cat = categories[i];
            float tx = x + i * tabWidth;
            boolean isActive = cat == activeCategory;
            boolean isHovered = mouseX >= tx && mouseX <= tx + tabWidth
                    && mouseY >= y && mouseY <= y + Theme.TAB_HEIGHT;

            // Animate Hover
            float hoverTarget = isHovered ? 1f : 0f;
            hoverAnimations[i] = smooth(hoverAnimations[i], hoverTarget, ANIMATION_SPEED);

            float intensity = Math.max(hoverAnimations[i], isActive ? 1f : 0f);

            // Default shadow for all tabs
            // Removed drop shadow

            // Default background to prevent hollow shadow
            int baseTabColor = HadesAPI.Render.color(18, 18, 22, 255);
            int hoverTabColor = HadesAPI.Render.color(28, 28, 34, 255);
            int activeTabColor = HadesAPI.Render.color(35, 35, 42, 255);

            int tabBg = isActive ? activeTabColor : HadesAPI.Render.lerpColor(baseTabColor, hoverTabColor, hoverAnimations[i]);

            HadesAPI.Render.drawRoundedRect(tx + 2, y + 2, tabWidth - 4, Theme.TAB_HEIGHT - 4, 4f, tabBg);

            if (isActive) {
                // Subtle accent line under text
                HadesAPI.Render.drawRect(tx + tabWidth/2f - 12f, y + Theme.TAB_HEIGHT - 4, 24f, 2f, Theme.ACCENT_PRIMARY);
            }

            String iconFile = "pictures/icons/" + cat.getIconName();
            float iconSize = 14f;
            String displayText = cat.getDisplayName();
            float textWidth = HadesAPI.Render.getStringWidth(displayText, 13f, true, false);
            float totalWidth = iconSize + 4 + textWidth;

            float startX = tx + (tabWidth - totalWidth) / 2f;
            float iconY = y + (Theme.TAB_HEIGHT - iconSize) / 2f;

            // Smooth text color
            int textColor;
            if (isActive) {
                textColor = Theme.TEXT_PRIMARY;
            } else {
                textColor = HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_SECONDARY, hoverAnimations[i]);
            }

            // Draw image with color overlay? HadesAPI.Render.drawImage doesn't support
            // color tinting yet, so we just draw it.
            boolean drawn = HadesAPI.Render.drawImage("hades", iconFile, startX, iconY, iconSize, iconSize);

            if (drawn) {
                HadesAPI.Render.drawString(displayText, startX + iconSize + 4,
                        y + (Theme.TAB_HEIGHT - HadesAPI.Render.getFontHeight(13f, true, false)) / 2f,
                        textColor, 13f, true, false, false);
            } else {
                HadesAPI.Render.drawCenteredString(displayText,
                        tx + tabWidth / 2f,
                        y + (Theme.TAB_HEIGHT - HadesAPI.Render.getFontHeight(13f, true, false)) / 2f,
                        textColor, 13f, true, false, false);
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0)
            return;

        Module.Category[] categories = java.util.Arrays.stream(Module.Category.values())
                .filter(c -> c != Module.Category.HUD)
                .toArray(Module.Category[]::new);
        float tabWidth = width / categories.length;

        for (int i = 0; i < categories.length; i++) {
            float tx = x + i * tabWidth;
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= y && mouseY <= y + Theme.TAB_HEIGHT) {
                if (onSelect != null) {
                    onSelect.accept(categories[i]);
                }
                break;
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}

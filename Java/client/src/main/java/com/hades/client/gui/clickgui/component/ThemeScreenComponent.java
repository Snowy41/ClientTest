package com.hades.client.gui.clickgui.component;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;

import java.util.Arrays;
import java.util.List;

public class ThemeScreenComponent extends Component {

    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;

    private static class ThemePreset {
        String name;
        int primary, secondary, gradStart, gradEnd;

        ThemePreset(String name, int p, int s, int gs, int ge) {
            this.name = name;
            this.primary = p;
            this.secondary = s;
            this.gradStart = gs;
            this.gradEnd = ge;
        }
    }

    private final List<ThemePreset> presets = Arrays.asList(
            new ThemePreset("Orange (Default)", 
                    HadesAPI.Render.color(255, 140, 0),
                    HadesAPI.Render.color(255, 180, 0),
                    HadesAPI.Render.color(255, 120, 0),
                    HadesAPI.Render.color(255, 170, 0)),
            new ThemePreset("Cherry Red", 
                    HadesAPI.Render.color(235, 60, 80),
                    HadesAPI.Render.color(255, 100, 120),
                    HadesAPI.Render.color(230, 40, 60),
                    HadesAPI.Render.color(255, 80, 100)),
            new ThemePreset("Ocean Blue", 
                    HadesAPI.Render.color(40, 160, 255),
                    HadesAPI.Render.color(80, 200, 255),
                    HadesAPI.Render.color(20, 140, 255),
                    HadesAPI.Render.color(60, 180, 255)),
            new ThemePreset("Emerald Green", 
                    HadesAPI.Render.color(40, 210, 100),
                    HadesAPI.Render.color(80, 240, 140),
                    HadesAPI.Render.color(20, 190, 80),
                    HadesAPI.Render.color(60, 220, 120)),
            new ThemePreset("Amethyst Purple", 
                    HadesAPI.Render.color(160, 80, 255),
                    HadesAPI.Render.color(200, 120, 255),
                    HadesAPI.Render.color(140, 60, 255),
                    HadesAPI.Render.color(180, 100, 255)),
            new ThemePreset("Cyberpunk Pink", 
                    HadesAPI.Render.color(255, 40, 180),
                    HadesAPI.Render.color(255, 80, 220),
                    HadesAPI.Render.color(250, 20, 160),
                    HadesAPI.Render.color(255, 60, 200))
    );

    private float[] hoverAnims = new float[presets.size()];
    private static final float ITEM_HEIGHT = 44f;
    private static final float ITEM_MARGIN = 6f;

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        scrollOffset = smooth(scrollOffset, targetScrollOffset, 0.6f);

        // Header
        float headerY = y + 4;
        HadesAPI.Render.drawString("Theme Configurator", x + 6, headerY, Theme.TEXT_PRIMARY, 15f, false, false, false);
        HadesAPI.Render.drawString("Select a color scheme", x + 6, headerY + 16, Theme.TEXT_MUTED, 11f, false, false, false);

        // List
        float listY = headerY + 36;
        float listHeight = height - (listY - y) - 4;
        float currentY = listY - scrollOffset;

        // Clamp scissor to both the list area and the parent's clip bounds
        float scissorTop = Math.max(listY, clipTop);
        float scissorBottom = Math.min(listY + listHeight, clipBottom);
        float scissorHeight = scissorBottom - scissorTop;

        if (scissorHeight > 0) {
            float cy = currentY;
            for (int i = 0; i < presets.size(); i++) {
                ThemePreset preset = presets.get(i);
                float itemY = cy + i * (ITEM_HEIGHT + ITEM_MARGIN);

                // Skip items completely outside visible area
                if (itemY + ITEM_HEIGHT < scissorTop || itemY > scissorBottom) continue;

                renderPreset(preset, i, x + 6, itemY, width - 12, mouseX, mouseY, scissorTop, scissorBottom);
            }
        }

        // Scrollbar
        float totalContentHeight = presets.size() * (ITEM_HEIGHT + ITEM_MARGIN);
        if (totalContentHeight > listHeight) {
            float scrollbarHeight = (listHeight / totalContentHeight) * listHeight;
            float scrollbarY = listY + (scrollOffset / totalContentHeight) * listHeight;
            HadesAPI.Render.drawRoundedRect(x + width - 8, scrollbarY, 3f, scrollbarHeight, 1.5f, Theme.SCROLLBAR_THUMB);
        }
    }

    private void renderPreset(ThemePreset preset, int index, float itemX, float itemY, float itemWidth,
                              int mouseX, int mouseY, float cTop, float cBottom) {
        boolean hovered = mouseX >= itemX && mouseX <= itemX + itemWidth && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;
        boolean isActive = Theme.ACCENT_PRIMARY == preset.primary;

        hoverAnims[index] = smooth(hoverAnims[index], hovered ? 1f : 0f, ANIMATION_SPEED);

        // Software-clamp the item background to clip bounds (same approach as ModuleButton)
        float drawY = Math.max(itemY, cTop);
        float drawBottom = Math.min(itemY + ITEM_HEIGHT, cBottom);
        float drawH = drawBottom - drawY;
        if (drawH <= 0) return;

        int bgColor = isActive ? HadesAPI.Render.color(35, 35, 40) : (hovered ? HadesAPI.Render.color(30, 30, 35) : HadesAPI.Render.color(24, 24, 28));
        HadesAPI.Render.drawRoundedRect(itemX, drawY, itemWidth, drawH, 5f, bgColor);

        if (isActive) {
            float borderY = Math.max(itemY - 1, cTop);
            float borderBottom = Math.min(itemY + ITEM_HEIGHT + 1, cBottom);
            float borderH = borderBottom - borderY;
            if (borderH > 0) {
                HadesAPI.Render.drawRoundedRect(itemX - 1, borderY, itemWidth + 2, borderH, 6f, HadesAPI.Render.colorWithAlpha(preset.primary, 100));
                // Redraw inner bg over the border
                float innerY = Math.max(itemY, cTop);
                float innerBottom = Math.min(itemY + ITEM_HEIGHT, cBottom);
                float innerH = innerBottom - innerY;
                if (innerH > 0) {
                    HadesAPI.Render.drawRoundedRect(itemX, innerY, itemWidth, innerH, 5f, bgColor);
                }
            }
        }

        // Gradient preview circle/pill — software clamp
        float previewSize = 24f;
        float previewX = itemX + 10f;
        float previewRawY = itemY + (ITEM_HEIGHT - previewSize) / 2f;
        float pvY = Math.max(previewRawY, cTop);
        float pvBottom = Math.min(previewRawY + previewSize, cBottom);
        float pvH = pvBottom - pvY;
        if (pvH > 0) {
            HadesAPI.Render.drawRoundedGradientRect(previewX, pvY, previewSize, pvH, previewSize / 2f, preset.gradStart, preset.gradEnd);
        }

        // Name — use scissor for text only
        float nameY = itemY + (ITEM_HEIGHT - HadesAPI.Render.getFontHeight(13.5f, false, false)) / 2f;
        if (nameY + HadesAPI.Render.getFontHeight(13.5f, false, false) > cTop && nameY < cBottom) {
            HadesAPI.Render.runWithScissor(itemX, cTop, itemWidth, cBottom - cTop, () -> {
                HadesAPI.Render.drawString(preset.name, previewX + previewSize + 12f, nameY, isActive ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY, 13.5f, false, false, false);
            });
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return;

        float headerY = y + 4;
        float listY = headerY + 36;
        float currentY = listY - scrollOffset;

        for (int i = 0; i < presets.size(); i++) {
            float itemY = currentY + i * (ITEM_HEIGHT + ITEM_MARGIN);
            if (mouseX >= x + 6 && mouseX <= x + 6 + width - 12 && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {
                ThemePreset p = presets.get(i);
                Theme.applyTheme(p.primary, p.secondary, p.gradStart, p.gradEnd);
                
                com.hades.client.notification.NotificationManager.getInstance().show(
                        "Theme Applied", p.name, com.hades.client.notification.Notification.Type.INFO);
                return;
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {}

    @Override
    public void keyTyped(char typedChar, int keyCode) {}

    public void scroll(int amount) {
        float totalContentHeight = presets.size() * (ITEM_HEIGHT + ITEM_MARGIN);
        float listY = y + 4 + 36;
        float listHeight = height - (listY - y) - 4;
        float maxScroll = Math.max(0, totalContentHeight - listHeight);

        targetScrollOffset -= amount * 35f;
        targetScrollOffset = Math.max(0, Math.min(maxScroll, targetScrollOffset));
    }
}

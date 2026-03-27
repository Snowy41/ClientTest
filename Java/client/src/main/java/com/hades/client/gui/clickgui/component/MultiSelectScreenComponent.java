package com.hades.client.gui.clickgui.component;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.setting.MultiSelectSetting;

public class MultiSelectScreenComponent extends Component {

    private MultiSelectSetting activeSetting;
    private float openAnimation = 0f;
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;

    public void open(MultiSelectSetting setting) {
        this.activeSetting = setting;
        this.openAnimation = 0f;
        this.scrollOffset = 0f;
        this.targetScrollOffset = 0f;
    }

    public void close() {
        this.activeSetting = null;
    }

    public boolean isOpen() {
        return activeSetting != null;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (activeSetting == null) return;

        openAnimation = smooth(openAnimation, 1f, ANIMATION_SPEED * 1.5f);
        scrollOffset = smooth(scrollOffset, targetScrollOffset, 0.6f);

        int[] sr = HadesAPI.Game.getScaledResolution();
        float screenW = sr[0];
        float screenH = sr[1];

        // Darken background
        HadesAPI.Render.drawRect(0, 0, screenW, screenH, HadesAPI.Render.color(0, 0, 0, (int) (120 * openAnimation)));

        // Window size
        float winWidth = 360f;
        float winHeight = 240f;
        float winX = (screenW - winWidth) / 2f;
        float winY = (screenH - winHeight) / 2f;

        // Intro animation scale
        float scale = 0.9f + (0.1f * openAnimation);
        float scaledX = winX + (winWidth / 2f * (1f - scale));
        float scaledY = winY + (winHeight / 2f * (1f - scale));
        float scaledW = winWidth * scale;
        float scaledH = winHeight * scale;

        HadesAPI.Render.drawRoundedShadow(scaledX, scaledY, scaledW, scaledH, Theme.WINDOW_RADIUS, Theme.SHADOW_SIZE);
        HadesAPI.Render.drawRoundedRect(scaledX - 1, scaledY - 1, scaledW + 2, scaledH + 2, Theme.WINDOW_RADIUS, Theme.WINDOW_OUTLINE);
        HadesAPI.Render.drawRoundedRect(scaledX, scaledY, scaledW, scaledH, Theme.WINDOW_RADIUS, Theme.WINDOW_BG);

        // Header
        float headerY = scaledY + 12;
        HadesAPI.Render.drawCenteredString(activeSetting.getName(), scaledX + scaledW / 2f, headerY, Theme.TEXT_PRIMARY, 1.2f);
        HadesAPI.Render.drawCenteredString(activeSetting.getDescription(), scaledX + scaledW / 2f, headerY + 14, Theme.TEXT_MUTED, 0.8f);

        HadesAPI.Render.drawRect(scaledX + 10, headerY + 28, scaledW - 20, 1, Theme.SIDEBAR_SEPARATOR);

        // Grid (inside scissor)
        float gridY = headerY + 34;
        float gridHeight = scaledH - (gridY - scaledY) - 40; // leave bottom for close button

        MultiSelectSetting.Option[] options = activeSetting.getOptions();

        int cols = 3; // 3 columns
        float padding = 8f;
        float itemW = (scaledW - 20 - padding * (cols - 1)) / cols;
        float itemH = 34f; // Tile height

        int rows = (int) Math.ceil((double) options.length / cols);

        float currentY = gridY - scrollOffset;

        HadesAPI.Render.enableScissor(scaledX + 10, gridY, scaledW - 20, gridHeight);

        for (int i = 0; i < options.length; i++) {
            int row = i / cols;
            int col = i % cols;

            float itemX = scaledX + 10 + col * (itemW + padding);
            float itemTopY = currentY + row * (itemH + padding);

            if (itemTopY + itemH < gridY || itemTopY > gridY + gridHeight) continue; // Culling

            renderOptionTile(options[i], itemX, itemTopY, itemW, itemH, mouseX, mouseY);
        }

        HadesAPI.Render.disableScissor();

        // Scrollbar if needed
        float totalH = rows * (itemH + padding) - padding;
        if (totalH > gridHeight) {
            float scrollH = (gridHeight / totalH) * gridHeight;
            float scrollY = gridY + (scrollOffset / totalH) * gridHeight;
            HadesAPI.Render.drawRoundedRect(scaledX + scaledW - 8, scrollY, 3f, scrollH, 1.5f, Theme.SCROLLBAR_THUMB);
        }

        // Close Button
        float btnW = 80f;
        float btnH = 22f;
        float btnX = scaledX + (scaledW - btnW) / 2f;
        float btnY = scaledY + scaledH - btnH - 8f;

        boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int btnColor = btnHovered ? Theme.ACCENT_PRIMARY : HadesAPI.Render.color(35, 35, 40);

        HadesAPI.Render.drawRoundedRect(btnX, btnY, btnW, btnH, 4f, btnColor);
        HadesAPI.Render.drawCenteredString("Done", btnX + btnW / 2f, btnY + (btnH - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.9f);
    }

    private void renderOptionTile(MultiSelectSetting.Option option, float x, float y, float w, float h, int mouseX, int mouseY) {
        boolean selected = activeSetting.isSelected(option.id);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int bg;
        if (selected) {
            bg = HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, hovered ? 150 : 100);
            HadesAPI.Render.drawRoundedRect(x - 1, y - 1, w + 2, h + 2, 5f, Theme.ACCENT_PRIMARY);
        } else {
            bg = hovered ? HadesAPI.Render.color(40, 40, 45) : HadesAPI.Render.color(28, 28, 32);
        }

        HadesAPI.Render.drawRoundedRect(x, y, w, h, 4f, bg);

        int textColor = selected ? Theme.TEXT_PRIMARY : (hovered ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);

        float textX = x + w / 2f;
        
        if (option.icon != null) {
            try {
                // Determine if it is a LabyMod Icon
                Class<?> iconClass = Class.forName("net.labymod.api.client.gui.icon.Icon");
                if (iconClass.isInstance(option.icon)) {
                    Class<?> stackClass = Class.forName("net.labymod.api.client.render.matrix.Stack");
                    Object stack = stackClass.getMethod("getDefaultEmptyStack").invoke(null);
                    
                    float iconSize = 24f;
                    float iconX = x + 4f;
                    float iconY = y + (h - iconSize) / 2f;
                    
                    java.lang.reflect.Method renderMethod = iconClass.getMethod("render", stackClass, float.class, float.class, float.class, float.class);
                    renderMethod.invoke(option.icon, stack, iconX, iconY, iconSize, iconSize);
                    
                    textX = iconX + iconSize + 6f + (w - iconSize - 10f) / 2f; // Shift text right
                }
            } catch (Exception ignore) {}
        }
        
        HadesAPI.Render.drawCenteredString(option.name, textX, y + (h - HadesAPI.Render.getFontHeight()) / 2f, textColor, 0.9f);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!isOpen() || button != 0) return;

        int[] sr = HadesAPI.Game.getScaledResolution();
        float winWidth = 360f;
        float winHeight = 240f;
        float winX = (sr[0] - winWidth) / 2f;
        float winY = (sr[1] - winHeight) / 2f;

        // Close Button
        float btnW = 80f;
        float btnH = 22f;
        float btnX = winX + (winWidth - btnW) / 2f;
        float btnY = winY + winHeight - btnH - 8f;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            close();
            return;
        }

        // Grid Click
        float headerY = winY + 12;
        float gridY = headerY + 34;
        float gridHeight = winHeight - (gridY - winY) - 40;

        if (mouseX >= winX + 10 && mouseX <= winX + winWidth - 10 && mouseY >= gridY && mouseY <= gridY + gridHeight) {
            MultiSelectSetting.Option[] options = activeSetting.getOptions();
            int cols = 3;
            float padding = 8f;
            float itemW = (winWidth - 20 - padding * (cols - 1)) / cols;
            float itemH = 34f;
            float currentY = gridY - scrollOffset;

            for (int i = 0; i < options.length; i++) {
                int row = i / cols;
                int col = i % cols;

                float itemX = winX + 10 + col * (itemW + padding);
                float itemTopY = currentY + row * (itemH + padding);

                if (mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= itemTopY && mouseY <= itemTopY + itemH) {
                    activeSetting.toggle(options[i].id);
                    return;
                }
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        // Nothing to drag right now
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        // Handled by ClickGUI (e.g. ESC = close)
    }

    public void scroll(int amount) {
        if (!isOpen()) return;

        MultiSelectSetting.Option[] options = activeSetting.getOptions();
        int cols = 3;
        float padding = 8f;
        float itemH = 34f;
        int rows = (int) Math.ceil((double) options.length / cols);
        float totalH = rows * (itemH + padding) - padding;
        
        float gridHeight = 240f - (12 + 34) - 40; 
        float maxScroll = Math.max(0, totalH - gridHeight);

        targetScrollOffset -= amount * 25f;
        targetScrollOffset = Math.max(0, Math.min(maxScroll, targetScrollOffset));
    }
}

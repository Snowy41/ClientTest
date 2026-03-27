package com.hades.client.gui.clickgui.component;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.component.settings.*;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.module.setting.*;

import java.util.ArrayList;
import java.util.List;

public class HudScreenComponent extends Component {

    private float scrollOffset = 0f;
    private float targetScroll = 0f;

    // Grid Metrics
    private static final float CARD_HEIGHT = 42f;
    private static final float CARD_MARGIN = 8f;
    private static final int COLUMNS = 3;

    // Hover Animation Cache
    private final float[] hoverAnims = new float[64];
    private final float[] enableAnims = new float[64];

    // Popover State
    private Module selectedModule = null;
    private final List<SettingComponent<?>> popoverSettings = new ArrayList<>();
    private float popoverX, popoverY;
    private float popoverWidth = 220f;
    private float popoverAlpha = 0f;
    private boolean draggingPopover = false;
    private float dragOffsetX, dragOffsetY;

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        List<Module> hudModules = HadesClient.getInstance().getModuleManager().getModulesByCategory(Module.Category.HUD);
        
        scrollOffset = smooth(scrollOffset, targetScroll, 0.5f);

        // Header Title
        HadesAPI.Render.drawString("HUD Widgets", x + 10, y + 5, Theme.TEXT_PRIMARY, 1.2f);
        HadesAPI.Render.drawString("Right-click to toggle, Left-click to customize settings.", x + 10, y + 22, Theme.TEXT_MUTED, 0.85f);
        
        // HUD Editor Button (Top Right)
        float editBtnW = 120f;
        float editBtnH = 24f;
        float editBtnX = x + width - editBtnW - 10;
        float editBtnY = y + 8f;
        
        boolean editHovered = selectedModule == null && mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH;
        int editBg = editHovered ? Theme.ACCENT_PRIMARY : HadesAPI.Render.colorWithAlpha(Theme.WINDOW_OUTLINE, 200);
        
        HadesAPI.Render.drawRoundedRect(editBtnX, editBtnY, editBtnW, editBtnH, 4f, editBg);
        int editColor = editHovered ? HadesAPI.Render.color(20, 20, 20) : Theme.TEXT_PRIMARY;
        HadesAPI.Render.drawCenteredString("Edit HUD Layout", editBtnX + editBtnW / 2f, editBtnY + (editBtnH - HadesAPI.Render.getFontHeight()) / 2f, editColor, 0.9f);

        float gridY = y + 45 - scrollOffset;
        float usableWidth = width - 20;
        float cardW = (usableWidth - (CARD_MARGIN * (COLUMNS - 1))) / COLUMNS;

        HadesAPI.Render.runWithScissor(x, y + 40, width, height - 40, () -> {
            for (int i = 0; i < hudModules.size(); i++) {
                Module mod = hudModules.get(i);
                
                int row = i / COLUMNS;
                int col = i % COLUMNS;
                
                float cardX = x + 10 + (col * (cardW + CARD_MARGIN));
                float cardY = gridY + (row * (CARD_HEIGHT + CARD_MARGIN));

                // Don't render if completely out of bounds (optimization)
                if (cardY + CARD_HEIGHT < y + 40 || cardY > y + height) continue;

                boolean hovered = selectedModule == null && mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT;
                hoverAnims[i] = smooth(hoverAnims[i], hovered ? 1f : 0f, ANIMATION_SPEED);
                enableAnims[i] = smooth(enableAnims[i], mod.isEnabled() ? 1f : 0f, ANIMATION_SPEED);

                int baseBg = HadesAPI.Render.color(24, 24, 28, 255);
                int hoverBg = HadesAPI.Render.color(32, 32, 38, 255);
                int activeBg = HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 30);
                
                int bg = mod.isEnabled() ? HadesAPI.Render.lerpColor(baseBg, activeBg, enableAnims[i]) : HadesAPI.Render.lerpColor(baseBg, hoverBg, hoverAnims[i]);
                int outline = mod.isEnabled() ? Theme.ACCENT_PRIMARY : HadesAPI.Render.color(40, 40, 45, 255);

                HadesAPI.Render.drawRoundedRect(cardX, cardY, cardW, CARD_HEIGHT, 6f, bg);
                HadesAPI.Render.drawRoundedRect(cardX, cardY, cardW, 1f, 0f, outline); // Top border accent

                // Draw Icon (placeholder colored dot if no specific icon)
                int dotColor = mod.isEnabled() ? Theme.ACCENT_PRIMARY : Theme.TEXT_MUTED;
                HadesAPI.Render.drawRoundedRect(cardX + 10, cardY + (CARD_HEIGHT - 8) / 2f, 8, 8, 4f, dotColor);
                
                // Draw Target Info
                int titleColor = mod.isEnabled() ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY;
                HadesAPI.Render.drawString(mod.getName(), cardX + 26, cardY + 14, titleColor, 1.0f);
            }
        });

        // Calculate max scroll
        int totalRows = (int) Math.ceil((double) hudModules.size() / COLUMNS);
        float totalHeight = totalRows * (CARD_HEIGHT + CARD_MARGIN);
        float visibleHeight = height - 40;
        if (totalHeight > visibleHeight) {
            float sbHeight = (visibleHeight / totalHeight) * visibleHeight;
            float sbY = y + 40 + (scrollOffset / totalHeight) * visibleHeight;
            HadesAPI.Render.drawRoundedRect(x + width - 6, sbY, 3f, sbHeight, 1.5f, Theme.SCROLLBAR_THUMB);
        }

        // Draw Floating Settings Popover
        if (selectedModule != null) {
            popoverAlpha = smooth(popoverAlpha, 1f, ANIMATION_SPEED);
            renderPopover(mouseX, mouseY, partialTicks);
        } else {
            popoverAlpha = 0f;
        }
    }

    private void renderPopover(int mouseX, int mouseY, float partialTicks) {
        if (popoverAlpha < 0.05f) return;
        
        if (draggingPopover) {
            popoverX = mouseX - dragOffsetX;
            popoverY = mouseY - dragOffsetY;
        }

        // Calculate dynamic height based on settings
        float settingsH = 0;
        List<SettingComponent<?>> visibleSettings = new ArrayList<>();
        for (SettingComponent<?> comp : popoverSettings) {
            if (comp.getSetting().isVisible()) {
                settingsH += comp.getHeight() + 4;
                visibleSettings.add(comp);
            }
        }
        
        float currentHeight = 35f + settingsH + 10f; // Header + settings + padding
        
        int alphaInt = (int) (255 * popoverAlpha);
        
        // Window Background + Shadow
        HadesAPI.Render.drawRoundedShadow(popoverX, popoverY, popoverWidth, currentHeight, 6f, 10f);
        HadesAPI.Render.drawRoundedRect(popoverX - 1, popoverY - 1, popoverWidth + 2, currentHeight + 2, 7f, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_OUTLINE, alphaInt));
        HadesAPI.Render.drawRoundedRect(popoverX, popoverY, popoverWidth, currentHeight, 6f, HadesAPI.Render.color(20, 20, 24, alphaInt));

        // Window Header
        HadesAPI.Render.drawRoundedRect(popoverX, popoverY, popoverWidth, 30f, 6f, 6f, 0f, 0f, HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, Math.min(alphaInt, 40)));
        HadesAPI.Render.drawRect(popoverX, popoverY + 30f, popoverWidth, 1f, HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, alphaInt));
        HadesAPI.Render.drawString(selectedModule.getName() + " Settings", popoverX + 10, popoverY + 10, HadesAPI.Render.colorWithAlpha(Theme.TEXT_PRIMARY, alphaInt), 1.05f);

        // Close Button (X)
        float closeX = popoverX + popoverWidth - 20;
        float closeY = popoverY + 8;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        int closeColor = closeHovered ? HadesAPI.Render.color(255, 100, 100, alphaInt) : HadesAPI.Render.colorWithAlpha(Theme.TEXT_MUTED, alphaInt);
        HadesAPI.Render.drawString("X", closeX, closeY + 2, closeColor, 1.0f);

        // Render Settings
        if (!visibleSettings.isEmpty()) {
            float offsetY = popoverY + 35f;
            for (SettingComponent<?> comp : visibleSettings) {
                comp.setPosition(popoverX + 10, offsetY);
                comp.setSize(popoverWidth - 20, comp.getHeight());
                // We don't clip because it dynamically sizes, but we pass bounds just in case.
                comp.setClipBounds(popoverY, popoverY + currentHeight);
                comp.render(mouseX, mouseY, partialTicks);
                offsetY += comp.getHeight() + 4;
            }
        } else {
            HadesAPI.Render.drawCenteredString("No customizable settings.", popoverX + popoverWidth / 2f, popoverY + 50, HadesAPI.Render.colorWithAlpha(Theme.TEXT_MUTED, alphaInt), 0.9f);
            currentHeight = 80f; // Force min height
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible) return;

        // Popover Intercept
        if (selectedModule != null) {
            // Check Close button
            float closeX = popoverX + popoverWidth - 20;
            float closeY = popoverY + 8;
            if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12 && button == 0) {
                selectedModule = null;
                popoverSettings.clear();
                return;
            }

            // Check Header Drag
            if (mouseX >= popoverX && mouseX <= popoverX + popoverWidth && mouseY >= popoverY && mouseY <= popoverY + 30 && button == 0) {
                draggingPopover = true;
                dragOffsetX = mouseX - popoverX;
                dragOffsetY = mouseY - popoverY;
                return;
            }

            // Route to settings
            boolean clickedInside = mouseX >= popoverX && mouseX <= popoverX + popoverWidth && mouseY >= popoverY && mouseY <= popoverY + 500; // rough bounds
            if (clickedInside) {
                for (SettingComponent<?> comp : popoverSettings) {
                    if (comp.getSetting().isVisible()) {
                        comp.mouseClicked(mouseX, mouseY, button);
                    }
                }
                return; // Consume click if inside popover
            }
            
            // If clicked outside, close popover (optional, makes it act like a native modal)
            if (button == 0 || button == 1) {
                selectedModule = null;
                popoverSettings.clear();
            }
            return;
        }

        // Grid Intercept
        
        // Edit Button click intercept
        float editBtnW = 120f;
        float editBtnH = 24f;
        float editBtnX = x + width - editBtnW - 10;
        float editBtnY = y + 8f;
        if (mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH && button == 0) {
            HadesClient.getInstance().getHudEditorScreen().open();
            return;
        }

        List<Module> hudModules = HadesClient.getInstance().getModuleManager().getModulesByCategory(Module.Category.HUD);
        float gridY = y + 45 - scrollOffset;
        float usableWidth = width - 20;
        float cardW = (usableWidth - (CARD_MARGIN * (COLUMNS - 1))) / COLUMNS;

        if (mouseY < y + 40 || mouseY > y + height) return;

        for (int i = 0; i < hudModules.size(); i++) {
            Module mod = hudModules.get(i);
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            float cardX = x + 10 + (col * (cardW + CARD_MARGIN));
            float cardY = gridY + (row * (CARD_HEIGHT + CARD_MARGIN));

            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT) {
                if (button == 0) { // Left Click = Toggle
                    mod.toggle();
                } else if (button == 1) { // Right Click = Open Settings Popover
                    buildPopover(mod, mouseX, mouseY);
                }
                return;
            }
        }
    }

    private void buildPopover(Module mod, int mouseX, int mouseY) {
        this.selectedModule = mod;
        this.popoverSettings.clear();
        this.popoverX = mouseX + 10; // Spawn near cursor
        this.popoverY = mouseY + 10;
        
        // Factory mappings
        for (Setting<?> setting : mod.getSettings()) {
            if (setting.isHidden()) continue;
            if (setting instanceof BooleanSetting) popoverSettings.add(new BooleanComponent((BooleanSetting) setting));
            else if (setting instanceof NumberSetting) popoverSettings.add(new SliderComponent((NumberSetting) setting));
            else if (setting instanceof ModeSetting) popoverSettings.add(new ModeComponent((ModeSetting) setting));
            else if (setting instanceof MultiSelectSetting) popoverSettings.add(new MultiSelectComponent((MultiSelectSetting) setting));
            else if (setting instanceof InventorySetting) popoverSettings.add(new InventoryComponent((InventorySetting) setting));
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        draggingPopover = false;
        if (selectedModule != null) {
            for (SettingComponent<?> comp : popoverSettings) {
                if (comp.getSetting().isVisible()) {
                    comp.mouseReleased(mouseX, mouseY, button);
                }
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (selectedModule != null) {
            if (keyCode == 1) { // ESC closes popover instead of GUI
                selectedModule = null;
                popoverSettings.clear();
                return;
            }
            for (SettingComponent<?> comp : popoverSettings) {
                if (comp.getSetting().isVisible()) {
                    comp.keyTyped(typedChar, keyCode);
                }
            }
        }
    }

    public void scroll(int amount) {
        if (selectedModule != null) return; // Don't scroll grid if popover is open

        List<Module> hudModules = HadesClient.getInstance().getModuleManager().getModulesByCategory(Module.Category.HUD);
        int totalRows = (int) Math.ceil((double) hudModules.size() / COLUMNS);
        float totalHeight = totalRows * (CARD_HEIGHT + CARD_MARGIN);
        float visibleHeight = height - 40;
        float maxScroll = Math.max(0, totalHeight - visibleHeight);

        targetScroll -= amount * 35f;
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
    }
}

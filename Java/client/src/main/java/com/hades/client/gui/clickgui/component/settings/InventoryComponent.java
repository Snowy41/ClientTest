package com.hades.client.gui.clickgui.component.settings;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.setting.InventorySetting;

public class InventoryComponent extends SettingComponent<InventorySetting> {

    private String draggingIconId = null;
    private int draggingFromSlot = -1; // -1 if dragged from palette
    private float dragX, dragY;
    private float dragOffsetX, dragOffsetY;

    public InventoryComponent(InventorySetting setting) {
        super(setting, 180f);
        updateHeight();
    }

    private void updateHeight() {
        int numIcons = setting.getAvailableIcons().size();
        int rows = (int) Math.ceil((double) numIcons / 9.0);
        // Base start height (title + grid + hotbar + palette title + offset) = 132f
        // Each row adds 22f (20 slot + 2 spacing)
        // Add 10f padding at the bottom
        this.height = 132f + (rows * 22f) + 10f;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        float cx = x + width / 2f;
        
        // Draw title
        HadesAPI.Render.drawString(setting.getName(), x + 4, y + 4, Theme.TEXT_PRIMARY, 1f);

        // Calculate grid geometry
        float slotSize = 20f;
        float spacing = 2f;
        float gridW = (9 * slotSize) + (8 * spacing);
        float startX = cx - (gridW / 2f);
        float startY = y + 20f;
        float hotbarY = startY + 3 * (slotSize + spacing) + 4f; // extra gap
        float paletteY = hotbarY + slotSize + 10f;

        // --- PASS 1: 2D Backgrounds & Fallback Text ---
        // Draw slots 9-35 (Main Inventory)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = startX + col * (slotSize + spacing);
                float sy = startY + row * (slotSize + spacing);
                int slotIndex = 9 + (row * 9) + col;
                drawSlotBgAndFallback(sx, sy, slotSize, slotIndex);
            }
        }

        // Draw slots 0-8 (Hotbar)
        for (int col = 0; col < 9; col++) {
            float sx = startX + col * (slotSize + spacing);
            drawSlotBgAndFallback(sx, hotbarY, slotSize, col);
        }

        // Draw Icon Palette
        HadesAPI.Render.drawString("Available Items (Drag to assign)", startX, paletteY, Theme.TEXT_SECONDARY, 0.8f);
        
        float px = startX;
        float py = paletteY + 12f;
        for (InventorySetting.Icon icon : setting.getAvailableIcons()) {
            if (px + slotSize > cx + gridW / 2f) {
                px = startX;
                py += slotSize + spacing;
            }
            
            // Draw palette slot bg
            HadesAPI.Render.drawRect(px, py, slotSize, slotSize, Theme.MODULE_BG);
            HadesAPI.Render.drawRect(px + 1, py + 1, slotSize - 2, slotSize - 2, Theme.WINDOW_BG);
            
            // Draw fallback if no 3D item
            if (!(draggingIconId != null && draggingFromSlot == -1 && draggingIconId.equals(icon.getId()))) {
                drawIconFallback(icon, px, py, slotSize);
            }
            
            px += slotSize + spacing;
        }
        
        // Handle dragging bg/fallback
        if (org.lwjgl.input.Mouse.isButtonDown(0)) {
            if (draggingIconId != null) {
                dragX = mouseX - dragOffsetX;
                dragY = mouseY - dragOffsetY;
                InventorySetting.Icon icon = getIcon(draggingIconId);
                if (icon != null) drawIconFallback(icon, dragX, dragY, slotSize);
            }
        } else if (draggingIconId != null) {
            int targetSlot = getSlotAtPoint(mouseX, mouseY, startX, startY, slotSize, spacing, hotbarY);
            if (targetSlot != -1) {
                if (draggingFromSlot != -1) setting.getValue().remove(draggingFromSlot);
                setting.getValue().put(targetSlot, draggingIconId);
            } else {
                if (draggingFromSlot != -1) setting.getValue().remove(draggingFromSlot);
            }
            draggingIconId = null;
        }

        // --- PASS 2: Batch Render 3D Items ---
        com.hades.client.util.ItemRenderUtil.beginItemRender();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = startX + col * (slotSize + spacing);
                float sy = startY + row * (slotSize + spacing);
                int slotIndex = 9 + (row * 9) + col;
                drawSlotItem(sx, sy, slotSize, slotIndex);
            }
        }

        for (int col = 0; col < 9; col++) {
            float sx = startX + col * (slotSize + spacing);
            drawSlotItem(sx, hotbarY, slotSize, col);
        }

        px = startX;
        py = paletteY + 12f;
        for (InventorySetting.Icon icon : setting.getAvailableIcons()) {
            if (px + slotSize > cx + gridW / 2f) {
                px = startX;
                py += slotSize + spacing;
            }
            if (!(draggingIconId != null && draggingFromSlot == -1 && draggingIconId.equals(icon.getId()))) {
                drawItemOnly(icon, px, py, slotSize);
            }
            px += slotSize + spacing;
        }
        
        if (org.lwjgl.input.Mouse.isButtonDown(0) && draggingIconId != null) {
            InventorySetting.Icon icon = getIcon(draggingIconId);
            if (icon != null) drawItemOnly(icon, dragX, dragY, slotSize);
        }

        com.hades.client.util.ItemRenderUtil.endItemRender();
    }

    private void drawSlotBgAndFallback(float sx, float sy, float size, int slotIndex) {
        HadesAPI.Render.drawRect(sx, sy, size, size, Theme.MODULE_BG_HOVER);
        HadesAPI.Render.drawRect(sx + 1, sy + 1, size - 2, size - 2, Theme.WINDOW_BG);

        String iconId = setting.getValue().get(slotIndex);
        if (iconId != null && !(draggingFromSlot == slotIndex && draggingIconId != null)) {
            InventorySetting.Icon icon = getIcon(iconId);
            if (icon != null) drawIconFallback(icon, sx, sy, size);
        }
    }

    private void drawSlotItem(float sx, float sy, float size, int slotIndex) {
        String iconId = setting.getValue().get(slotIndex);
        if (iconId != null && !(draggingFromSlot == slotIndex && draggingIconId != null)) {
            InventorySetting.Icon icon = getIcon(iconId);
            if (icon != null) drawItemOnly(icon, sx, sy, size);
        }
    }

    private void drawIconFallback(InventorySetting.Icon icon, float x, float y, float size) {
        Object itemStack = icon.getCachedItemStack();
        if (itemStack == null) {
            float pad = 3f;
            HadesAPI.Render.drawRect(x + pad, y + pad, size - pad*2, size - pad*2, HadesAPI.Render.color(100, 100, 100));
            String initials = icon.getDisplayName().substring(0, Math.min(2, icon.getDisplayName().length())).toUpperCase();
            HadesAPI.Render.drawCenteredString(initials, x + size/2f, y + size/2f - 3f, Theme.TEXT_PRIMARY, 0.7f);
        }
    }

    private void drawItemOnly(InventorySetting.Icon icon, float x, float y, float size) {
        Object itemStack = icon.getCachedItemStack();
        if (itemStack != null) {
            float pad = (size - 16f) / 2f;
            com.hades.client.util.ItemRenderUtil.drawItemIconInner(itemStack, x + pad, y + pad);
        }
    }

    private InventorySetting.Icon getIcon(String id) {
        for (InventorySetting.Icon icon : setting.getAvailableIcons()) {
            if (icon.getId().equals(id)) return icon;
        }
        return null;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;

        float cx = x + width / 2f;
        float slotSize = 20f;
        float spacing = 2f;
        float gridW = (9 * slotSize) + (8 * spacing);
        float startX = cx - (gridW / 2f);
        float startY = y + 20f;
        float hotbarY = startY + 3 * (slotSize + spacing) + 4f;

        // Check if clicking existing slot
        int clickedSlot = getSlotAtPoint(mouseX, mouseY, startX, startY, slotSize, spacing, hotbarY);
        if (clickedSlot != -1) {
            String iconId = setting.getValue().get(clickedSlot);
            if (iconId != null) {
                draggingIconId = iconId;
                draggingFromSlot = clickedSlot;
                
                // Calculate offset
                int row, col;
                if (clickedSlot >= 9 && clickedSlot <= 35) {
                    row = (clickedSlot - 9) / 9;
                    col = (clickedSlot - 9) % 9;
                    dragOffsetX = mouseX - (startX + col * (slotSize + spacing));
                    dragOffsetY = mouseY - (startY + row * (slotSize + spacing));
                } else if (clickedSlot >= 0 && clickedSlot <= 8) {
                    col = clickedSlot;
                    dragOffsetX = mouseX - (startX + col * (slotSize + spacing));
                    dragOffsetY = mouseY - hotbarY;
                }
                return;
            }
        }

        // Check if clicking palette
        float paletteY = hotbarY + slotSize + 10f;
        float px = startX;
        float py = paletteY + 12f;
        for (InventorySetting.Icon icon : setting.getAvailableIcons()) {
            if (px + slotSize > cx + gridW / 2f) {
                px = startX;
                py += slotSize + spacing;
            }
            if (mouseX >= px && mouseX <= px + slotSize && mouseY >= py && mouseY <= py + slotSize) {
                draggingIconId = icon.getId();
                draggingFromSlot = -1;
                dragOffsetX = mouseX - px;
                dragOffsetY = mouseY - py;
                return;
            }
            px += slotSize + spacing;
        }
    }

    private int getSlotAtPoint(int mx, int my, float sx, float sy, float size, float spacing, float hotbarY) {
        // Main Inv
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float x = sx + col * (size + spacing);
                float y = sy + row * (size + spacing);
                if (mx >= x && mx <= x + size && my >= y && my <= y + size) {
                    return 9 + (row * 9) + col;
                }
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            float x = sx + col * (size + spacing);
            if (mx >= x && mx <= x + size && my >= hotbarY && my <= hotbarY + size) {
                return col;
            }
        }
        return -1;
    }

    @Override
    public void renderShadow(int mouseX, int mouseY, float partialTicks) {
        // Implementation for shadow if needed
    }
}

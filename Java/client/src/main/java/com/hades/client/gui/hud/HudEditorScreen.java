package com.hades.client.gui.hud;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.impl.render.ArrayListModule;
import com.hades.client.module.impl.render.HUD;
import com.hades.client.module.impl.render.TargetHUD;
import com.hades.client.module.impl.render.PickupHUD;
import com.hades.client.module.impl.render.KeybindsModule;
import com.hades.client.module.impl.render.DynamicIslandModule;

public class HudEditorScreen {
    private boolean visible;

    // Dragging state
    private DraggingElement draggingElement = null;
    private float dragOffsetX = 0;
    private float dragOffsetY = 0;

    enum DraggingElement {
        WATERMARK,
        STATS,
        ARRAYLIST,
        TARGETHUD,
        PICKUPHUD,
        KEYBINDS,
        DYNAMIC_ISLAND
    }

    public void open() {
        visible = true;
        HadesClient.getInstance().setHudEditorOpen(true);
        // The GuiScreen proxy (HadesScreen) is ALREADY displayed since we're called from inside the ClickGUI.
        // Just hide the ClickGUI so the proxy renders the HudEditorScreen instead.
        HadesClient.getInstance().getClickGUI().setVisible(false);
    }

    public void close() {
        visible = false;
        HadesClient.getInstance().setHudEditorOpen(false);
        // Restore the ClickGUI so the proxy seamlessly switches back
        HadesClient.getInstance().getClickGUI().setVisible(true);
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        int screenW = MC_Screen_Width();
        int screenH = MC_Screen_Height();

        // High opacity background (approx 90% dim = ~220 alpha)
        HadesAPI.Render.drawRect(0, 0, 10000, 10000, HadesAPI.Render.color(0, 0, 0, 220));

        // Screen demarcations
        int lineColor = HadesAPI.Render.color(255, 255, 255, 20);
        int middleColor = HadesAPI.Render.color(255, 255, 255, 40);

        // Middle crosses
        HadesAPI.Render.drawRect(screenW / 2f, 0, 1f, screenH, middleColor);
        HadesAPI.Render.drawRect(0, screenH / 2f, screenW, 1f, middleColor);
        
        // 1/4 and 3/4 crosses
        HadesAPI.Render.drawRect(screenW / 4f, 0, 1f, screenH, lineColor);
        HadesAPI.Render.drawRect(screenW * 3f / 4f, 0, 1f, screenH, lineColor);
        HadesAPI.Render.drawRect(0, screenH / 4f, screenW, 1f, lineColor);
        HadesAPI.Render.drawRect(0, screenH * 3f / 4f, screenW, 1f, lineColor);

        // Fire Render2DEvent manually so ALL HUD elements (Watermark, Text, Arrays)
        // gracefully draw themselves ON TOP of this dark editor overlay.
        HadesClient.getInstance().getEventBus().post(new com.hades.client.event.events.Render2DEvent(partialTicks, screenW, screenH));

        HUD hud = (HUD) HadesClient.getInstance().getModuleManager().getModule(HUD.class);
        ArrayListModule arrayList = (ArrayListModule) HadesClient.getInstance().getModuleManager().getModule(ArrayListModule.class);
        TargetHUD targetHud = (TargetHUD) HadesClient.getInstance().getModuleManager().getModule(TargetHUD.class);
        PickupHUD pickupHud = (PickupHUD) HadesClient.getInstance().getModuleManager().getModule(PickupHUD.class);
        KeybindsModule keybinds = (KeybindsModule) HadesClient.getInstance().getModuleManager().getModule(KeybindsModule.class);
        DynamicIslandModule dynamicIsland = (DynamicIslandModule) HadesClient.getInstance().getModuleManager().getModule(DynamicIslandModule.class);

        if (hud == null || arrayList == null) return;

        // Process dragging
        if (draggingElement != null) {
            float newX = mouseX - dragOffsetX;
            float newY = mouseY - dragOffsetY;

            // Anchor Snapping Logic
            float snapThreshold = 10f;
            
            // Center X snapping
            if (Math.abs(newX - (screenW / 2f)) < snapThreshold) {
                newX = screenW / 2f;
                // Draw strong snap line
                HadesAPI.Render.drawRect(screenW / 2f, 0, 2f, screenH, Theme.ACCENT_PRIMARY);
            }
            // Center Y snapping
            if (Math.abs(newY - (screenH / 2f)) < snapThreshold) {
                newY = screenH / 2f;
                HadesAPI.Render.drawRect(0, screenH / 2f, screenW, 2f, Theme.ACCENT_PRIMARY);
            }

            // Edge snapping
            if (newX < snapThreshold) newX = 2f;
            if (newY < snapThreshold) newY = 2f;
            // Note: Right/Bottom edge snapping depends on element width/height, 
            // but we can just snap to the raw coordinate for now to keep it simple.

            if (draggingElement == DraggingElement.WATERMARK) {
                hud.watermarkX.setValue((double) newX);
                hud.watermarkY.setValue((double) newY);
            } else if (draggingElement == DraggingElement.STATS) {
                hud.statsX.setValue((double) newX);
                hud.statsY.setValue((double) newY);
            } else if (draggingElement == DraggingElement.ARRAYLIST) {
                // listX represents the RIGHT edge of the ArrayList area
                float rightEdge = newX + 100f; // newX is drag position (left edge of box), so right edge = newX + boxWidth
                // If it's dragged near the right edge, snap it to -1 (right aligned mode)
                if (screenW - rightEdge < snapThreshold * 3) {
                    arrayList.listX.setValue(-1.0);
                    HadesAPI.Render.drawRect(screenW - 2f, 0, 2f, screenH, Theme.ACCENT_PRIMARY);
                } else {
                    arrayList.listX.setValue((double) rightEdge);
                }
                arrayList.listY.setValue((double) newY);
            } else if (draggingElement == DraggingElement.TARGETHUD && targetHud != null) {
                // TargetHUD uses X/Y offsets from screen center
                float offsetX = newX - (screenW / 2f) + 75f; // 75 = half of PANEL_WIDTH (150)
                float offsetY = newY - (screenH / 2f);
                targetHud.posX.setValue((double) offsetX);
                targetHud.posY.setValue((double) offsetY);
            } else if (draggingElement == DraggingElement.PICKUPHUD && pickupHud != null) {
                float offsetX = newX - (screenW / 2f) + 60f; // 60 = visual center approx
                float offsetY = newY - (screenH / 2f);
                pickupHud.posX.setValue((double) offsetX);
                pickupHud.posY.setValue((double) offsetY);
            } else if (draggingElement == DraggingElement.KEYBINDS && keybinds != null) {
                keybinds.listX.setValue((double) newX);
                keybinds.listY.setValue((double) newY);
            } else if (draggingElement == DraggingElement.DYNAMIC_ISLAND && dynamicIsland != null) {
                float w = Math.max(120f, dynamicIsland.getCurrentWidth());
                float offsetX = newX - (screenW / 2f) + (w / 2f);
                dynamicIsland.xOffset.setValue((double) offsetX);
                dynamicIsland.yOffset.setValue((double) newY);
            }
        }

        // Draw bounding boxes for interactive areas
        drawBounds(hud.watermarkX.getValue().floatValue(), hud.watermarkY.getValue().floatValue(), 48f, 48f, "Watermark", mouseX, mouseY);
        
        // Approximate stats box
        drawBounds(hud.statsX.getValue().floatValue(), hud.statsY.getValue().floatValue(), 100f, 22f, "Stats", mouseX, mouseY);
        
        // ArrayList bounds — listX is the RIGHT edge, so draw from (listX - width)
        float alBoxW = 100f;
        float alX = arrayList.listX.getValue().floatValue();
        if (alX == -1f) alX = screenW; // right-aligned: right edge is screen edge
        float alDrawX = alX - alBoxW;
        drawBounds(alDrawX, arrayList.listY.getValue().floatValue(), alBoxW, 60f, "ArrayList", mouseX, mouseY);

        // TargetHUD bounds
        if (targetHud != null) {
            float thCenterX = screenW / 2f + targetHud.posX.getValue().floatValue();
            float thCenterY = screenH / 2f + targetHud.posY.getValue().floatValue();
            float thX = thCenterX - 75f; // 150 / 2
            float thY = thCenterY;
            drawBounds(thX, thY, 150f, 48f, "TargetHUD", mouseX, mouseY);
        }

        // PickupHUD bounds
        if (pickupHud != null) {
            float pkCenterX = screenW / 2f + pickupHud.posX.getValue().floatValue();
            float pkCenterY = screenH / 2f + pickupHud.posY.getValue().floatValue();
            float pkX = pkCenterX - 60f;
            float pkY = pkCenterY;
            drawBounds(pkX, pkY, 120f, 60f, "Pickup Notifs", mouseX, mouseY);
        }

        // Keybinds bounds
        if (keybinds != null) {
            float kbX = keybinds.listX.getValue().floatValue();
            float kbY = keybinds.listY.getValue().floatValue();
            drawBounds(kbX, kbY, 120f, 60f, "Keybinds HUD", mouseX, mouseY);
        }

        // Dynamic Island bounds (requires centering math)
        if (dynamicIsland != null) {
            float w = Math.max(120f, dynamicIsland.getCurrentWidth());
            float h = Math.max(30f, dynamicIsland.getCurrentHeight());
            float centerX = (screenW / 2f) + dynamicIsland.xOffset.getValue().floatValue();
            float islandX = centerX - (w / 2f);
            float islandY = dynamicIsland.yOffset.getValue().floatValue();
            drawBounds(islandX, islandY, w, h, "Dynamic Island", mouseX, mouseY);
        }

        // Title text
        float titleW = HadesAPI.Render.getStringWidth("HUD Editor", 1.5f);
        HadesAPI.Render.drawStringWithShadow("HUD Editor", screenW / 2f - titleW / 2f, 20f, Theme.TEXT_PRIMARY, 1.5f);
        HadesAPI.Render.drawCenteredString("Drag elements to move. Press ESC to close.", screenW / 2f, 40f, Theme.TEXT_MUTED, 1.0f);
    }

    private void drawBounds(float x, float y, float w, float h, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int color = hovered ? HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 100) : HadesAPI.Render.color(255, 255, 255, 40);
        int outline = hovered ? Theme.ACCENT_PRIMARY : HadesAPI.Render.color(255, 255, 255, 100);

        HadesAPI.Render.drawRect(x, y, w, h, color);
        
        // Outer box
        HadesAPI.Render.drawRect(x, y, w, 1f, outline); // Top
        HadesAPI.Render.drawRect(x, y + h, w, 1f, outline); // Bottom
        HadesAPI.Render.drawRect(x, y, 1f, h, outline); // Left
        HadesAPI.Render.drawRect(x + w, y, 1f, h, outline); // Right

        // Draw label in center
        float labelW = HadesAPI.Render.getStringWidth(label, 0.9f);
        HadesAPI.Render.drawStringWithShadow(label, x + w / 2f - labelW / 2f, y + h / 2f - 4f, Theme.TEXT_PRIMARY, 0.9f);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return;

        HUD hud = (HUD) HadesClient.getInstance().getModuleManager().getModule(HUD.class);
        ArrayListModule arrayList = (ArrayListModule) HadesClient.getInstance().getModuleManager().getModule(ArrayListModule.class);
        TargetHUD targetHud = (TargetHUD) HadesClient.getInstance().getModuleManager().getModule(TargetHUD.class);
        PickupHUD pickupHud = (PickupHUD) HadesClient.getInstance().getModuleManager().getModule(PickupHUD.class);
        KeybindsModule keybinds = (KeybindsModule) HadesClient.getInstance().getModuleManager().getModule(KeybindsModule.class);
        DynamicIslandModule dynamicIsland = (DynamicIslandModule) HadesClient.getInstance().getModuleManager().getModule(DynamicIslandModule.class);

        if (hud == null || arrayList == null) return;

        // Check Watermark
        float wx = hud.watermarkX.getValue().floatValue();
        float wy = hud.watermarkY.getValue().floatValue();
        if (isHovered(mouseX, mouseY, wx, wy, 48f, 48f)) {
            draggingElement = DraggingElement.WATERMARK;
            dragOffsetX = mouseX - wx;
            dragOffsetY = mouseY - wy;
            return;
        }

        // Check Stats
        float sx = hud.statsX.getValue().floatValue();
        float sy = hud.statsY.getValue().floatValue();
        if (isHovered(mouseX, mouseY, sx, sy, 100f, 22f)) {
            draggingElement = DraggingElement.STATS;
            dragOffsetX = mouseX - sx;
            dragOffsetY = mouseY - sy;
            return;
        }

        // Check ArrayList (listX is the RIGHT edge)
        int screenW = MC_Screen_Width();
        int screenH = MC_Screen_Height();
        float alBoxW = 100f;
        float alRightX = arrayList.listX.getValue().floatValue();
        if (alRightX == -1f) alRightX = screenW;
        float alDrawX = alRightX - alBoxW;
        float alY = arrayList.listY.getValue().floatValue();
        
        if (isHovered(mouseX, mouseY, alDrawX, alY, alBoxW, 60f)) {
            draggingElement = DraggingElement.ARRAYLIST;
            dragOffsetX = mouseX - alDrawX;
            dragOffsetY = mouseY - alY;
            return;
        }

        // Check TargetHUD
        if (targetHud != null) {
            float thCenterX = screenW / 2f + targetHud.posX.getValue().floatValue();
            float thCenterY = screenH / 2f + targetHud.posY.getValue().floatValue();
            float thX = thCenterX - 75f;
            float thY = thCenterY;
            if (isHovered(mouseX, mouseY, thX, thY, 150f, 48f)) {
                draggingElement = DraggingElement.TARGETHUD;
                dragOffsetX = mouseX - thX;
                dragOffsetY = mouseY - thY;
                return;
            }
        }

        // Check PickupHUD
        if (pickupHud != null) {
            float pkCenterX = screenW / 2f + pickupHud.posX.getValue().floatValue();
            float pkCenterY = screenH / 2f + pickupHud.posY.getValue().floatValue();
            float pkX = pkCenterX - 60f;
            float pkY = pkCenterY;
            if (isHovered(mouseX, mouseY, pkX, pkY, 120f, 60f)) {
                draggingElement = DraggingElement.PICKUPHUD;
                dragOffsetX = mouseX - pkX;
                dragOffsetY = mouseY - pkY;
                return;
            }
        }

        // Check Keybinds
        if (keybinds != null) {
            float kbX = keybinds.listX.getValue().floatValue();
            float kbY = keybinds.listY.getValue().floatValue();
            if (isHovered(mouseX, mouseY, kbX, kbY, 120f, 60f)) {
                draggingElement = DraggingElement.KEYBINDS;
                dragOffsetX = mouseX - kbX;
                dragOffsetY = mouseY - kbY;
                return;
            }
        }

        // Check DynamicIsland
        if (dynamicIsland != null) {
            float w = Math.max(120f, dynamicIsland.getCurrentWidth());
            float h = Math.max(30f, dynamicIsland.getCurrentHeight());
            float centerX = (screenW / 2f) + dynamicIsland.xOffset.getValue().floatValue();
            float islandX = centerX - (w / 2f);
            float islandY = dynamicIsland.yOffset.getValue().floatValue();
            if (isHovered(mouseX, mouseY, islandX, islandY, w, h)) {
                draggingElement = DraggingElement.DYNAMIC_ISLAND;
                dragOffsetX = mouseX - islandX;
                dragOffsetY = mouseY - islandY;
                return;
            }
        }
    }

    private boolean isHovered(int mouseX, int mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            draggingElement = null;
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC
            close();
        }
    }

    public void handleMouseScroll(int amount) {
        // None needed for HUD editor
    }

    public boolean isVisible() {
        return visible;
    }

    private int MC_Screen_Width() {
        int[] sr = HadesAPI.Game.getScaledResolution();
        return sr[0];
    }

    private int MC_Screen_Height() {
        int[] sr = HadesAPI.Game.getScaledResolution();
        return sr[1];
    }
}

package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.ItemRenderUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PickupHUD extends Module {

    private final NumberSetting maxMessages = new NumberSetting("Max Messages", "Maximum items to show at once", 6, 1, 10, 1);
    private final NumberSetting displayTime = new NumberSetting("Display Time (s)", "How long each item stays on screen", 3.0, 1.0, 10.0, 0.5);
    public final NumberSetting posX = new NumberSetting("Pos X", "X position offset from center", 110.0, -1000.0, 1000.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y position offset from center", 120.0, -1000.0, 1000.0, 1.0);
    public final ModeSetting alignment = new ModeSetting("Alignment", "Right", "Right", "Left");
    public final BooleanSetting blur = new BooleanSetting("Blur Background", true);
    public final BooleanSetting dropShadow = new BooleanSetting("Drop Shadow", true);

    private final List<PickupEntry> pickups = new ArrayList<>();
    private final Object[] previousInventory = new Object[36];
    private final int[] prevCounts = new int[36];

    public PickupHUD() {
        super("PickupHUD", "Displays picked up items on your screen", Category.HUD, 0);
        this.register(maxMessages);
        this.register(displayTime);
        this.register(posX);
        this.register(posY);
        this.register(alignment);
        this.register(blur);
        this.register(dropShadow);
        posX.setHidden(true);
        posY.setHidden(true);
    }

    @Override
    public void onEnable() {
        resetInventoryCache();
        pickups.clear();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        if (HadesAPI.player == null || HadesAPI.player.isNull()) return;

        InventoryManager inv = InventoryManager.getInstance();

        for (int i = 0; i < 36; i++) {
            IItemStack currentStack = inv.getSlot(i);
            int currentCount = currentStack.isNull() ? 0 : currentStack.getStackSize();

            Object prevRaw = previousInventory[i];
            int prevCount = prevCounts[i];

            if (!currentStack.isNull()) {
                if (prevRaw == null) {
                    // New item entirely
                    handlePickup(currentStack.getRaw(), currentCount, currentStack.getDisplayName());
                } else {
                    // Compare with previous item in this slot
                    IItemStack prevWrapper = new IItemStack(prevRaw);
                    
                    if (!prevWrapper.isNull() && !currentStack.getItem().isNull() && !prevWrapper.getItem().isNull()) {
                        Object currentItemRaw = currentStack.getItem().getRaw();
                        Object prevItemRaw = prevWrapper.getItem().getRaw();

                        if (currentItemRaw != null && currentItemRaw.equals(prevItemRaw)) {
                            if (currentCount > prevCount) {
                                handlePickup(currentStack.getRaw(), currentCount - prevCount, currentStack.getDisplayName());
                            }
                        } else {
                            // Replaced slot
                            handlePickup(currentStack.getRaw(), currentCount, currentStack.getDisplayName());
                        }
                    } else if (currentCount > prevCount) {
                        // Fallback safely
                        handlePickup(currentStack.getRaw(), currentCount - prevCount, currentStack.getDisplayName());
                    }
                }
            }

            // Cache for next tick
            previousInventory[i] = currentStack.getRaw();
            prevCounts[i] = currentCount;
        }

        // Decay logic
        long now = System.currentTimeMillis();
        long maxDiff = (long) (displayTime.getValue() * 1000L);
        Iterator<PickupEntry> iterator = pickups.iterator();
        while (iterator.hasNext()) {
            PickupEntry entry = iterator.next();
            if (now - entry.timestamp > maxDiff) {
                iterator.remove();
            }
        }
    }

    private void handlePickup(Object rawStack, int amountAdded, String name) {
        if (amountAdded <= 0 || name == null) return;

        // Check if we already have this item active to stack it
        for (PickupEntry existing : pickups) {
            if (existing.name.equals(name) && System.currentTimeMillis() - existing.timestamp < (displayTime.getValue() * 1000L)) {
                existing.amount += amountAdded;
                existing.timestamp = System.currentTimeMillis(); // Reset decay timer
                existing.pingAnimation = 1.0f; // Reset bounce animation
                return;
            }
        }

        // Create new entry
        PickupEntry entry = new PickupEntry(name, rawStack, amountAdded);
        pickups.add(0, entry); // Add to top

        // Clamp max messages
        while (pickups.size() > maxMessages.getValue()) {
            pickups.remove(pickups.size() - 1);
        }
    }

    private void resetInventoryCache() {
        for (int i = 0; i < 36; i++) {
            previousInventory[i] = null;
            prevCounts[i] = 0;
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || pickups.isEmpty()) return;

        int[] resolution = HadesAPI.Game.getScaledResolution();
        float startX = resolution[0] / 2f + posX.getValue().floatValue(); 
        float startY = resolution[1] / 2f + posY.getValue().floatValue();

        float yOffset = 0;
        
        float mcScale = 1f;
        try {
            int[] sr = HadesAPI.Game.getScaledResolution();
            if (sr[0] > 0) mcScale = (float) org.lwjgl.opengl.Display.getWidth() / sr[0];
        } catch (Throwable ignored) {}

        for (int i = 0; i < pickups.size(); i++) {
            PickupEntry entry = pickups.get(i);
            
            // Layout
            float boxHeight = 24f;
            float fontSize = 14f;
            float fontHeight = HadesAPI.Render.getFontHeight(fontSize, false, false);
            
            // Name + amount string
            String txt = entry.amount > 1 ? entry.name + " §8x" + entry.amount : entry.name;
            float txtWidth = HadesAPI.Render.getStringWidth(txt, fontSize, false, false);
            float boxWidth = 32f + txtWidth + 8f; // Pad(4) + Icon(16) + Pad(8) + Text(txtWidth) + Pad(8)
            
            // Animations
            long elapsed = System.currentTimeMillis() - entry.timestamp;
            long maxLife = (long) (displayTime.getValue() * 1000L);

            // Slide in & Fade out
            float alphaAnim = 1.0f;
            if (elapsed < 200) {
                alphaAnim = elapsed / 200f; 
            } else if (maxLife - elapsed < 300) {
                alphaAnim = (maxLife - elapsed) / 300f;
            }

            // Ping bounce animation (when stacked)
            entry.pingAnimation = Math.max(0f, entry.pingAnimation - 0.05f);
            float scale = 1.0f + (entry.pingAnimation * 0.1f);

            int alpha = (int) (255 * alphaAnim);
            if (alpha <= 5) continue;

            float currentY = startY - yOffset;

            // Box dimensions
            float scaledW = boxWidth * scale;
            float scaledH = boxHeight * scale;
            float drawX = startX - (scaledW - boxWidth) / 2f;
            float drawY = currentY - (scaledH - boxHeight) / 2f;
            float radius = 4f;

            // Draw Box and Blur
            if (dropShadow.getValue()) {
                HadesAPI.Render.drawRoundedShadow(drawX, drawY, scaledW, scaledH, radius, 5f);
            }

            if (blur.getValue()) {
                int tint = HadesAPI.Render.colorWithAlpha(0xFF0A0A0C, (int)(40 * alphaAnim)); // 15% dark tint for readability
                try {
                    com.hades.client.util.BlurUtil.drawBlurredRect(drawX, drawY, scaledW, scaledH, radius, tint, 2, mcScale);
                } catch (Throwable t) {
                    int bgColor = HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, (int)(200 * alphaAnim));
                    HadesAPI.Render.drawRoundedRect(drawX, drawY, scaledW, scaledH, radius, bgColor);
                }
            } else {
                int bgColor = HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, (int)(200 * alphaAnim));
                HadesAPI.Render.drawRoundedRect(drawX, drawY, scaledW, scaledH, radius, bgColor);
            }

            boolean isRightAligned = alignment.getValue().equals("Right");
            int accentColor = HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, alpha);

            // Draw Accent Bar, Icon, and Text based on Alignment
            if (isRightAligned) {
                // Bar on the Right
                HadesAPI.Render.drawRoundedRect(drawX + scaledW - 2f, drawY + 3f, 2f, scaledH - 6f, 1f, accentColor);
                
                // Icon on the Left
                org.lwjgl.opengl.GL11.glPushMatrix();
                HadesAPI.Render.color(255, 255, 255, alpha);
                ItemRenderUtil.drawItemIcon(entry.rawStack, drawX + 8f, drawY + (scaledH - 16f) / 2f);
                org.lwjgl.opengl.GL11.glPopMatrix();
                
                // Text aligned normally after icon
                HadesAPI.Render.drawString(txt, drawX + 32f, drawY + (scaledH - fontHeight) / 2f + 1f, 
                        HadesAPI.Render.colorWithAlpha(Theme.TEXT_PRIMARY, alpha), fontSize, true, false, true);
            } else {
                // Bar on the Left
                HadesAPI.Render.drawRoundedRect(drawX, drawY + 3f, 2f, scaledH - 6f, 1f, accentColor);
                
                // Text aligned directly after bar
                HadesAPI.Render.drawString(txt, drawX + 8f, drawY + (scaledH - fontHeight) / 2f + 1f, 
                        HadesAPI.Render.colorWithAlpha(Theme.TEXT_PRIMARY, alpha), fontSize, true, false, true);
                
                // Icon on the Right
                org.lwjgl.opengl.GL11.glPushMatrix();
                HadesAPI.Render.color(255, 255, 255, alpha);
                ItemRenderUtil.drawItemIcon(entry.rawStack, drawX + scaledW - 24f, drawY + (scaledH - 16f) / 2f);
                org.lwjgl.opengl.GL11.glPopMatrix();
            }

            yOffset += (boxHeight + 4) * alphaAnim; // Shrink space if fading out
        }
    }

    private static class PickupEntry {
        String name;
        Object rawStack;
        int amount;
        long timestamp;
        float pingAnimation;

        public PickupEntry(String name, Object rawStack, int amount) {
            this.name = name;
            this.rawStack = rawStack;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
            this.pingAnimation = 0f;
        }
    }
}

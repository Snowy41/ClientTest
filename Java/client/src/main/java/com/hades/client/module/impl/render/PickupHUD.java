package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
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

    private final List<PickupEntry> pickups = new ArrayList<>();
    private final Object[] previousInventory = new Object[36];
    private final int[] prevCounts = new int[36];

    public PickupHUD() {
        super("PickupHUD", "Displays picked up items on your screen", Category.HUD, 0);
        this.register(maxMessages);
        this.register(displayTime);
        this.register(posX);
        this.register(posY);
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

        for (int i = 0; i < pickups.size(); i++) {
            PickupEntry entry = pickups.get(i);
            
            // Layout
            float boxHeight = 22f;
            float fontHeight = HadesAPI.Render.getFontHeight(0.9f);
            
            // Name + amount string
            String txt = entry.amount > 1 ? entry.name + " §8x" + entry.amount : entry.name;
            float txtWidth = HadesAPI.Render.getStringWidth(txt, 0.9f);
            float boxWidth = 24f + txtWidth + 6f; // Icon + spacing + text + padding
            
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

            // Draw Glassmorphism Box
            int bgColor = HadesAPI.Render.color(15, 15, 18, (int) (180 * alphaAnim));
            int outlineColor = HadesAPI.Render.colorWithAlpha(Theme.WINDOW_OUTLINE, (int) (100 * alphaAnim));
            
            // Center scaling offset
            float scaledW = boxWidth * scale;
            float scaledH = boxHeight * scale;
            float drawX = startX - (scaledW - boxWidth) / 2f;
            float drawY = currentY - (scaledH - boxHeight) / 2f;

            HadesAPI.Render.drawRoundedRect(drawX, drawY, scaledW, scaledH, 4f, bgColor);
            HadesAPI.Render.drawRoundedRect(drawX, drawY, scaledW, 0.5f, 0f, outlineColor); // Top border

            // Draw Item Icon
            org.lwjgl.opengl.GL11.glPushMatrix();
            HadesAPI.Render.color(255, 255, 255, alpha); // Doesn't affect raw item render directly, but cleans state
            ItemRenderUtil.drawItemIcon(entry.rawStack, drawX + 4, drawY + (scaledH - 16) / 2f);
            org.lwjgl.opengl.GL11.glPopMatrix();

            // Draw Text
            HadesAPI.Render.drawString(txt, drawX + 24, drawY + (scaledH - fontHeight) / 2f + 1, HadesAPI.Render.colorWithAlpha(Theme.TEXT_PRIMARY, alpha), 0.9f);

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

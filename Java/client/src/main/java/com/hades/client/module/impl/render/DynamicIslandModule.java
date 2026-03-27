package com.hades.client.module.impl.render;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.backend.BackendConnection;
import com.hades.client.combat.TargetManager;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.BlurUtil;
import com.hades.client.util.UrlImageCache;

import java.util.List;

public class DynamicIslandModule extends Module {

    public enum IslandState {
        WELCOME, IDLE, TARGET_HUD, SCAFFOLD
    }

    public final NumberSetting xOffset = new NumberSetting("X Offset", 0.0, -500.0, 500.0, 1.0);
    public final NumberSetting yOffset = new NumberSetting("Y Offset", 10.0, 0.0, 500.0, 1.0);
    private final BooleanSetting blurBackground = new BooleanSetting("Blur Background", true);
    private final NumberSetting blurPasses = new NumberSetting("Blur Passes", 4, 1, 8, 1);

    // Animation state
    private IslandState currentState = IslandState.WELCOME;
    private float currentWidth = 0f;
    private float currentHeight = 0f;

    // Per-state opacities for crossfading
    private float welcomeAlpha = 0f;
    private float idleAlpha = 0f;
    private float targetAlpha = 0f;
    private float scaffoldAlpha = 0f;

    // Target tracking
    private IEntity currentTarget = null;
    private float animatedHealth = 20f;
    
    // Welcome animation tracker
    private long welcomeStartTime = System.currentTimeMillis();

    // Smooth factor
    private static final float ANIM_SPEED = 0.08f;

    public DynamicIslandModule() {
        super("DynamicIsland", "Dynamic top HUD notch", Module.Category.HUD, 0);
        xOffset.setHidden(true);
        yOffset.setHidden(true);
        this.register(xOffset);
        this.register(yOffset);
        this.register(blurBackground);
        this.register(blurPasses);

        // Force enable on default without triggering a notification spam
        try {
            java.lang.reflect.Field enabledField = Module.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(this, true);
        } catch (Exception e) {
            // Fallback: just use standard method
            this.setEnabled(true);
        }

        // Ensure it's registered
        try {
            HadesClient.getInstance().getEventBus().register(this);
        } catch (Exception e) {
        }
    }

    public float getCurrentWidth() { return currentWidth > 0 ? currentWidth : 120f; }
    public float getCurrentHeight() { return currentHeight > 0 ? currentHeight : 30f; }
    
    @Override
    protected void onEnable() {
        welcomeStartTime = System.currentTimeMillis();
        currentWidth = 0f;
        currentHeight = 0f;
        welcomeAlpha = 0f;
        idleAlpha = 0f;
        targetAlpha = 0f;
        scaffoldAlpha = 0f;
    }

    @EventHandler
    public void onRender(Render2DEvent event) {
        try {
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
            
            // 1. Determine target State
            IslandState targetState = IslandState.IDLE;
            
            long timeSinceWelcome = System.currentTimeMillis() - welcomeStartTime;
            if (timeSinceWelcome < 2500) {
                targetState = IslandState.WELCOME;
            } else {
                com.hades.client.module.Module scaffold = com.hades.client.HadesClient.getInstance().getModuleManager().getModule("Scaffold");
                com.hades.client.module.Module bridgeHelperInstance = com.hades.client.HadesClient.getInstance().getModuleManager().getModule("BridgeHelper");
                
                boolean isBridging = (scaffold != null && scaffold.isEnabled());
                if (bridgeHelperInstance instanceof com.hades.client.module.impl.movement.BridgeHelper && bridgeHelperInstance.isEnabled()) {
                    if (((com.hades.client.module.impl.movement.BridgeHelper) bridgeHelperInstance).isActivelyBridging()) {
                        isBridging = true;
                    }
                }
                
                if (isBridging) {
                    targetState = IslandState.SCAFFOLD;
                } else {
                    IEntity auraTarget = TargetManager.getInstance().getTarget();
                    if (auraTarget != null && auraTarget.getName() != null) {
                        targetState = IslandState.TARGET_HUD;
                        currentTarget = auraTarget;
                    } else {
                        currentTarget = null;
                    }
                }
            }

            this.currentState = targetState;

            float targetW = 140f;
            float targetH = 30f;

            switch (currentState) {
                case WELCOME:
                    targetW = HadesAPI.Render.getStringWidth("Welcome Back!", 14f, true, false) + 40f;
                    targetH = 26f;
                    break;
                case IDLE:
                    targetW = 160f;
                    targetH = 26f;
                    break;
                case TARGET_HUD:
                    targetW = 200f;
                    targetH = 34f; // More compact
                    break;
                case SCAFFOLD:
                    targetW = 120f;
                    targetH = 34f;
                    break;
            }

            // 3. Smoothly Interpolate Dimensions
            currentWidth = smooth(currentWidth, targetW, ANIM_SPEED);
            currentHeight = smooth(currentHeight, targetH, ANIM_SPEED);

            // 4. Interpolate Opacities
            welcomeAlpha = smooth(welcomeAlpha, currentState == IslandState.WELCOME ? 1f : 0f, ANIM_SPEED);
            idleAlpha = smooth(idleAlpha, currentState == IslandState.IDLE ? 1f : 0f, ANIM_SPEED);
            targetAlpha = smooth(targetAlpha, currentState == IslandState.TARGET_HUD ? 1f : 0f, ANIM_SPEED);
            scaffoldAlpha = smooth(scaffoldAlpha, currentState == IslandState.SCAFFOLD ? 1f : 0f, ANIM_SPEED);

            // 5. Render Container
            int screenW = event.getScaledWidth();
            float centerX = (screenW / 2f) + xOffset.getValue().floatValue();
            float rectX = centerX - (currentWidth / 2f);
            float rectY = yOffset.getValue().floatValue();

            float radius = Math.min(currentWidth, currentHeight) / 2f;

            HadesAPI.Render.drawRoundedShadow(rectX, rectY, currentWidth, currentHeight, radius, 6f);

            if (blurBackground.getValue()) {
                try {
                    int tint = HadesAPI.Render.colorWithAlpha(0xFF0A0A0C, 50);

                    // Safe mcScale calculation
                    float mcScale = 1f;
                    int[] sr = HadesAPI.Game.getScaledResolution();
                    if (sr[0] > 0)
                        mcScale = (float) org.lwjgl.opengl.Display.getWidth() / sr[0];
                    if (mcScale <= 0)
                        mcScale = 2f;

                    BlurUtil.drawBlurredRect(rectX, rectY, currentWidth, currentHeight, radius, tint,
                            blurPasses.getValue().intValue(), mcScale);
                } catch (Throwable t) {
                    HadesAPI.Render.drawRoundedRect(rectX, rectY, currentWidth, currentHeight, radius,
                            HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
                }
            } else {
                HadesAPI.Render.drawRoundedRect(rectX, rectY, currentWidth, currentHeight, radius,
                        HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 240));
            }

            // 6. Draw Content
            // Removed runWithScissor as it might be unstable during raw GL Canvas rendering
            // in LabyMod
            if (welcomeAlpha > 0.01f) {
                renderWelcome(rectX, rectY, currentWidth, currentHeight, welcomeAlpha);
            }
            if (idleAlpha > 0.01f) {
                renderIdle(rectX, rectY, currentWidth, currentHeight, idleAlpha);
            }
            if (targetAlpha > 0.01f && currentTarget != null) {
                renderTargetHUD(rectX, rectY, currentWidth, currentHeight, targetAlpha);
            }
            if (scaffoldAlpha > 0.01f) {
                renderScaffold(rectX, rectY, currentWidth, currentHeight, scaffoldAlpha);
            }
        } catch (Throwable t) {
            com.hades.client.util.HadesLogger.get().error("[Hades] Exception in DynamicIsland render: ", t);
        }
    }
    
    private void renderWelcome(float x, float y, float w, float h, float alpha) {
        int alphaInt = (int) (255f * alpha);
        int textColor = HadesAPI.Render.color(255, 255, 255, alphaInt);
        float fontSize = 14f;
        String text = "Welcome Back!";
        
        float textWidth = HadesAPI.Render.getStringWidth(text, fontSize, true, false);
        float textX = x + Math.max(0f, (w - textWidth) / 2f);
        float textY = y + Math.max(0f, (h - HadesAPI.Render.getFontHeight(fontSize, true, false)) / 2f);
        
        // Use scissors so text doesn't overflow during pop-up animation
        HadesAPI.Render.runWithScissor(x, y, w, h, () -> {
            HadesAPI.Render.drawString(text, textX, textY, textColor, fontSize, true, false, true);
        });
    }

    private void renderIdle(float x, float y, float w, float h, float alpha) {
        // Text sizes
        float fontSize = 14f;

        // Left side: "Hades.tf"
        int textColor = HadesAPI.Render.color(255, 255, 255, (int) (255 * alpha));
        float paddingX = 12f;
        float brandY = y + (h - HadesAPI.Render.getFontHeight(fontSize, true, false)) / 2f;

        HadesAPI.Render.drawString("Hades.tf", x + paddingX, brandY, textColor, fontSize, true, false, true);

        // Right side: Profile Picture + Avatar
        String username = HadesClient.getInstance().getSessionUsername();
        if (username == null || username.isEmpty())
            username = "Developer";

        float avatarSize = 16f;
        float profileNameWidth = HadesAPI.Render.getStringWidth(username, fontSize, false, false);
        float rightContentWidth = avatarSize + 6 + profileNameWidth;

        float startX = x + w - paddingX - rightContentWidth;
        float avatarY = y + (h - avatarSize) / 2f;

        List<String> roles = BackendConnection.getInstance().getProfileRoles();
        boolean isAdmin = roles != null && roles.contains("admin");
        String avatarUrl = BackendConnection.getInstance().getProfileAvatarUrl();

        // Push alpha to GlStateManager for the image
        // GlStateManager.color(1f, 1f, 1f, alpha); // Removed GlStateManager
        boolean avatarRendered = UrlImageCache.drawUrlImageCircle(avatarUrl, startX, avatarY, avatarSize, isAdmin);
        if (!avatarRendered) {
            String initial = username.substring(0, 1).toUpperCase();
            int accentColor = Theme.ACCENT_PRIMARY;
            accentColor = (accentColor & 0x00FFFFFF) | ((int) (255 * alpha) << 24); // apply alpha
            HadesAPI.Render.drawRoundedRect(startX, avatarY, avatarSize, avatarSize, avatarSize / 2f, accentColor);
            HadesAPI.Render.drawCenteredString(initial, startX + avatarSize / 2f,
                    avatarY + (avatarSize - HadesAPI.Render.getFontHeight(11f, false, false)) / 2f,
                    HadesAPI.Render.color(255, 255, 255, (int) (255 * alpha)), 11f, true, false, false);
        }
        // GlStateManager.color(1f, 1f, 1f, 1f); // Removed GlStateManager

        float nameY = y + (h - HadesAPI.Render.getFontHeight(fontSize, false, false)) / 2f;
        HadesAPI.Render.drawString(username, startX + avatarSize + 6, nameY, textColor, fontSize, false, false, true);
    }

    private void renderTargetHUD(float x, float y, float w, float h, float alpha) {
        if (currentTarget == null || currentTarget.getName() == null) return;
        
        int alphaInt = (int) (255 * alpha);
        int textColor = HadesAPI.Render.color(255, 255, 255, alphaInt);

        float padding = 6f; // Increased vertical padding
        float avatarSize = h - padding * 2; 
        float avatarX = x + 10f; // Push right to avoid clipping the island's left curve
        float avatarY = y + padding;
        
        // Render Avatar (Head)
        try {
            String name = currentTarget.getName();
            
            int targetHurtTime = 0;
            java.lang.reflect.Field hurtTimeField = com.hades.client.util.ReflectionUtil.findField(currentTarget.getRaw().getClass(), "hurtTime", "au", "field_70737_aN");
            if (hurtTimeField != null) {
                targetHurtTime = hurtTimeField.getInt(currentTarget.getRaw());
            }
            
            int avatarTint = HadesAPI.Render.color(255, 255, 255, alphaInt);
            if (targetHurtTime > 0) {
                float hurtPct = Math.min(1f, targetHurtTime / 10f);
                avatarTint = HadesAPI.Render.lerpColor(
                    HadesAPI.Render.color(255, 255, 255, alphaInt), 
                    HadesAPI.Render.color(255, 50, 50, alphaInt), 
                    hurtPct
                );
            }

            boolean avatarRendered = UrlImageCache.drawUrlImageCircle("https://minotar.net/helm/" + name + "/64.png", avatarX, avatarY, avatarSize, false, avatarTint);
            if (!avatarRendered) {
                HadesAPI.Render.drawRoundedRect(avatarX, avatarY, avatarSize, avatarSize, avatarSize / 2f, HadesAPI.Render.colorWithAlpha(Theme.TEXT_MUTED, (int)(255 * alpha)));
            }
        } catch (Exception e) {}

        // Name
        float textX = avatarX + avatarSize + 8f;
        float nameY = y + padding + 2f;
        String nameStr = currentTarget.getName();
        HadesAPI.Render.drawString(nameStr, textX, nameY, textColor, 16f, true, false, true);
        
        // Armor positioning - Right of the name
        float nameWidth = HadesAPI.Render.getStringWidth(nameStr, 16f, true, false);
        float armorX = textX + nameWidth + 8f;
        
        // Draw Armor and Held Item (scaled down to 0.75)
        drawTargetArmor(currentTarget, armorX, nameY - 2f, alphaInt);
        
        // Health Bar
        float maxHealth = currentTarget.getMaxHealth();
        float health = currentTarget.getHealth();
        // Safe check for NaN or <= 0 max health
        if (maxHealth <= 0f || Float.isNaN(maxHealth)) maxHealth = 20f;
        if (Float.isNaN(health)) health = 20f;
        
        // AntiCheat Micro-fluctuation Smoothing
        animatedHealth += (health - animatedHealth) * 0.15f;
        if (Math.abs(animatedHealth - health) > 20f || alphaInt < 10) {
            animatedHealth = health;
        }
        
        float healthPct = Math.max(0f, Math.min(1f, animatedHealth / maxHealth));

        // Adjust health bar width to have symmetric padding on the right
        float hpBarWidth = w - (textX - x) - 10f;
        float hpBarHeight = 4f;
        float hpBarY = y + h - padding - hpBarHeight - 1f;

        // Health bar bed
        HadesAPI.Render.drawRoundedRect(textX, hpBarY, hpBarWidth, hpBarHeight, hpBarHeight / 2f, HadesAPI.Render.color(20, 20, 20, alphaInt));

        // Health bar fill (Radius dynamically reduces to prevent SDF squish)
        int hpColor = HadesAPI.Render.lerpColor(HadesAPI.Render.color(255, 50, 50, alphaInt), HadesAPI.Render.color(50, 255, 50, alphaInt), healthPct);
        float currentFillWidth = hpBarWidth * healthPct;
        float curRadius = Math.min(hpBarHeight / 2f, currentFillWidth / 2f);
        if (currentFillWidth > 0f) {
            HadesAPI.Render.drawRoundedRect(textX, hpBarY, currentFillWidth, hpBarHeight, curRadius, hpColor);
        }
        

    }
    
    private void drawTargetArmor(IEntity target, float x, float y, int alphaInt) {
        try {
            Object rawEntity = target.getRaw();
            if (rawEntity == null) return;
            
            // Re-use reflection to avoid lookup costs every frame would be better, but fast enough for HUD
            Class<?> eplayClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.entity.player.EntityPlayer", "wn", "ahd");
            if (eplayClass == null || !eplayClass.isInstance(rawEntity)) return;
            
            java.lang.reflect.Field invField = com.hades.client.util.ReflectionUtil.findField(eplayClass, "inventory", "bi", "field_71071_by");
            if (invField == null) return;
            Object inventory = invField.get(rawEntity);
            
            java.lang.reflect.Field armorField = com.hades.client.util.ReflectionUtil.findField(inventory.getClass(), "armorInventory", "b", "field_70460_b");
            if (armorField == null) return;
            Object[] armor = (Object[]) armorField.get(inventory);
            
            // Scale block
            float iconScale = 0.7f;
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glScalef(iconScale, iconScale, 1f);
            
            // Use cached ItemRenderUtil setup entirely
            com.hades.client.util.ItemRenderUtil.beginItemRender();
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, alphaInt / 255f);
            
            // Draw backwards so helmet is first
            int renderX = (int) (x / iconScale);
            int renderY = (int) (y / iconScale);
            for (int i = 3; i >= 0; i--) {
                Object item = armor[i];
                if (item != null) {
                    com.hades.client.util.ItemRenderUtil.drawItemIconInner(item, renderX, renderY);
                    renderX += 16;
                }
            }
            
            // Optional: Draw held item
            java.lang.reflect.Field mainInvField = com.hades.client.util.ReflectionUtil.findField(inventory.getClass(), "mainInventory", "a", "field_70462_a");
            java.lang.reflect.Field currentItemField = com.hades.client.util.ReflectionUtil.findField(inventory.getClass(), "currentItem", "c", "field_70461_c");
            
            if (mainInvField != null && currentItemField != null) {
                Object[] mainInv = (Object[]) mainInvField.get(inventory);
                int currentItemIdx = currentItemField.getInt(inventory);
                if (currentItemIdx >= 0 && currentItemIdx < mainInv.length) {
                    Object currentItem = mainInv[currentItemIdx];
                    if (currentItem != null) {
                        renderX += 4; // Add slight gap
                        com.hades.client.util.ItemRenderUtil.drawItemIconInner(currentItem, renderX, renderY);
                    }
                }
            }
            com.hades.client.util.ItemRenderUtil.endItemRender();
            org.lwjgl.opengl.GL11.glPopMatrix();
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("[Hades DI] Armor Render failed!", e);
        }
    }

    private void renderScaffold(float x, float y, float w, float h, float alpha) {
        int alphaInt = (int) (255 * alpha);
        int textColor = HadesAPI.Render.color(255, 255, 255, alphaInt);

        int totalBlocks = 0;
        Object currentBlockRender = null;
        
        com.hades.client.manager.InventoryManager im = com.hades.client.manager.InventoryManager.getInstance();
        int bestSlot = im.getHeldItemSlot();
        com.hades.client.api.interfaces.IItemStack held = im.getSlot(bestSlot);
        if (held == null || held.isNull() || !held.getItem().isBlock()) {
            bestSlot = im.getBestBlockSlot();
        }
        
        if (bestSlot != -1) {
            com.hades.client.api.interfaces.IItemStack bestStack = im.getSlot(bestSlot);
            if (bestStack != null && !bestStack.isNull()) {
                currentBlockRender = bestStack.getRaw();
                int targetId = bestStack.getItem().getId();
                int targetDamage = bestStack.getDamage();
                
                for (int i = 0; i < 45; i++) {
                    com.hades.client.api.interfaces.IItemStack stack = im.getSlot(i);
                    if (stack != null && stack.getItem() != null && stack.getItem().isBlock()) {
                        if (stack.getItem().getId() == targetId && stack.getDamage() == targetDamage) {
                            totalBlocks += stack.getStackSize();
                        }
                    }
                }
            }
        }

        String text = totalBlocks + " Blocks";
        float fontSize = 16f;
        float textWidth = HadesAPI.Render.getStringWidth(text, fontSize, true, false);
        
        float iconSize = 16f;
        float renderGap = 8f;
        
        float contentWidth = textWidth;
        if (currentBlockRender != null) {
            contentWidth += iconSize + renderGap;
        }

        float startX = x + (w - contentWidth) / 2f;
        float textY = y + 5f;
        
        if (currentBlockRender != null) {
            drawItemIcon(currentBlockRender, startX, y + 4f, alphaInt);
            startX += iconSize + renderGap;
        }

        HadesAPI.Render.drawString(text, startX, textY, textColor, fontSize, true, false, true);
        
        // Progress Bar tracking remaining safety threshold (Max visually is 64)
        float barWidth = w - 24f;
        float barHeight = 4f;
        float barX = x + 12f;
        float barY = y + h - barHeight - 6f; 
        
        HadesAPI.Render.drawRoundedRect(barX, barY, barWidth, barHeight, barHeight/2f, HadesAPI.Render.color(20, 20, 20, alphaInt));
        
        float maxBlocks = (float) Math.ceil((double) totalBlocks / 64.0) * 64f;
        if (maxBlocks < 64f) maxBlocks = 64f;
        
        float fillPct = Math.min(1f, totalBlocks / maxBlocks);
        if (fillPct > 0f) {
            float fillW = barWidth * fillPct;
            int accent = HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, alphaInt);
            
            // Dynamic shift down thresholds (scale warning thresholds against ratio, absolute to 16/32 of remaining active stack)
            int currentStackRemaining = totalBlocks % 64;
            if (currentStackRemaining == 0 && totalBlocks > 0) currentStackRemaining = 64;
            
            if (currentStackRemaining <= 16) {
                accent = HadesAPI.Render.color(255, 50, 50, alphaInt);
            } else if (currentStackRemaining <= 32) {
                accent = HadesAPI.Render.color(255, 180, 50, alphaInt);
            }
            float curRadius = Math.min(barHeight / 2f, fillW / 2f);
            HadesAPI.Render.drawRoundedRect(barX, barY, fillW, barHeight, curRadius, accent);
        }
    }

    private void drawItemIcon(Object rawItemStack, float x, float y, int alphaInt) {
        try {
            org.lwjgl.opengl.GL11.glPushMatrix();
            HadesAPI.Render.color(255, 255, 255, alphaInt);
            com.hades.client.util.ItemRenderUtil.drawItemIcon(rawItemStack, x, y);
            org.lwjgl.opengl.GL11.glPopMatrix();
        } catch (Exception ignored) {}
    }

    /**
     * Simple frame-independent lerp for smooth transitions.
     */
    private float smooth(float current, float target, float speed) {
        if (Math.abs(current - target) < 0.1f)
            return target;
        return current + (target - current) * speed;
    }
}

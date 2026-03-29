package com.hades.client.module.impl.render;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.backend.BackendConnection;
import com.hades.client.combat.TargetManager;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL14;
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

    // ── NATIVE REFLECTION CACHE (Fixes the 50 FPS drop) ──
    private static Class<?> eplayClass = null;
    private static java.lang.reflect.Field hurtTimeField = null;

    private static java.lang.reflect.Field killauraReachField = null;
    private static boolean reflectionInitialized = false;

    private static void initReflection() {
        if (reflectionInitialized)
            return;
        try {
            eplayClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.entity.player.EntityPlayer",
                    "wn", "ahd");
            if (eplayClass != null) {
                hurtTimeField = com.hades.client.util.ReflectionUtil.findField(eplayClass, "hurtTime", "au",
                        "field_70737_aN");

            }

            // Cache KillAura reach
            com.hades.client.module.Module killaura = HadesClient.getInstance().getModuleManager()
                    .getModule("KillAura");
            if (killaura != null) {
                killauraReachField = com.hades.client.util.ReflectionUtil.findField(killaura.getClass(), "reach");
                if (killauraReachField != null)
                    killauraReachField.setAccessible(true);
            }
        } catch (Exception e) {
            com.hades.client.util.HadesLogger.get().error("[DynamicIsland] Failed to cache reflection", e);
        }
        reflectionInitialized = true;
    }

    public enum IslandState {
        WELCOME, IDLE, TARGET_HUD, SCAFFOLD
    }

    // Module References Cache
    private Module killauraCache;
    private Module scaffoldCache;
    private Module bridgeHelperCache;

    // Tick-Cached Target State to save 2,000+ reflection calls per second (UI FPS
    // buffer)
    private float cachedTargetHealth = 20f;
    private float cachedTargetMaxHealth = 20f;
    private String cachedTargetName = "";
    private int cachedTargetHurtTime = 0;
    private boolean cachedIsOutOfRange = false;


    // Scaffold block count cache (updated 20 TPS)
    private int cachedScaffoldBlockCount = 0;
    private Object cachedScaffoldBlockRender = null;
    private boolean cachedIsBridging = false;
    private IEntity cachedTarget = null;

    // ── FBO Scaffold Block Cache (same FBO approach as armor) ──
    private int scaffoldFbo = -1;
    private int scaffoldTex = -1;
    private static final int SCAFFOLD_FBO_SIZE = 20; // 16px icon + 4px padding
    private boolean scaffoldBlockDirty = true;
    private boolean scaffoldFboValid = false;
    private Object prevScaffoldBlockRender = null;

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

        initReflection(); // Initialize cached fields on startup

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

    public float getCurrentWidth() {
        return currentWidth > 0 ? currentWidth : 120f;
    }

    public float getCurrentHeight() {
        return currentHeight > 0 ? currentHeight : 30f;
    }

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
                if (cachedIsBridging) {
                    targetState = IslandState.SCAFFOLD;
                } else {
                    if (this.cachedTarget != null && !this.cachedTargetName.isEmpty()) {
                        targetState = IslandState.TARGET_HUD;
                        currentTarget = this.cachedTarget;
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

            com.hades.client.event.EventBus.startSection("DI_Shadow");
            HadesAPI.Render.drawRoundedShadow(rectX, rectY, currentWidth, currentHeight, radius, 6f);
            com.hades.client.event.EventBus.endSection("DI_Shadow");

            if (blurBackground.getValue()) {
                com.hades.client.event.EventBus.startSection("DI_Blur");
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
                com.hades.client.event.EventBus.endSection("DI_Blur");
            } else {
                HadesAPI.Render.drawRoundedRect(rectX, rectY, currentWidth, currentHeight, radius,
                        HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 240));
            }

            // 6. Draw Content
            com.hades.client.event.EventBus.startSection("DI_Content");
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
            com.hades.client.event.EventBus.endSection("DI_Content");
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
        if (currentTarget == null || cachedTargetName.isEmpty())
            return;

        int alphaInt = (int) (255 * alpha);
        int textColor = HadesAPI.Render.color(255, 255, 255, alphaInt);

        float padding = 6f; // Increased vertical padding
        float avatarSize = h - padding * 2;
        float avatarX = x + 10f; // Push right to avoid clipping the island's left curve
        float avatarY = y + padding;

        // Render Avatar (Head)
        com.hades.client.event.EventBus.startSection("Target_Avatar");
        try {
            int avatarTint = HadesAPI.Render.color(255, 255, 255, alphaInt);
            if (cachedIsOutOfRange) {
                // Dim to greyscale if out of range
                avatarTint = HadesAPI.Render.colorWithAlpha(Theme.TEXT_MUTED, alphaInt);
                textColor = HadesAPI.Render.colorWithAlpha(Theme.TEXT_MUTED, alphaInt);
            } else if (cachedTargetHurtTime > 0) {
                float hurtPct = Math.min(1f, cachedTargetHurtTime / 10f);
                avatarTint = HadesAPI.Render.lerpColor(
                        HadesAPI.Render.color(255, 255, 255, alphaInt),
                        HadesAPI.Render.color(255, 50, 50, alphaInt),
                        hurtPct);
            }

            boolean avatarRendered = UrlImageCache.drawUrlImageCircle(
                    "https://minotar.net/helm/" + cachedTargetName + "/64.png", avatarX, avatarY, avatarSize, false,
                    avatarTint);
            if (!avatarRendered) {
                HadesAPI.Render.drawRoundedRect(avatarX, avatarY, avatarSize, avatarSize, avatarSize / 2f,
                        HadesAPI.Render.colorWithAlpha(Theme.TEXT_MUTED, (int) (255 * alpha)));
            }
        } catch (Exception e) {
        }
        com.hades.client.event.EventBus.endSection("Target_Avatar");

        // Name
        float textX = avatarX + avatarSize + 8f;
        float nameY = y + padding + 2f;
        HadesAPI.Render.drawString(cachedTargetName, textX, nameY, textColor, 16f, true, false, true);

        // Health Bar
        float maxHealth = cachedTargetMaxHealth;
        float health = cachedTargetHealth;
        // Safe check for NaN or <= 0 max health
        if (maxHealth <= 0f || Float.isNaN(maxHealth))
            maxHealth = 20f;
        if (Float.isNaN(health))
            health = 20f;

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
        HadesAPI.Render.drawRoundedRect(textX, hpBarY, hpBarWidth, hpBarHeight, hpBarHeight / 2f,
                HadesAPI.Render.color(20, 20, 20, alphaInt));

        // Health bar fill (Radius dynamically reduces to prevent SDF squish)
        int hpColor = HadesAPI.Render.lerpColor(HadesAPI.Render.color(255, 50, 50, alphaInt),
                HadesAPI.Render.color(50, 255, 50, alphaInt), healthPct);
        float currentFillWidth = hpBarWidth * healthPct;
        float curRadius = Math.min(hpBarHeight / 2f, currentFillWidth / 2f);
        if (currentFillWidth > 0f) {
            HadesAPI.Render.drawRoundedRect(textX, hpBarY, currentFillWidth, hpBarHeight, curRadius, hpColor);
        }

    }

    private void renderScaffold(float x, float y, float w, float h, float alpha) {
        int alphaInt = (int) (255 * alpha);
        int textColor = HadesAPI.Render.color(255, 255, 255, alphaInt);

        int totalBlocks = cachedScaffoldBlockCount;
        Object currentBlockRender = cachedScaffoldBlockRender;

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
            drawScaffoldBlockCached(startX, y + 4f, alphaInt);

            // Draw standard quantity overlay over the block icon natively
            if (totalBlocks > 1) {
                String quantityStr = String.valueOf(totalBlocks);
                float numScale = 14f;
                float sw = HadesAPI.Render.getStringWidth(quantityStr, numScale, true, false);
                float strY = y + 4f + 16f - HadesAPI.Render.getFontHeight(numScale, true, false);
                HadesAPI.Render.drawString(quantityStr, startX + 16f - sw, strY,
                        HadesAPI.Render.color(255, 255, 255, alphaInt), numScale, true, true, true);
            }

            startX += iconSize + renderGap;
        }

        HadesAPI.Render.drawString(text, startX, textY, textColor, fontSize, true, false, true);

        // Progress Bar tracking remaining safety threshold (Max visually is 64)
        float barWidth = w - 24f;
        float barHeight = 4f;
        float barX = x + 12f;
        float barY = y + h - barHeight - 6f;

        HadesAPI.Render.drawRoundedRect(barX, barY, barWidth, barHeight, barHeight / 2f,
                HadesAPI.Render.color(20, 20, 20, alphaInt));

        float maxBlocks = (float) Math.ceil((double) totalBlocks / 64.0) * 64f;
        if (maxBlocks < 64f)
            maxBlocks = 64f;

        float fillPct = Math.min(1f, totalBlocks / maxBlocks);
        if (fillPct > 0f) {
            float fillW = barWidth * fillPct;
            int accent = HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, alphaInt);

            // Dynamic shift down thresholds (scale warning thresholds against ratio,
            // absolute to 16/32 of remaining active stack)
            int currentStackRemaining = totalBlocks % 64;
            if (currentStackRemaining == 0 && totalBlocks > 0)
                currentStackRemaining = 64;

            if (currentStackRemaining <= 16) {
                accent = HadesAPI.Render.color(255, 50, 50, alphaInt);
            } else if (currentStackRemaining <= 32) {
                accent = HadesAPI.Render.color(255, 180, 50, alphaInt);
            }
            float curRadius = Math.min(barHeight / 2f, fillW / 2f);
            HadesAPI.Render.drawRoundedRect(barX, barY, fillW, barHeight, curRadius, accent);
        }
    }

    private void ensureScaffoldFbo() {
        if (scaffoldFbo != -1)
            return;
        try {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            scaffoldTex = org.lwjgl.opengl.GL11.glGenTextures();
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, scaffoldTex);
            org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA8,
                    SCAFFOLD_FBO_SIZE, SCAFFOLD_FBO_SIZE, 0, org.lwjgl.opengl.GL11.GL_RGBA,
                    org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, GL14.GL_CLAMP_TO_EDGE);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, GL14.GL_CLAMP_TO_EDGE);

            scaffoldFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scaffoldFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, scaffoldTex, 0);

            int depthRbo = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRbo);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, SCAFFOLD_FBO_SIZE,
                    SCAFFOLD_FBO_SIZE);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER,
                    depthRbo);

            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                scaffoldFbo = -1;
                scaffoldTex = -1;
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        } catch (Exception e) {
            scaffoldFbo = -1;
            scaffoldTex = -1;
        }
    }

    private void renderScaffoldBlockToFbo() {
        if (scaffoldFbo == -1 || cachedScaffoldBlockRender == null)
            return;

        int prevFbo = org.lwjgl.opengl.GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_VIEWPORT_BIT | org.lwjgl.opengl.GL11.GL_SCISSOR_BIT
                | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);

        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scaffoldFbo);
            org.lwjgl.opengl.GL11.glViewport(0, 0, SCAFFOLD_FBO_SIZE, SCAFFOLD_FBO_SIZE);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);

            // Force completely pure color mask to prevent Alpha channel blackouts from GUI
            org.lwjgl.opengl.GL11.glColorMask(true, true, true, true);

            org.lwjgl.opengl.GL11.glClearColor(0f, 0f, 0f, 0f);
            org.lwjgl.opengl.GL11
                    .glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);

            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glLoadIdentity();
            org.lwjgl.opengl.GL11.glOrtho(0, SCAFFOLD_FBO_SIZE, SCAFFOLD_FBO_SIZE, 0, 1000.0, 3000.0);
            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glLoadIdentity();
            org.lwjgl.opengl.GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            // Standard blending ensures correct source alpha accumulation unconditionally
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                    org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);

            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDepthMask(true);
            org.lwjgl.opengl.GL11.glAlphaFunc(org.lwjgl.opengl.GL11.GL_GREATER, 0.1f);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);

            com.hades.client.util.ItemRenderUtil.beginItemRenderFlat();
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
            com.hades.client.util.ItemRenderUtil.drawItemIconFlatInner(cachedScaffoldBlockRender, 2, 2);
            com.hades.client.util.ItemRenderUtil.endItemRender();

            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            org.lwjgl.opengl.GL11.glPopMatrix();
            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            org.lwjgl.opengl.GL11.glPopMatrix();

            scaffoldFboValid = true;
            scaffoldBlockDirty = false;
        } catch (Exception e) {
            scaffoldFboValid = false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            org.lwjgl.opengl.GL11.glPopAttrib();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                    org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private void drawScaffoldBlockCached(float x, float y, int alphaInt) {
        try {
            ensureScaffoldFbo();

            if (scaffoldBlockDirty && scaffoldFbo != -1) {
                renderScaffoldBlockToFbo();
            }

            if (!scaffoldFboValid || scaffoldFbo == -1) {
                // Fallback: direct render
                drawItemIcon(cachedScaffoldBlockRender, x, y, alphaInt);
                return;
            }

            // Draw cached FBO texture as a single quad
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);

            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                    org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, scaffoldTex);
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, alphaInt / 255f);

            float size = 16f;
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glTexCoord2f(0f, 1f);
            org.lwjgl.opengl.GL11.glVertex2f(x, y);
            org.lwjgl.opengl.GL11.glTexCoord2f(1f, 1f);
            org.lwjgl.opengl.GL11.glVertex2f(x + size, y);
            org.lwjgl.opengl.GL11.glTexCoord2f(1f, 0f);
            org.lwjgl.opengl.GL11.glVertex2f(x + size, y + size);
            org.lwjgl.opengl.GL11.glTexCoord2f(0f, 0f);
            org.lwjgl.opengl.GL11.glVertex2f(x, y + size);
            org.lwjgl.opengl.GL11.glEnd();

            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
        } catch (Exception e) {
            drawItemIcon(cachedScaffoldBlockRender, x, y, alphaInt);
        }
    }

    private void drawItemIcon(Object rawItemStack, float x, float y, int alphaInt) {
        try {
            org.lwjgl.opengl.GL11.glPushMatrix();
            HadesAPI.Render.color(255, 255, 255, alphaInt);
            com.hades.client.util.ItemRenderUtil.drawItemIconFlat(rawItemStack, x, y);
            org.lwjgl.opengl.GL11.glPopMatrix();
        } catch (Exception ignored) {
        }
    }

    /**
     * Updates target state precisely 20 times per second during game ticks.
     * Prevents UI code from spamming JVM Reflection hooks over 500 times/sec
     * per-frame.
     */
    @EventHandler
    public void onTick(com.hades.client.event.events.TickEvent event) {
        if (HadesAPI.mc.isInGui() || HadesAPI.player == null)
            return;

        // Setup modules if null
        if (killauraCache == null)
            killauraCache = HadesClient.getInstance().getModuleManager().getModule("KillAura");
        if (scaffoldCache == null)
            scaffoldCache = HadesClient.getInstance().getModuleManager().getModule("Scaffold");
        if (bridgeHelperCache == null)
            bridgeHelperCache = HadesClient.getInstance().getModuleManager().getModule("BridgeHelper");

        // 1. Determine Bridging State
        cachedIsBridging = (scaffoldCache != null && scaffoldCache.isEnabled());
        if (bridgeHelperCache instanceof com.hades.client.module.impl.movement.BridgeHelper
                && bridgeHelperCache.isEnabled()) {
            if (((com.hades.client.module.impl.movement.BridgeHelper) bridgeHelperCache).isActivelyBridging()) {
                cachedIsBridging = true;
            }
        }

        // 2. Refresh Inventory Block counts dynamically once per tick!
        if (cachedIsBridging) {
            cachedScaffoldBlockCount = 0;
            cachedScaffoldBlockRender = null;

            com.hades.client.manager.InventoryManager im = com.hades.client.manager.InventoryManager.getInstance();
            int heldSlot = im.getHeldItemSlot();

            // Determine which block item to display and count
            int targetSlot = -1;
            com.hades.client.api.interfaces.IItemStack heldStack = (heldSlot >= 0 && heldSlot <= 8)
                    ? im.getSlot(heldSlot)
                    : null;

            if (heldStack != null && !heldStack.isNull() && !heldStack.getItem().isNull()
                    && heldStack.getItem().isBlock()) {
                // Player is holding a block — use it
                targetSlot = heldSlot;
            } else {
                // Held item is not a block — find any block in hotbar
                targetSlot = im.getBestBlockSlot();
            }

            if (targetSlot != -1) {
                com.hades.client.api.interfaces.IItemStack targetStack = im.getSlot(targetSlot);
                if (targetStack != null && !targetStack.isNull()) {
                    Object newBlockRender = targetStack.getRaw();
                    int targetId = targetStack.getItem().getId();
                    int targetDamage = targetStack.getDamage();

                    // Detect block type change → mark scaffold FBO dirty
                    if (newBlockRender != prevScaffoldBlockRender) {
                        scaffoldBlockDirty = true;
                        prevScaffoldBlockRender = newBlockRender;
                    }
                    cachedScaffoldBlockRender = newBlockRender;

                    // Count all matching blocks across the entire inventory (slots 0-35)
                    for (int i = 0; i < 36; i++) {
                        com.hades.client.api.interfaces.IItemStack slot = im.getSlot(i);
                        if (slot != null && !slot.isNull() && !slot.getItem().isNull() && slot.getItem().isBlock()) {
                            if (slot.getItem().getId() == targetId && slot.getDamage() == targetDamage) {
                                cachedScaffoldBlockCount += slot.getStackSize();
                            }
                        }
                    }
                }
            }
        }

        // 3. Cache KillAura Target Variables
        IEntity target = TargetManager.getInstance().getTarget();
        cachedTarget = target;

        if (target != null && target.getName() != null) {
            cachedTargetName = target.getName();
            cachedTargetHealth = target.getHealth();
            cachedTargetMaxHealth = target.getMaxHealth();

            // Out of range computation
            cachedIsOutOfRange = false;
            if (killauraCache != null && killauraCache.isEnabled()) {
                double reach = 3.0;
                try {
                    if (killauraReachField != null) {
                        NumberSetting rs = (NumberSetting) killauraReachField.get(killauraCache);
                        if (rs != null)
                            reach = rs.getValue().doubleValue();
                    }
                } catch (Exception ignored) {
                }

                double dist = com.hades.client.combat.CombatUtil.getDistanceToBox(HadesAPI.player, target);
                if (dist > reach) {
                    cachedIsOutOfRange = true;
                }
            }

            // Hurt Time reflection
            cachedTargetHurtTime = 0;
            if (hurtTimeField != null) {
                try {
                    cachedTargetHurtTime = hurtTimeField.getInt(target.getRaw());
                } catch (Exception ignored) {
                }
            }
        } else {
            cachedTargetName = "";
        }
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

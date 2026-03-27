package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render3DEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.platform.PlatformManager;
import com.hades.client.platform.ClientPlatform;
import com.hades.client.util.ReflectionUtil;

import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Always-on background system that broadcasts and renders Hades role tags.
 * NOT a module — registered directly on the EventBus in HadesClient.start().
 *
 * Broadcasting: Supabase Realtime broadcast every 10s.
 * Receiving: WebSocket message handler in BackendConnection.
 * Rendering: 3D billboard text above players via Render3DEvent.
 */
public class HadesRoleTags {

    private static final HadesRoleTags INSTANCE = new HadesRoleTags();
    public static HadesRoleTags getInstance() { return INSTANCE; }

    private int tickCounter = 0;

    // RenderManager view angles (obf from biu.java: e=playerViewY, f=playerViewX)
    private Object renderManager;
    private Field playerViewYField, playerViewXField;

    // LabyMod Spotify detection (cached reflection)
    private boolean spotifyReflectionAttempted = false;
    private Method spotifyAddonGetMethod;     // SpotifyAddon.get()
    private Method getSpotifyAPIMethod;        // addon.getSpotifyAPI()
    private Method spotifyIsPlayingMethod;     // spotifyAPI.isPlaying()

    private HadesRoleTags() {}

    // ═══════════════════════════════════════════════════════════
    // Broadcasting
    // ═══════════════════════════════════════════════════════════

    @EventHandler(priority = 100)
    public void onTick(TickEvent event) {
        if (HadesAPI.Player.isNull()) return;

        tickCounter++;
        if (tickCounter >= 200) { // 10 seconds
            tickCounter = 0;
            broadcastRole();
        }
    }

    private void broadcastRole() {
        try {
            List<String> roles = com.hades.client.backend.BackendConnection.getInstance().getProfileRoles();
            if (roles == null || roles.isEmpty()) return;

            String highestRole = determineHighestRole(roles);
            if (highestRole == null) return;

            UUID myUuid = HadesAPI.player.getUUID();
            if (myUuid == null) return;

            com.hades.client.backend.BackendConnection.getInstance().broadcastRole(myUuid.toString(), highestRole);
        } catch (Exception e) {
            // Suppress — broadcast failures are non-critical
        }
    }

    private String determineHighestRole(List<String> roles) {
        if (roles.contains("owner")) return "OWNER";
        if (roles.contains("admin")) return "ADMIN";
        if (roles.contains("developer")) return "DEV";
        if (roles.contains("moderator")) return "MOD";
        if (roles.contains("beta")) return "BETA";
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // Rendering — 3D billboard text above tagged players
    // ═══════════════════════════════════════════════════════════

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (HadesAPI.world.isNull() || HadesAPI.Player.isNull()) return;

        // Execute self-role registration unconditionally BEFORE reflections
        UUID selfUuid = HadesAPI.player.getUUID();
        if (selfUuid != null) {
            List<String> roles = com.hades.client.backend.BackendConnection.getInstance().getProfileRoles();
            if (roles != null && !roles.isEmpty()) {
                String highestRole = determineHighestRole(roles);
                if (highestRole != null) {
                    RoleManager.setRole(selfUuid, highestRole);
                }
            }
        }

        // Automatically propagates robust 3D Billboard tags on ALL platforms
        // Platform agnostic design successfully guarantees alignment natively.

        try {
            initRenderManagerIfNeeded();
            if (playerViewYField == null) return;

            double renderPosX = HadesAPI.renderer.getRenderPosX();
            double renderPosY = HadesAPI.renderer.getRenderPosY();
            double renderPosZ = HadesAPI.renderer.getRenderPosZ();

            float playerViewY = playerViewYField.getFloat(renderManager);
            float playerViewX = playerViewXField.getFloat(renderManager);

            // Check if LabyMod Spotify is currently playing (shifts nametag up)
            boolean spotifyPlaying = isLabyModSpotifyPlaying();

            // Also render the self role tag directly (independent of entity list,
            // since some platforms might exclude the local player from loadedEntityList)
            if (selfUuid != null) {
                try {
                    boolean isThirdPerson = HadesAPI.mc.getThirdPersonView() > 0;
                    
                    if (isThirdPerson && selfUuid != null && RoleManager.hasRole(selfUuid)) {
                        String role = RoleManager.getRole(selfUuid);
                        // Use HadesAPI.player as entity for position access
                        drawRoleTag(HadesAPI.player, role, event.getPartialTicks(),
                                renderPosX, renderPosY, renderPosZ,
                                playerViewY, playerViewX, true, spotifyPlaying);
                    }
                } catch (Exception e) {
                    // Suppress
                }
            }

            for (IEntity entity : HadesAPI.world.getLoadedEntities()) {
                if (!entity.isPlayer()) continue;

                UUID uuid = entity.getUUID();
                
                // Skip self — we already rendered it above
                if (selfUuid != null && selfUuid.equals(uuid)) continue;

                float health = entity.getHealth();
                if (health <= 0 || Float.isNaN(health)) continue;
                if (entity.isInvisible()) continue;

                if (uuid != null && RoleManager.hasRole(uuid)) {
                    String role = RoleManager.getRole(uuid);
                    drawRoleTag(entity, role, event.getPartialTicks(),
                            renderPosX, renderPosY, renderPosZ,
                            playerViewY, playerViewX, false, spotifyPlaying);
                }
            }
        } catch (Exception e) {
            // Suppress
        }
    }

    private void initRenderManagerIfNeeded() {
        if (renderManager != null) return;
        try {
            Object mcInstance = HadesAPI.mc.getRaw();
            Method getRenderManager = ReflectionUtil.findMethod(mcInstance.getClass(),
                    new String[]{"af", "getRenderManager", "func_175598_ae"});
            renderManager = getRenderManager.invoke(mcInstance);

            playerViewYField = ReflectionUtil.findField(renderManager.getClass(),
                    "playerViewY", "e", "field_78735_i");
            playerViewXField = ReflectionUtil.findField(renderManager.getClass(),
                    "playerViewX", "f", "field_78732_j");
        } catch (Exception e) {
            renderManager = null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LabyMod Spotify Detection
    // ═══════════════════════════════════════════════════════════

    /**
     * Checks if LabyMod's Spotify addon is currently playing a track.
     * When Spotify is playing, LabyMod pushes the nametag up to make room for the
     * Spotify track info, so our role tag needs to move up with it.
     */
    private boolean isLabyModSpotifyPlaying() {
        if (PlatformManager.getDetectedPlatform() != ClientPlatform.LABYMOD) return false;

        try {
            if (!spotifyReflectionAttempted) {
                spotifyReflectionAttempted = true;
                try {
                    Class<?> spotifyAddonClass = Class.forName("net.labymod.addons.spotify.core.SpotifyAddon");
                    spotifyAddonGetMethod = spotifyAddonClass.getMethod("get");
                    getSpotifyAPIMethod = spotifyAddonClass.getMethod("getSpotifyAPI");
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    // Spotify addon not installed — will always return false
                    return false;
                }
            }

            if (spotifyAddonGetMethod == null) return false;

            Object addon = spotifyAddonGetMethod.invoke(null);
            if (addon == null) return false;

            Object spotifyAPI = getSpotifyAPIMethod.invoke(addon);
            if (spotifyAPI == null) return false;

            // Cache isPlaying method on first successful call
            if (spotifyIsPlayingMethod == null) {
                spotifyIsPlayingMethod = spotifyAPI.getClass().getMethod("isPlaying");
            }

            Object result = spotifyIsPlayingMethod.invoke(spotifyAPI);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Tag Drawing
    // ═══════════════════════════════════════════════════════════

    public void renderRoleTagForEntity(IEntity entity, String role) {
        initRenderManagerIfNeeded();
        if (playerViewYField == null || renderManager == null) return;
        try {
            double renderPosX = HadesAPI.renderer.getRenderPosX();
            double renderPosY = HadesAPI.renderer.getRenderPosY();
            double renderPosZ = HadesAPI.renderer.getRenderPosZ();
            float playerViewY = playerViewYField.getFloat(renderManager);
            float playerViewX = playerViewXField.getFloat(renderManager);
            boolean spotifyPlaying = isLabyModSpotifyPlaying();
            
            drawRoleTag(entity, role, HadesAPI.mc.partialTicks(), renderPosX, renderPosY, renderPosZ, playerViewY, playerViewX, false, spotifyPlaying);
        } catch (Exception e) {}
    }

    private void drawRoleTag(IEntity entity, String role, float partialTicks,
                              double renderPosX, double renderPosY, double renderPosZ,
                              float viewY, float viewX, boolean isSelf, boolean spotifyPlaying) {
        double x = entity.getLastTickX() + (entity.getX() - entity.getLastTickX()) * partialTicks - renderPosX;
        double y = entity.getLastTickY() + (entity.getY() - entity.getLastTickY()) * partialTicks - renderPosY;
        double z = entity.getLastTickZ() + (entity.getZ() - entity.getLastTickZ()) * partialTicks - renderPosZ;

        // We want to sit beautifully flush just above the Vanilla Nametag rendering coordinates
        // Vanilla naturally renders labels at entity.height + 0.5
        double nametagBaseY = entity.getHeight() + 0.5;

        // By dropping the hardcoded 0.6 gap from LabyMod "predictions", it docks cleanly
        // to the top of the standard entity bounding label natively.
        y += nametagBaseY + 0.15; // 0.15 translates to roughly ~2-3 pixels on-screen gap

        // If LabyMod is rendering the Spotify Addon Tag, organically shift our RoleTag upwards
        if (spotifyPlaying) y += 0.40;

        float scale = 0.02f; // Sweet spot between 0.016 and 0.026

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-viewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(viewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);

        drawRoleTagGraphics(role);
        
        GL11.glPopMatrix();
    }

    public void drawRoleTagGraphics(String role) {
        int roleColor = getRoleColor(role);
        int whiteColor = 0xFFFFFFFF;

        float FONT_SIZE = 16f;
        String PREFIX = "HADES";

        // Measure text widths using custom font
        float prefixWidth = HadesAPI.Render.getStringWidth(PREFIX, FONT_SIZE, true, false);
        float separatorWidth = HadesAPI.Render.getStringWidth(" ", FONT_SIZE, false, false);
        float roleWidth = HadesAPI.Render.getStringWidth(role, FONT_SIZE, true, false);
        float totalWidth = prefixWidth + separatorWidth + roleWidth;

        float paddingX = 5f;
        float paddingY = 2f;
        float pillWidth = totalWidth + paddingX * 2;
        float pillHeight = HadesAPI.Render.getFontHeight(FONT_SIZE, false, false) + paddingY * 2;
        float pillRadius = 3f;

        float halfPill = pillWidth / 2.0f;

        // ── Save exact GL states (bulletproof state preservation) ──
        boolean wasLighting = GL11.glGetBoolean(GL11.GL_LIGHTING);
        boolean wasDepthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        boolean wasBlend = GL11.glGetBoolean(GL11.GL_BLEND);
        boolean wasTex2D = GL11.glGetBoolean(GL11.GL_TEXTURE_2D);
        boolean wasAlphaTest = GL11.glGetBoolean(GL11.GL_ALPHA_TEST);
        int oldTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // ── Pill background ──
        float bgX = -halfPill;
        float bgY = -paddingY;
        HadesAPI.Render.drawRoundedRect(bgX, bgY, pillWidth, pillHeight, pillRadius,
                HadesAPI.Render.colorWithAlpha(0xFF1A1A1A, 160));

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // ── Text: "HADES" (white) ──
        float textX = -halfPill + paddingX;
        float textY = -1f;
        HadesAPI.Render.drawString(PREFIX, textX, textY, whiteColor, FONT_SIZE, true, false, true);

        // ── Text: role name (colored) ──
        HadesAPI.Render.drawString(role, textX + prefixWidth + separatorWidth, textY,
                roleColor, FONT_SIZE, true, false, true);

        // ── Restore exact previous GL states ──
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTex); // Crucial to prevent black hand bug (TTF font binds its own texture)
        GL11.glDepthMask(true);

        if (wasLighting) GL11.glEnable(GL11.GL_LIGHTING); else GL11.glDisable(GL11.GL_LIGHTING);
        if (wasDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (wasBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
        if (wasTex2D) GL11.glEnable(GL11.GL_TEXTURE_2D); else GL11.glDisable(GL11.GL_TEXTURE_2D);
        if (wasAlphaTest) GL11.glEnable(GL11.GL_ALPHA_TEST); else GL11.glDisable(GL11.GL_ALPHA_TEST);
        
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static int getRoleColor(String role) {
        switch (role) {
            case "OWNER":  return 0xFFFF5555; // Red
            case "ADMIN":  return 0xFFFF5555; // Red
            case "DEV":    return 0xFF55FFFF; // Aqua
            case "MOD":    return 0xFFFFAA00; // Yellow/Orange
            case "BETA":   return 0xFF55FF55; // Green
            default:       return 0xFFAAAAAA; // Gray
        }
    }
}

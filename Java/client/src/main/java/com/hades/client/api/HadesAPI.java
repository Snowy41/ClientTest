package com.hades.client.api;

import com.hades.client.api.interfaces.*;
import com.hades.client.api.provider.*;
import com.hades.client.platform.PlatformManager;
import com.hades.client.platform.ClientPlatform;
import com.hades.client.util.HadesLogger;

/**
 * HadesAPI is the universal, cross-platform router for all client interactions.
 * It provides clean static accessor nested classes (HadesAPI.Entity, HadesAPI.Render)
 * but routes the underlying calls to the dynamically selected injected interfaces.
 */
public class HadesAPI {

    public static IMinecraft mc;
    public static IPlayer player;
    public static IWorld world;
    public static INetwork network;
    public static IRenderer renderer;
    private static LabyRendererProvider labyRendererProvider;

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        
        // Setup Vanilla Providers as baseline (1.8.9 mappings)
        mc = new Vanilla189Minecraft();
        player = new Vanilla189Player();
        world = new Vanilla189World();
        network = new Vanilla189Network();
        
        // Graphics Routing
        if (PlatformManager.getActiveAdapter().getPlatform() == ClientPlatform.LABYMOD) {
            labyRendererProvider = new LabyRendererProvider();
            renderer = labyRendererProvider;
            HadesLogger.get().info("HadesAPI Initialized: LabyMod Graphics Backend");
        } else {
            renderer = new ShaderRendererProvider();
            HadesLogger.get().info("HadesAPI Initialized: Vanilla GL Graphics Backend (with Shaders)");
        }

        initialized = true;
    }

    public static class Render {
        
        public static void setLabyRenderContext(boolean active) {
            if (labyRendererProvider != null) {
                labyRendererProvider.setLabyRenderContext(active);
            }
        }
        
        private static boolean forceRenderUtil = false;

        public static void setForceRenderUtil(boolean force) {
            forceRenderUtil = force;
        }

        public static boolean isForceRenderUtil() {
            return forceRenderUtil;
        }
        
        public static void drawRect(float x, float y, float w, float h, int color) { renderer.drawRect(x, y, w, h, color); }
        public static void drawRoundedRect(float x, float y, float w, float h, float radius, int color) { renderer.drawRoundedRect(x, y, w, h, radius, color); }
        public static void drawRoundedRect(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int color) { renderer.drawRoundedRect(x, y, w, h, rtl, rtr, rbr, rbl, color); }
        public static void drawGradientRect(float x, float y, float w, float h, int ct, int cb) { renderer.drawGradientRect(x, y, w, h, ct, cb); }
        public static void drawHorizontalGradient(float x, float y, float w, float h, int cl, int cr) { renderer.drawHorizontalGradient(x, y, w, h, cl, cr); }
        public static void drawRoundedGradientRect(float x, float y, float w, float h, float r, int ct, int cb) { renderer.drawRoundedGradientRect(x, y, w, h, r, ct, cb); }
        public static void drawRoundedShadow(float x, float y, float w, float h, float r, float shadow) { renderer.drawRoundedShadow(x, y, w, h, r, shadow); }
        public static void drawString(String text, float x, float y, int color, float scale) { renderer.drawString(text, x, y, color, scale); }
        public static void drawString(String text, float x, float y, int color) { renderer.drawString(text, x, y, color); }
        public static void drawStringWithShadow(String text, float x, float y, int color, float scale) { renderer.drawStringWithShadow(text, x, y, color, scale); }
        public static void drawStringWithShadow(String text, float x, float y, int color) { renderer.drawStringWithShadow(text, x, y, color); }
        public static void drawCenteredString(String text, float x, float y, int color, float scale) { renderer.drawCenteredString(text, x, y, color, scale); }
        public static void drawCenteredString(String text, float x, float y, int color) { renderer.drawCenteredString(text, x, y, color); }
        public static float getStringWidth(String text, float scale) { return renderer.getStringWidth(text, scale); }
        public static float getStringWidth(String text) { return renderer.getStringWidth(text); }
        public static float getFontHeight(float scale) { return renderer.getFontHeight(scale); }
        public static float getFontHeight() { return renderer.getFontHeight(); }
        
        public static void drawString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) { renderer.drawString(text, x, y, color, size, bold, italic, shadow); }
        public static void drawCenteredString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) { renderer.drawCenteredString(text, x, y, color, size, bold, italic, shadow); }
        public static float getStringWidth(String text, float size, boolean bold, boolean italic) { return renderer.getStringWidth(text, size, bold, italic); }
        public static float getFontHeight(float size, boolean bold, boolean italic) { return renderer.getFontHeight(size, bold, italic); }
        public static boolean drawImage(String ns, String p, float x, float y, float w, float h) { return renderer.drawImage(ns, p, x, y, w, h); }
        public static void enableScissor(float x, float y, float w, float h) { renderer.enableScissor(x, y, w, h); }
        public static void disableScissor() { renderer.disableScissor(); }
        public static void runWithScissor(float x, float y, float w, float h, Runnable a) { renderer.runWithScissor(x, y, w, h, a); }

        public static int color(int r, int g, int b, int a) { return com.hades.client.util.RenderUtil.color(r, g, b, a); }
        public static int color(int r, int g, int b) { return com.hades.client.util.RenderUtil.color(r, g, b); }
        public static int colorWithAlpha(int color, int alpha) { return com.hades.client.util.RenderUtil.colorWithAlpha(color, alpha); }
        public static int lerpColor(int c1, int c2, float t) { return com.hades.client.util.RenderUtil.lerpColor(c1, c2, t); }
    }

    public static class Game {
        public static int[] getScaledResolution() {
            // On LabyMod, use its native Window API for correct GUI-scaled dimensions
            if (PlatformManager.getActiveAdapter() != null 
                    && PlatformManager.getActiveAdapter().getPlatform() == ClientPlatform.LABYMOD) {
                try {
                    net.labymod.api.client.gui.window.Window window = 
                            net.labymod.api.Laby.labyAPI().minecraft().minecraftWindow();
                    int scaledW = window.getScaledWidth();
                    int scaledH = window.getScaledHeight();
                    int scaleFactor = scaledW > 0 ? Math.max(1, mc.displayWidth() / scaledW) : 2;
                    return new int[] { scaledW, scaledH, scaleFactor };
                } catch (Throwable ignored) {}
            }
            return mc.scaledResolution();
        }
        public static boolean isGuiOpen() { return mc.isInGui(); }
        public static io.netty.channel.Channel getNettyChannel() { return network.getNettyChannel(); }
        public static void setKeySprintPressed(boolean pressed) { mc.setKeySprintPressed(pressed); }
        public static boolean isKeySprintPhysicallyDown() { return mc.isKeySprintPhysicallyDown(); }
        public static boolean isKeyForwardDown() { return mc.isKeyForwardDown(); }
    }

    public static class Input {
        public static boolean isKeyDown(int key) { return mc.isKeyDown(key); }
        public static boolean isButtonDown(int button) { return mc.isButtonDown(button); }
        public static int getMouseX() {
            // On LabyMod, use its native mouse API for correct scaled coordinates
            if (PlatformManager.getActiveAdapter() != null 
                    && PlatformManager.getActiveAdapter().getPlatform() == ClientPlatform.LABYMOD) {
                try {
                    net.labymod.api.client.gui.mouse.Mouse mouse = net.labymod.api.Laby.labyAPI().minecraft().mouse();
                    return (int) mouse.getX();
                } catch (Throwable ignored) {}
            }
            return mc.getMouseX();
        }
        public static int getMouseY() {
            // On LabyMod, use its native mouse API for correct scaled coordinates
            if (PlatformManager.getActiveAdapter() != null 
                    && PlatformManager.getActiveAdapter().getPlatform() == ClientPlatform.LABYMOD) {
                try {
                    net.labymod.api.client.gui.mouse.Mouse mouse = net.labymod.api.Laby.labyAPI().minecraft().mouse();
                    return (int) mouse.getY();
                } catch (Throwable ignored) {}
            }
            return mc.getMouseY();
        }
    }

    public static class Player {
        public static boolean isNull() { return player.isNull(); }
        public static double getX() { return player.getX(); }
        public static double getY() { return player.getY(); }
        public static double getZ() { return player.getZ(); }
        public static float getYaw() { return player.getYaw(); }
        public static float getPitch() { return player.getPitch(); }
        public static float getHealth() { return player.getHealth(); }
        public static float getEyeHeight() { return player.getEyeHeight(); }
        public static int getItemInUseDuration() { return player.getItemInUseDuration(); }
        public static boolean isOnGround() { return player.isOnGround(); }
        public static boolean isSneaking() { return player.isSneaking(); }
        public static boolean isCollidedHorizontally() { return player.isCollidedHorizontally(); }
        public static float getMoveForward() { return player.getMoveForward(); }
        public static float getMoveStrafing() { return player.getMoveStrafing(); }
        public static float getMovementInputForward(Object movementInput) { return player.getMovementInputForward(movementInput); }
        public static float getMovementInputStrafe(Object movementInput) { return player.getMovementInputStrafe(movementInput); }
        public static void overrideMovementInput(Object movementInput, float forward, float strafe) { player.overrideMovementInput(movementInput, forward, strafe); }
        public static double getMotionX() { return player.getMotionX(); }
        public static double getMotionY() { return player.getMotionY(); }
        public static double getMotionZ() { return player.getMotionZ(); }
        public static void setMotionX(double v) { player.setMotionX(v); }
        public static void setMotionY(double v) { player.setMotionY(v); }
        public static void setMotionZ(double v) { player.setMotionZ(v); }
        public static void setYaw(float yaw) { player.setYaw(yaw); }
        public static void setPitch(float pitch) { player.setPitch(pitch); }
        public static void setSprinting(boolean sprinting) { player.setSprinting(sprinting); }
        public static float getMouseSensitivity() { return mc.getMouseSensitivity(); }
        public static void sendPacket(Object p) { network.sendPacket(p); }
        public static void sendPacketDirect(Object p) { network.sendPacketDirect(p); }
    }
}

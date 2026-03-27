package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.ITileEntity;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render3DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.RenderUtil;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

public class StorageESP extends Module {

    private final BooleanSetting chests = new BooleanSetting("Chests", true);
    private final BooleanSetting enderChests = new BooleanSetting("EnderChests", true);

    public StorageESP() {
        super("StorageESP", "Visually highlights storage blocks through walls", Category.RENDER, 0);
        register(chests);
        register(enderChests);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !HadesAPI.mc.isInGame() || HadesAPI.world == null)
            return;

        try {
            List<ITileEntity> tileEntities = HadesAPI.world.getLoadedTileEntities();

            double renderPosX = HadesAPI.renderer.getRenderPosX();
            double renderPosY = HadesAPI.renderer.getRenderPosY();
            double renderPosZ = HadesAPI.renderer.getRenderPosZ();

            for (ITileEntity tileEntity : tileEntities) {
                if (tileEntity == null) continue;

                boolean isChest = tileEntity.isChest();
                boolean isEnderChest = tileEntity.isEnderChest();

                if (!isChest && !isEnderChest) continue;
                if (isChest && !chests.getValue()) continue;
                if (isEnderChest && !enderChests.getValue()) continue;

                // Colors: Chests -> Orange | EnderChests -> Purple
                int color = isEnderChest ? new Color(128, 0, 128, 255).getRGB() : new Color(255, 128, 0, 255).getRGB();

                double x = tileEntity.getX();
                double y = tileEntity.getY();
                double z = tileEntity.getZ();

                // Render relative to view projection
                double renderX = x - renderPosX;
                double renderY = y - renderPosY;
                double renderZ = z - renderPosZ;

                drawOutlinedBlockESP(renderX, renderY, renderZ, color);
            }
        } catch (Exception e) {
            HadesLogger.get().error("StorageESP rendering exception", e);
        }
    }

    private void drawOutlinedBlockESP(double x, double y, double z, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        boolean wasDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean wasLineSmooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        boolean wasDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2f);
            GL11.glColor4f(r, g, b, a);

            // True bounding box of a standard block is exactly 1x1x1.
            // Chests are technically slightly smaller (0.0625 offset on edges) usually,
            // but for ESP drawing a crisp 1x1 wireframe cell is visually standard and extremely clean.
            RenderUtil.drawOutlinedBoundingBox(x, y, z, x + 1.0, y + 1.0, z + 1.0);

        } finally {
            if (wasDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (wasBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (wasTexture2D) GL11.glEnable(GL11.GL_TEXTURE_2D); else GL11.glDisable(GL11.GL_TEXTURE_2D);
            if (wasLineSmooth) GL11.glEnable(GL11.GL_LINE_SMOOTH); else GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glDepthMask(wasDepthMask);

            GL11.glPopMatrix();
        }
    }
}

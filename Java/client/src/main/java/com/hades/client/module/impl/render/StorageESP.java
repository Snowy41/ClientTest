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


import java.util.List;

public class StorageESP extends Module {

    private static final int COLOR_ENDER_CHEST = 0xFF800080; // Purple ARGB
    private static final int COLOR_CHEST = 0xFFFF8000;       // Orange ARGB

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

            RenderUtil.beginOutlinedESP();
            for (ITileEntity tileEntity : tileEntities) {
                if (tileEntity == null) continue;

                boolean isChest = tileEntity.isChest();
                boolean isEnderChest = tileEntity.isEnderChest();

                if (!isChest && !isEnderChest) continue;
                if (isChest && !chests.getValue()) continue;
                if (isEnderChest && !enderChests.getValue()) continue;

                // Colors: Chests -> Orange | EnderChests -> Purple
                int color = isEnderChest ? COLOR_ENDER_CHEST : COLOR_CHEST;

                double x = tileEntity.getX();
                double y = tileEntity.getY();
                double z = tileEntity.getZ();

                // Render relative to view projection
                double renderX = x - renderPosX;
                double renderY = y - renderPosY;
                double renderZ = z - renderPosZ;

                float a = ((color >> 24) & 0xFF) / 255f;
                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                GL11.glColor4f(r, g, b, a);

                RenderUtil.drawOutlinedBoundingBox(renderX, renderY, renderZ, renderX + 1.0, renderY + 1.0, renderZ + 1.0);
            }
            RenderUtil.endOutlinedESP();
        } catch (Exception e) {
            HadesLogger.get().error("StorageESP rendering exception", e);
        }
    }
}

package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render3DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.OutlineESPManager;


import java.util.List;

public class ESP extends Module {

    private boolean debugLogged = false;

    private final ModeSetting mode = new ModeSetting("Mode", "Box", "Box", "Outline", "2D");
    private final BooleanSetting players = new BooleanSetting("Players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", false);
    private final BooleanSetting animals = new BooleanSetting("Animals", false);

    private final NumberSetting red = new NumberSetting("Red", 255, 0, 255, 1);
    private final NumberSetting green = new NumberSetting("Green", 0, 0, 255, 1);
    private final NumberSetting blue = new NumberSetting("Blue", 0, 0, 255, 1);

    public ESP() {
        super("ESP", "Highlights entities through walls", Category.RENDER, 0); // 0 = no keybind
        register(mode);
        register(players);
        register(mobs);
        register(animals);
        register(red);
        register(green);
        register(blue);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !HadesAPI.mc.isInGame())
            return;

        try {
            if (!debugLogged) {
                HadesLogger.get().info("[ESP] Mode=" + mode.getValue() + ", Players=" + players.getValue() + ", Mobs="
                        + mobs.getValue());
                debugLogged = true;
            }

            if ("Outline".equals(mode.getValue())) {
                int color = (0xFF << 24) | (red.getValue().intValue() << 16) | (green.getValue().intValue() << 8) | blue.getValue().intValue();

                List<com.hades.client.api.interfaces.IEntity> entities = HadesAPI.world.getLoadedEntities();
                com.hades.client.api.interfaces.IPlayer localPlayer = HadesAPI.player;

                for (com.hades.client.api.interfaces.IEntity entity : entities) {
                    if (entity.getRaw() == localPlayer.getRaw())
                        continue;

                    if (isValid(entity)) {
                        OutlineESPManager.registerEntity(entity.getRaw(), color);
                    }
                }

                // Perform the masked drawing operation
                OutlineESPManager.renderOutlines(event.getPartialTicks());
            } else if ("Box".equals(mode.getValue())) {
                int color = (0xFF << 24) | (red.getValue().intValue() << 16) | (green.getValue().intValue() << 8) | blue.getValue().intValue();

                List<com.hades.client.api.interfaces.IEntity> entities = HadesAPI.world.getLoadedEntities();
                com.hades.client.api.interfaces.IPlayer localPlayer = HadesAPI.player;

                double renderPosX = HadesAPI.renderer.getRenderPosX();
                double renderPosY = HadesAPI.renderer.getRenderPosY();
                double renderPosZ = HadesAPI.renderer.getRenderPosZ();

                com.hades.client.util.RenderUtil.beginOutlinedESP();
                for (com.hades.client.api.interfaces.IEntity entity : entities) {
                    if (entity.getRaw() == localPlayer.getRaw())
                        continue;

                    if (isValid(entity)) {
                        double x = entity.getX();
                        double y = entity.getY();
                        double z = entity.getZ();

                        double lastX = entity.getLastTickX();
                        double lastY = entity.getLastTickY();
                        double lastZ = entity.getLastTickZ();

                        // Interpolate
                        double interpX = lastX + (x - lastX) * event.getPartialTicks();
                        double interpY = lastY + (y - lastY) * event.getPartialTicks();
                        double interpZ = lastZ + (z - lastZ) * event.getPartialTicks();

                        // Render relative
                        double renderX = interpX - renderPosX;
                        double renderY = interpY - renderPosY;
                        double renderZ = interpZ - renderPosZ;

                        float width = entity.getWidth();
                        float height = entity.getHeight();

                        // If width/height defaults to 0 from reflection failure, provide fallback for
                        // players
                        if (width <= 0)
                            width = 0.6f;
                        if (height <= 0)
                            height = 1.8f;

                        com.hades.client.util.RenderUtil.drawBatchedOutlinedEntityESP(renderX, renderY, renderZ, width, height,
                                color);
                    }
                }
                com.hades.client.util.RenderUtil.endOutlinedESP();
            }
        } catch (Exception e) {
            HadesLogger.get().error("ESP Module error during Render3DEvent", e);
        }
    }

    private boolean isValid(com.hades.client.api.interfaces.IEntity entity) {
        if (entity == null)
            return false;

        float health = entity.getHealth();
        if (health <= 0 || Float.isNaN(health)) 
            return false;

        if (entity.isInvisible()) 
            return false;

        boolean isPlayer = entity.isPlayer();
        boolean isLiving = entity.isLiving();

        if (!isLiving)
            return false;

        if (isPlayer)
            return players.getValue();

        // Simple heuristic for mobs vs animals without full class mappings:
        // Everything else that is living we treat as mob for now unless we do specific
        // checks.
        // For a full implementation, we'd add isEntityAnimal and isEntityMob to
        // MinecraftBridge.
        if (isLiving && !isPlayer) {
            // we will just respect 'mobs' setting for all non-player living entities for
            // this demo
            return mobs.getValue() || animals.getValue();
        }

        return false;
    }
}

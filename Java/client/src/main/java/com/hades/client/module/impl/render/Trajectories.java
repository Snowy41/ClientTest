package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.TraceResult;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render3DEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.NumberSetting;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * Trajectories — predicts projectile flight path and landing point.
 * Physics ported 1:1 from Moonware reference source.
 */
public class Trajectories extends Module {

    private final NumberSetting lineWidth = new NumberSetting("Line Width", 2.0, 1.0, 6.0, 0.5);

    public Trajectories() {
        super("Trajectories", "Draws the predicted path of thrown projectiles", Category.RENDER, 0);
        register(lineWidth);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !HadesAPI.mc.isInGame() || HadesAPI.player == null || HadesAPI.player.getRaw() == null)
            return;

        try {
            // --- Determine held item type ---
            int slot = com.hades.client.manager.InventoryManager.getInstance().getHeldItemSlot();
            if (slot == -1) return;
            com.hades.client.api.interfaces.IItemStack itemStack = com.hades.client.manager.InventoryManager.getInstance().getSlot(slot);
            if (itemStack == null || itemStack.isNull()) return;
            String name = itemStack.getItem().getUnlocalizedName().toLowerCase();

            boolean isBow = name.contains("bow");
            boolean isPearl = name.contains("enderpearl") || name.contains("ender_pearl");
            boolean isSnowball = name.contains("snowball");
            boolean isEgg = name.contains("egg");
            boolean isPotion = name.contains("potion");

            if (!isBow && !isPearl && !isSnowball && !isEgg && !isPotion) return;

            // --- Starting position: use renderPos (= interpolated camera position) ---
            double rpX = HadesAPI.renderer.getRenderPosX();
            double rpY = HadesAPI.renderer.getRenderPosY();
            double rpZ = HadesAPI.renderer.getRenderPosZ();

            double playerYaw = HadesAPI.player.getYaw();
            double playerPitch = HadesAPI.player.getPitch();

            // Reference: projectilePosX = RenderManager.renderPosX - cos(yaw) * 0.16
            double projectilePosX = rpX - Math.cos(Math.toRadians(playerYaw)) * 0.16;
            double projectilePosY = rpY + HadesAPI.player.getEyeHeight() - 0.1;
            double projectilePosZ = rpZ - Math.sin(Math.toRadians(playerYaw)) * 0.16;

            // Reference motion calculation
            double projectileMotionX = (-Math.sin(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch)));
            double projectileMotionY = -Math.sin(Math.toRadians(playerPitch - (isPotion ? 20 : 0)));
            double projectileMotionZ = (Math.cos(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch)));

            // --- Bow charge power ---
            double shootPower = HadesAPI.player.getItemInUseDuration();

            if (isBow) {
                shootPower /= 20.0;
                shootPower = ((shootPower * shootPower) + (shootPower * 2.0)) / 3.0;
                if (shootPower < 0.1) return;
                if (shootPower > 1.0) shootPower = 1.0;
            }

            // Normalize
            double distance = Math.sqrt(projectileMotionX * projectileMotionX + projectileMotionY * projectileMotionY + projectileMotionZ * projectileMotionZ);
            projectileMotionX /= distance;
            projectileMotionY /= distance;
            projectileMotionZ /= distance;

            // Scale by power (reference formula exactly)
            double velocity = isBow ? (shootPower * 3.0) : (isPotion ? 0.5 : 1.5);
            projectileMotionX *= velocity;
            projectileMotionY *= velocity;
            projectileMotionZ *= velocity;

            // --- GL State Setup ---
            boolean wasBlend = GL11.glGetBoolean(GL11.GL_BLEND);
            boolean wasTex2D = GL11.glGetBoolean(GL11.GL_TEXTURE_2D);
            boolean wasDepthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
            boolean wasLineSmooth = GL11.glGetBoolean(GL11.GL_LINE_SMOOTH);

            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);

            Color c = new Color(21, 121, 230);
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.7f);
            GL11.glLineWidth(lineWidth.getValue().floatValue());

            // --- Simulation + Inline Rendering (exactly like reference) ---
            boolean projectileHasLanded = false;
            TraceResult landingPosition = null;

            GL11.glBegin(GL11.GL_LINE_STRIP);
            while (!projectileHasLanded && projectilePosY > 0) {
                double nextX = projectilePosX + projectileMotionX;
                double nextY = projectilePosY + projectileMotionY;
                double nextZ = projectilePosZ + projectileMotionZ;

                TraceResult possibleHit = HadesAPI.world.rayTraceBlocks(
                        projectilePosX, projectilePosY, projectilePosZ,
                        nextX, nextY, nextZ);

                double traceNextX = possibleHit != null && possibleHit.didHit ? possibleHit.hitX : nextX;
                double traceNextY = possibleHit != null && possibleHit.didHit ? possibleHit.hitY : nextY;
                double traceNextZ = possibleHit != null && possibleHit.didHit ? possibleHit.hitZ : nextZ;

                TraceResult entityHit = HadesAPI.world.checkEntityIntercept(
                        projectilePosX, projectilePosY, projectilePosZ,
                        traceNextX, traceNextY, traceNextZ, HadesAPI.player.getRaw());

                if (entityHit != null && entityHit.didHit) {
                    landingPosition = entityHit;
                    projectileHasLanded = true;
                } else if (possibleHit != null && possibleHit.didHit) {
                    landingPosition = possibleHit;
                    projectileHasLanded = true;
                }

                // Advance
                projectilePosX += projectileMotionX;
                projectilePosY += projectileMotionY;
                projectilePosZ += projectileMotionZ;

                // Drag
                projectileMotionX *= 0.99;
                projectileMotionY *= 0.99;
                projectileMotionZ *= 0.99;

                // Gravity (reference: bow/potion = 0.05, others = 0.03)
                projectileMotionY -= (isBow ? 0.05 : isPotion ? 0.05 : 0.03);

                // Draw vertex relative to renderPos
                GL11.glVertex3d(
                        projectilePosX - rpX,
                        projectilePosY - rpY,
                        projectilePosZ - rpZ);
            }
            GL11.glEnd();

            // --- Landing indicator ---
            if (landingPosition != null) {
                double hx = landingPosition.hitX - rpX;
                double hy = landingPosition.hitY - rpY;
                double hz = landingPosition.hitZ - rpZ;

                GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.3f);
                GL11.glLineWidth(2.0f);

                double cx = hx;
                double cy = hy;
                double cz = hz;
                double offset = 0.02; // Prevents Z-fighting
                double s = 0.5;

                if (landingPosition.isEntityHit) {
                    // Draw a standing cross (sideways too) for entities
                    drawHitSquare(cx - s, cy - s, cz, cx - s, cy + s, cz, cx + s, cy + s, cz, cx + s, cy - s, cz); // SOUTH/NORTH
                    drawHitSquare(cx, cy - s, cz - s, cx, cy + s, cz - s, cx, cy + s, cz + s, cx, cy - s, cz + s); // EAST/WEST
                    drawHitSquare(cx - s, cy, cz - s, cx - s, cy, cz + s, cx + s, cy, cz + s, cx + s, cy, cz - s); // UP/DOWN
                } else {
                    switch(landingPosition.sideHit) {
                        case "UP":
                            cy += offset;
                            drawHitSquare(cx - s, cy, cz - s, cx - s, cy, cz + s, cx + s, cy, cz + s, cx + s, cy, cz - s);
                            break;
                        case "DOWN":
                            cy -= offset;
                            drawHitSquare(cx - s, cy, cz - s, cx - s, cy, cz + s, cx + s, cy, cz + s, cx + s, cy, cz - s);
                            break;
                        case "NORTH":
                            cz -= offset;
                            drawHitSquare(cx - s, cy - s, cz, cx - s, cy + s, cz, cx + s, cy + s, cz, cx + s, cy - s, cz);
                            break;
                        case "SOUTH":
                            cz += offset;
                            drawHitSquare(cx - s, cy - s, cz, cx - s, cy + s, cz, cx + s, cy + s, cz, cx + s, cy - s, cz);
                            break;
                        case "WEST":
                            cx -= offset;
                            drawHitSquare(cx, cy - s, cz - s, cx, cy + s, cz - s, cx, cy + s, cz + s, cx, cy - s, cz + s);
                            break;
                        case "EAST":
                            cx += offset;
                            drawHitSquare(cx, cy - s, cz - s, cx, cy + s, cz - s, cx, cy + s, cz + s, cx, cy - s, cz + s);
                            break;
                        default:
                            drawHitSquare(cx - s, cy, cz - s, cx - s, cy, cz + s, cx + s, cy, cz + s, cx + s, cy, cz - s);
                            break;
                    }
                }
            }

            // --- Restore GL state ---
            GL11.glLineWidth(1.0f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDepthMask(true);

            if (wasBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (wasTex2D) GL11.glEnable(GL11.GL_TEXTURE_2D); else GL11.glDisable(GL11.GL_TEXTURE_2D);
            if (wasDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (wasLineSmooth) GL11.glEnable(GL11.GL_LINE_SMOOTH); else GL11.glDisable(GL11.GL_LINE_SMOOTH);

            GL11.glPopMatrix();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawHitSquare(double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               double x3, double y3, double z3,
                               double x4, double y4, double z4) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x3, y3, z3);
        GL11.glVertex3d(x4, y4, z4);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x3, y3, z3);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x4, y4, z4);
        GL11.glEnd();
    }
}

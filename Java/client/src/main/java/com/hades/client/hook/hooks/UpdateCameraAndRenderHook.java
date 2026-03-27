package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.api.HadesAPI;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.HadesClient;
import org.lwjgl.opengl.GL11;

public class UpdateCameraAndRenderHook {

    public static boolean logged = false;
    public static boolean loggedErr = false;

    @Advice.OnMethodExit
    public static void onPostUpdateCameraAndRender(@Advice.Argument(0) float partialTicks) {
        try {
            if (com.hades.client.platform.PlatformManager.getActiveAdapter() != null &&
                com.hades.client.platform.PlatformManager.getActiveAdapter().getPlatform() == com.hades.client.platform.ClientPlatform.LABYMOD) {
                return; // Let LabyMod's native HadesIngameActivity handle HUD rendering to avoid double calls or overlapping GL states
            }
            
            // Unconditionally fire our Render2DEvent at the very end of EntityRenderer's frame
            // Natively, setupOverlayRendering() is still perfectly active here, 
            // allowing us to draw raw GL flawlessly OVER the ClickGUI and EVERYTHING in LabyMod.
            
            boolean blend = GL11.glGetBoolean(GL11.GL_BLEND);
            boolean depth = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
            
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            
            int[] sr = HadesAPI.Game.getScaledResolution();
            HadesClient.getInstance().getEventBus().post(new Render2DEvent(partialTicks, sr[0], sr[1]));
            
            if (!blend) GL11.glDisable(GL11.GL_BLEND);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            
            if (!logged) {
                System.out.println("[Hades Hook] UpdateCameraAndRenderHook FIRED SUCCESSFULLY!");
                logged = true;
            }
        } catch (Throwable t) {
            if (!loggedErr) {
                System.out.println("[Hades Hook] Exception in UpdateCameraAndRenderHook: " + t.getMessage());
                t.printStackTrace();
                loggedErr = true;
            }
        }
    }
}

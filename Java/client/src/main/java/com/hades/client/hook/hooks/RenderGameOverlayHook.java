package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.api.HadesAPI;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.HadesClient;

public class RenderGameOverlayHook {

    @Advice.OnMethodExit
    public static void onRenderGameOverlay(@Advice.Argument(0) float partialTicks) {
        try {
            // Unconditionally fire our Render2DEvent AFTER the vanilla/LabyMod HUD renders.
            int[] sr = HadesAPI.Game.getScaledResolution();
            HadesClient.getInstance().getEventBus().post(new Render2DEvent(partialTicks, sr[0], sr[1]));
        } catch (Throwable t) {
            // Ignore to protect the render thread
        }
    }
}

package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.util.HadesLogger;

/**
 * ByteBuddy Advice for Minecraft.clickMouse()
 * Obfuscated 1.8.9: ave.aw()
 */
public class ClickMouseHook {

    @Advice.OnMethodEnter
    public static void onEnter() {
        try {
            // Future: Fire a Hades EventBus click event if needed
            // com.hades.client.HadesClient.getInstance().getEventBus().post(new
            // ClickEvent());
            HadesLogger.get().info("Native clickMouse intercepted via ByteBuddy!");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

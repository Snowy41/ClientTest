package com.hades.client.hook.hooks;

import com.hades.client.event.events.TickEvent;
import net.bytebuddy.asm.Advice;

/**
 * ByteBuddy Advice for Minecraft.runTick()
 * Obfuscated 1.8.9: ave.s()
 */
public class RunTickHook {

    /**
     * Injected at the very start of the runTick() method.
     */
    @Advice.OnMethodEnter
    public static void onEnter() {
        // We catch everything to ensure a bug in our event handlers
        // doesn't crash the entire Minecraft client loop.
        try {
            com.hades.client.event.EventBus bus = com.hades.client.HadesClient.getInstance().getEventBus();
            if (bus != null) {
                bus.post(new TickEvent());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

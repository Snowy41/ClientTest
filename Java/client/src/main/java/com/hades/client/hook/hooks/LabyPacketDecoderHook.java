package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.util.HadesLogger;

public class LabyPacketDecoderHook {

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Thrown(readOnly = false) Throwable throwable) {
        if (throwable != null) {
            String msg = throwable.getMessage();
            if (msg != null && msg.contains("is not registered")) {
                // Silently suppress the LabyConnect garbage packet crash during channel inactive
                throwable = null;
            }
        }
    }
}

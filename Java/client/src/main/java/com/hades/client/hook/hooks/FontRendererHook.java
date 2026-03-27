package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;

/**
 * Hooks into Vanilla FontRenderer to intercept drawString and getStringWidth.
 * This ensures that if ANY client module intercepts native rendering, we can override
 * the font globally (e.g., when the user selects a custom font).
 */
public class FontRendererHook {

    public static class DrawString {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static int onDrawString(
                @Advice.Argument(value = 0) String text,
                @Advice.Argument(value = 1, readOnly = false) float x,
                @Advice.Argument(value = 2, readOnly = false) float y,
                @Advice.Argument(value = 3, readOnly = false) int color,
                @Advice.Argument(value = 4) boolean dropShadow
        ) {
            return 0; // Default execution
        }
    }

    public static class GetStringWidth {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static int onGetStringWidth(
                @Advice.Argument(0) String text
        ) {
            return 0; // Default execution
        }
    }
}

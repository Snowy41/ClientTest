package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;

/**
 * Intercepts Gui.drawRect calls to apply custom blur/rounded corner
 * effects globally across Vanilla UI elements.
 */
public class GuiDrawRectHook {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onDrawRect(
            @Advice.Argument(value = 0, readOnly = false) int left,
            @Advice.Argument(value = 1, readOnly = false) int top,
            @Advice.Argument(value = 2, readOnly = false) int right,
            @Advice.Argument(value = 3, readOnly = false) int bottom,
            @Advice.Argument(value = 4, readOnly = false) int color
    ) {
        // Obsolete Scoreboard interception removed.
        return false; // Let native method run
    }
}

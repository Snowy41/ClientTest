package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.module.impl.render.ScoreboardModule;

/**
 * Hooks Scoreboard.getObjectiveInDisplaySlot(int) (auo.a).
 * If the active Hades Scoreboard module is rendering, we intercept calls asking for the sidebar (slot 1).
 * Returning null gracefully tells Vanilla and LabyMod to skip drawing the widget securely, leaving all matrix 
 * Stacks flawlessly formatted for downstream HUD elements like the Vignette and Hotbar without dangerous UI hooks!
 */
public class ScoreboardObjectiveHook {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onGetObjectiveEnter(@Advice.Argument(0) int slot) {
        try {
            ScoreboardModule mod = ScoreboardModule.getInstance();
            if (mod != null && mod.isEnabled() && slot == 1) {
                // If it's asking for the sidebar objective, seamlessly abort native evaluation.
                // Since the return type is an Object (ScoreObjective), the uninitialized value is natively null!
                return true; 
            }
        } catch (Exception ignored) {}
        return false;
    }
}

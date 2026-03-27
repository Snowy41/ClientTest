package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.module.impl.render.ScoreboardModule;

/**
 * Hooks into GuiIngame.renderScoreboard(ScoreObjective, ScaledResolution).
 * Since we now render the scoreboard natively in ScoreboardModule via Render2DEvent,
 * we entirely skip the native Vanilla renderScoreboard method to prevent duplicate rendering.
 */
public class RenderScoreboardHook {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onRenderScoreboardEnter(
            @Advice.Origin String signature
    ) {
        try {
            ScoreboardModule mod = ScoreboardModule.getInstance();
            if (mod != null && mod.isEnabled()) {
                // Safely determine which obfuscated a(...) method we hooked using Method Signature
                if (signature != null) {
                    // renderScoreboard takes ScoreObjective (auk).
                    // If the signature does NOT contain ScoreObjective (or auk), it's NOT the scoreboard.
                    if (!signature.contains("ScoreObjective") && !signature.contains("auk")) {
                        return false; 
                    }
                }
                // If it IS the scoreboard, skip native rendering!
                return true; 
            }
        } catch (Exception ignored) {}
        return false;
    }
}

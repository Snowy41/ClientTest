package com.hades.client.hook.hooks;

import net.bytebuddy.asm.Advice;
import com.hades.client.module.impl.render.ScoreboardModule;

/**
 * Hooks LabyMod 4's ScoreboardHudWidget to intercept its rendering and logic.
 * Instead of violently skipping the method (which breaks LabyMod's native GL state and makes the Vignette vanish),
 * we gracefully push a matrix, translate it thousands of pixels off-screen, and then cleanly pop it.
 */
public class LabyScoreboardHook {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(0) Object stackRaw) {
        try {
            ScoreboardModule mod = ScoreboardModule.getInstance();
            if (mod != null && mod.isEnabled() && stackRaw != null) {
                net.labymod.api.client.render.matrix.Stack stack = (net.labymod.api.client.render.matrix.Stack) stackRaw;
                stack.push();
                stack.translate(9999f, 9999f, 0f);
                stack.scale(0f, 0f, 0f);
            }
        } catch (Throwable ignored) {}
    }

    @Advice.OnMethodExit
    public static void onExit(@Advice.Argument(0) Object stackRaw) {
        try {
            ScoreboardModule mod = ScoreboardModule.getInstance();
            if (mod != null && mod.isEnabled() && stackRaw != null) {
                net.labymod.api.client.render.matrix.Stack stack = (net.labymod.api.client.render.matrix.Stack) stackRaw;
                stack.pop();
            }
        } catch (Throwable ignored) {}
    }
}

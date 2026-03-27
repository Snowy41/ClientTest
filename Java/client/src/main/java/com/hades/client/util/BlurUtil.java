package com.hades.client.util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import java.nio.ByteBuffer;

/**
 * Gaussian blur utility for frosted-glass background effects.
 * Uses fullscreen-sized FBOs with scissored blur passes and gl_FragCoord sampling.
 */
public class BlurUtil {

    private static ShaderUtil blurShader;
    private static ShaderUtil roundedTexShader;
    private static int fboA = -1, fboB = -1;
    private static int texA = -1, texB = -1;
    private static int fboWidth = 0, fboHeight = 0;
    private static boolean initialized = false;
    private static boolean initFailed = false;
    private static int frameCount = 0;

    private static void init() {
        if (initialized) return;
        initialized = true;

        HadesLogger.get().info("[BlurUtil] Initializing shaders...");

        // Build unrolled blur shader on CPU
        int fixedRadius = 15;
        float sigma = fixedRadius / 2.0f;
        StringBuilder blurFragBuilder = new StringBuilder();
        blurFragBuilder.append("#version 120\n");
        blurFragBuilder.append("uniform sampler2D tex;\n");
        blurFragBuilder.append("uniform vec2 texelSize;\n");
        blurFragBuilder.append("uniform vec2 direction;\n");
        blurFragBuilder.append("void main() {\n");
        blurFragBuilder.append("    vec2 uv = gl_TexCoord[0].st;\n");
        blurFragBuilder.append("    vec4 color = texture2D(tex, uv);\n");
        float totalWeight = 1.0f;
        for (int i = 1; i <= fixedRadius; i++) {
            float weight = (float) Math.exp(-(i * i) / (2.0 * sigma * sigma));
            blurFragBuilder.append("    color += texture2D(tex, uv - ").append(i).append(".0 * texelSize * direction) * ").append(weight).append(";\n");
            blurFragBuilder.append("    color += texture2D(tex, uv + ").append(i).append(".0 * texelSize * direction) * ").append(weight).append(";\n");
            totalWeight += weight * 2.0f;
        }
        blurFragBuilder.append("    gl_FragColor = color / ").append(totalWeight).append(";\n");
        blurFragBuilder.append("}\n");
        String blurFrag = blurFragBuilder.toString();
        HadesLogger.get().info("[BlurUtil] Generated blur shader (" + blurFrag.length() + " chars), totalWeight=" + totalWeight);

        String roundedTexFrag = "#version 120\n" +
                "uniform sampler2D tex;\n" +
                "uniform vec2 size;\n" +
                "uniform float radius;\n" +
                "uniform vec4 tintColor;\n" +
                "uniform vec2 displaySize;\n" +
                "float roundedBoxSDF(vec2 center, vec2 halfSize, float r) {\n" +
                "    return length(max(abs(center) - halfSize + r, 0.0)) - r;\n" +
                "}\n" +
                "void main() {\n" +
                "    vec2 localUV = gl_TexCoord[0].st;\n" +
                "    vec2 pos = localUV * size;\n" +
                "    float dist = roundedBoxSDF(pos - size * 0.5, size * 0.5, radius);\n" +
                "    float alpha = 1.0 - smoothstep(0.0, 1.0, dist);\n" +
                "    if (alpha <= 0.001) discard;\n" +
                "    vec2 screenUV = gl_FragCoord.xy / displaySize;\n" +
                "    vec4 blurColor = texture2D(tex, screenUV);\n" +
                "    gl_FragColor = vec4(mix(blurColor.rgb, tintColor.rgb, tintColor.a), alpha);\n" +
                "}";

        try {
            blurShader = new ShaderUtil(blurFrag);
            roundedTexShader = new ShaderUtil(roundedTexFrag);
            HadesLogger.get().info("[BlurUtil] Shaders compiled OK");
        } catch (Exception e) {
            HadesLogger.get().error("[BlurUtil] Shader init FAILED", e);
            initFailed = true;
        }
    }

    private static void ensureFBO(int displayW, int displayH) {
        if (displayW == fboWidth && displayH == fboHeight && fboA != -1) return;

        HadesLogger.get().info("[BlurUtil] Creating FBOs: " + displayW + "x" + displayH);

        if (fboA != -1) { GL30.glDeleteFramebuffers(fboA); GL11.glDeleteTextures(texA); }
        if (fboB != -1) { GL30.glDeleteFramebuffers(fboB); GL11.glDeleteTextures(texB); }

        fboWidth = displayW;
        fboHeight = displayH;

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        // FBO A
        texA = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texA);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, displayW, displayH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_CLAMP_TO_EDGE);
        fboA = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texA, 0);
        int statusA = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        HadesLogger.get().info("[BlurUtil] FBO A status: " + statusA + " (expected " + GL30.GL_FRAMEBUFFER_COMPLETE + "), texA=" + texA + " fboA=" + fboA);

        // FBO B
        texB = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texB);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, displayW, displayH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_CLAMP_TO_EDGE);
        fboB = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboB);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texB, 0);
        int statusB = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        HadesLogger.get().info("[BlurUtil] FBO B status: " + statusB + " (expected " + GL30.GL_FRAMEBUFFER_COMPLETE + "), texB=" + texB + " fboB=" + fboB);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public static void drawBlurredRect(float x, float y, float width, float height,
                                        float radius, int tintColor, int passes, float mcScale) {
        init();
        if (initFailed || blurShader == null || roundedTexShader == null) return;

        // Convert to absolute pixel coordinates
        int px = (int) (x * mcScale);
        int py = (int) (y * mcScale);
        int pw = (int) (width * mcScale);
        int ph = (int) (height * mcScale);

        if (pw <= 0 || ph <= 0) return;

        int displayW = org.lwjgl.opengl.Display.getWidth();
        int displayH = org.lwjgl.opengl.Display.getHeight();
        if (displayW <= 0 || displayH <= 0) return;

        ensureFBO(displayW, displayH);

        // Flip Y to GL space (origin bottom-left)
        int glY = displayH - py - ph;

        // Padded region for blur bounds
        int padding = 30;
        int cPx = Math.max(0, px - padding);
        int cPy = Math.max(0, glY - padding);
        int cPw = Math.min(displayW, px + pw + padding) - cPx;
        int cPh = Math.min(displayH, glY + ph + padding) - cPy;

        int currentFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (cPw <= 0 || cPh <= 0) return; // Prevent GL_INVALID_VALUE in glScissor
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        passes = Math.max(1, passes);

        // Diagnostic logging (only first few frames)
        frameCount++;
        boolean doLog = frameCount <= 5;
        if (doLog) {
            HadesLogger.get().info("[BlurUtil] Frame " + frameCount + ": currentFBO=" + currentFBO
                    + " display=" + displayW + "x" + displayH
                    + " pill=(" + px + "," + py + " " + pw + "x" + ph + ")"
                    + " scissor=(" + cPx + "," + cPy + " " + cPw + "x" + cPh + ")"
                    + " passes=" + passes);
            // Check GL error before we start
            int err = GL11.glGetError();
            if (err != 0) HadesLogger.get().info("[BlurUtil] PRE-ERROR: " + err);
        }

        try {
            // Step 1: Blit from current FB to FBO A
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, currentFBO);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboA);
            GL30.glBlitFramebuffer(cPx, cPy, cPx + cPw, cPy + cPh,
                                   cPx, cPy, cPx + cPw, cPy + cPh,
                                   GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            if (doLog) {
                int err = GL11.glGetError();
                if (err != 0) HadesLogger.get().info("[BlurUtil] After blit ERROR: " + err);
                else HadesLogger.get().info("[BlurUtil] Blit OK");
            }

            // Step 2: Blur passes
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(cPx, cPy, cPw, cPh);
            GL11.glViewport(0, 0, displayW, displayH);

            for (int pass = 0; pass < passes; pass++) {
                // Horizontal: A → B
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboB);
                blurShader.useShader();
                int progId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                if (doLog && pass == 0) {
                    HadesLogger.get().info("[BlurUtil] Blur shader program active: " + progId);
                    int texLoc = GL20.glGetUniformLocation(progId, "tex");
                    int tsLoc = GL20.glGetUniformLocation(progId, "texelSize");
                    int dirLoc = GL20.glGetUniformLocation(progId, "direction");
                    HadesLogger.get().info("[BlurUtil] Uniform locs: tex=" + texLoc + " texelSize=" + tsLoc + " direction=" + dirLoc);
                }
                blurShader.setUniformi("tex", 0);
                blurShader.setUniformf("texelSize", 1.0f / displayW, 1.0f / displayH);
                blurShader.setUniformf("direction", 1.0f, 0.0f);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texA);
                drawFullscreenQuad();
                blurShader.releaseShader();

                if (doLog && pass == 0) {
                    int err = GL11.glGetError();
                    if (err != 0) HadesLogger.get().info("[BlurUtil] After H-blur ERROR: " + err);
                    else HadesLogger.get().info("[BlurUtil] H-blur pass OK");
                }

                // Vertical: B → A
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
                blurShader.useShader();
                blurShader.setUniformi("tex", 0);
                blurShader.setUniformf("texelSize", 1.0f / displayW, 1.0f / displayH);
                blurShader.setUniformf("direction", 0.0f, 1.0f);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texB);
                drawFullscreenQuad();
                blurShader.releaseShader();

                if (doLog && pass == 0) {
                    int err = GL11.glGetError();
                    if (err != 0) HadesLogger.get().info("[BlurUtil] After V-blur ERROR: " + err);
                    else HadesLogger.get().info("[BlurUtil] V-blur pass OK");
                }
            }

            // Step 3: Render rounded pill
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, currentFBO);
            GL11.glViewport(0, 0, displayW, displayH);
            if (!wasScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);

            // Re-enable cull face for other normal rendering if needed, though we just rely on standard state restore usually.
            // We explicitly restore GL_CULL_FACE at the end.

            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                                     GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            roundedTexShader.useShader();
            roundedTexShader.setUniformi("tex", 0);
            roundedTexShader.setUniformf("size", width, height);
            roundedTexShader.setUniformf("radius", radius);
            roundedTexShader.setUniformf("displaySize", (float) displayW, (float) displayH);

            float tR = ((tintColor >> 16) & 0xFF) / 255f;
            float tG = ((tintColor >> 8) & 0xFF) / 255f;
            float tB = (tintColor & 0xFF) / 255f;
            float tA = ((tintColor >> 24) & 0xFF) / 255f;
            roundedTexShader.setUniformf("tintColor", tR, tG, tB, tA);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            ShaderUtil.drawQuads(x, y, width, height);
            roundedTexShader.releaseShader();

            if (doLog) {
                int err = GL11.glGetError();
                if (err != 0) HadesLogger.get().info("[BlurUtil] After pill render ERROR: " + err);
                else HadesLogger.get().info("[BlurUtil] Pill render OK");
            }

        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, currentFBO);
            GL11.glViewport(0, 0, displayW, displayH);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            if (wasDepth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (wasBlend) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
            if (wasScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE); // Re-enable by default to avoid breaking MC
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private static void drawFullscreenQuad() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        // CCW Winding Order (Bottom-Left -> Bottom-Right -> Top-Right -> Top-Left)
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0); // Bottom-Left
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0); // Bottom-Right
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1); // Top-Right
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1); // Top-Left
        GL11.glEnd();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}

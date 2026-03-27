package com.hades.client.api.provider;

import com.hades.client.api.interfaces.IRenderer;
import com.hades.client.util.ShaderUtil;
import org.lwjgl.opengl.GL11;

public class ShaderRendererProvider implements IRenderer {
    
    private final Vanilla189Renderer fallbackRenderer = new Vanilla189Renderer();
    
    private ShaderUtil roundedRectShader;
    private ShaderUtil roundedGradientShader;
    private ShaderUtil roundedShadowShader;
    private boolean shadersInitialized = false;
    
    // State tracking
    private boolean wasBlend;
    private boolean wasTexture2D;
    
    public ShaderRendererProvider() {
        // Do NOT call initShaders() here — this constructor may be called
        // from the Hades-Init thread which has no OpenGL context.
        // Shaders are lazily initialized on the first render call from the GL thread.
    }
    
    /**
     * Lazily initializes shaders on the first call from the GL/render thread.
     * Safe to call repeatedly — will only initialize once.
     */
    private void ensureShaders() {
        if (shadersInitialized) return;
        shadersInitialized = true;
        initShaders();
    }
    
    private void initShaders() {
        String roundedRectFrag = "#version 120\n" +
                "uniform vec2 size;\n" +
                "uniform float radius;\n" +
                "uniform vec4 color;\n" +
                "float roundedBoxSDF(vec2 CenterPosition, vec2 Size, float Radius) {\n" +
                "    return length(max(abs(CenterPosition)-Size+Radius,0.0))-Radius;\n" +
                "}\n" +
                "void main() {\n" +
                "    vec2 pos = gl_TexCoord[0].st * size;\n" +
                "    vec2 halfSize = size * 0.5;\n" +
                "    float dist = roundedBoxSDF(pos - halfSize, halfSize, radius);\n" +
                "    float smoothedAlpha = 1.0 - smoothstep(0.0, 1.0, dist);\n" +
                "    if (smoothedAlpha <= 0.001) discard;\n" +
                "    gl_FragColor = vec4(color.rgb, color.a * smoothedAlpha);\n" +
                "}";
                
        String roundedGradientFrag = "#version 120\n" +
                "uniform vec2 size;\n" +
                "uniform float radius;\n" +
                "uniform vec4 color1;\n" +
                "uniform vec4 color2;\n" +
                "uniform int vertical;\n" + // 1 for vertical, 0 for horizontal
                "float roundedBoxSDF(vec2 CenterPosition, vec2 Size, float Radius) {\n" +
                "    return length(max(abs(CenterPosition)-Size+Radius,0.0))-Radius;\n" +
                "}\n" +
                "void main() {\n" +
                "    vec2 st = gl_TexCoord[0].st;\n" +
                "    vec2 pos = st * size;\n" +
                "    vec2 halfSize = size * 0.5;\n" +
                "    float dist = roundedBoxSDF(pos - halfSize, halfSize, radius);\n" +
                "    float smoothedAlpha = 1.0 - smoothstep(0.0, 1.0, dist);\n" +
                "    if (smoothedAlpha <= 0.001) discard;\n" +
                "    vec4 mixedColor = mix(color1, color2, vertical == 1 ? st.y : st.x);\n" +
                "    gl_FragColor = vec4(mixedColor.rgb, mixedColor.a * smoothedAlpha);\n" +
                "}";

        String roundedShadowFrag = "#version 120\n" +
                "uniform vec2 size;\n" +
                "uniform vec2 innerSize;\n" +
                "uniform float radius;\n" +
                "uniform float shadowSize;\n" +
                "uniform vec4 color;\n" +
                "float roundedBoxSDF(vec2 CenterPosition, vec2 Size, float Radius) {\n" +
                "    return length(max(abs(CenterPosition)-Size+Radius,0.0))-Radius;\n" +
                "}\n" +
                "void main() {\n" +
                "    vec2 pos = gl_TexCoord[0].st * size;\n" +
                "    vec2 halfSize = size * 0.5;\n" +
                "    vec2 innerHalf = innerSize * 0.5;\n" +
                "    float dist = roundedBoxSDF(pos - halfSize, innerHalf, radius);\n" +
                "    if(dist <= 0.0) discard;\n" +
                "    float alpha = 1.0 - smoothstep(0.0, shadowSize, dist);\n" +
                "    if(alpha <= 0.001) discard;\n" +
                "    gl_FragColor = vec4(color.rgb, color.a * alpha);\n" +
                "}";

        try {
            roundedRectShader = new ShaderUtil(roundedRectFrag);
            roundedGradientShader = new ShaderUtil(roundedGradientFrag);
            roundedShadowShader = new ShaderUtil(roundedShadowFrag);
        } catch (Exception e) {
            System.err.println("Failed to initialize ShaderRendererProvider shaders.");
        }
    }

    private void setupGL() {
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_ENABLE_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST); // Vital: prevent alpha clipping creating a solid box
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        org.lwjgl.opengl.GL11.glDepthMask(false);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        // Use glBlendFuncSeparate to ensure we don't zero out the framebuffer's alpha channel!
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void restoreGL() {
        org.lwjgl.opengl.GL11.glPopAttrib();
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private float[] getRGBA(int color) {
        return new float[] {
            ((color >> 16) & 0xFF) / 255f,
            ((color >> 8) & 0xFF) / 255f,
            (color & 0xFF) / 255f,
            ((color >> 24) & 0xFF) / 255f
        };
    }

    @Override
    public void drawRect(float x, float y, float width, float height, int color) {
        fallbackRenderer.drawRect(x, y, width, height, color); // Simple quads are fine for raw rects
    }

    @Override
    public void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        ensureShaders();
        if (roundedRectShader == null) {
            fallbackRenderer.drawRoundedRect(x, y, width, height, radius, color);
            return;
        }
        int previousProgram = 0;
        try { previousProgram = org.lwjgl.opengl.GL11.glGetInteger(0x8B8D); } catch (Throwable t) {}
        
        setupGL();
        try {
            roundedRectShader.useShader();
            roundedRectShader.setUniformf("size", width, height);
            roundedRectShader.setUniformf("radius", radius);
            roundedRectShader.setUniformf("color", getRGBA(color));
            
            ShaderUtil.drawQuads(x, y, width, height);
        } finally {
            try { org.lwjgl.opengl.GL20.glUseProgram(previousProgram); } catch (Throwable t) {}
            restoreGL();
        }
    }

    @Override
    public void drawRoundedRect(float x, float y, float width, float height, float radTL, float radTR, float radBR, float radBL, int color) {
        // Fallback for unequal radii (we could write a complex shader, but usually max(radius) is okay for Slinky style)
        drawRoundedRect(x, y, width, height, Math.max(Math.max(radTL, radTR), Math.max(radBR, radBL)), color);
    }

    @Override
    public void drawGradientRect(float x, float y, float width, float height, int colorTop, int colorBottom) {
        fallbackRenderer.drawGradientRect(x, y, width, height, colorTop, colorBottom); // Simple quads fine
    }

    @Override
    public void drawHorizontalGradient(float x, float y, float width, float height, int colorLeft, int colorRight) {
        fallbackRenderer.drawHorizontalGradient(x, y, width, height, colorLeft, colorRight); // Simple quads fine
    }

    @Override
    public void drawRoundedGradientRect(float x, float y, float width, float height, float radius, int colorTop, int colorBottom) {
        ensureShaders();
        if (roundedGradientShader == null) {
            fallbackRenderer.drawRoundedGradientRect(x, y, width, height, radius, colorTop, colorBottom);
            return;
        }
        int previousProgram = 0;
        try { previousProgram = org.lwjgl.opengl.GL11.glGetInteger(0x8B8D); } catch (Throwable t) {}
        
        setupGL();
        try {
            roundedGradientShader.useShader();
            roundedGradientShader.setUniformf("size", width, height);
            roundedGradientShader.setUniformf("radius", radius);
            roundedGradientShader.setUniformf("color1", getRGBA(colorTop));
            roundedGradientShader.setUniformf("color2", getRGBA(colorBottom));
            roundedGradientShader.setUniformf("horizontal", 0f);
            
            ShaderUtil.drawQuads(x, y, width, height);
        } finally {
            try { org.lwjgl.opengl.GL20.glUseProgram(previousProgram); } catch (Throwable t) {}
            restoreGL();
        }
    }

    @Override
    public void drawRoundedShadow(float x, float y, float width, float height, float radius, float shadowSize) {
        ensureShaders();
        if (roundedShadowShader == null) return;
        
        float eX = x - shadowSize;
        float eY = y - shadowSize;
        float eW = width + shadowSize * 2;
        float eH = height + shadowSize * 2;
        
        int previousProgram = 0;
        try { previousProgram = org.lwjgl.opengl.GL11.glGetInteger(0x8B8D); } catch (Throwable t) {}
        
        setupGL();
        try {
            roundedShadowShader.useShader();
            roundedShadowShader.setUniformf("size", eW, eH);
            roundedShadowShader.setUniformf("innerSize", width, height);
            roundedShadowShader.setUniformf("radius", radius);
            roundedShadowShader.setUniformf("shadowSize", shadowSize);
            // Subtle black shadow
            roundedShadowShader.setUniformf("color", 0f, 0f, 0f, 0.25f);
            
            ShaderUtil.drawQuads(eX, eY, eW, eH);
        } finally {
            try { org.lwjgl.opengl.GL20.glUseProgram(previousProgram); } catch (Throwable t) {}
            restoreGL();
        }
    }

    // Delegate the rest to fallback Renderer
    
    @Override
    public void drawString(String text, float x, float y, int color, float scale) {
        fallbackRenderer.drawString(text, x, y, color, scale);
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        fallbackRenderer.drawString(text, x, y, color);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color, float scale) {
        fallbackRenderer.drawStringWithShadow(text, x, y, color, scale);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        fallbackRenderer.drawStringWithShadow(text, x, y, color);
    }

    @Override
    public void drawCenteredString(String text, float x, float y, int color, float scale) {
        fallbackRenderer.drawCenteredString(text, x, y, color, scale);
    }

    @Override
    public void drawCenteredString(String text, float x, float y, int color) {
        fallbackRenderer.drawCenteredString(text, x, y, color);
    }

    @Override
    public float getStringWidth(String text, float scale) {
        return fallbackRenderer.getStringWidth(text, scale);
    }

    @Override
    public float getStringWidth(String text) {
        return fallbackRenderer.getStringWidth(text);
    }

    @Override
    public float getFontHeight(float scale) {
        return fallbackRenderer.getFontHeight(scale);
    }

    @Override
    public float getFontHeight() {
        return fallbackRenderer.getFontHeight();
    }

    @Override
    public void drawString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {
        fallbackRenderer.drawString(text, x, y, color, size, bold, italic, shadow);
    }

    @Override
    public void drawCenteredString(String text, float x, float y, int color, float size, boolean bold, boolean italic, boolean shadow) {
        fallbackRenderer.drawCenteredString(text, x, y, color, size, bold, italic, shadow);
    }

    @Override
    public float getStringWidth(String text, float size, boolean bold, boolean italic) {
        return fallbackRenderer.getStringWidth(text, size, bold, italic);
    }

    @Override
    public float getFontHeight(float size, boolean bold, boolean italic) {
        return fallbackRenderer.getFontHeight(size, bold, italic);
    }

    @Override
    public boolean drawImage(String namespace, String path, float x, float y, float width, float height) {
        return fallbackRenderer.drawImage(namespace, path, x, y, width, height);
    }

    @Override
    public void enableScissor(float x, float y, float width, float height) {
        fallbackRenderer.enableScissor(x, y, width, height);
    }

    @Override
    public void disableScissor() {
        fallbackRenderer.disableScissor();
    }

    @Override
    public void runWithScissor(float x, float y, float width, float height, Runnable action) {
        fallbackRenderer.runWithScissor(x, y, width, height, action);
    }

    @Override
    public double getRenderPosX() {
        return fallbackRenderer.getRenderPosX();
    }

    @Override
    public double getRenderPosY() {
        return fallbackRenderer.getRenderPosY();
    }

    @Override
    public double getRenderPosZ() {
        return fallbackRenderer.getRenderPosZ();
    }
}

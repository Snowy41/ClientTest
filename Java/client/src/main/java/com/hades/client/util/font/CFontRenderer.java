package com.hades.client.util.font;

import org.lwjgl.opengl.GL11;
import java.awt.Font;

/**
 * Custom TTF Font Renderer with full bold/italic/shadow support.
 * Each style uses its own pre-baked texture atlas (Font.BOLD, Font.ITALIC, Font.BOLD|Font.ITALIC).
 * Fully standalone — no Minecraft or LabyMod dependencies for text rendering.
 */
public class CFontRenderer extends CFont {

    protected CharData[] boldChars = new CharData[256];
    protected CharData[] italicChars = new CharData[256];
    protected CharData[] boldItalicChars = new CharData[256];

    protected int boldTex;
    protected int italicTex;
    protected int boldItalicTex;

    private final int[] colorCode = new int[32];

    public CFontRenderer(Font font, boolean antiAlias, boolean fractionalMetrics) {
        super(font, antiAlias, fractionalMetrics);
        setupMinecraftColorcodes();
        setupBoldItalicIDs();
    }

    // ── Public styled API ──────────────────────────────────────────

    public float drawStringWithShadow(String text, double x, double y, int color) {
        float shadowWidth = drawString(text, x + 1.0D, y + 1.0D, color, true, false, false);
        return Math.max(shadowWidth, drawString(text, x, y, color, false, false, false));
    }

    public float drawString(String text, float x, float y, int color) {
        return drawString(text, x, y, color, false, false, false);
    }

    public float drawCenteredString(String text, float x, float y, int color) {
        return drawString(text, x - getStringWidth(text) / 2.0f, y, color);
    }

    /**
     * Draw text with explicit bold/italic/shadow styling.
     * This selects the proper pre-baked texture atlas for the style.
     */
    public float drawString(String text, double x, double y, int color, boolean shadow, boolean bold, boolean italic) {
        if (shadow) {
            drawStringInternal(text, x + 1.0D, y + 1.0D, color, true, bold, italic);
        }
        return drawStringInternal(text, x, y, color, false, bold, italic);
    }

    /**
     * Core rendering method. Draws text character-by-character using the appropriate atlas.
     */
    private float drawStringInternal(String text, double x, double y, int color, boolean shadow, boolean forceBold, boolean forceItalic) {
        x -= 1;

        if (text == null) {
            return 0.0F;
        }

        if (color == 553648127) {
            color = 16777215;
        }

        if ((color & 0xFC000000) == 0) {
            color |= -16777216;
        }

        if (shadow) {
            color = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
        }

        // Select initial atlas based on forced style
        CharData[] currentData;
        int currentTex;
        if (forceBold && forceItalic) {
            currentData = this.boldItalicChars;
            currentTex = this.boldItalicTex;
        } else if (forceBold) {
            currentData = this.boldChars;
            currentTex = this.boldTex;
        } else if (forceItalic) {
            currentData = this.italicChars;
            currentTex = this.italicTex;
        } else {
            currentData = this.charData;
            currentTex = this.tex;
        }

        float alpha = (color >> 24 & 0xFF) / 255.0F;
        boolean bold = forceBold;
        boolean italic = forceItalic;
        boolean strike = false;
        boolean underline = false;

        x *= 2.0D;
        y = (y - 1.0D) * 2.0D;

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        GL11.glEnable(GL11.GL_BLEND);
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTex);

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);

            if (character == '§') {
                int colorIndex = 21;

                try {
                    colorIndex = "0123456789abcdefklmnor".indexOf(text.charAt(i + 1));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (colorIndex < 16) {
                    bold = forceBold;
                    italic = forceItalic;
                    strike = false;
                    underline = false;
                    // Reset to default style atlas
                    currentData = getCharDataForStyle(bold, italic);
                    currentTex = getTexForStyle(bold, italic);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTex);

                    if (colorIndex < 0) {
                        colorIndex = 15;
                    }

                    if (shadow) {
                        colorIndex += 16;
                    }

                    int colorcode = this.colorCode[colorIndex];
                    GL11.glColor4f((colorcode >> 16 & 0xFF) / 255.0F, (colorcode >> 8 & 0xFF) / 255.0F,
                            (colorcode & 0xFF) / 255.0F, alpha);
                } else if (colorIndex == 17) { // Bold
                    bold = true;
                    currentData = getCharDataForStyle(bold, italic);
                    currentTex = getTexForStyle(bold, italic);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTex);
                } else if (colorIndex == 18) { // Strikethrough
                    strike = true;
                } else if (colorIndex == 19) { // Underline
                    underline = true;
                } else if (colorIndex == 20) { // Italic
                    italic = true;
                    currentData = getCharDataForStyle(bold, italic);
                    currentTex = getTexForStyle(bold, italic);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTex);
                } else if (colorIndex == 21) { // Reset
                    bold = forceBold;
                    italic = forceItalic;
                    strike = false;
                    underline = false;
                    GL11.glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                            (color & 0xFF) / 255.0F, alpha);
                    currentData = getCharDataForStyle(bold, italic);
                    currentTex = getTexForStyle(bold, italic);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTex);
                }

                i++;
            } else if (character < currentData.length && character >= 0) {
                GL11.glBegin(GL11.GL_QUADS);
                drawChar(currentData, character, (float) x, (float) y);
                GL11.glEnd();

                if (strike) {
                    drawLine(x, y + currentData[character].height / 2f, x + currentData[character].width - 8.0D,
                            y + currentData[character].height / 2f, 1.0F);
                }

                if (underline) {
                    drawLine(x, y + currentData[character].height - 2.0D, x + currentData[character].width - 8.0D,
                            y + currentData[character].height - 2.0D, 1.0F);
                }

                x += currentData[character].width - 8 + this.charOffset;
            }
        }

        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_DONT_CARE);

        GL11.glPopMatrix();

        // Restore texture and color to MC defaults securely
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        GL11.glPopAttrib();
        if (!wasBlend) GL11.glDisable(GL11.GL_BLEND);
        if (!wasTexture2D) GL11.glDisable(GL11.GL_TEXTURE_2D);

        return (float) x / 2.0F;
    }

    // ── Metrics ────────────────────────────────────────────────────

    @Override
    public int getStringWidth(String text) {
        return getStringWidth(text, false, false);
    }

    /** Measure text width using the correct style atlas */
    public int getStringWidth(String text, boolean bold, boolean italic) {
        if (text == null) {
            return 0;
        }

        int width = 0;
        CharData[] currentData = getCharDataForStyle(bold, italic);
        int size = text.length();

        for (int i = 0; i < size; i++) {
            char character = text.charAt(i);

            if (character == '§') {
                i++;
            } else if (character < currentData.length && character >= 0) {
                width += currentData[character].width - 8 + this.charOffset;
            }
        }

        return width / 2;
    }

    /** Get font height for a specific style */
    public int getHeight(boolean bold, boolean italic) {
        // All styles share the same base font size, height is consistent
        return getHeight();
    }

    // ── Style helpers ──────────────────────────────────────────────

    private CharData[] getCharDataForStyle(boolean bold, boolean italic) {
        if (bold && italic) return this.boldItalicChars;
        if (bold) return this.boldChars;
        if (italic) return this.italicChars;
        return this.charData;
    }

    private int getTexForStyle(boolean bold, boolean italic) {
        if (bold && italic) return this.boldItalicTex;
        if (bold) return this.boldTex;
        if (italic) return this.italicTex;
        return this.tex;
    }

    // ── Initialization ─────────────────────────────────────────────

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        setupBoldItalicIDs();
    }

    @Override
    public void setAntiAlias(boolean antiAlias) {
        super.setAntiAlias(antiAlias);
        setupBoldItalicIDs();
    }

    @Override
    public void setFractionalMetrics(boolean fractionalMetrics) {
        super.setFractionalMetrics(fractionalMetrics);
        setupBoldItalicIDs();
    }

    /**
     * Generate separate texture atlases for bold, italic, and bold+italic styles.
     * Each atlas uses a Java2D Font.deriveFont() with the appropriate style flag,
     * ensuring true typographic rendering, not fake bolding/italicizing.
     */
    private void setupBoldItalicIDs() {
        Font boldFont = this.font.deriveFont(Font.BOLD, this.font.getSize2D());
        Font italicFont = this.font.deriveFont(Font.ITALIC, this.font.getSize2D());
        Font boldItalicFont = this.font.deriveFont(Font.BOLD | Font.ITALIC, this.font.getSize2D());

        boldTex = setupMinecraftFont(boldFont, this.antiAlias, this.fractionalMetrics, this.boldChars);
        italicTex = setupMinecraftFont(italicFont, this.antiAlias, this.fractionalMetrics, this.italicChars);
        boldItalicTex = setupMinecraftFont(boldItalicFont, this.antiAlias, this.fractionalMetrics, this.boldItalicChars);
    }

    // ── Private rendering helpers ──────────────────────────────────

    private void drawLine(double x, double y, double x1, double y1, float width) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void setupMinecraftColorcodes() {
        for (int index = 0; index < 32; index++) {
            int noClue = (index >> 3 & 0x1) * 85;
            int red = (index >> 2 & 0x1) * 170 + noClue;
            int green = (index >> 1 & 0x1) * 170 + noClue;
            int blue = (index & 0x1) * 170 + noClue;

            if (index == 6) {
                red += 85;
            }

            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }

            this.colorCode[index] = (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
        }
    }
}

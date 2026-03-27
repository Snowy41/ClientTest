package com.hades.client.gui.clickgui.theme;

import com.hades.client.api.HadesAPI;

/**
 * Modern dark theme with accent gradients, inspired by Slinky/Vape style.
 */
public class Theme {

    // ── Windows & Panels ──
    public static final int WINDOW_BG = HadesAPI.Render.color(10, 10, 12, 250); // Very dark, almost solid
    public static final int WINDOW_BORDER = HadesAPI.Render.color(21, 21, 26, 255);
    public static final int WINDOW_OUTLINE = HadesAPI.Render.color(28, 28, 34, 255); // Inner glossy line
    public static final float WINDOW_RADIUS = 4f; // Sharp
    public static final float SHADOW_SIZE = 12f; // Smaller, tighter shadow
    public static final int SHADOW_COLOR = HadesAPI.Render.color(0, 0, 0, 160);

    // ── Sidebar ──
    public static final int SIDEBAR_BG = HadesAPI.Render.color(14, 14, 18, 255); // Lighter than background but almost opaque
    public static final int SIDEBAR_WIDTH = 120; // Adjusted for sleek text layout
    public static final int SIDEBAR_ICON_SIZE = 24;
    public static final int SIDEBAR_SEPARATOR = HadesAPI.Render.color(40, 40, 45, 255);

    // ── Category Tabs ──
    public static final int TAB_BG = HadesAPI.Render.color(0, 0, 0, 0); // Transparent tabs
    public static int TAB_ACTIVE = HadesAPI.Render.color(255, 255, 255, 255); // White Accent
    public static final int TAB_HOVER = HadesAPI.Render.color(255, 255, 255, 15);
    public static final int TAB_HEIGHT = 40;

    // ── Accent / Gradients ──
    public static int ACCENT_PRIMARY = HadesAPI.Render.color(255, 255, 255); // Pure White
    public static int ACCENT_SECONDARY = HadesAPI.Render.color(200, 200, 200); // Light Gray
    public static int ACCENT_GRADIENT_START = HadesAPI.Render.color(255, 255, 255);
    public static int ACCENT_GRADIENT_END = HadesAPI.Render.color(200, 200, 200);

    // ── Module Cards ──
    public static final int MODULE_BG = HadesAPI.Render.color(20, 20, 24, 255); // Solid dark gray
    public static final int MODULE_BG_HOVER = HadesAPI.Render.color(28, 28, 34, 255); // Solid hover lightening
    public static final int MODULE_BG_ENABLED = HadesAPI.Render.color(35, 35, 42, 255); // Solid enabled state
    public static final int MODULE_CARD_RADIUS = 3; // Sharp corners
    public static final int MODULE_CARD_HEIGHT = 36; // Very compact vertically
    public static final int MODULE_CARD_MARGIN = 4; // Tighter vertical stacking

    // ── Settings ──
    public static final int SETTING_BG = HadesAPI.Render.color(0, 0, 0, 0); // No bulky boxes, rely on group line
    public static final int SETTING_HEIGHT = 20; // Thinner settings
    public static final int SETTING_PADDING = 8; // Less gap between settings

    // ── Toggle Switch ──
    public static int TOGGLE_ON = ACCENT_PRIMARY;
    public static final int TOGGLE_OFF = HadesAPI.Render.color(65, 65, 75);
    public static final int TOGGLE_KNOB = HadesAPI.Render.color(255, 255, 255);
    public static final int TOGGLE_WIDTH = 28;
    public static final int TOGGLE_HEIGHT = 14;

    // ── Slider ──
    public static final int SLIDER_BG = HadesAPI.Render.color(45, 45, 52); // Darker slider track
    public static int SLIDER_FILL = ACCENT_PRIMARY;
    public static final int SLIDER_KNOB = HadesAPI.Render.color(255, 255, 255);
    public static final int SLIDER_HEIGHT = 4; // Thinner, sleek bar
    public static final int SLIDER_KNOB_SIZE = 10;

    // ── Checkbox ──
    public static int CHECKBOX_ON = ACCENT_PRIMARY;
    public static final int CHECKBOX_OFF = HadesAPI.Render.color(50, 50, 55);
    public static final int CHECKBOX_SIZE = 14;

    // ── Text ──
    public static final int TEXT_PRIMARY = HadesAPI.Render.color(245, 245, 250);
    public static final int TEXT_SECONDARY = HadesAPI.Render.color(180, 180, 190);
    public static final int TEXT_MUTED = HadesAPI.Render.color(120, 120, 130);
    public static int TEXT_ACCENT = ACCENT_SECONDARY;

    public static void applyTheme(int primary, int secondary, int gradientStart, int gradientEnd) {
        ACCENT_PRIMARY = primary;
        ACCENT_SECONDARY = secondary;
        ACCENT_GRADIENT_START = gradientStart;
        ACCENT_GRADIENT_END = gradientEnd;

        TAB_ACTIVE = primary;
        TOGGLE_ON = primary;
        SLIDER_FILL = primary;
        CHECKBOX_ON = primary;
        TEXT_ACCENT = secondary;
    }

    // ── Scrollbar ──
    public static final int SCROLLBAR_BG = HadesAPI.Render.color(30, 30, 35);
    public static final int SCROLLBAR_THUMB = HadesAPI.Render.color(60, 60, 70);
    public static final int SCROLLBAR_WIDTH = 4;

    // ── Spacing ──
    public static final int PADDING = 10;
    public static final int PADDING_SMALL = 5;
    public static final int PADDING_LARGE = 20;

    // ── Window Dimensions ──
    public static final int DEFAULT_WIDTH = 520;
    public static final int DEFAULT_HEIGHT = 360;
}

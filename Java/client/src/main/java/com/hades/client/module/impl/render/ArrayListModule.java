package com.hades.client.module.impl.render;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HudAvoidanceUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArrayListModule extends Module {

    private final ModeSetting colorTheme = new ModeSetting("Color Theme", "Orange", "Orange", "Rainbow", "Fade",
            "White");
    private final ModeSetting sortMode = new ModeSetting("Sort Mode", "Width", "Width", "Alphabetical");
    private final BooleanSetting showSuffix = new BooleanSetting("Show Suffix", true);
    private final ModeSetting alignment = new ModeSetting("Alignment", "Right", "Right", "Left");
    private final BooleanSetting blurBackground = new BooleanSetting("Blur Background", true);
    private final NumberSetting blurPasses = new NumberSetting("Blur Passes", 2, 1, 3, 1);

    public final NumberSetting listX = new NumberSetting("List X", -1, -1, 4000, 1); // -1 = Dynamic right align
    public final NumberSetting listY = new NumberSetting("List Y", 5, 0, 4000, 1);

    private final Map<Module, Float> moduleAnimations = new HashMap<>();

    public ArrayListModule() {
        super("ArrayList", "Displays enabled modules", Module.Category.HUD, 0);
        this.register(alignment);
        this.register(colorTheme);
        this.register(sortMode);
        this.register(showSuffix);
        this.register(blurBackground);
        this.register(blurPasses);

        listX.setHidden(true);
        listY.setHidden(true);
        this.register(listX);
        this.register(listY);

        blurPasses.setVisibility(() -> blurBackground.getValue());

        this.setEnabled(true);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (HadesAPI.Player.isNull())
            return;

        List<Module> modules = HadesClient.getInstance().getModuleManager().getModules();

        // Build display text for width calculation
        List<ModuleEntry> sorted = modules.stream()
                .filter(m -> m.isEnabled() || moduleAnimations.getOrDefault(m, 0f) > 0.01f)
                .filter(m -> m.getCategory() != Module.Category.HUD)
                .map(m -> new ModuleEntry(m, buildDisplayText(m)))
                .sorted((e1, e2) -> {
                    if (sortMode.getValue().equals("Alphabetical")) {
                        return e1.module.getName().compareToIgnoreCase(e2.module.getName());
                    } else {
                        float w1 = HadesAPI.Render.getStringWidth(e1.displayText, 14f, false, false);
                        float w2 = HadesAPI.Render.getStringWidth(e2.displayText, 14f, false, false);
                        return Float.compare(w2, w1);
                    }
                })
                .collect(Collectors.toList());

        int scaledWidth = event.getScaledWidth();
        int scaledHeight = event.getScaledHeight();

        float currentY = listY.getValue().floatValue();
        float fontSize = 14f;
        float fontHeight = HadesAPI.Render.getFontHeight(fontSize, false, false);
        float paddingX = 10f;
        float paddingY = 5f;
        float rowHeight = fontHeight + paddingY * 2;
        float gap = 2f; // Gap between pills
        float pillRadius = 4f;

        boolean isRightAligned = alignment.getValue().equals("Right");

        // Handle old auto-right align logic fallback smoothly
        if (listX.getValue().floatValue() == -1f) {
            isRightAligned = true;
        }

        // MC GUI scale factor for blur
        float mcScale = 1f;
        try {
            int[] sr = HadesAPI.Game.getScaledResolution();
            if (sr[0] > 0)
                mcScale = (float) org.lwjgl.opengl.Display.getWidth() / sr[0];
        } catch (Throwable ignored) {
        }

        // --- PASS 1: Animation Logic ---
        float simY = currentY;
        for (int i = 0; i < sorted.size(); i++) {
            ModuleEntry entry = sorted.get(i);
            float currentAnim = moduleAnimations.getOrDefault(entry.module, 0f);
            float targetAnim = entry.module.isEnabled() ? 1f : 0f;
            currentAnim = currentAnim + (targetAnim - currentAnim) * 0.15f;
            moduleAnimations.put(entry.module, currentAnim);
        }

        // --- PASS 2: Render Content ---
        for (int i = 0; i < sorted.size(); i++) {
            ModuleEntry entry = sorted.get(i);
            Module module = entry.module;
            String displayText = entry.displayText;
            String suffix = entry.suffix;

            float textWidth = HadesAPI.Render.getStringWidth(displayText, fontSize, false, false);

            float currentAnim = moduleAnimations.getOrDefault(module, 0f);
            if (currentAnim < 0.01f)
                continue;

            float fullWidth = textWidth + paddingX * 2;

            float renderX;
            if (isRightAligned) {
                float customX = listX.getValue().floatValue() == -1f ? scaledWidth : listX.getValue().floatValue();
                renderX = customX - (fullWidth * currentAnim) - 4f;
            } else {
                float customX = listX.getValue().floatValue() == -1f ? 5f : listX.getValue().floatValue();
                renderX = customX - (fullWidth * (1 - currentAnim));
            }

            float safeY = HudAvoidanceUtil.findSafeY(renderX, currentY, fullWidth, rowHeight, scaledWidth,
                    scaledHeight);

            int accentColor = getColor(i, sorted.size());

            // ── Background & Shadow ──
            HadesAPI.Render.drawRoundedShadow(renderX, safeY, fullWidth, rowHeight - 1f, pillRadius, 5f);
            
            // Frosted glass blur background directly per-pill
            if (blurBackground.getValue()) {
                try {
                    int tint = HadesAPI.Render.colorWithAlpha(0xFF0A0A0C, 40); 
                    com.hades.client.util.BlurUtil.drawBlurredRect(
                            renderX, safeY, fullWidth, rowHeight - 1f,
                            pillRadius, tint, blurPasses.getValue().intValue(), mcScale);
                } catch (Throwable t) {
                    HadesAPI.Render.drawRoundedRect(renderX, safeY, fullWidth, rowHeight - 1f,
                            pillRadius, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
                }
            } else {
                HadesAPI.Render.drawRoundedRect(renderX, safeY, fullWidth, rowHeight - 1f,
                        pillRadius, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
            }

            // ── Left accent bar (thin 2px rounded) ──
            if (isRightAligned) {
                HadesAPI.Render.drawRoundedRect(renderX + fullWidth - 2f, safeY + 3f,
                        2f, rowHeight - 7f, 1f, accentColor);
            } else {
                HadesAPI.Render.drawRoundedRect(renderX, safeY + 3f,
                        2f, rowHeight - 7f, 1f, accentColor);
            }

            // ── Module name (white) ──
            float textX = isRightAligned ? renderX + paddingX - 1f : renderX + paddingX + 2f;
            HadesAPI.Render.drawString(module.getName(), textX, safeY + paddingY,
                    Theme.TEXT_PRIMARY, fontSize, true, false, true); // Bold text shadow

            // ── Suffix (muted, after name) ──
            if (suffix != null) {
                float nameWidth = HadesAPI.Render.getStringWidth(module.getName(), fontSize, true, false);
                HadesAPI.Render.drawString(" " + suffix, textX + nameWidth, safeY + paddingY,
                        Theme.TEXT_MUTED, fontSize, false, false, true); // Added text shadow
            }

            // Stack next module below
            currentY = safeY + (rowHeight + gap) * currentAnim;
        }
    }

    private String buildDisplayText(Module module) {
        String name = module.getName();
        String suffix = null;
        if (showSuffix.getValue()) {
            suffix = module.getDisplaySuffix();
        }
        if (suffix != null) {
            return name + " " + suffix;
        }
        return name;
    }

    private int getColor(int index, int total) {
        String theme = colorTheme.getValue();

        if ("White".equals(theme)) {
            return Theme.TEXT_PRIMARY;
        }

        if ("Rainbow".equals(theme)) {
            float offset = (System.currentTimeMillis() % 3000) / 3000f;
            float hue = (index / (float) Math.max(1, total) + offset) % 1f;
            return java.awt.Color.HSBtoRGB(hue, 0.65f, 1.0f) | 0xFF000000;
        }

        if ("Fade".equals(theme)) {
            float ratio = total > 1 ? index / (float) (total - 1) : 0f;
            return HadesAPI.Render.lerpColor(Theme.ACCENT_PRIMARY, Theme.ACCENT_SECONDARY, ratio);
        }

        // "Orange" — animated wave
        float offset = (System.currentTimeMillis() % 2000) / 2000f;
        float wave = (float) Math.sin((index / (float) Math.max(1, total) - offset) * Math.PI * 2) * 0.5f + 0.5f;
        return HadesAPI.Render.lerpColor(Theme.ACCENT_GRADIENT_START, Theme.ACCENT_GRADIENT_END, wave);
    }

    /** Internal helper to pair a module with its pre-computed display text */
    private class ModuleEntry {
        final Module module;
        final String displayText;
        final String suffix;

        ModuleEntry(Module module, String displayText) {
            this.module = module;
            this.displayText = displayText;
            this.suffix = showSuffix.getValue() ? module.getDisplaySuffix() : null;
        }
    }
}

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
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeybindsModule extends Module {

    private final ModeSetting colorTheme = new ModeSetting("Color Theme", "Orange", "Orange", "Rainbow", "Fade", "White");
    private final BooleanSetting blurBackground = new BooleanSetting("Blur Background", true);
    private final NumberSetting blurPasses = new NumberSetting("Blur Passes", 2, 1, 3, 1);

    // Draggable positions
    public final NumberSetting listX = new NumberSetting("List X", 10, 0, 4000, 1); 
    public final NumberSetting listY = new NumberSetting("List Y", 60, 0, 4000, 1);

    private final Map<Module, Float> moduleAnimations = new HashMap<>();

    public KeybindsModule() {
        super("KeybindsHUD", "Displays active module keybinds", Category.HUD, 0);
        this.register(colorTheme);
        this.register(blurBackground);
        this.register(blurPasses);
        
        listX.setHidden(true);
        listY.setHidden(true);
        this.register(listX);
        this.register(listY);
        
        blurPasses.setVisibility(() -> blurBackground.getValue());
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (HadesAPI.Player.isNull()) return;

        List<Module> modules = HadesClient.getInstance().getModuleManager().getModules();

        // Build list of modules that have a keybind
        List<ModuleEntry> sorted = modules.stream()
            .filter(m -> m.getKeyBind() != Keyboard.KEY_NONE)
            .map(m -> new ModuleEntry(m, buildDisplayText(m)))
            .sorted((e1, e2) -> {
                float w1 = HadesAPI.Render.getStringWidth(e1.displayText, 14f, false, false);
                float w2 = HadesAPI.Render.getStringWidth(e2.displayText, 14f, false, false);
                return Float.compare(w2, w1);
            })
            .collect(Collectors.toList());

        if (sorted.isEmpty() && !HadesClient.getInstance().getClickGUI().isVisible()) return;

        float currentX = listX.getValue().floatValue();
        float currentY = listY.getValue().floatValue();
        float fontSize = 14f;
        float fontHeight = HadesAPI.Render.getFontHeight(fontSize, false, false);
        float paddingX = 8f;
        float paddingY = 4f;
        float rowHeight = fontHeight + paddingY * 2;
        float gap = 2f; 
        float pillRadius = 4f;

        // MC GUI scale factor for blur
        float mcScale = 1f;
        try {
            int[] sr = HadesAPI.Game.getScaledResolution();
            if (sr[0] > 0) mcScale = (float) org.lwjgl.opengl.Display.getWidth() / sr[0];
        } catch (Throwable ignored) {}

        // --- Draw Header ---
        String headerText = "Keybinds";
        float headerWidth = HadesAPI.Render.getStringWidth(headerText, 15f, true, false) + paddingX * 2;
        float maxContentWidth = headerWidth;
        for (ModuleEntry entry : sorted) {
            float w = HadesAPI.Render.getStringWidth(entry.module.getName(), fontSize, false, false) 
                      + HadesAPI.Render.getStringWidth(" [" + entry.keyName + "]", fontSize, false, false) 
                      + paddingX * 2 + 4f; // Add 4f spacing buffer mapping Key to Name
            if (w > maxContentWidth) maxContentWidth = w;
        }

        HadesAPI.Render.drawRoundedShadow(currentX, currentY, maxContentWidth, rowHeight - 1f, pillRadius, 5f);
        if (blurBackground.getValue()) {
            try {
                int tint = HadesAPI.Render.colorWithAlpha(0xFF0A0A0C, 40); 
                com.hades.client.util.BlurUtil.drawBlurredRect(
                        currentX, currentY, maxContentWidth, rowHeight - 1f,
                        pillRadius, tint, blurPasses.getValue().intValue(), mcScale);
            } catch (Throwable t) {
                HadesAPI.Render.drawRoundedRect(currentX, currentY, maxContentWidth, rowHeight - 1f,
                        pillRadius, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
            }
        } else {
            HadesAPI.Render.drawRoundedRect(currentX, currentY, maxContentWidth, rowHeight - 1f,
                    pillRadius, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
        }

        HadesAPI.Render.drawRoundedRect(currentX, currentY + 2f, 2f, rowHeight - 5f, 1f, getColor(0, 1));
        HadesAPI.Render.drawString(headerText, currentX + paddingX + 2f, currentY + paddingY, Theme.TEXT_PRIMARY, 15f, true, false, true);
        
        float drawY = currentY + rowHeight + gap;

        // --- Draw Elements ---
        for (int i = 0; i < sorted.size(); i++) {
            ModuleEntry entry = sorted.get(i);
            Module module = entry.module;
            String keyName = entry.keyName;

            float renderX = currentX;
            float safeY = drawY;

            int accentColor = getColor(i + 1, sorted.size() + 1);

            HadesAPI.Render.drawRoundedShadow(renderX, safeY, maxContentWidth, rowHeight - 1f, pillRadius, 5f);
            if (blurBackground.getValue()) {
                try {
                    int tint = HadesAPI.Render.colorWithAlpha(0xFF0A0A0C, 40); 
                    com.hades.client.util.BlurUtil.drawBlurredRect(
                            renderX, safeY, maxContentWidth, rowHeight - 1f,
                            pillRadius, tint, blurPasses.getValue().intValue(), mcScale);
                } catch (Throwable t) {
                    HadesAPI.Render.drawRoundedRect(renderX, safeY, maxContentWidth, rowHeight - 1f,
                            pillRadius, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
                }
            } else {
                HadesAPI.Render.drawRoundedRect(renderX, safeY, maxContentWidth, rowHeight - 1f,
                        pillRadius, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
            }

            // Text
            float nameWidth = HadesAPI.Render.getStringWidth(module.getName(), fontSize, false, false);
            HadesAPI.Render.drawString(module.getName(), renderX + paddingX, safeY + paddingY,
                    Theme.TEXT_SECONDARY, fontSize, false, false, true); // Module Name
            
            // Key Text (right aligned)
            String keyStr = "[" + keyName + "]";
            float keyWidth = HadesAPI.Render.getStringWidth(keyStr, fontSize, false, false);
            HadesAPI.Render.drawString(keyStr, renderX + maxContentWidth - keyWidth - paddingX, safeY + paddingY,
                    module.isEnabled() ? accentColor : Theme.TEXT_MUTED, fontSize, false, false, true);

            drawY += rowHeight + gap;
        }
    }

    private String buildDisplayText(Module module) {
        String name = module.getName();
        String key = Keyboard.getKeyName(module.getKeyBind());
        return name + " [" + key + "]";
    }

    private int getColor(int index, int total) {
        String theme = colorTheme.getValue();
        if ("White".equals(theme)) return Theme.TEXT_PRIMARY;
        if ("Rainbow".equals(theme)) {
            float offset = (System.currentTimeMillis() % 3000) / 3000f;
            float hue = (index / (float) Math.max(1, total) + offset) % 1f;
            return java.awt.Color.HSBtoRGB(hue, 0.65f, 1.0f) | 0xFF000000;
        }
        if ("Fade".equals(theme)) {
            float ratio = total > 1 ? index / (float) (total - 1) : 0f;
            return HadesAPI.Render.lerpColor(Theme.ACCENT_PRIMARY, Theme.ACCENT_SECONDARY, ratio);
        }
        float offset = (System.currentTimeMillis() % 2000) / 2000f;
        float wave = (float) Math.sin((index / (float) Math.max(1, total) - offset) * Math.PI * 2) * 0.5f + 0.5f;
        return HadesAPI.Render.lerpColor(Theme.ACCENT_GRADIENT_START, Theme.ACCENT_GRADIENT_END, wave);
    }

    private class ModuleEntry {
        final Module module;
        final String displayText;
        final String keyName;
        
        ModuleEntry(Module module, String displayText) {
            this.module = module;
            this.displayText = displayText;
            this.keyName = Keyboard.getKeyName(module.getKeyBind());
        }
    }
}

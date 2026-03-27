package com.hades.client.gui.clickgui.component;

import com.hades.client.gui.clickgui.component.settings.*;
import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.module.setting.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A module card: shows name + toggle, expandable to reveal settings.
 */
public class ModuleButton extends Component {
    private final Module module;
    private final List<SettingComponent<?>> settingComponents = new ArrayList<>();
    private boolean expanded;
    private boolean binding;
    private float expandAnimation = 0f;
    private float enableAnimation = 0f;

    public ModuleButton(Module module) {
        this.module = module;
        this.height = Theme.MODULE_CARD_HEIGHT;

        for (Setting<?> setting : module.getSettings()) {
            if (setting.isHidden()) continue;
            
            if (setting instanceof BooleanSetting) {
                settingComponents.add(new BooleanComponent((BooleanSetting) setting));
            } else if (setting instanceof NumberSetting) {
                settingComponents.add(new SliderComponent((NumberSetting) setting));
            } else if (setting instanceof ModeSetting) {
                settingComponents.add(new ModeComponent((ModeSetting) setting));
            } else if (setting instanceof MultiSelectSetting) {
                settingComponents.add(new MultiSelectComponent((MultiSelectSetting) setting));
            } else if (setting instanceof InventorySetting) {
                settingComponents.add(new InventoryComponent((InventorySetting) setting));
            }
        }
    }

    @Override
    public void renderShadow(int mouseX, int mouseY, float partialTicks) {
        float enableTarget = module.isEnabled() ? 1f : 0f;
        enableAnimation = smooth(enableAnimation, enableTarget, ANIMATION_SPEED);

        float expandTarget = expanded ? 1f : 0f;
        expandAnimation = smooth(expandAnimation, expandTarget, ANIMATION_SPEED);

    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        // State updates already processed in renderShadow, but we can process again.
        float enableTarget = module.isEnabled() ? 1f : 0f;
        enableAnimation = smooth(enableAnimation, enableTarget, ANIMATION_SPEED);

        float expandTarget = expanded ? 1f : 0f;
        expandAnimation = smooth(expandAnimation, expandTarget, ANIMATION_SPEED);

        // ── Draw the module card ──
        {
            boolean hovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + Theme.MODULE_CARD_HEIGHT;

            // Card background
            int bgColor;
            if (module.isEnabled()) {
                int baseEnabled = HadesAPI.Render.lerpColor(Theme.MODULE_BG, Theme.MODULE_BG_ENABLED, enableAnimation);
                bgColor = hovered ? HadesAPI.Render.color(
                        (baseEnabled >> 16 & 0xFF) + 10,
                        (baseEnabled >> 8 & 0xFF) + 10,
                        (baseEnabled & 0xFF) + 10,
                        (baseEnabled >> 24 & 0xFF)) : baseEnabled;
            } else {
                bgColor = hovered ? Theme.MODULE_BG_HOVER : Theme.MODULE_BG;
            }
            // Clamp card background to clip bounds
            float cardDrawY = Math.max(y, clipTop);
            float cardDrawBottom = Math.min(y + Theme.MODULE_CARD_HEIGHT, clipBottom);
            float cardDrawH = cardDrawBottom - cardDrawY;
            if (cardDrawH > 0) {
                // Subtle 1px glossy border
                HadesAPI.Render.drawRoundedRect(x - 0.5f, cardDrawY - 0.5f, width + 1f, cardDrawH + 1f, Theme.MODULE_CARD_RADIUS + 0.5f, HadesAPI.Render.color(255, 255, 255, 12));
                HadesAPI.Render.drawRoundedRect(x, cardDrawY, width, cardDrawH, Theme.MODULE_CARD_RADIUS, bgColor);
            }

            // Accent bar on left when enabled
            if (enableAnimation > 0.01f) {
                float pillY = y + 4;
                float pillH = Theme.MODULE_CARD_HEIGHT - 8;
                float clampedPY = Math.max(pillY, clipTop);
                float clampedPBottom = Math.min(pillY + pillH, clipBottom);
                float clampedPH = clampedPBottom - clampedPY;
                if (clampedPH > 0) {
                    int accentColor = HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY,
                            (int) (255 * enableAnimation));
                    float radTop = (clampedPY > pillY + 0.1f) ? 0f : 1.5f;
                    float radBot = (clampedPBottom < pillY + pillH - 0.1f) ? 0f : 1.5f;
                    HadesAPI.Render.drawRoundedRect(x, clampedPY, 3, clampedPH, radTop, radTop, radBot, radBot,
                            accentColor);
                }
            }

            // Module name + Description (clipped to scroll bounds)
            int textColor = module.isEnabled() ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY;
            float nameX = x + 18;
            float nameHeight = HadesAPI.Render.getFontHeight(14f, false, false);
            float descHeight = HadesAPI.Render.getFontHeight(10.5f, false, true); // SMALL_ITALIC

            float nameY;
            if (module.getDescription().isEmpty()) {
                nameY = y + (Theme.MODULE_CARD_HEIGHT - nameHeight) / 2f;
            } else {
                float blockHeight = nameHeight + 2 + descHeight;
                nameY = y + (Theme.MODULE_CARD_HEIGHT - blockHeight) / 2f;
            }

            final float finalNameY = nameY;
            final int finalTextColor = textColor;
            String displayName = module.getName();
            if (binding) {
                displayName += " [Press Key]";
            } else if (module.getKeyBind() != org.lwjgl.input.Keyboard.KEY_NONE) {
                displayName += " [" + org.lwjgl.input.Keyboard.getKeyName(module.getKeyBind()) + "]";
            }
            HadesAPI.Render.drawString(displayName, nameX, finalNameY, finalTextColor, 14f, false, false, false);

            if (!module.getDescription().isEmpty()) {
                HadesAPI.Render.drawString(module.getDescription(), nameX,
                        finalNameY + nameHeight + 2, Theme.TEXT_MUTED, 10.5f, false, true, false); // SMALL_ITALIC
            }

            // Toggle switch
            float toggleX = x + width - Theme.TOGGLE_WIDTH - Theme.PADDING - 20;
            float toggleY = y + (Theme.MODULE_CARD_HEIGHT - Theme.TOGGLE_HEIGHT) / 2f;
            int trackColor = HadesAPI.Render.lerpColor(Theme.TOGGLE_OFF, Theme.TOGGLE_ON, enableAnimation);

            float clampedTY = Math.max(toggleY, clipTop);
            float clampedTBottom = Math.min(toggleY + Theme.TOGGLE_HEIGHT, clipBottom);
            float clampedTH = clampedTBottom - clampedTY;
            if (clampedTH > 0) {
                float radTop = (clampedTY > toggleY + 0.1f) ? 0f : Theme.TOGGLE_HEIGHT / 2f;
                float radBot = (clampedTBottom < toggleY + Theme.TOGGLE_HEIGHT - 0.1f) ? 0f : Theme.TOGGLE_HEIGHT / 2f;
                HadesAPI.Render.drawRoundedRect(toggleX, clampedTY, Theme.TOGGLE_WIDTH, clampedTH, radTop, radTop,
                        radBot, radBot, trackColor);
            }

            float knobSize = Theme.TOGGLE_HEIGHT - 4;
            float knobX = toggleX + 2 + enableAnimation * (Theme.TOGGLE_WIDTH - knobSize - 4);
            float knobY = toggleY + 2;

            // Knob
            int darkKnob = HadesAPI.Render.color(20, 20, 24);
            int lightKnob = HadesAPI.Render.color(220, 220, 225);
            int knobColor = HadesAPI.Render.lerpColor(lightKnob, darkKnob, enableAnimation);

            float clampedKY = Math.max(knobY, clipTop);
            float clampedKBottom = Math.min(knobY + knobSize, clipBottom);
            float clampedKH = clampedKBottom - clampedKY;
            if (clampedKH > 0) {
                float radTop = (clampedKY > knobY + 0.1f) ? 0f : knobSize / 2f;
                float radBot = (clampedKBottom < knobY + knobSize - 0.1f) ? 0f : knobSize / 2f;
                HadesAPI.Render.drawRoundedRect(knobX, clampedKY, knobSize, clampedKH, radTop, radTop, radBot, radBot,
                        knobColor);
            }

            // Expand chevron
            if (!settingComponents.isEmpty()) {
                float chevronY = y + (Theme.MODULE_CARD_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f;
                String arrow = expanded ? "▼" : "▶";
                HadesAPI.Render.drawString(arrow, x + width - 20, chevronY,
                        hovered ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);
            }
        }

        // ── Settings panel (expanded) — each setting checked individually ──
        if (expandAnimation > 0.01f && !settingComponents.isEmpty()) {
            List<SettingComponent<?>> visibleSettings = new ArrayList<>();
            for (SettingComponent<?> comp : settingComponents) {
                if (comp.getSetting().isVisible()) visibleSettings.add(comp);
            }

            if (!visibleSettings.isEmpty()) {
                float settingsY = y + Theme.MODULE_CARD_HEIGHT;
                float settingsHeight = getSettingsHeight() * expandAnimation;
                int settingCount = visibleSettings.size();

                // Draw sleek vertical grouping line
                float clampedLineY = Math.max(settingsY + 4, clipTop);
                float clampedLineBottom = Math.min(settingsY + settingsHeight - 4, clipBottom);
                if (clampedLineBottom > clampedLineY) {
                    HadesAPI.Render.drawRect(x + 4, clampedLineY, 2f, clampedLineBottom - clampedLineY, 
                            HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 100)); // Subtle accent line
                }

                // Render shadows first
                float shadowY = settingsY + 2;
                for (int i = 0; i < settingCount; i++) {
                    SettingComponent<?> comp = visibleSettings.get(i);
                    float compH = comp.getHeight();

                    comp.setPosition(x + 10, shadowY); // Shifted slightly right to avoid group line
                    comp.setSize(width - 14 - Theme.SCROLLBAR_WIDTH, compH);
                    comp.setClipBounds(clipTop, clipBottom);

                    // Only render if within expand animation
                    if (shadowY < settingsY + settingsHeight) {
                        comp.renderShadow(mouseX, mouseY, partialTicks);
                    }
                    shadowY += compH + 4;
                }

                // Render main components securely within a nested scissor
                // This guarantees items and text are perfectly clipped exactly at the bottom of the animating dropdown!
                float totalW = width;
                float totalH = settingsHeight;
                
                HadesAPI.Render.runWithScissor(x, settingsY, totalW, totalH, () -> {
                    float offsetY = settingsY + 2;
                    for (int i = 0; i < settingCount; i++) {
                        SettingComponent<?> comp = visibleSettings.get(i);
                        float compH = comp.getHeight();

                        // Only render if within the absolute scroll bounds (clipTop / clipBottom)
                        if (offsetY + compH > clipTop && offsetY < clipBottom) {
                            comp.render(mouseX, mouseY, partialTicks);
                        }
                        offsetY += compH + 4;
                    }
                });
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + Theme.MODULE_CARD_HEIGHT) {
            if (button == 0) { // Left click
                module.toggle();
            } else if (button == 1) { // Right click
                if (!settingComponents.isEmpty()) {
                    expanded = !expanded;
                }
            } else if (button == 2) { // Middle click
                binding = !binding;
            }
        }

        if (expanded) {
            for (SettingComponent<?> comp : settingComponents) {
                if (comp.getSetting().isVisible()) {
                    comp.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (expanded) {
            for (SettingComponent<?> comp : settingComponents) {
                if (comp.getSetting().isVisible()) {
                    comp.mouseReleased(mouseX, mouseY, button);
                }
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (binding) {
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE || keyCode == org.lwjgl.input.Keyboard.KEY_DELETE || keyCode == org.lwjgl.input.Keyboard.KEY_BACK) {
                module.setKeyBind(org.lwjgl.input.Keyboard.KEY_NONE);
            } else {
                module.setKeyBind(keyCode);
            }
            binding = false;
            return;
        }

        if (expanded) {
            for (SettingComponent<?> comp : settingComponents) {
                if (comp.getSetting().isVisible()) {
                    comp.keyTyped(typedChar, keyCode);
                }
            }
        }
    }

    public float getTotalHeight() {
        float h = Theme.MODULE_CARD_HEIGHT + Theme.MODULE_CARD_MARGIN;
        if (expandAnimation > 0.01f) {
            h += getSettingsHeight() * expandAnimation + 4;
        }
        return h;
    }

    private float getSettingsHeight() {
        float h = 4; // padding
        boolean hasVisible = false;
        for (SettingComponent<?> comp : settingComponents) {
            if (comp.getSetting().isVisible()) {
                h += comp.getHeight() + 4;
                hasVisible = true;
            }
        }
        return hasVisible ? h : 0;
    }

    public Module getModule() {
        return module;
    }
}

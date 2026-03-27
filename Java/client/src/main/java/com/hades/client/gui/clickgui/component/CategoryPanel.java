package com.hades.client.gui.clickgui.component;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a category's content: a scrollable list of ModuleButtons.
 * Uses GL scissor for hard clipping (LabyMod routes GL11.glScissor through
 * GlStateTracker).
 */
public class CategoryPanel extends Component {
    private final Module.Category category;
    private final List<ModuleButton> moduleButtons = new ArrayList<>();
    private float scrollOffset = 0;
    private float targetScrollOffset = 0;
    private float maxScroll = 0;
    private float totalContentHeight = 0;

    // Scrollbar dragging
    private boolean scrollbarDragging = false;
    private float scrollbarDragStartY = 0;
    private float scrollbarDragStartOffset = 0;
    private float scrollbarHoverAnimation = 0f;

    public CategoryPanel(Module.Category category, List<Module> modules) {
        this.category = category;
        for (Module module : modules) {
            moduleButtons.add(new ModuleButton(module));
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible)
            return;

        // ── Scrollbar drag handling ──
        if (scrollbarDragging) {
            if (org.lwjgl.input.Mouse.isButtonDown(0)) {
                float scrollbarTrackHeight = height;
                float thumbHeight = Math.max(20,
                        scrollbarTrackHeight * (scrollbarTrackHeight / (totalContentHeight + Theme.PADDING)));
                float usableTrack = scrollbarTrackHeight - thumbHeight;
                if (usableTrack > 0) {
                    float deltaY = mouseY - scrollbarDragStartY;
                    float scrollRatio = deltaY / usableTrack;
                    targetScrollOffset = scrollbarDragStartOffset + scrollRatio * maxScroll;
                    targetScrollOffset = Math.max(0, Math.min(maxScroll, targetScrollOffset));
                }
            } else {
                scrollbarDragging = false;
            }
        }

        // Smooth scroll (snappier response)
        scrollOffset = scrollOffset + (targetScrollOffset - scrollOffset) * 0.6f;
        if (Math.abs(targetScrollOffset - scrollOffset) < 0.5f)
            scrollOffset = targetScrollOffset;

        // Calculate total content height (with expanded settings)
        totalContentHeight = 0;
        for (ModuleButton btn : moduleButtons) {
            totalContentHeight += btn.getTotalHeight();
        }

        // Clip bounds for this scroll area
        final float clipTop = y + 5;
        final float clipBottom = y + height - 5;
        final float viewportHeight = clipBottom - clipTop;
        final float listPadding = 4f;

        maxScroll = Math.max(0, totalContentHeight - viewportHeight + listPadding * 2f + 10f);
        targetScrollOffset = Math.max(0, Math.min(maxScroll, targetScrollOffset));

        float startingOffsetY = clipTop + listPadding - scrollOffset;

        HadesAPI.Render.runWithScissor(x, clipTop, width, clipBottom - clipTop, () -> {
            // Pass 1: Render Shadows
            float currentOffsetY = startingOffsetY;
            for (ModuleButton btn : moduleButtons) {
                float btnHeight = btn.getTotalHeight();

                btn.setPosition(x + Theme.PADDING_SMALL, currentOffsetY);
                btn.setSize(width - Theme.PADDING_SMALL * 2 - Theme.SCROLLBAR_WIDTH, 0);
                btn.width = width - Theme.PADDING_SMALL * 2 - Theme.SCROLLBAR_WIDTH;
                btn.setClipBounds(clipTop, clipBottom);

                if (currentOffsetY + btnHeight > clipTop && currentOffsetY < clipBottom) {
                    btn.renderShadow(mouseX, mouseY, partialTicks);
                }

                currentOffsetY += btnHeight;
            }

            // Pass 2: Render Content
            currentOffsetY = startingOffsetY;
            for (ModuleButton btn : moduleButtons) {
                float btnHeight = btn.getTotalHeight();
                
                // Positions already set in Pass 1, just render
                if (currentOffsetY + btnHeight > clipTop && currentOffsetY < clipBottom) {
                    btn.render(mouseX, mouseY, partialTicks);
                }

                currentOffsetY += btnHeight;
            }
        });

        // ── Scrollbar (outside scissor) ──
        if (maxScroll > 0) {
            float scrollbarHeight = height * (height / (totalContentHeight + Theme.PADDING));
            scrollbarHeight = Math.max(20, scrollbarHeight);
            float scrollbarY = y + (scrollOffset / maxScroll) * (height - scrollbarHeight);

            boolean scrollHovered = mouseX >= x + width - Theme.SCROLLBAR_WIDTH - 2 && mouseX <= x + width
                    && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight;

            scrollbarHoverAnimation = smooth(scrollbarHoverAnimation, scrollHovered || scrollbarDragging ? 1f : 0f,
                    ANIMATION_SPEED);

            // Track background
            HadesAPI.Render.drawRoundedRect(x + width - Theme.SCROLLBAR_WIDTH, y,
                    Theme.SCROLLBAR_WIDTH, height, 2f, Theme.SCROLLBAR_BG);

            // Thumb
            int thumbColor = HadesAPI.Render.lerpColor(Theme.SCROLLBAR_THUMB, HadesAPI.Render.color(120, 120, 125, 200),
                    scrollbarHoverAnimation);
            HadesAPI.Render.drawRoundedRect(x + width - Theme.SCROLLBAR_WIDTH, scrollbarY,
                    Theme.SCROLLBAR_WIDTH, scrollbarHeight, 2f, thumbColor);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || !isHovered(mouseX, mouseY))
            return;

        // Scrollbar click-to-drag
        if (button == 0 && maxScroll > 0) {
            float scrollbarHeight = height * (height / (totalContentHeight + Theme.PADDING));
            scrollbarHeight = Math.max(20, scrollbarHeight);
            float scrollbarY = y + (scrollOffset / maxScroll) * (height - scrollbarHeight);

            // Check if clicking on the scrollbar track area
            if (mouseX >= x + width - Theme.SCROLLBAR_WIDTH && mouseX <= x + width) {
                if (mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight) {
                    // Clicking on thumb — start drag
                    scrollbarDragging = true;
                    scrollbarDragStartY = mouseY;
                    scrollbarDragStartOffset = scrollOffset;
                    return;
                } else {
                    // Clicking on track but not on thumb — jump to position
                    float ratio = (mouseY - y - scrollbarHeight / 2f) / (height - scrollbarHeight);
                    ratio = Math.max(0, Math.min(1, ratio));
                    targetScrollOffset = ratio * maxScroll;
                    return;
                }
            }
        }

        for (ModuleButton btn : moduleButtons) {
            btn.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (!visible)
            return;
        if (button == 0) {
            scrollbarDragging = false;
        }
        for (ModuleButton btn : moduleButtons) {
            btn.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!visible)
            return;
        for (ModuleButton btn : moduleButtons) {
            btn.keyTyped(typedChar, keyCode);
        }
    }

    public void scroll(int amount) {
        targetScrollOffset -= amount * 35f;
        targetScrollOffset = Math.max(0, Math.min(maxScroll, targetScrollOffset));
    }

    public Module.Category getCategory() {
        return category;
    }

    public List<ModuleButton> getModuleButtons() {
        return moduleButtons;
    }
}

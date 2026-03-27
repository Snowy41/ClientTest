package com.hades.client.gui.clickgui;

import com.hades.client.HadesClient;
import com.hades.client.gui.clickgui.component.CategoryPanel;
import com.hades.client.gui.clickgui.component.ConfigScreenComponent;
import com.hades.client.gui.clickgui.component.ThemeScreenComponent;
import com.hades.client.gui.clickgui.component.ProfileScreenComponent;
import com.hades.client.gui.clickgui.component.HudScreenComponent;
import com.hades.client.gui.clickgui.component.Sidebar;
import com.hades.client.gui.clickgui.component.TabSelector;
import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.module.Module;
import com.hades.client.gui.clickgui.component.MultiSelectScreenComponent;
import java.util.ArrayList;
import java.util.List;

public class ClickGUI {
    private final List<CategoryPanel> panels = new ArrayList<>();
    private final Sidebar sidebar;
    private final TabSelector tabSelector;
    private final ConfigScreenComponent configScreen = new ConfigScreenComponent();
    private final ThemeScreenComponent themeScreen = new ThemeScreenComponent();
    private final ProfileScreenComponent profileScreen = new ProfileScreenComponent();
    private final HudScreenComponent hudScreen = new HudScreenComponent();
    private Module.Category activeCategory = Module.Category.COMBAT;
    private boolean visible;
    private boolean showHudScreen = false;
    private boolean showConfigScreen = false;
    private boolean showThemeScreen = false;
    private boolean showProfileScreen = false;

    private final MultiSelectScreenComponent multiSelectScreen = new MultiSelectScreenComponent();

    public MultiSelectScreenComponent getMultiSelectScreen() {
        return multiSelectScreen;
    }

    public ClickGUI() {
        tabSelector = new TabSelector(activeCategory, this::setActiveCategory);
        sidebar = new Sidebar(this::onHudClicked, this::onConfigsClicked, this::onThemeClicked, this::onProfileClicked);
    }

    private void setActiveCategory(Module.Category category) {
        this.activeCategory = category;
        this.showHudScreen = false;
        this.showConfigScreen = false; 
        this.showThemeScreen = false;
        this.showProfileScreen = false;
        tabSelector.setActiveCategory(category);
    }

    private void onHudClicked() {
        showHudScreen = !showHudScreen;
        showConfigScreen = false;
        showThemeScreen = false;
        showProfileScreen = false;
    }

    private void onConfigsClicked() {
        showConfigScreen = !showConfigScreen;
        showHudScreen = false;
        showThemeScreen = false;
        showProfileScreen = false;
    }

    private void onThemeClicked() {
        showThemeScreen = !showThemeScreen;
        showHudScreen = false;
        showConfigScreen = false;
        showProfileScreen = false;
    }

    private void onProfileClicked() {
        showProfileScreen = !showProfileScreen;
        showHudScreen = false;
        showConfigScreen = false;
        showThemeScreen = false;
    }

    // Window position & drag
    private float windowX, windowY;
    private float windowWidth, windowHeight;
    private boolean dragging;
    private float dragOffsetX, dragOffsetY;

    // Animation
    private float openAnimation = 0f;
    private boolean isClosing;

    public void init(com.hades.client.module.ModuleManager moduleManager) {
        panels.clear();
        for (Module.Category category : Module.Category.values()) {
            if (category == Module.Category.HUD) continue; // Exclude HUD Widgets from standard vertical layout
            List<Module> modules = moduleManager.getModulesByCategory(category);
            panels.add(new CategoryPanel(category, modules));
        }

        windowWidth = Theme.DEFAULT_WIDTH;
        windowHeight = Theme.DEFAULT_HEIGHT;
    }

    public void open() {
        visible = true;
        openAnimation = 0f;

        if (windowX == 0 && windowY == 0) {
            int[] sr = com.hades.client.api.HadesAPI.Game.getScaledResolution();
            if (sr[0] > 0 && sr[1] > 0) {
                windowX = (sr[0] - windowWidth) / 2f;
                windowY = (sr[1] - windowHeight) / 2f;
            }
        }

        HadesClient.getInstance().openClickGUI();
    }

    public void close() {
        if (isClosing) return;
        isClosing = true;
        try {
            visible = false;
            HadesClient.getInstance().closeClickGUI();
        } finally {
            isClosing = false;
        }
    }

    public void toggle() {
        if (visible) close();
        else open();
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        int scroll = org.lwjgl.input.Mouse.getDWheel();
        if (scroll != 0) {
            handleMouseScrollBounds(mouseX, mouseY, scroll > 0 ? 1 : -1);
        }

        openAnimation = openAnimation + (1f - openAnimation) * 0.15f;
        if (openAnimation > 0.99f) openAnimation = 1f;

        if (dragging) {
            windowX = mouseX - dragOffsetX;
            windowY = mouseY - dragOffsetY;
        }

        float alpha = openAnimation;

        HadesAPI.Render.drawRect(0, 0, 10000, 10000, HadesAPI.Render.color(0, 0, 0, (int) (100 * alpha)));
        HadesAPI.Render.drawRoundedShadow(windowX, windowY, windowWidth, windowHeight, Theme.WINDOW_RADIUS, Theme.SHADOW_SIZE);
        HadesAPI.Render.drawRoundedRect(windowX - 1, windowY - 1, windowWidth + 2, windowHeight + 2, Theme.WINDOW_RADIUS, Theme.WINDOW_OUTLINE);
        HadesAPI.Render.drawRoundedRect(windowX, windowY, windowWidth, windowHeight, Theme.WINDOW_RADIUS, Theme.WINDOW_BG);

        renderContent(mouseX, mouseY, partialTicks);

        float contentY = windowY + Theme.PADDING + Theme.TAB_HEIGHT + Theme.PADDING_SMALL;

        sidebar.setPosition(windowX, windowY);
        sidebar.setSize(Theme.SIDEBAR_WIDTH, windowHeight);
        sidebar.setVisible(true);
        sidebar.setActiveState(showHudScreen, showConfigScreen, showThemeScreen, showProfileScreen);
        sidebar.render(mouseX, mouseY, partialTicks);

        float tabsX = windowX + Theme.SIDEBAR_WIDTH + Theme.PADDING;
        float tabsY = windowY + Theme.PADDING;
        float tabsWidth = windowWidth - Theme.SIDEBAR_WIDTH - Theme.PADDING * 2;

        tabSelector.setPosition(tabsX, tabsY);
        tabSelector.setSize(tabsWidth, Theme.TAB_HEIGHT);
        tabSelector.setVisible(true);
        tabSelector.render(mouseX, mouseY, partialTicks);

        float separatorX = windowX + Theme.SIDEBAR_WIDTH + Theme.PADDING;
        HadesAPI.Render.drawRect(separatorX, contentY - 3, tabsWidth, 1, Theme.SIDEBAR_SEPARATOR);

        // HUD Editor button removed globally; moved to HUD section explicitly
        
        if (multiSelectScreen.isOpen()) {
            multiSelectScreen.render(mouseX, mouseY, partialTicks);
        }
    }

    private void renderContent(int mouseX, int mouseY, float partialTicks) {
        float contentX = windowX + Theme.SIDEBAR_WIDTH + Theme.PADDING;
        float contentY = windowY + Theme.PADDING + Theme.TAB_HEIGHT + Theme.PADDING_SMALL;
        float contentWidth = windowWidth - Theme.SIDEBAR_WIDTH - Theme.PADDING * 2;
        float contentHeight = (windowY + windowHeight) - contentY - Theme.PADDING;

        boolean hidePanels = showHudScreen || showConfigScreen || showThemeScreen || showProfileScreen;

        if (showHudScreen) {
            hudScreen.setPosition(contentX, contentY);
            hudScreen.setSize(contentWidth, contentHeight);
            hudScreen.setVisible(true);
            hudScreen.render(mouseX, mouseY, partialTicks);
        } else {
            hudScreen.setVisible(false);
        }

        if (showConfigScreen) {
            configScreen.setPosition(contentX, contentY);
            configScreen.setSize(contentWidth, contentHeight);
            configScreen.setVisible(true);
            configScreen.render(mouseX, mouseY, partialTicks);
        } else {
            configScreen.setVisible(false);
        }

        if (showThemeScreen) {
            themeScreen.setPosition(contentX, contentY);
            themeScreen.setSize(contentWidth, contentHeight);
            themeScreen.setVisible(true);
            themeScreen.render(mouseX, mouseY, partialTicks);
        } else {
            themeScreen.setVisible(false);
        }

        if (showProfileScreen) {
            profileScreen.setPosition(contentX, contentY);
            profileScreen.setSize(contentWidth, contentHeight);
            profileScreen.setVisible(true);
            profileScreen.render(mouseX, mouseY, partialTicks);
        } else {
            profileScreen.setVisible(false);
        }

        if (!hidePanels) {
            for (CategoryPanel panel : panels) {
                boolean isActive = panel.getCategory() == activeCategory;
                panel.setVisible(isActive);
                if (isActive) {
                    panel.setPosition(contentX, contentY);
                    panel.setSize(contentWidth, contentHeight);
                    panel.render(mouseX, mouseY, partialTicks);
                }
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible) return;

        if (multiSelectScreen.isOpen()) {
            multiSelectScreen.mouseClicked(mouseX, mouseY, button);
            return;
        }

        tabSelector.mouseClicked(mouseX, mouseY, button);

        // Editor hook moved below


        if (mouseX >= windowX && mouseX <= windowX + windowWidth
                && mouseY >= windowY && mouseY <= windowY + Theme.PADDING + Theme.TAB_HEIGHT
                && button == 0) {
            dragging = true;
            dragOffsetX = mouseX - windowX;
            dragOffsetY = mouseY - windowY;
            return;
        }

        sidebar.mouseClicked(mouseX, mouseY, button);

        if (showHudScreen) {
            hudScreen.mouseClicked(mouseX, mouseY, button);
        } else if (showConfigScreen) {
            configScreen.mouseClicked(mouseX, mouseY, button);
        } else if (showThemeScreen) {
            themeScreen.mouseClicked(mouseX, mouseY, button);
        } else if (showProfileScreen) {
            profileScreen.mouseClicked(mouseX, mouseY, button);
        } else {
            for (CategoryPanel panel : panels) {
                if (panel.getCategory() == activeCategory) {
                    panel.mouseClicked(mouseX, mouseY, button);
                    break;
                }
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;
        if (!visible) return;

        if (multiSelectScreen.isOpen()) {
            multiSelectScreen.mouseReleased(mouseX, mouseY, button);
            return;
        }

        sidebar.mouseReleased(mouseX, mouseY, button);

        if (showHudScreen) {
            hudScreen.mouseReleased(mouseX, mouseY, button);
        } else if (showConfigScreen) {
            configScreen.mouseReleased(mouseX, mouseY, button);
        } else if (showThemeScreen) {
            themeScreen.mouseReleased(mouseX, mouseY, button);
        } else if (showProfileScreen) {
            profileScreen.mouseReleased(mouseX, mouseY, button);
        } else {
            for (CategoryPanel panel : panels) {
                if (panel.getCategory() == activeCategory) {
                    panel.mouseReleased(mouseX, mouseY, button);
                    break;
                }
            }
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (multiSelectScreen.isOpen()) {
            if (keyCode == 1) { // ESC 
                multiSelectScreen.close();
                return;
            }
            multiSelectScreen.keyTyped(typedChar, keyCode);
            return;
        }

        if (keyCode == 1) { // ESC
            if (showHudScreen || showConfigScreen || showThemeScreen || showProfileScreen) {
                showHudScreen = false;
                showConfigScreen = false;
                showThemeScreen = false;
                showProfileScreen = false;
                return;
            }
            close();
            return;
        }
        if (showHudScreen) {
            hudScreen.keyTyped(typedChar, keyCode);
        } else if (showConfigScreen) {
            configScreen.keyTyped(typedChar, keyCode);
        } else if (showThemeScreen) {
            themeScreen.keyTyped(typedChar, keyCode);
        } else if (showProfileScreen) {
            profileScreen.keyTyped(typedChar, keyCode);
        } else {
            for (CategoryPanel panel : panels) {
                if (panel.getCategory() == activeCategory) {
                    panel.keyTyped(typedChar, keyCode);
                    break;
                }
            }
        }
    }

    public void handleMouseScrollBounds(int mouseX, int mouseY, int amount) {
        if (multiSelectScreen.isOpen()) {
            multiSelectScreen.scroll(amount);
            return;
        }

        if (showHudScreen) {
            if (hudScreen.isHovered(mouseX, mouseY)) hudScreen.scroll(amount);
        } else if (showConfigScreen) {
            configScreen.scroll(amount); // Config screen takes up full space anyway
        } else if (showThemeScreen) {
            themeScreen.scroll(amount);
        } else if (showProfileScreen) {
            profileScreen.scroll(amount);
        } else {
            for (CategoryPanel panel : panels) {
                if (panel.getCategory() == activeCategory) {
                    if (panel.isHovered(mouseX, mouseY)) {
                        panel.scroll(amount);
                    }
                    break;
                }
            }
        }
    }

    public void handleMouseScroll(int amount) {
        // Fallback for API compatibility, un-guarded
        this.handleMouseScrollBounds(0, 0, amount);
    }

    public boolean isVisible() {
        return visible;
    }
}

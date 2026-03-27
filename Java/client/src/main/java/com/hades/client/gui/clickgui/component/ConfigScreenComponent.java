package com.hades.client.gui.clickgui.component;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.backend.BackendConnection;
import com.hades.client.backend.CloudConfig;
import com.hades.client.config.ConfigManager;
import com.hades.client.gui.clickgui.theme.Theme;

import java.util.List;

/**
 * Split-pane config screen with Local and Cloud tabs.
 * Cloud configs auto-sync from the Hades Web Hub via BackendConnection.
 */
public class ConfigScreenComponent extends Component {

    // Tab state: 0 = Local, 1 = Cloud
    private int activeTab = 0;
    private float localTabHoverAnim = 0f;
    private float cloudTabHoverAnim = 0f;

    // Scroll state (separate for each tab)
    private float localScrollOffset = 0f;
    private float localTargetScroll = 0f;
    private float cloudScrollOffset = 0f;
    private float cloudTargetScroll = 0f;

    // Config name input (local tab only)
    private String inputText = "";
    private boolean inputFocused = false;
    private float inputCursorBlink = 0f;

    // Per-config hover animations (max 32)
    private float[] loadHoverAnim = new float[32];
    private float[] saveHoverAnim = new float[32];
    private float[] deleteHoverAnim = new float[32];
    private float saveAsHoverAnim = 0f;

    // Cloud tab hover animations
    private float[] cloudLoadHoverAnim = new float[32];

    // Constants
    private static final float ITEM_HEIGHT = 36f;
    private static final float ITEM_MARGIN = 3f;
    private static final float BTN_WIDTH = 50f;
    private static final float BTN_HEIGHT = 20f;
    private static final float BTN_RADIUS = 4f;
    private static final float INPUT_HEIGHT = 28f;
    private static final float TAB_HEIGHT = 28f;

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible)
            return;

        // ═══════════════════════════════
        // Tab bar: [ Local | Cloud ]
        // ═══════════════════════════════
        float tabY = y + 4;
        float tabWidth = width / 2f;

        // Local tab
        boolean localHovered = mouseX >= x && mouseX <= x + tabWidth && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
        localTabHoverAnim = smooth(localTabHoverAnim, localHovered || activeTab == 0 ? 1f : 0f, ANIMATION_SPEED);

        int localTabBg = activeTab == 0
                ? HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 60)
                : HadesAPI.Render.color(28, 28, 32, (int) (200 + 55 * localTabHoverAnim));
        HadesAPI.Render.drawRoundedRect(x + 2, tabY, tabWidth - 2, TAB_HEIGHT, 4f, localTabBg);

        int localTextColor = activeTab == 0 ? Theme.ACCENT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, localTabHoverAnim);
        HadesAPI.Render.drawCenteredString("Local", x + tabWidth / 2f, tabY + (TAB_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, localTextColor, 0.95f);

        // Cloud tab
        boolean cloudHovered = mouseX >= x + tabWidth && mouseX <= x + width && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
        cloudTabHoverAnim = smooth(cloudTabHoverAnim, cloudHovered || activeTab == 1 ? 1f : 0f, ANIMATION_SPEED);

        int cloudTabBg = activeTab == 1
                ? HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 60)
                : HadesAPI.Render.color(28, 28, 32, (int) (200 + 55 * cloudTabHoverAnim));
        HadesAPI.Render.drawRoundedRect(x + tabWidth, tabY, tabWidth - 2, TAB_HEIGHT, 4f, cloudTabBg);

        BackendConnection bc = BackendConnection.getInstance();
        int cloudCount = bc.isConfigsFetched() ? bc.getCloudConfigs().size() : 0;
        String cloudLabel = "Cloud" + (cloudCount > 0 ? " (" + cloudCount + ")" : "");
        int cloudTextColor = activeTab == 1 ? Theme.ACCENT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, cloudTabHoverAnim);
        HadesAPI.Render.drawCenteredString(cloudLabel, x + tabWidth + tabWidth / 2f, tabY + (TAB_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, cloudTextColor, 0.95f);

        // Active tab underline
        float underlineX = activeTab == 0 ? x + 2 : x + tabWidth;
        HadesAPI.Render.drawRect(underlineX + 10, tabY + TAB_HEIGHT - 2, tabWidth - 22, 2, Theme.ACCENT_PRIMARY);

        // Separator below tabs
        float contentY = tabY + TAB_HEIGHT + 4;
        HadesAPI.Render.drawRect(x + 6, contentY, width - 12, 1, HadesAPI.Render.color(40, 40, 45, 255));
        contentY += 6;

        // ═══════════════════════════════
        // Tab content
        // ═══════════════════════════════
        if (activeTab == 0) {
            renderLocalTab(mouseX, mouseY, contentY, partialTicks);
        } else {
            renderCloudTab(mouseX, mouseY, contentY, partialTicks);
        }
    }

    // ══════════════════════════════════════════
    // LOCAL TAB
    // ══════════════════════════════════════════

    private void renderLocalTab(int mouseX, int mouseY, float startY, float partialTicks) {
        ConfigManager configManager = HadesClient.getInstance().getConfigManager();
        List<String> configs = configManager.getConfigNames();

        localScrollOffset = smooth(localScrollOffset, localTargetScroll, 0.6f);

        // Header
        HadesAPI.Render.drawString(configs.size() + " saved",
                x + width - HadesAPI.Render.getStringWidth(configs.size() + " saved", 0.8f) - 8, startY,
                Theme.TEXT_MUTED, 0.8f);

        // Input bar + Save button
        float inputY = startY;
        float inputWidth = width - BTN_WIDTH - 16;

        int inputBg = inputFocused ? HadesAPI.Render.color(35, 35, 40) : HadesAPI.Render.color(28, 28, 32);
        HadesAPI.Render.drawRoundedRect(x + 6, inputY, inputWidth, INPUT_HEIGHT, 4f, inputBg);

        if (inputFocused) {
            HadesAPI.Render.drawRoundedRect(x + 5, inputY - 1, inputWidth + 2, INPUT_HEIGHT + 2, 5f,
                    HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 100));
            HadesAPI.Render.drawRoundedRect(x + 6, inputY, inputWidth, INPUT_HEIGHT, 4f, inputBg);
        }

        float textY = inputY + (INPUT_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f;
        if (inputText.isEmpty() && !inputFocused) {
            HadesAPI.Render.drawString("Config name...", x + 14, textY, Theme.TEXT_MUTED, 1.0f);
        } else {
            inputCursorBlink += 0.05f;
            String displayText = inputText + (inputFocused && ((int) (inputCursorBlink * 2) % 2 == 0) ? "|" : "");
            HadesAPI.Render.drawString(displayText, x + 14, textY, Theme.TEXT_PRIMARY, 1.0f);
        }

        // Save As button
        float saveAsBtnX = x + 6 + inputWidth + 6;
        boolean saveAsHovered = mouseX >= saveAsBtnX && mouseX <= saveAsBtnX + BTN_WIDTH
                && mouseY >= inputY && mouseY <= inputY + INPUT_HEIGHT;
        saveAsHoverAnim = smooth(saveAsHoverAnim, saveAsHovered ? 1f : 0f, ANIMATION_SPEED);

        int saveAsBg = HadesAPI.Render.lerpColor(Theme.ACCENT_PRIMARY, Theme.ACCENT_SECONDARY, saveAsHoverAnim);
        HadesAPI.Render.drawRoundedRect(saveAsBtnX, inputY + (INPUT_HEIGHT - BTN_HEIGHT) / 2f, BTN_WIDTH, BTN_HEIGHT,
                BTN_RADIUS, saveAsBg);
        HadesAPI.Render.drawCenteredString("Save", saveAsBtnX + BTN_WIDTH / 2f,
                inputY + (INPUT_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.9f);

        // Config list
        float listY = inputY + INPUT_HEIGHT + 8;
        float listHeight = height - (listY - y) - 4;

        float currentY = listY - localScrollOffset;
        for (int i = 0; i < configs.size(); i++) {
            String configName = configs.get(i);
            float itemY = currentY + i * (ITEM_HEIGHT + ITEM_MARGIN);

            if (itemY + ITEM_HEIGHT < listY || itemY > listY + listHeight)
                continue;

            renderLocalConfigItem(configName, i, x + 6, itemY, width - 12, mouseX, mouseY);
        }

        // Scrollbar
        float totalContentHeight = configs.size() * (ITEM_HEIGHT + ITEM_MARGIN);
        if (totalContentHeight > listHeight) {
            float scrollbarHeight = (listHeight / totalContentHeight) * listHeight;
            float scrollbarY = listY + (localScrollOffset / totalContentHeight) * listHeight;
            HadesAPI.Render.drawRoundedRect(x + width - 8, scrollbarY, 3f, scrollbarHeight, 1.5f,
                    Theme.SCROLLBAR_THUMB);
        }
    }

    private void renderLocalConfigItem(String name, int index, float itemX, float itemY, float itemWidth, int mouseX, int mouseY) {
        boolean itemHovered = mouseX >= itemX && mouseX <= itemX + itemWidth
                && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;

        int bgColor = itemHovered ? HadesAPI.Render.color(32, 32, 38) : HadesAPI.Render.color(24, 24, 28);
        HadesAPI.Render.drawRoundedRect(itemX, itemY, itemWidth, ITEM_HEIGHT, 5f, bgColor);

        float nameY = itemY + (ITEM_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f;
        HadesAPI.Render.drawString(name, itemX + 12, nameY, Theme.TEXT_PRIMARY, 1.0f);

        float btnY = itemY + (ITEM_HEIGHT - BTN_HEIGHT) / 2f;
        float btnSpacing = 4f;
        float deleteBtnX = itemX + itemWidth - BTN_WIDTH - 8;
        float saveBtnX = deleteBtnX - BTN_WIDTH - btnSpacing;
        float loadBtnX = saveBtnX - BTN_WIDTH - btnSpacing;

        if (index >= loadHoverAnim.length) return;

        // Load
        boolean loadHovered = mouseX >= loadBtnX && mouseX <= loadBtnX + BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT;
        loadHoverAnim[index] = smooth(loadHoverAnim[index], loadHovered ? 1f : 0f, ANIMATION_SPEED);
        int loadBg = HadesAPI.Render.lerpColor(HadesAPI.Render.color(40, 40, 48), Theme.ACCENT_PRIMARY, loadHoverAnim[index]);
        HadesAPI.Render.drawRoundedRect(loadBtnX, btnY, BTN_WIDTH, BTN_HEIGHT, BTN_RADIUS, loadBg);
        HadesAPI.Render.drawCenteredString("Load", loadBtnX + BTN_WIDTH / 2f,
                btnY + (BTN_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.85f);

        // Save
        boolean saveHovered = mouseX >= saveBtnX && mouseX <= saveBtnX + BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT;
        saveHoverAnim[index] = smooth(saveHoverAnim[index], saveHovered ? 1f : 0f, ANIMATION_SPEED);
        int saveBg = HadesAPI.Render.lerpColor(HadesAPI.Render.color(40, 40, 48), HadesAPI.Render.color(60, 180, 75), saveHoverAnim[index]);
        HadesAPI.Render.drawRoundedRect(saveBtnX, btnY, BTN_WIDTH, BTN_HEIGHT, BTN_RADIUS, saveBg);
        HadesAPI.Render.drawCenteredString("Save", saveBtnX + BTN_WIDTH / 2f,
                btnY + (BTN_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.85f);

        // Delete
        boolean deleteHovered = mouseX >= deleteBtnX && mouseX <= deleteBtnX + BTN_WIDTH && mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT;
        deleteHoverAnim[index] = smooth(deleteHoverAnim[index], deleteHovered ? 1f : 0f, ANIMATION_SPEED);
        int deleteBg = HadesAPI.Render.lerpColor(HadesAPI.Render.color(40, 40, 48), HadesAPI.Render.color(220, 50, 50), deleteHoverAnim[index]);
        HadesAPI.Render.drawRoundedRect(deleteBtnX, btnY, BTN_WIDTH, BTN_HEIGHT, BTN_RADIUS, deleteBg);
        HadesAPI.Render.drawCenteredString("Delete", deleteBtnX + BTN_WIDTH / 2f,
                btnY + (BTN_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.85f);
    }

    // ══════════════════════════════════════════
    // CLOUD TAB
    // ══════════════════════════════════════════

    private void renderCloudTab(int mouseX, int mouseY, float startY, float partialTicks) {
        BackendConnection bc = BackendConnection.getInstance();

        if (!bc.isConfigsFetched()) {
            // Loading state
            HadesAPI.Render.drawCenteredString("Syncing configs from Hades Hub...", x + width / 2f, startY + 40, Theme.TEXT_MUTED, 1.0f);

            // Pulsing dot animation
            float dotPhase = (System.currentTimeMillis() % 1500) / 1500f;
            int dotAlpha = (int) (100 + 155 * Math.abs(Math.sin(dotPhase * Math.PI)));
            HadesAPI.Render.drawRoundedRect(x + width / 2f - 3, startY + 60, 6, 6, 3f,
                    HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, dotAlpha));
            return;
        }

        List<CloudConfig> configs = bc.getCloudConfigs();

        if (configs.isEmpty()) {
            HadesAPI.Render.drawCenteredString("No cloud configs", x + width / 2f, startY + 30, Theme.TEXT_MUTED, 1.0f);
            HadesAPI.Render.drawCenteredString("Upload or subscribe on the Web Hub", x + width / 2f, startY + 50, Theme.TEXT_MUTED, 0.8f);
            return;
        }

        cloudScrollOffset = smooth(cloudScrollOffset, cloudTargetScroll, 0.6f);

        // Connection status indicator
        boolean live = bc.isConnected();
        String statusText = live ? "● Live Synced" : "○ Offline";
        int statusColor = live ? HadesAPI.Render.color(50, 205, 50, 255) : HadesAPI.Render.color(255, 100, 50, 255);
        HadesAPI.Render.drawString(statusText, x + width - HadesAPI.Render.getStringWidth(statusText, 0.75f) - 8, startY, statusColor, 0.75f);

        HadesAPI.Render.drawString(configs.size() + " config" + (configs.size() != 1 ? "s" : ""),
                x + 8, startY, Theme.TEXT_MUTED, 0.8f);

        float listY = startY + 16;
        float listHeight = height - (listY - y) - 4;

        float currentY = listY - cloudScrollOffset;
        for (int i = 0; i < configs.size(); i++) {
            CloudConfig config = configs.get(i);
            float itemY = currentY + i * (ITEM_HEIGHT + ITEM_MARGIN);

            if (itemY + ITEM_HEIGHT < listY || itemY > listY + listHeight)
                continue;

            renderCloudConfigItem(config, i, x + 6, itemY, width - 12, mouseX, mouseY);
        }

        // Scrollbar
        float totalContentHeight = configs.size() * (ITEM_HEIGHT + ITEM_MARGIN);
        if (totalContentHeight > listHeight) {
            float scrollbarHeight = (listHeight / totalContentHeight) * listHeight;
            float scrollbarY = listY + (cloudScrollOffset / totalContentHeight) * listHeight;
            HadesAPI.Render.drawRoundedRect(x + width - 8, scrollbarY, 3f, scrollbarHeight, 1.5f,
                    Theme.SCROLLBAR_THUMB);
        }
    }

    private void renderCloudConfigItem(CloudConfig config, int index, float itemX, float itemY, float itemWidth, int mouseX, int mouseY) {
        boolean itemHovered = mouseX >= itemX && mouseX <= itemX + itemWidth
                && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;

        int bgColor = itemHovered ? HadesAPI.Render.color(32, 32, 38) : HadesAPI.Render.color(24, 24, 28);
        HadesAPI.Render.drawRoundedRect(itemX, itemY, itemWidth, ITEM_HEIGHT, 5f, bgColor);

        // Config name
        float nameY = itemY + 4;
        int nameColor = config.isOfficial ? Theme.ACCENT_PRIMARY : Theme.TEXT_PRIMARY;
        HadesAPI.Render.drawString(config.name, itemX + 12, nameY, nameColor, 0.95f);

        // Category + downloads on second line
        String meta = config.category + "  •  " + config.downloads + " downloads";
        if (config.rating > 0) {
            meta += "  •  ★ " + String.format("%.1f", config.rating);
        }
        HadesAPI.Render.drawString(meta, itemX + 12, nameY + 14, Theme.TEXT_MUTED, 0.7f);

        // Official badge
        if (config.isOfficial) {
            String badge = "OFFICIAL";
            float badgeW = HadesAPI.Render.getStringWidth(badge, 0.6f) + 8;
            float badgeX = itemX + 12 + HadesAPI.Render.getStringWidth(config.name, 0.95f) + 6;
            HadesAPI.Render.drawRoundedRect(badgeX, nameY, badgeW, 12, 3f,
                    HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 80));
            HadesAPI.Render.drawString(badge, badgeX + 4, nameY + 1, Theme.ACCENT_PRIMARY, 0.6f);
        }

        // Load button
        float btnY = itemY + (ITEM_HEIGHT - BTN_HEIGHT) / 2f;
        float loadBtnX = itemX + itemWidth - BTN_WIDTH - 8;

        if (index < cloudLoadHoverAnim.length) {
            boolean loadHovered = mouseX >= loadBtnX && mouseX <= loadBtnX + BTN_WIDTH
                    && mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT;
            cloudLoadHoverAnim[index] = smooth(cloudLoadHoverAnim[index], loadHovered ? 1f : 0f, ANIMATION_SPEED);

            int loadBg = HadesAPI.Render.lerpColor(HadesAPI.Render.color(40, 40, 48), Theme.ACCENT_PRIMARY,
                    cloudLoadHoverAnim[index]);
            HadesAPI.Render.drawRoundedRect(loadBtnX, btnY, BTN_WIDTH, BTN_HEIGHT, BTN_RADIUS, loadBg);
            HadesAPI.Render.drawCenteredString("Load", loadBtnX + BTN_WIDTH / 2f,
                    btnY + (BTN_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.85f);
        }
    }

    // ══════════════════════════════════════════
    // Input Handling
    // ══════════════════════════════════════════

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0)
            return;

        // Tab clicks
        float tabY = y + 4;
        float tabWidth = width / 2f;

        if (mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
            if (mouseX >= x && mouseX <= x + tabWidth) {
                activeTab = 0;
                return;
            } else if (mouseX >= x + tabWidth && mouseX <= x + width) {
                activeTab = 1;
                return;
            }
        }

        if (activeTab == 0) {
            handleLocalClick(mouseX, mouseY);
        } else {
            handleCloudClick(mouseX, mouseY);
        }
    }

    private void handleLocalClick(int mouseX, int mouseY) {
        ConfigManager configManager = HadesClient.getInstance().getConfigManager();
        List<String> configs = configManager.getConfigNames();

        float contentY = y + 4 + TAB_HEIGHT + 4 + 6;
        float inputY = contentY;
        float inputWidth = width - BTN_WIDTH - 16;

        inputFocused = mouseX >= x + 6 && mouseX <= x + 6 + inputWidth
                && mouseY >= inputY && mouseY <= inputY + INPUT_HEIGHT;

        // Save As button
        float saveAsBtnX = x + 6 + inputWidth + 6;
        if (mouseX >= saveAsBtnX && mouseX <= saveAsBtnX + BTN_WIDTH
                && mouseY >= inputY && mouseY <= inputY + INPUT_HEIGHT) {
            if (!inputText.trim().isEmpty()) {
                configManager.save(inputText.trim());
                inputText = "";
            }
            return;
        }

        // Config list buttons
        float listY = inputY + INPUT_HEIGHT + 8;
        float currentY = listY - localScrollOffset;

        for (int i = 0; i < configs.size(); i++) {
            String configName = configs.get(i);
            float itemY = currentY + i * (ITEM_HEIGHT + ITEM_MARGIN);
            float btnY = itemY + (ITEM_HEIGHT - BTN_HEIGHT) / 2f;
            float itemWidth = width - 12;
            float itemX = x + 6;

            float btnSpacing = 4f;
            float deleteBtnX = itemX + itemWidth - BTN_WIDTH - 8;
            float saveBtnX = deleteBtnX - BTN_WIDTH - btnSpacing;
            float loadBtnX = saveBtnX - BTN_WIDTH - btnSpacing;

            if (mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT) {
                if (mouseX >= loadBtnX && mouseX <= loadBtnX + BTN_WIDTH) {
                    configManager.load(configName);
                    return;
                }
                if (mouseX >= saveBtnX && mouseX <= saveBtnX + BTN_WIDTH) {
                    configManager.save(configName);
                    return;
                }
                if (mouseX >= deleteBtnX && mouseX <= deleteBtnX + BTN_WIDTH) {
                    configManager.delete(configName);
                    return;
                }
            }
        }
    }

    private void handleCloudClick(int mouseX, int mouseY) {
        BackendConnection bc = BackendConnection.getInstance();
        if (!bc.isConfigsFetched()) return;

        List<CloudConfig> configs = bc.getCloudConfigs();
        float contentY = y + 4 + TAB_HEIGHT + 4 + 6;
        float listY = contentY + 16;

        float currentY = listY - cloudScrollOffset;

        for (int i = 0; i < configs.size(); i++) {
            CloudConfig config = configs.get(i);
            float itemY = currentY + i * (ITEM_HEIGHT + ITEM_MARGIN);
            float btnY = itemY + (ITEM_HEIGHT - BTN_HEIGHT) / 2f;
            float itemWidth = width - 12;
            float itemX = x + 6;

            float loadBtnX = itemX + itemWidth - BTN_WIDTH - 8;

            if (mouseX >= loadBtnX && mouseX <= loadBtnX + BTN_WIDTH
                    && mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT) {
                // Download and apply the cloud config in a background thread
                final String filePath = config.filePath;
                final String configName = config.name;
                new Thread(() -> {
                    String json = bc.downloadConfigJson(filePath);
                    if (json != null) {
                        HadesClient.getInstance().getConfigManager().loadFromJson(json, configName);
                    }
                }, "Hades-CloudConfigLoad").start();
                return;
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!inputFocused || activeTab != 0)
            return;

        if (keyCode == 14) { // Backspace
            if (!inputText.isEmpty()) {
                inputText = inputText.substring(0, inputText.length() - 1);
            }
        } else if (keyCode == 28) { // Enter
            if (!inputText.trim().isEmpty()) {
                HadesClient.getInstance().getConfigManager().save(inputText.trim());
                inputText = "";
            }
            inputFocused = false;
        } else if (keyCode == 1) { // ESC
            inputFocused = false;
        } else if (typedChar >= 32 && typedChar < 127 && inputText.length() < 24) {
            if (Character.isLetterOrDigit(typedChar) || typedChar == ' ' || typedChar == '-' || typedChar == '_') {
                inputText += typedChar;
            }
        }
    }

    public void scroll(int amount) {
        if (activeTab == 0) {
            ConfigManager configManager = HadesClient.getInstance().getConfigManager();
            float totalContentHeight = configManager.getConfigNames().size() * (ITEM_HEIGHT + ITEM_MARGIN);
            float contentY = y + 4 + TAB_HEIGHT + 4 + 6;
            float listY = contentY + INPUT_HEIGHT + 8;
            float listHeight = height - (listY - y) - 4;
            float maxScroll = Math.max(0, totalContentHeight - listHeight);

            localTargetScroll -= amount * 35f;
            localTargetScroll = Math.max(0, Math.min(maxScroll, localTargetScroll));
        } else {
            BackendConnection bc = BackendConnection.getInstance();
            List<CloudConfig> configs = bc.getCloudConfigs();
            float totalContentHeight = configs.size() * (ITEM_HEIGHT + ITEM_MARGIN);
            float contentY = y + 4 + TAB_HEIGHT + 4 + 6;
            float listY = contentY + 16;
            float listHeight = height - (listY - y) - 4;
            float maxScroll = Math.max(0, totalContentHeight - listHeight);

            cloudTargetScroll -= amount * 35f;
            cloudTargetScroll = Math.max(0, Math.min(maxScroll, cloudTargetScroll));
        }
    }
}

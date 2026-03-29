package com.hades.client.gui.clickgui.component;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.manager.ProxyManager;

import java.util.List;
import java.util.stream.Collectors;

public class ProxyScreenComponent extends Component {

    // Inputs
    private String inputStr = "";
    private boolean isFocused = false;
    private float inputCursorBlink = 0f;
    
    // Buttons Animations
    private float addHoverAnim = 0f;
    private float refreshHoverAnim = 0f;
    private float disableHoverAnim = 0f;
    
    // Scrolling
    private float scrollY = 0f;
    private float targetScrollY = 0f;

    // Tabs
    private int currentTab = 0;

    // Status message for feedback
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private int statusMessageType = 0; // 0 normal, 1 error, 2 success

    // Constants
    private static final float INPUT_HEIGHT = 28f;

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            isFocused = false;
        } else {
            // Sync Current Tab with Active ProxyType if exists
            if (ProxyManager.getInstance().getActiveProxy() != null) {
                currentTab = ProxyManager.getInstance().getActiveProxy().type.ordinal();
            }
        }
    }

    private void pushStatus(String msg, int type) {
        this.statusMessage = msg;
        this.statusMessageType = type;
        this.statusMessageTime = System.currentTimeMillis();
    }

    private float smoothLoc(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        // Big Header
        HadesAPI.Render.drawString("Proxy Manager", x + 12, y + 12, Theme.TEXT_PRIMARY, 1.25f);
        HadesAPI.Render.drawString("Tunnel network connection for IP masking", x + 12, y + 26, Theme.TEXT_MUTED, 0.8f);
        
        // Separator
        float contentY = y + 40;
        HadesAPI.Render.drawRect(x + 10, contentY, width - 20, 1, HadesAPI.Render.color(40, 40, 45, 255));
        
        float currentY = contentY + 15;
        
        // Render Tabs
        float tabWidth = 80f;
        float totalTabWidth = tabWidth * 3 + 10 * 2;
        float startTabX = x + (width - totalTabWidth) / 2f;
        ProxyManager.ProxyType[] types = ProxyManager.ProxyType.values();
        
        for (int i = 0; i < types.length; i++) {
            boolean activeTab = currentTab == i;
            float tX = startTabX + i * (tabWidth + 10);
            boolean hovered = mouseX >= tX && mouseX <= tX + tabWidth && mouseY >= currentY && mouseY <= currentY + 22f;
            
            int bgColor = activeTab ? Theme.ACCENT_PRIMARY : (hovered ? HadesAPI.Render.color(50, 50, 55) : HadesAPI.Render.color(40, 40, 45));
            HadesAPI.Render.drawRoundedRect(tX, currentY, tabWidth, 22f, 4f, bgColor);
            HadesAPI.Render.drawCenteredString(types[i].name(), tX + tabWidth / 2f, currentY + 6, Theme.TEXT_PRIMARY, 0.85f);
        }
        
        currentY += 35;
        
        // Input & Controls Row
        float inputWidth = width - 180;
        float inputX = x + 15;
        
        int inputBg = isFocused ? HadesAPI.Render.color(35, 35, 40) : HadesAPI.Render.color(28, 28, 32);
        HadesAPI.Render.drawRoundedRect(inputX, currentY, inputWidth, INPUT_HEIGHT, 4f, inputBg);

        if (isFocused) {
            HadesAPI.Render.drawRoundedRect(inputX - 1, currentY - 1, inputWidth + 2, INPUT_HEIGHT + 2, 5f, HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 100));
            HadesAPI.Render.drawRoundedRect(inputX, currentY, inputWidth, INPUT_HEIGHT, 4f, inputBg);
        }

        float textY = currentY + (INPUT_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f;
        if (inputStr.isEmpty() && !isFocused) {
            HadesAPI.Render.drawString("Paste proxy (IP:Port:User:Pass)", inputX + 10, textY, Theme.TEXT_MUTED, 1.0f);
        } else {
            if (isFocused) inputCursorBlink += 0.05f;
            String displayVal = inputStr;
            // Trim display if too long
            if (displayVal.length() > 30) displayVal = "..." + displayVal.substring(displayVal.length() - 30);
            String displayText = displayVal + (isFocused && ((int) (inputCursorBlink * 2) % 2 == 0) ? "|" : "");
            HadesAPI.Render.drawString(displayText, inputX + 10, textY, Theme.TEXT_PRIMARY, 1.0f);
        }
        
        // Add Button
        float addBtnX = inputX + inputWidth + 10;
        float addBtnWidth = 60f;
        boolean addHovered = mouseX >= addBtnX && mouseX <= addBtnX + addBtnWidth && mouseY >= currentY && mouseY <= currentY + INPUT_HEIGHT;
        addHoverAnim = smoothLoc(addHoverAnim, addHovered ? 1f : 0f, 0.2f);
        int addBg = HadesAPI.Render.lerpColor(HadesAPI.Render.color(40, 40, 45, 255), Theme.ACCENT_PRIMARY, addHoverAnim);
        HadesAPI.Render.drawRoundedRect(addBtnX, currentY, addBtnWidth, INPUT_HEIGHT, 4f, addBg);
        HadesAPI.Render.drawCenteredString("Add", addBtnX + addBtnWidth / 2f, currentY + (INPUT_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.95f);

        // Refresh Button
        float refBtnX = addBtnX + addBtnWidth + 10;
        float refBtnWidth = 60f;
        boolean refHovered = mouseX >= refBtnX && mouseX <= refBtnX + refBtnWidth && mouseY >= currentY && mouseY <= currentY + INPUT_HEIGHT;
        refreshHoverAnim = smoothLoc(refreshHoverAnim, refHovered ? 1f : 0f, 0.2f);
        int refBg = HadesAPI.Render.lerpColor(HadesAPI.Render.color(40, 40, 45, 255), Theme.ACCENT_SECONDARY, refreshHoverAnim);
        HadesAPI.Render.drawRoundedRect(refBtnX, currentY, refBtnWidth, INPUT_HEIGHT, 4f, refBg);
        HadesAPI.Render.drawCenteredString("Refresh", refBtnX + refBtnWidth / 2f, currentY + (INPUT_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.95f);

        currentY += INPUT_HEIGHT + 15;
        
        // List Title
        HadesAPI.Render.drawString("Saved " + types[currentTab].name() + " Proxies", x + 15, currentY, Theme.TEXT_PRIMARY, 0.9f);
        
        // Disable Global Button
        boolean hasActiveProxy = ProxyManager.getInstance().getActiveProxy() != null;
        if (hasActiveProxy) {
            float disW = 80f;
            float disX = x + width - 15 - disW;
            boolean disHovered = mouseX >= disX && mouseX <= disX + disW && mouseY >= currentY - 5 && mouseY <= currentY - 5 + 20;
            disableHoverAnim = smoothLoc(disableHoverAnim, disHovered ? 1f : 0f, 0.2f);
            int disBg = HadesAPI.Render.lerpColor(HadesAPI.Render.color(240, 60, 60, 150), HadesAPI.Render.color(240, 60, 60, 255), disableHoverAnim);
            HadesAPI.Render.drawRoundedRect(disX, currentY - 5, disW, 20f, 3f, disBg);
            HadesAPI.Render.drawCenteredString("Disable", disX + disW / 2f, currentY - 5 + (20 - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.85f);
        }

        currentY += 15;
        
        // Draw List
        float listStartY = currentY;
        float listBottom = y + height - 30; // Leave room for status
        
        HadesAPI.Render.drawRect(x + 10, listStartY, width - 20, listBottom - listStartY, HadesAPI.Render.color(20, 20, 24, 255)); // List BG
        
        // Render Proxies Masked
        ProxyManager.ProxyType targetType = types[currentTab];
        List<ProxyManager.ProxyEntry> visibleProxies = ProxyManager.getInstance().getSavedProxies().stream()
                .filter(p -> p.type == targetType)
                .collect(Collectors.toList());

        // Apply scrolling
        scrollY = smoothLoc(scrollY, targetScrollY, 0.3f);
        float itemY = listStartY + 5 + scrollY;
        
        if (visibleProxies.isEmpty()) {
            HadesAPI.Render.drawCenteredString("No Proxies found.", x + width / 2f, listStartY + 20 + scrollY, Theme.TEXT_MUTED, 0.9f);
        } else {
            for (ProxyManager.ProxyEntry entry : visibleProxies) {
                if (itemY + 30 > listStartY && itemY < listBottom) {
                    boolean isActive = ProxyManager.getInstance().getActiveProxy() == entry;
                    
                    int cardBg = isActive ? HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 80) : HadesAPI.Render.color(30, 30, 35, 255);
                    HadesAPI.Render.drawRoundedRect(x + 15, itemY, width - 30, 30f, 4f, cardBg);
                    
                    // Format: IP:PORT
                    HadesAPI.Render.drawString(entry.ip + ":" + entry.port, x + 25, itemY + 6, Theme.TEXT_PRIMARY, 1.0f);
                    
                    if (entry.username != null && !entry.username.isEmpty()) {
                        HadesAPI.Render.drawString("User: " + entry.username, x + 25, itemY + 18, Theme.TEXT_MUTED, 0.75f);
                    }
                    
                    // State Badge
                    String stateText;
                    int stateColor;
                    switch (entry.state) {
                        case TESTING: stateText = "Testing..."; stateColor = HadesAPI.Render.color(240, 200, 60); break;
                        case VALID: stateText = entry.ping != -1 ? "OK (" + entry.ping + "ms)" : "Valid"; stateColor = HadesAPI.Render.color(80, 255, 80); break;
                        case INVALID: stateText = "Dead / Invalid"; stateColor = HadesAPI.Render.color(255, 80, 80); break;
                        default: stateText = "Unknown"; stateColor = Theme.TEXT_MUTED; break;
                    }
                    float strW = HadesAPI.Render.getStringWidth(stateText) * 0.85f;
                    HadesAPI.Render.drawString(stateText, x + width - 50 - strW, itemY + 11, stateColor, 0.85f);
                    
                    // Delete Button (X)
                    HadesAPI.Render.drawString("X", x + width - 30, itemY + 10, HadesAPI.Render.color(255, 80, 80, 255), 1.0f);
                }
                itemY += 35;
            }
        }
        
        
        // Status Message Rendering Bottom
        if (System.currentTimeMillis() - statusMessageTime < 4000) {
            int msgCol = Theme.TEXT_PRIMARY;
            if (statusMessageType == 1) msgCol = HadesAPI.Render.color(255, 80, 80);
            if (statusMessageType == 2) msgCol = HadesAPI.Render.color(80, 255, 80);
            
            float fade = 1.0f - ((System.currentTimeMillis() - statusMessageTime) / 4000f);
            if (fade < 0) fade = 0;
            
            HadesAPI.Render.drawCenteredString(statusMessage, x + width / 2f, y + height - 20, HadesAPI.Render.colorWithAlpha(msgCol, (int)(255 * fade)), 0.9f);
        } else if (hasActiveProxy) {
            String activeFmt = ProxyManager.getInstance().getActiveProxy().type.name() + " -> " + ProxyManager.getInstance().getActiveProxy().ip + ":" + ProxyManager.getInstance().getActiveProxy().port;
            HadesAPI.Render.drawCenteredString("Active: " + activeFmt, x + width / 2f, y + height - 20, HadesAPI.Render.color(80, 255, 80, 255), 0.85f);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return;

        float contentY = y + 40;
        float currentY = contentY + 15;
        
        // Tab Clicks
        float tabWidth = 80f;
        float totalTabWidth = tabWidth * 3 + 10 * 2;
        float startTabX = x + (width - totalTabWidth) / 2f;
        for (int i = 0; i < 3; i++) {
            float tX = startTabX + i * (tabWidth + 10);
            if (mouseX >= tX && mouseX <= tX + tabWidth && mouseY >= currentY && mouseY <= currentY + 22f) {
                currentTab = i;
                targetScrollY = 0f;
                return;
            }
        }
        
        currentY += 35;
        
        // Input Clicks
        float inputWidth = width - 180;
        float inputX = x + 15;
        isFocused = mouseX >= inputX && mouseX <= inputX + inputWidth && mouseY >= currentY && mouseY <= currentY + INPUT_HEIGHT;

        // Add
        float addBtnX = inputX + inputWidth + 10;
        float addBtnWidth = 60f;
        if (mouseX >= addBtnX && mouseX <= addBtnX + addBtnWidth && mouseY >= currentY && mouseY <= currentY + INPUT_HEIGHT) {
            addProxy();
            return;
        }

        // Refresh
        float refBtnX = addBtnX + addBtnWidth + 10;
        float refBtnWidth = 60f;
        if (mouseX >= refBtnX && mouseX <= refBtnX + refBtnWidth && mouseY >= currentY && mouseY <= currentY + INPUT_HEIGHT) {
            ProxyManager.getInstance().refreshAll();
            pushStatus("Testing all proxies...", 2);
            return;
        }

        currentY += INPUT_HEIGHT + 15;
        
        // Disable Global Button
        if (ProxyManager.getInstance().getActiveProxy() != null) {
            float disW = 80f;
            float disX = x + width - 15 - disW;
            if (mouseX >= disX && mouseX <= disX + disW && mouseY >= currentY - 5 && mouseY <= currentY - 5 + 20) {
                ProxyManager.getInstance().disableProxy();
                pushStatus("Proxy connection disabled.", 2);
                return;
            }
        }

        currentY += 15;
        
        // List Item Clicks
        float listStartY = currentY;
        float listBottom = y + height - 30;
        
        if (mouseY >= listStartY && mouseY <= listBottom && mouseX >= x + 10 && mouseX <= x + width - 10) {
            ProxyManager.ProxyType targetType = ProxyManager.ProxyType.values()[currentTab];
            List<ProxyManager.ProxyEntry> visibleProxies = ProxyManager.getInstance().getSavedProxies().stream()
                    .filter(p -> p.type == targetType)
                    .collect(Collectors.toList());
                    
            float itemY = listStartY + 5 + targetScrollY; // Use purely target for instant click registering
            for (ProxyManager.ProxyEntry entry : visibleProxies) {
                if (itemY + 30 > listStartY && itemY < listBottom) {
                    // Check bounds for item
                    if (mouseY >= itemY && mouseY <= itemY + 30 && mouseX >= x + 15 && mouseX <= x + width - 15) {
                        // Check if delete clicked
                        if (mouseX >= x + width - 40) {
                            ProxyManager.getInstance().removeProxy(entry);
                            pushStatus("Proxy deleted.", 1);
                        } else {
                            // Select
                            ProxyManager.getInstance().setActiveProxy(entry);
                            pushStatus("Active Proxy selected.", 2);
                        }
                        return;
                    }
                }
                itemY += 35;
            }
        }
    }

    private void addProxy() {
        if (inputStr.trim().isEmpty()) {
            pushStatus("Input cannot be empty!", 1);
            return;
        }

        String[] parts = inputStr.trim().split(":");
        if (parts.length < 2) {
            pushStatus("Format must be IP:PORT[:USER:PASS]", 1);
            return;
        }

        String ip = parts[0].trim();
        String port = parts[1].trim();
        String user = parts.length > 2 ? parts[2].trim() : "";
        String pass = parts.length > 3 ? parts[3].trim() : "";

        try {
            Integer.parseInt(port);
        } catch (NumberFormatException e) {
            pushStatus("Port must be a valid number!", 1);
            return;
        }

        ProxyManager.ProxyType selectedType = ProxyManager.ProxyType.values()[currentTab];
        ProxyManager.ProxyEntry entry = new ProxyManager.ProxyEntry(ip, port, user, pass, selectedType);
        ProxyManager.getInstance().addProxy(entry);
        ProxyManager.getInstance().testProxyAsync(entry);
        
        inputStr = ""; // Clear input
        isFocused = false;
        pushStatus("Proxy added & testing...", 2);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {}

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!isFocused) return;

        if (keyCode == 14) { // Backspace
            if (!inputStr.isEmpty()) {
                inputStr = inputStr.substring(0, inputStr.length() - 1);
            }
        } else if (keyCode == 28) { // Enter
            addProxy();
        } else if (keyCode == 1) { // ESC
            isFocused = false;
        } else if (typedChar >= 32 && typedChar <= 126 && inputStr.length() < 120) {
            inputStr += typedChar;
        }
    }

    public void scroll(int amount) {
        if (!visible) return;
        ProxyManager.ProxyType targetType = ProxyManager.ProxyType.values()[currentTab];
        long count = ProxyManager.getInstance().getSavedProxies().stream().filter(p -> p.type == targetType).count();
        
        float maxScroll = 0;
        float totalHeight = count * 35;
        float visibleHeight = (y + height - 30) - (y + 40 + 15 + 35 + INPUT_HEIGHT + 15 + 15);
        if (totalHeight > visibleHeight) {
            maxScroll = totalHeight - visibleHeight + 10;
        }
        
        targetScrollY += amount * 20f;
        if (targetScrollY > 0) targetScrollY = 0;
        if (targetScrollY < -maxScroll) targetScrollY = -maxScroll;
    }
}

package com.hades.client.gui.clickgui.component;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;
import com.hades.client.manager.SessionManager;

public class AccountScreenComponent extends Component {

    // Login Form
    private String inputText = "";
    private boolean inputFocused = false;
    private float inputCursorBlink = 0f;
    private float loginHoverAnim = 0f;
    
    // Status message for feedback
    private String statusMessage = "";
    private long statusMessageTime = 0;
    private int statusMessageType = 0; // 0 normal, 1 error, 2 success

    // Constants
    private static final float INPUT_HEIGHT = 30f;
    private static final float BTN_WIDTH = 80f;
    private static final float BTN_HEIGHT = 26f;
    private static final float BTN_RADIUS = 4f;

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            inputFocused = false;
        }
    }

    private void pushStatus(String msg, int type) {
        this.statusMessage = msg;
        this.statusMessageType = type;
        this.statusMessageTime = System.currentTimeMillis();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        // Big Header
        HadesAPI.Render.drawString("Account Manager", x + 12, y + 12, Theme.TEXT_PRIMARY, 1.25f);
        HadesAPI.Render.drawString("Manage your active multiplayer identity", x + 12, y + 26, Theme.TEXT_MUTED, 0.8f);
        
        // Separator
        float contentY = y + 40;
        HadesAPI.Render.drawRect(x + 10, contentY, width - 20, 1, HadesAPI.Render.color(40, 40, 45, 255));
        
        // Render Cracked Login Form (Centered)
        float currentY = contentY + 30;
        
        HadesAPI.Render.drawCenteredString("Offline Session Spoofing", x + width / 2f, currentY, Theme.TEXT_PRIMARY, 1.0f);
        currentY += 15;
        HadesAPI.Render.drawCenteredString("Changes will apply on your next server connection.", x + width / 2f, currentY, Theme.TEXT_MUTED, 0.8f);
        currentY += 25;

        // Input Field
        float inputWidth = 200f;
        float inputX = x + (width - inputWidth) / 2f;
        
        int inputBg = inputFocused ? HadesAPI.Render.color(35, 35, 40) : HadesAPI.Render.color(28, 28, 32);
        HadesAPI.Render.drawRoundedRect(inputX, currentY, inputWidth, INPUT_HEIGHT, 4f, inputBg);

        if (inputFocused) {
            HadesAPI.Render.drawRoundedRect(inputX - 1, currentY - 1, inputWidth + 2, INPUT_HEIGHT + 2, 5f, HadesAPI.Render.colorWithAlpha(Theme.ACCENT_PRIMARY, 100));
            HadesAPI.Render.drawRoundedRect(inputX, currentY, inputWidth, INPUT_HEIGHT, 4f, inputBg);
        }

        float textY = currentY + (INPUT_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f;
        if (inputText.isEmpty() && !inputFocused) {
            HadesAPI.Render.drawString("Cracked Username...", inputX + 10, textY, Theme.TEXT_MUTED, 1.0f);
        } else {
            inputCursorBlink += 0.05f;
            String displayText = inputText + (inputFocused && ((int) (inputCursorBlink * 2) % 2 == 0) ? "|" : "");
            HadesAPI.Render.drawString(displayText, inputX + 10, textY, Theme.TEXT_PRIMARY, 1.0f);
        }
        
        currentY += INPUT_HEIGHT + 15;

        // Login Button
        float btnX = x + (width - BTN_WIDTH) / 2f;
        boolean loginHovered = mouseX >= btnX && mouseX <= btnX + BTN_WIDTH && mouseY >= currentY && mouseY <= currentY + BTN_HEIGHT;
        loginHoverAnim = smooth(loginHoverAnim, loginHovered ? 1f : 0f, 0.2f);

        int loginBg = HadesAPI.Render.lerpColor(Theme.ACCENT_PRIMARY, Theme.ACCENT_SECONDARY, loginHoverAnim);
        HadesAPI.Render.drawRoundedRect(btnX, currentY, BTN_WIDTH, BTN_HEIGHT, BTN_RADIUS, loginBg);
        HadesAPI.Render.drawCenteredString("Login", btnX + BTN_WIDTH / 2f, currentY + (BTN_HEIGHT - HadesAPI.Render.getFontHeight()) / 2f, Theme.TEXT_PRIMARY, 0.95f);

        currentY += BTN_HEIGHT + 20;

        // Status Message Rendering
        if (System.currentTimeMillis() - statusMessageTime < 4000) {
            int msgCol = Theme.TEXT_PRIMARY;
            if (statusMessageType == 1) msgCol = HadesAPI.Render.color(255, 80, 80);
            if (statusMessageType == 2) msgCol = HadesAPI.Render.color(80, 255, 80);
            
            // Fade out
            float fade = 1.0f - ((System.currentTimeMillis() - statusMessageTime) / 4000f);
            if (fade < 0) fade = 0;
            
            HadesAPI.Render.drawCenteredString(statusMessage, x + width / 2f, currentY, HadesAPI.Render.colorWithAlpha(msgCol, (int)(255 * fade)), 0.9f);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return;

        float contentY = y + 40;
        float currentY = contentY + 30 + 15 + 25;
        
        float inputWidth = 200f;
        float inputX = x + (width - inputWidth) / 2f;

        inputFocused = mouseX >= inputX && mouseX <= inputX + inputWidth && mouseY >= currentY && mouseY <= currentY + INPUT_HEIGHT;

        currentY += INPUT_HEIGHT + 15;
        float btnX = x + (width - BTN_WIDTH) / 2f;

        if (mouseX >= btnX && mouseX <= btnX + BTN_WIDTH && mouseY >= currentY && mouseY <= currentY + BTN_HEIGHT) {
            submitLogin();
        }
    }

    private void submitLogin() {
        if (inputText.trim().isEmpty()) {
            pushStatus("Username cannot be empty!", 1);
            return;
        }

        boolean success = SessionManager.getInstance().setCrackedSession(inputText.trim());
        if (success) {
            pushStatus("Successfully injected identity: " + inputText.trim(), 2);
            inputText = "";
            inputFocused = false;
        } else {
            pushStatus("Failed to inject tracking hooks. See logs.", 1);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {}

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!inputFocused) return;

        if (keyCode == 14) { // Backspace
            if (!inputText.isEmpty()) {
                inputText = inputText.substring(0, inputText.length() - 1);
            }
        } else if (keyCode == 28) { // Enter
            submitLogin();
        } else if (keyCode == 1) { // ESC
            inputFocused = false;
        } else if (typedChar >= 32 && typedChar <= 126 && inputText.length() < 16) {
            // Regex match for allowed Minecraft username characters
            if (Character.isLetterOrDigit(typedChar) || typedChar == '_') {
                inputText += typedChar;
            }
        }
    }

    public void scroll(int amount) {
        // No scrolling needed currently on the crack login form
    }
}

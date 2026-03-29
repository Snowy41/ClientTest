package com.hades.client.gui.clickgui.component;

import com.hades.client.api.HadesAPI;
import com.hades.client.gui.clickgui.theme.Theme;

public class Sidebar extends Component {
    private float hudHoverAnimation = 0f;
    private float configHoverAnimation = 0f;
    private float themeHoverAnimation = 0f;
    private float profileHoverAnimation = 0f;

    private float accountHoverAnimation = 0f;
    private float proxyHoverAnimation = 0f;

    private final Runnable onHudClick;
    private final Runnable onConfigsClick;
    private final Runnable onThemeClick;
    private final Runnable onAccountClick;
    private final Runnable onProxyClick;
    private final Runnable onProfileClick;

    private boolean hudActive;
    private boolean configActive;
    private boolean themeActive;
    private boolean accountActive;
    private boolean proxyActive;
    private boolean profileActive;

    public Sidebar(Runnable onHudClick, Runnable onConfigsClick, Runnable onThemeClick, Runnable onAccountClick, Runnable onProxyClick, Runnable onProfileClick) {
        this.onHudClick = onHudClick;
        this.onConfigsClick = onConfigsClick;
        this.onThemeClick = onThemeClick;
        this.onAccountClick = onAccountClick;
        this.onProxyClick = onProxyClick;
        this.onProfileClick = onProfileClick;
    }

    public void setActiveState(boolean hud, boolean config, boolean theme, boolean account, boolean proxy, boolean profile) {
        this.hudActive = hud;
        this.configActive = config;
        this.themeActive = theme;
        this.accountActive = account;
        this.proxyActive = proxy;
        this.profileActive = profile;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        HadesAPI.Render.drawRoundedRect(x, y, width, height, Theme.WINDOW_RADIUS, 0, 0, Theme.WINDOW_RADIUS, Theme.SIDEBAR_BG);

        float logoSize = 48f;
        float logoX = x + (width - logoSize) / 2f;
        float logoY = y + Theme.PADDING_LARGE;

        boolean logoRendered = HadesAPI.Render.drawImage("hades", "pictures/logo.png", logoX, logoY, logoSize, logoSize);
        if (!logoRendered) {
            HadesAPI.Render.drawString("HADES", x + width / 2f - HadesAPI.Render.getStringWidth("HADES", 18f, true, false) / 2f,
                    logoY + logoSize / 2f - 5, Theme.ACCENT_PRIMARY, 18f, true, false, false);
        }

        // ── Pre-calculate positions and animations ──
        float sepY = logoY + logoSize + Theme.PADDING_LARGE;
        HadesAPI.Render.drawRect(x + 10, sepY, width - 20, 1, Theme.SIDEBAR_SEPARATOR);

        float currentBtnY = sepY + Theme.PADDING_LARGE;

        // Only explicitly enabled if HUD module exists and is enabled
        com.hades.client.module.Module hudMod = com.hades.client.HadesClient.getInstance().getModuleManager().getModule("HUD");
        boolean hudModuleOn = (hudMod != null && hudMod.isEnabled());

        // Calculate hovers dynamically
        
        // Config
        float configY = currentBtnY;
        boolean configHovered = mouseX >= x && mouseX <= x + width && mouseY >= configY && mouseY <= configY + 30;
        configHoverAnimation = smooth(configHoverAnimation, configHovered ? 1f : 0f, ANIMATION_SPEED);
        currentBtnY += 34;

        // Theme
        float themeY = currentBtnY;
        boolean themeHovered = mouseX >= x && mouseX <= x + width && mouseY >= themeY && mouseY <= themeY + 30;
        themeHoverAnimation = smooth(themeHoverAnimation, themeHovered ? 1f : 0f, ANIMATION_SPEED);
        currentBtnY += 34;

        // Accounts
        float accountY = currentBtnY;
        boolean accountHovered = mouseX >= x && mouseX <= x + width && mouseY >= accountY && mouseY <= accountY + 30;
        accountHoverAnimation = smooth(accountHoverAnimation, accountHovered ? 1f : 0f, ANIMATION_SPEED);
        currentBtnY += 34;

        // Proxies
        float proxyY = currentBtnY;
        boolean proxyHovered = mouseX >= x && mouseX <= x + width && mouseY >= proxyY && mouseY <= proxyY + 30;
        proxyHoverAnimation = smooth(proxyHoverAnimation, proxyHovered ? 1f : 0f, ANIMATION_SPEED);
        currentBtnY += 34;

        // HUD
        float hudY = currentBtnY;
        if (hudModuleOn) {
            boolean hudHovered = mouseX >= x && mouseX <= x + width && mouseY >= hudY && mouseY <= hudY + 30;
            hudHoverAnimation = smooth(hudHoverAnimation, hudHovered ? 1f : 0f, ANIMATION_SPEED);
            currentBtnY += 34;
        }

        // Profile
        float sep2Y = y + height - 60;
        float profileY = sep2Y + Theme.PADDING;
        float profileH = 34f;
        boolean profileHovered = mouseX >= x && mouseX <= x + width && mouseY >= profileY && mouseY <= profileY + profileH;
        profileHoverAnimation = smooth(profileHoverAnimation, profileHovered ? 1f : 0f, ANIMATION_SPEED);

        // ── Draw Shadows ──
        // Removed drop shadow


        // ── Buttons ──
        int baseBtnColor = HadesAPI.Render.color(22, 22, 26, 255);
        int hoverBtnColor = HadesAPI.Render.color(35, 35, 42, 255);
        int activeBtnColor = HadesAPI.Render.color(55, 55, 65, 255);
        float configIconSize = 14f;

        if (hudModuleOn) {
            int hudBg = hudActive ? activeBtnColor : HadesAPI.Render.lerpColor(baseBtnColor, hoverBtnColor, hudHoverAnimation);
            HadesAPI.Render.drawRoundedRect(x + 6, hudY, width - 12, 30, 4f, hudBg);
            
            int hudTextColor = hudActive ? Theme.TEXT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, hudHoverAnimation);
            float hudTextWidth = HadesAPI.Render.getStringWidth("HUD", 14f, true, false);
            float hudTotalW = configIconSize + 4 + hudTextWidth;
            float hudStartX = x + (width - hudTotalW) / 2f;
            
            if (HadesAPI.Render.drawImage("hades", "pictures/icons/theme.png", hudStartX, hudY + (30 - configIconSize) / 2f, configIconSize, configIconSize)) {
                HadesAPI.Render.drawString("HUD", hudStartX + configIconSize + 4, hudY + (30 - HadesAPI.Render.getFontHeight(14f, true, false)) / 2f, hudTextColor, 14f, true, false, false);
            } else {
                HadesAPI.Render.drawCenteredString("⊟ HUD", x + width / 2f, hudY + 10, hudTextColor, 14f, true, false, false);
            }
        }

        // ── Config Button ──
        
        int configBg = configActive ? activeBtnColor : HadesAPI.Render.lerpColor(baseBtnColor, hoverBtnColor, configHoverAnimation);
        HadesAPI.Render.drawRoundedRect(x + 6, configY, width - 12, 30, 4f, configBg);
        
        int configTextColor = configActive ? Theme.TEXT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, configHoverAnimation);
        float configTextWidth = HadesAPI.Render.getStringWidth("Configs", 14f, true, false); // MEDIUM_BOLD
        float configTotalWidth = configIconSize + 4 + configTextWidth;
        float configStartX = x + (width - configTotalWidth) / 2f;
        float configIconY = configY + (30 - configIconSize) / 2f;

        if (HadesAPI.Render.drawImage("hades", "pictures/icons/config.png", configStartX, configIconY, configIconSize, configIconSize)) {
            HadesAPI.Render.drawString("Configs", configStartX + configIconSize + 4, configY + (30 - HadesAPI.Render.getFontHeight(14f, true, false)) / 2f, configTextColor, 14f, true, false, false);
        } else {
            HadesAPI.Render.drawCenteredString("⚙ Configs", x + width / 2f, configY + 10, configTextColor, 14f, true, false, false);
        }

        // ── Theme Button ──
        int themeBg = themeActive ? activeBtnColor : HadesAPI.Render.lerpColor(baseBtnColor, hoverBtnColor, themeHoverAnimation);
        HadesAPI.Render.drawRoundedRect(x + 6, themeY, width - 12, 30, 4f, themeBg);

        int themeTextColor = themeActive ? Theme.TEXT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, themeHoverAnimation);
        float themeTextWidth = HadesAPI.Render.getStringWidth("Theme", 14f, true, false); // MEDIUM_BOLD
        float themeTotalWidth = configIconSize + 4 + themeTextWidth;
        float themeStartX = x + (width - themeTotalWidth) / 2f;
        
        if (HadesAPI.Render.drawImage("hades", "pictures/icons/theme.png", themeStartX, themeY + (30 - configIconSize) / 2f, configIconSize, configIconSize)) {
            HadesAPI.Render.drawString("Theme", themeStartX + configIconSize + 4, themeY + (30 - HadesAPI.Render.getFontHeight(14f, true, false)) / 2f, themeTextColor, 14f, true, false, false);
        } else {
            HadesAPI.Render.drawCenteredString("✨ Theme", x + width / 2f, themeY + 10, themeTextColor, 14f, true, false, false);
        }

        // ── Accounts Button ──
        int accountBg = accountActive ? activeBtnColor : HadesAPI.Render.lerpColor(baseBtnColor, hoverBtnColor, accountHoverAnimation);
        HadesAPI.Render.drawRoundedRect(x + 6, accountY, width - 12, 30, 4f, accountBg);

        int accountTextColor = accountActive ? Theme.TEXT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, accountHoverAnimation);
        float accountTextWidth = HadesAPI.Render.getStringWidth("Accounts", 14f, true, false);
        float accountTotalWidth = configIconSize + 4 + accountTextWidth;
        float accountStartX = x + (width - accountTotalWidth) / 2f;
        
        if (HadesAPI.Render.drawImage("hades", "pictures/icons/profile.png", accountStartX, accountY + (30 - configIconSize) / 2f, configIconSize, configIconSize)) {
            HadesAPI.Render.drawString("Accounts", accountStartX + configIconSize + 4, accountY + (30 - HadesAPI.Render.getFontHeight(14f, true, false)) / 2f, accountTextColor, 14f, true, false, false);
        } else {
            HadesAPI.Render.drawCenteredString("\uD83D\uDC64 Accounts", x + width / 2f, accountY + 10, accountTextColor, 14f, true, false, false);
        }

        // ── Proxies Button ──
        int proxyBg = proxyActive ? activeBtnColor : HadesAPI.Render.lerpColor(baseBtnColor, hoverBtnColor, proxyHoverAnimation);
        HadesAPI.Render.drawRoundedRect(x + 6, proxyY, width - 12, 30, 4f, proxyBg);

        int proxyTextColor = proxyActive ? Theme.TEXT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, proxyHoverAnimation);
        float proxyTextWidth = HadesAPI.Render.getStringWidth("Proxies", 14f, true, false);
        float proxyTotalWidth = configIconSize + 4 + proxyTextWidth;
        float proxyStartX = x + (width - proxyTotalWidth) / 2f;
        
        if (HadesAPI.Render.drawImage("hades", "pictures/icons/profile.png", proxyStartX, proxyY + (30 - configIconSize) / 2f, configIconSize, configIconSize)) {
            HadesAPI.Render.drawString("Proxies", proxyStartX + configIconSize + 4, proxyY + (30 - HadesAPI.Render.getFontHeight(14f, true, false)) / 2f, proxyTextColor, 14f, true, false, false);
        } else {
            HadesAPI.Render.drawCenteredString("\uD83C\uDF10 Proxies", x + width / 2f, proxyY + 10, proxyTextColor, 14f, true, false, false);
        }

        // ── Profile button at bottom ──
        HadesAPI.Render.drawRect(x + 10, sep2Y, width - 20, 1, Theme.SIDEBAR_SEPARATOR);

        int profileBg = profileActive ? activeBtnColor : HadesAPI.Render.lerpColor(baseBtnColor, hoverBtnColor, profileHoverAnimation);
        HadesAPI.Render.drawRoundedRect(x + 6, profileY, width - 12, profileH, 4f, profileBg);

        int profileColor = profileActive ? Theme.ACCENT_PRIMARY : HadesAPI.Render.lerpColor(Theme.TEXT_MUTED, Theme.ACCENT_PRIMARY, profileHoverAnimation);
        
        String username = com.hades.client.HadesClient.getInstance().getSessionUsername();
        if (username == null || username.isEmpty()) username = "Unknown";
        String initial = username.substring(0, 1).toUpperCase();

        float avatarSize = 20f;
        float textSize = 13.5f;
        float textWidth = HadesAPI.Render.getStringWidth(username, textSize, false, false);
        float totalWidth = avatarSize + 6 + textWidth;
        float startX = x + (width - totalWidth) / 2f;
        float avatarY = profileY + (profileH - avatarSize) / 2f;

        // Check if user has admin role to allow animated GIFs
        java.util.List<String> roles = com.hades.client.backend.BackendConnection.getInstance().getProfileRoles();
        boolean isAdmin = roles != null && roles.contains("admin");

        // Try to render the actual avatar from URL
        String avatarUrl = com.hades.client.backend.BackendConnection.getInstance().getProfileAvatarUrl();
        boolean avatarRendered = com.hades.client.util.UrlImageCache.drawUrlImageCircle(avatarUrl, startX, avatarY, avatarSize, isAdmin);
        
        if (!avatarRendered) {
            // Fallback: accent-colored circle avatar with initial
            HadesAPI.Render.drawRoundedRect(startX, avatarY, avatarSize, avatarSize, avatarSize / 2f, Theme.ACCENT_PRIMARY);
            HadesAPI.Render.drawCenteredString(initial, startX + avatarSize / 2f, avatarY + (avatarSize - HadesAPI.Render.getFontHeight(textSize, false, false)) / 2f, HadesAPI.Render.color(255, 255, 255, 255), textSize, true, false, false);
        }
        
        HadesAPI.Render.drawString(username, startX + avatarSize + 6, avatarY + (avatarSize - HadesAPI.Render.getFontHeight(textSize, false, false)) / 2f, profileColor, textSize, false, false, false);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;

        float logoSize = 48f;
        float logoY = y + Theme.PADDING_LARGE;
        float sepY = logoY + logoSize + Theme.PADDING_LARGE;
        float currentBtnY = sepY + Theme.PADDING_LARGE;

        com.hades.client.module.Module hudMod = com.hades.client.HadesClient.getInstance().getModuleManager().getModule("HUD");
        boolean hudModuleOn = (hudMod != null && hudMod.isEnabled());

        float configY = currentBtnY;
        currentBtnY += 34;
        
        float themeY = currentBtnY;
        currentBtnY += 34;

        float accountY = currentBtnY;
        currentBtnY += 34;

        float proxyY = currentBtnY;
        currentBtnY += 34;

        float hudY = currentBtnY;
        if (hudModuleOn) {
            currentBtnY += 34;
        }

        // Profile button: bottom area
        float sep2Y = y + height - 60;
        float profileY = sep2Y + Theme.PADDING;
        float profileH = 34f;

        if (mouseX >= x && mouseX <= x + width) {
            if (hudModuleOn && mouseY >= hudY && mouseY <= hudY + 30) {
                if (onHudClick != null) onHudClick.run();
            } else if (mouseY >= configY && mouseY <= configY + 30) {
                if (onConfigsClick != null) onConfigsClick.run();
            } else if (mouseY >= themeY && mouseY <= themeY + 30) {
                if (onThemeClick != null) onThemeClick.run();
            } else if (mouseY >= accountY && mouseY <= accountY + 30) {
                if (onAccountClick != null) onAccountClick.run();
            } else if (mouseY >= proxyY && mouseY <= proxyY + 30) {
                if (onProxyClick != null) onProxyClick.run();
            } else if (mouseY >= profileY && mouseY <= profileY + profileH) {
                if (onProfileClick != null) onProfileClick.run();
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {}

    @Override
    public void keyTyped(char typedChar, int keyCode) {}
}

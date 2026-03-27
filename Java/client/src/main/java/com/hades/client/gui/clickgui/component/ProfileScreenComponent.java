package com.hades.client.gui.clickgui.component;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.backend.BackendConnection;
import com.hades.client.gui.clickgui.theme.Theme;

import java.util.List;

public class ProfileScreenComponent extends Component {

    public ProfileScreenComponent() {
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        BackendConnection bc = BackendConnection.getInstance();
        String username = HadesClient.getInstance().getSessionUsername();
        String email = HadesClient.getInstance().getSessionEmail();
        if (username == null || username.isEmpty()) username = "Unknown";

        HadesAPI.Render.drawRoundedRect(x, y, width, height, 8f, Theme.WINDOW_BG);

        float padding = Theme.PADDING_LARGE;
        float currentY = y + padding;

        HadesAPI.Render.drawString("Profile", x + padding, currentY, Theme.TEXT_PRIMARY, 1.5f);
        currentY += 30;

        HadesAPI.Render.drawRect(x + padding, currentY, width - padding * 2, 1, Theme.SIDEBAR_SEPARATOR);
        currentY += 15;

        // ── Avatar + identity ──
        float avatarSize = 48f;
        String initial = username.substring(0, 1).toUpperCase();

        // Check if user has admin role to allow animated GIFs
        List<String> roles = bc.getProfileRoles();
        boolean isAdmin = roles != null && roles.contains("admin");

        // Try to render avatar from URL
        String avatarUrl = bc.getProfileAvatarUrl();
        boolean avatarRendered = com.hades.client.util.UrlImageCache.drawUrlImageCircle(avatarUrl, x + padding, currentY, avatarSize, isAdmin);
        
        if (!avatarRendered) {
            // Fallback: accent circle avatar
            HadesAPI.Render.drawRoundedRect(x + padding, currentY, avatarSize, avatarSize, avatarSize / 2f, Theme.ACCENT_PRIMARY);
            HadesAPI.Render.drawCenteredString(initial, x + padding + avatarSize / 2f, currentY + (avatarSize - HadesAPI.Render.getFontHeight(2.0f)) / 2f, HadesAPI.Render.color(255, 255, 255, 255), 2.0f);
        }

        float textX = x + padding + avatarSize + 15;

        // Username
        HadesAPI.Render.drawString(username, textX, currentY + 5, Theme.ACCENT_PRIMARY, 1.4f);

        // Email
        HadesAPI.Render.drawString(email != null && !email.isEmpty() ? email : "No email", textX, currentY + 28, Theme.TEXT_SECONDARY, 0.9f);

        // Connection status
        boolean connected = bc.isConnected();
        String connText = connected ? "Live Synced" : "Offline";
        int connColor = connected ? HadesAPI.Render.color(50, 205, 50, 255) : HadesAPI.Render.color(255, 69, 0, 255);
        HadesAPI.Render.drawString("Status: " + connText, textX, currentY + 45, connColor, 0.9f);

        currentY += avatarSize + 25;

        // ── Stats section (from backend) ──
        HadesAPI.Render.drawRect(x + padding, currentY, width - padding * 2, 1, Theme.SIDEBAR_SEPARATOR);
        currentY += 12;

        if (bc.isProfileFetched()) {
            // Hades Coins
            HadesAPI.Render.drawString("Hades Coins", x + padding, currentY, Theme.TEXT_SECONDARY, 0.9f);
            HadesAPI.Render.drawString(String.valueOf(bc.getProfileHadesCoins()), x + padding + 120, currentY, Theme.ACCENT_PRIMARY, 0.9f);
            currentY += 18;

            // Subscription
            String subText = bc.isProfileSubscriptionActive() ? "Active" : "Inactive";
            int subColor = bc.isProfileSubscriptionActive() ? HadesAPI.Render.color(50, 205, 50, 255) : Theme.TEXT_MUTED;
            HadesAPI.Render.drawString("Subscription", x + padding, currentY, Theme.TEXT_SECONDARY, 0.9f);
            HadesAPI.Render.drawString(subText, x + padding + 120, currentY, subColor, 0.9f);
            currentY += 18;

            // Roles
            if (roles != null && !roles.isEmpty()) {
                HadesAPI.Render.drawString("Roles", x + padding, currentY, Theme.TEXT_SECONDARY, 0.9f);
                HadesAPI.Render.drawString(String.join(", ", roles), x + padding + 120, currentY, Theme.TEXT_PRIMARY, 0.9f);
                currentY += 18;
            }

            // Badges
            List<String> badges = bc.getProfileBadgeNames();
            if (!badges.isEmpty()) {
                HadesAPI.Render.drawString("Badges", x + padding, currentY, Theme.TEXT_SECONDARY, 0.9f);
                HadesAPI.Render.drawString(String.join(", ", badges), x + padding + 120, currentY, Theme.TEXT_PRIMARY, 0.9f);
                currentY += 18;
            }

            // Description
            String desc = bc.getProfileDescription();
            if (desc != null && !desc.isEmpty()) {
                currentY += 8;
                HadesAPI.Render.drawString("Bio", x + padding, currentY, Theme.TEXT_SECONDARY, 0.9f);
                currentY += 16;
                HadesAPI.Render.drawString(desc, x + padding, currentY, Theme.TEXT_MUTED, 0.85f);
            }
        } else {
            HadesAPI.Render.drawString("Loading profile data...", x + padding, currentY, Theme.TEXT_MUTED, 0.9f);
        }
    }

    public void scroll(int amount) {
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}

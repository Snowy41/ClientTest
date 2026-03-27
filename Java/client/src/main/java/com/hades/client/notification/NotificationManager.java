package com.hades.client.notification;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.Render2DEvent;
import com.hades.client.gui.clickgui.theme.Theme;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages and renders toast notifications in the bottom-right corner of the
 * screen.
 *
 * Notifications slide in from the right, display a progress bar that fills up
 * over the
 * duration, then slide back out to the right. Multiple notifications stack
 * upwards.
 */
public class NotificationManager {

    private static NotificationManager instance;
    private final List<Notification> notifications = new ArrayList<>();

    // ── Dimensions ──
    private static final float NOTIF_WIDTH = 160f;
    private static final float NOTIF_HEIGHT = 32f;
    private static final float NOTIF_MARGIN = 6f;
    private static final float NOTIF_RADIUS = NOTIF_HEIGHT / 2f;
    private static final float SCREEN_MARGIN = 10f;

    // ── Colors ──
    private static final int GREEN = HadesAPI.Render.color(60, 210, 90);
    private static final int RED = HadesAPI.Render.color(220, 55, 55);
    private static final int ACCENT = Theme.ACCENT_PRIMARY;

    // ── Animation ──
    private static final float SLIDE_SPEED_IN = 0.12f;
    private static final float SLIDE_SPEED_OUT = 0.15f;

    public static NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    // ══════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════

    public void show(String title, String message, Notification.Type type) {
        show(title, message, type, 2500L);
    }

    public void show(String title, String message, Notification.Type type, long durationMs) {
        notifications.add(new Notification(title, message, type, durationMs));
    }

    // ══════════════════════════════════════════
    // Rendering (called from Render2DEvent)
    // ══════════════════════════════════════════

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (notifications.isEmpty())
            return;

        float screenW = event.getScaledWidth();
        float screenH = event.getScaledHeight();

        // Remove fully completed notifications
        Iterator<Notification> it = notifications.iterator();
        while (it.hasNext()) {
            if (it.next().isFullyDone()) {
                it.remove();
            }
        }

        // Render from bottom up
        float currentY = screenH - SCREEN_MARGIN;

        for (int i = notifications.size() - 1; i >= 0; i--) {
            Notification notif = notifications.get(i);

            // Update animation state
            updateAnimation(notif);

            // Calculate position (slide from right)
            float targetX = screenW - NOTIF_WIDTH - SCREEN_MARGIN;
            float slideOffset = (1f - notif.getSlideProgress()) * (NOTIF_WIDTH + SCREEN_MARGIN + 20);
            float notifX = targetX + slideOffset;
            float notifY = currentY - NOTIF_HEIGHT;

            // Don't render if completely off-screen
            if (notif.getSlideProgress() > 0.01f) {
                renderNotification(notif, notifX, notifY);
            }

            currentY = notifY - NOTIF_MARGIN;
        }
    }

    // ══════════════════════════════════════════
    // Animation Logic
    // ══════════════════════════════════════════

    private void updateAnimation(Notification notif) {
        if (!notif.isExiting()) {
            // Slide in
            float target = 1f;
            float current = notif.getSlideProgress();
            notif.setSlideProgress(current + (target - current) * SLIDE_SPEED_IN);
            if (notif.getSlideProgress() > 0.99f)
                notif.setSlideProgress(1f);

            // Check if timer has expired
            if (notif.getTimerProgress() >= 1f) {
                notif.setExiting(true);
            }
        } else {
            // Slide out
            float current = notif.getSlideProgress();
            notif.setSlideProgress(current + (0f - current) * SLIDE_SPEED_OUT);
            if (notif.getSlideProgress() < 0.01f)
                notif.setSlideProgress(0f);
        }
    }

    // ══════════════════════════════════════════
    // Rendering
    // ══════════════════════════════════════════

    private void renderNotification(Notification notif, float x, float y) {
        int barColor;
        switch (notif.getType()) {
            case ENABLED:
                barColor = GREEN;
                break;
            case DISABLED:
                barColor = RED;
                break;
            default:
                barColor = ACCENT;
                break;
        }

        // ── Drop Shadow ──
        HadesAPI.Render.drawRoundedShadow(x, y, NOTIF_WIDTH, NOTIF_HEIGHT, NOTIF_RADIUS, 6f);

        // ── Blurred Glass Background ──
        try {
            int tint = HadesAPI.Render.colorWithAlpha(0xFF0A0A0C, 50);
            float mcScale = 1f;
            int[] sr = HadesAPI.Game.getScaledResolution();
            if (sr[0] > 0) mcScale = (float) org.lwjgl.opengl.Display.getWidth() / sr[0];
            if (mcScale <= 0) mcScale = 2f;

            com.hades.client.util.BlurUtil.drawBlurredRect(x, y, NOTIF_WIDTH, NOTIF_HEIGHT, NOTIF_RADIUS, tint, 4, mcScale);
        } catch (Throwable t) {
            HadesAPI.Render.drawRoundedRect(x, y, NOTIF_WIDTH, NOTIF_HEIGHT, NOTIF_RADIUS, HadesAPI.Render.colorWithAlpha(Theme.WINDOW_BG, 200));
        }

        // ── Typography ──
        float textX = x + 14f;
        
        if (notif.getMessage() == null || notif.getMessage().isEmpty()) {
            float titleSize = 1.05f;
            float fontHeight = HadesAPI.Render.getFontHeight(titleSize);
            float titleY = y + (NOTIF_HEIGHT - fontHeight) / 2f - 1f; // Shift slightly up due to bottom bar
            HadesAPI.Render.drawString(notif.getTitle(), textX, titleY, Theme.TEXT_PRIMARY, titleSize);
        } else {
            float titleSize = 1.0f;
            float messageSize = 0.85f;
            float titleH = HadesAPI.Render.getFontHeight(titleSize);
            float messageH = HadesAPI.Render.getFontHeight(messageSize);
            float gap = 1.0f;
            float totalH = titleH + gap + messageH;
            
            float titleY = y + (NOTIF_HEIGHT - totalH) / 2f - 1f;
            HadesAPI.Render.drawString(notif.getTitle(), textX, titleY, Theme.TEXT_PRIMARY, titleSize);

            float messageY = titleY + titleH + gap;
            HadesAPI.Render.drawString(notif.getMessage(), textX, messageY, Theme.TEXT_MUTED, messageSize);
        }

        // ── Ultra-thin Progress Line ──
        float progress = notif.getTimerProgress();
        if (progress > 0) {
            float trackW = NOTIF_WIDTH - 30f;
            float trackH = 1.5f;
            float trackX = x + 15f;
            float trackY = y + NOTIF_HEIGHT - trackH - 3f;

            HadesAPI.Render.drawRoundedRect(trackX, trackY, trackW * progress, trackH, trackH / 2f, barColor);
        }
    }
}

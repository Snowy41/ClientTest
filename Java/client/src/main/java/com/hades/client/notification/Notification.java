package com.hades.client.notification;

/**
 * Represents a single toast notification with slide animation + progress bar.
 */
public class Notification {

    public enum Type {
        ENABLED, // Module enabled, config loaded/saved (green)
        DISABLED, // Module disabled (red)
        INFO // General info (client ready, etc.) (orange/accent)
    }

    private final String title;
    private final String message;
    private final Type type;
    private final long durationMs;
    private final long createdAt;

    // Animation state
    private float slideProgress = 0f; // 0 = off screen, 1 = fully visible
    private boolean exiting = false;

    public Notification(String title, String message, Type type, long durationMs) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.durationMs = durationMs;
        this.createdAt = System.currentTimeMillis();
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Type getType() {
        return type;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public float getSlideProgress() {
        return slideProgress;
    }

    public void setSlideProgress(float slideProgress) {
        this.slideProgress = slideProgress;
    }

    public boolean isExiting() {
        return exiting;
    }

    public void setExiting(boolean exiting) {
        this.exiting = exiting;
    }

    /**
     * Returns 0.0 to 1.0 representing how much of the display duration has elapsed.
     */
    public float getTimerProgress() {
        long elapsed = System.currentTimeMillis() - createdAt;
        return Math.min(1f, (float) elapsed / (float) durationMs);
    }

    /**
     * Returns true if the notification has lived past its duration and finished its
     * exit animation.
     */
    public boolean isFullyDone() {
        return exiting && slideProgress <= 0.01f;
    }
}

package com.hades.client.module;

import com.hades.client.HadesClient;
import com.hades.client.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private int keyBind;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category, int keyBind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = keyBind;
    }

    protected <T extends Setting<?>> T register(T setting) {
        settings.add(setting);
        return setting;
    }

    public void toggle() {
        enabled = !enabled;

        com.hades.client.notification.NotificationManager.getInstance().show(
                getName(),
                enabled ? "Enabled module" : "Disabled module",
                enabled ? com.hades.client.notification.Notification.Type.ENABLED
                        : com.hades.client.notification.Notification.Type.DISABLED);

        if (enabled) {
            onEnable();
            HadesClient.getInstance().getEventBus().register(this);
        } else {
            onDisable();
            HadesClient.getInstance().getEventBus().unregister(this);
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void onTick() {
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    /**
     * Returns a display suffix for the ArrayList (e.g. the active mode).
     * Override in subclasses for custom suffixes.
     * Returns null if no suffix should be shown.
     */
    public String getDisplaySuffix() {
        for (Setting<?> s : settings) {
            if (s instanceof com.hades.client.module.setting.ModeSetting && s.isVisible()) {
                return ((com.hades.client.module.setting.ModeSetting) s).getValue();
            }
        }
        return null;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled)
            toggle();
    }

    public enum Category {
        COMBAT("Combat", "combat.png"),
        MOVEMENT("Movement", "movement.png"),
        PLAYER("Player", "player.png"),
        RENDER("Render", "render.png"),
        MISC("Misc", "misc.png"),
        HUD("HUD", "hud.png");

        private final String displayName;
        private final String iconName;

        Category(String displayName, String iconName) {
            this.displayName = displayName;
            this.iconName = iconName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIconName() {
            return iconName;
        }
    }
}
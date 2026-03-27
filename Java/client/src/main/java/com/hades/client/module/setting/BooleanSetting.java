package com.hades.client.module.setting;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }
}
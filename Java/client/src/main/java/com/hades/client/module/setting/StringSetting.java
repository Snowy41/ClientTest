package com.hades.client.module.setting;

public class StringSetting extends Setting<String> {
    public StringSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    public StringSetting(String name, String description, String defaultValue) {
        super(name, description, defaultValue);
    }

    public String getValue() {
        return super.getValue();
    }
}

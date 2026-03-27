package com.hades.client.module.setting;

public class ModeSetting extends Setting<String> {
    private final String[] modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        this.modes = modes;
    }

    @Override
    public void setValue(String value) {
        // Validate that the value is one of the allowed modes
        for (String mode : modes) {
            if (mode.equals(value)) {
                super.setValue(value);
                return;
            }
        }
        // Invalid value (e.g. old config), fall back to first mode
        super.setValue(modes[0]);
    }

    public String[] getModes() { return modes; }

    public void cycle() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(getValue())) {
                setValue(modes[(i + 1) % modes.length]);
                return;
            }
        }
    }

    public int getIndex() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(getValue())) return i;
        }
        return 0;
    }
}
package com.hades.preview;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the configurable fields for a module preview.
 * Dynamically generates the launcher's config panel UI.
 */
public class ConfigSchema {
    private String moduleId;
    private List<ConfigField> fields = new ArrayList<>();

    public ConfigSchema(String moduleId) {
        this.moduleId = moduleId;
    }

    public ConfigSchema addColor(String name, String label, String defaultValue) {
        fields.add(new ConfigField(name, label, "color", defaultValue));
        return this;
    }

    public ConfigSchema addSlider(String name, String label, double defaultValue, double min, double max, double step) {
        ConfigField f = new ConfigField(name, label, "slider", defaultValue);
        f.min = min;
        f.max = max;
        f.step = step;
        fields.add(f);
        return this;
    }

    public ConfigSchema addToggle(String name, String label, boolean defaultValue) {
        fields.add(new ConfigField(name, label, "toggle", defaultValue));
        return this;
    }

    public ConfigSchema addDropdown(String name, String label, String defaultValue, String... options) {
        ConfigField f = new ConfigField(name, label, "dropdown", defaultValue);
        f.options = options;
        fields.add(f);
        return this;
    }

    public ConfigSchema addSection(String label) {
        fields.add(new ConfigField("_section", label, "section", null));
        return this;
    }

    public String getModuleId() { return moduleId; }
    public List<ConfigField> getFields() { return fields; }

    public static class ConfigField {
        public String name;
        public String label;
        public String type; // "color", "slider", "toggle", "dropdown", "section"
        public Object defaultValue;
        public double min, max, step;
        public String[] options;

        public ConfigField(String name, String label, String type, Object defaultValue) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.defaultValue = defaultValue;
        }
    }
}

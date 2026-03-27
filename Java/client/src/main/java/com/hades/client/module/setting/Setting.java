package com.hades.client.module.setting;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    private T value;
    private boolean hidden = false;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
    }

    public Setting(String name, T defaultValue) {
        this(name, "", defaultValue);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    private java.util.function.Supplier<Boolean> visibility = () -> true;

    public boolean isVisible() {
        return !isHidden() && visibility.get();
    }

    public Setting<T> setVisibility(java.util.function.Supplier<Boolean> visibility) {
        this.visibility = visibility;
        return this;
    }
}
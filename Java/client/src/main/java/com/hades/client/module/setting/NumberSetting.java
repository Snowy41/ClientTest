package com.hades.client.module.setting;

public class NumberSetting extends Setting<Double> {
    private final double min, max, increment;

    public NumberSetting(String name, double defaultValue, double min, double max, double increment) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double increment) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    @Override
    public void setValue(Double value) {
        double rounded = Math.round(value / increment) * increment;
        super.setValue(Math.max(min, Math.min(max, rounded)));
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }
    public double getPercentage() { return (getValue() - min) / (max - min); }
}
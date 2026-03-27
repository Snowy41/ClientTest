package com.hades.client.module.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class MultiSelectSetting extends Setting<List<Integer>> {

    public static class Option {
        public final String name;
        public final int id;
        public final Object icon;

        public Option(String name, int id, Object icon) {
            this.name = name;
            this.id = id;
            this.icon = icon;
        }
    }

    private final Supplier<List<Option>> optionsSupplier;

    public MultiSelectSetting(String name, String description, Supplier<List<Option>> optionsSupplier) {
        super(name, description, new ArrayList<>());
        this.optionsSupplier = optionsSupplier;
    }

    public MultiSelectSetting(String name, String description, Option... predefOptions) {
        this(name, description, () -> Arrays.asList(predefOptions));
    }

    public Option[] getOptions() {
        if (optionsSupplier != null) {
            List<Option> opts = optionsSupplier.get();
            if (opts != null) return opts.toArray(new Option[0]);
        }
        return new Option[0];
    }

    public boolean isSelected(int id) {
        return getValue() != null && getValue().contains(id);
    }

    public void toggle(int id) {
        List<Integer> current = new ArrayList<>(getValue() == null ? new ArrayList<>() : getValue());
        if (current.contains(id)) {
            current.remove(Integer.valueOf(id));
        } else {
            current.add(id);
        }
        setValue(current);
    }
}

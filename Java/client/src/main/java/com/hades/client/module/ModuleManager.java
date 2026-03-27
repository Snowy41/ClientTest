package com.hades.client.module;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void init() {
        try {
            // Dynamically load all classes in the "com.hades.client.module.impl" package
            // and its subpackages
            String packageName = "com.hades.client.module.impl";
            java.util.Set<Class<?>> classes = com.hades.client.util.ReflectionUtil.getClasses(packageName);

            for (Class<?> clazz : classes) {
                if (Module.class.isAssignableFrom(clazz)
                        && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    modules.add((Module) clazz.getDeclaredConstructor().newInstance());
                }
            }
        } catch (Exception e) {
            System.err.println("[Hades] Error initializing modules dynamically: " + e.getMessage());
            e.printStackTrace();
        }

        // Sort modules alphabetically by name
        modules.sort(java.util.Comparator.comparing(Module::getName));

        System.out.println("[Hades] Loaded " + modules.size() + " modules.");
    }

    public void onKeyPress(int keyCode) {
        for (Module module : modules) {
            if (module.getKeyBind() == keyCode) {
                module.toggle();
            }
        }
    }

    public Module getModule(String name) {
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        return modules.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst().orElse(null);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Module.Category category) {
        List<Module> list = modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(java.util.stream.Collectors.toList());
        return list;
    }
}
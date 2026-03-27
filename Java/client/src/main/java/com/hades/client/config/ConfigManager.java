package com.hades.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hades.client.HadesClient;
import com.hades.client.module.Module;
import com.hades.client.module.setting.Setting;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final Gson gson;
    private final File configDir;

    public ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Hades configs directory - uses user home as a reliable fallback
        String mcDir = System.getProperty("minecraft.appDir",
                System.getenv("APPDATA") != null
                        ? System.getenv("APPDATA") + File.separator + ".minecraft"
                        : System.getProperty("user.home") + File.separator + ".minecraft");

        this.configDir = new File(mcDir, "Hades" + File.separator + "configs");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
    }

    public File getConfigDirectory() {
        return configDir;
    }

    /** Returns a list of all saved config names (without .json extension). */
    public List<String> getConfigNames() {
        List<String> names = new ArrayList<>();
        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                names.add(f.getName().replace(".json", ""));
            }
        }
        return names;
    }

    public void load(String configName) {
        File configFile = resolveFile(configName);
        if (!configFile.exists()) {
            HadesLogger.get().info("Config file does not exist, skipping load: " + configFile.getName());
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            applyConfig(root, configName);
        } catch (Exception e) {
            HadesLogger.get().error("Failed to load config: " + configName, e);
            com.hades.client.notification.NotificationManager.getInstance().show(
                    "Error",
                    "Failed to load " + configName,
                    com.hades.client.notification.Notification.Type.DISABLED);
        }
    }

    /**
     * Load a config from a raw JSON string (e.g. downloaded from Supabase Storage).
     */
    public void loadFromJson(String json, String configName) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            applyConfig(root, configName);
        } catch (Exception e) {
            HadesLogger.get().error("Failed to load cloud config: " + configName, e);
            com.hades.client.notification.NotificationManager.getInstance().show(
                    "Error",
                    "Failed to load " + configName,
                    com.hades.client.notification.Notification.Type.DISABLED);
        }
    }

    private String activeConfigName = "default.json";

    public String getActiveConfigName() {
        return activeConfigName;
    }

    private void applyConfig(JsonObject root, String configName) {
        this.activeConfigName = configName;
        
        if (root.has("theme")) {
            JsonObject themeNode = root.getAsJsonObject("theme");
            if (themeNode.has("primary") && themeNode.has("secondary") && 
                themeNode.has("gradientStart") && themeNode.has("gradientEnd")) {
                com.hades.client.gui.clickgui.theme.Theme.applyTheme(
                    themeNode.get("primary").getAsInt(),
                    themeNode.get("secondary").getAsInt(),
                    themeNode.get("gradientStart").getAsInt(),
                    themeNode.get("gradientEnd").getAsInt()
                );
            }
        }

        if (root.has("modules")) {
            JsonObject modulesNode = root.getAsJsonObject("modules");

            for (Module module : HadesClient.getInstance().getModuleManager().getModules()) {
                if (modulesNode.has(module.getName())) {
                    JsonObject moduleNode = modulesNode.getAsJsonObject(module.getName());

                    if (moduleNode.has("enabled")) {
                        module.setEnabled(moduleNode.get("enabled").getAsBoolean());
                    }
                    if (moduleNode.has("keyBind")) {
                        module.setKeyBind(moduleNode.get("keyBind").getAsInt());
                    }

                    if (moduleNode.has("settings")) {
                        JsonObject settingsNode = moduleNode.getAsJsonObject("settings");

                        for (Setting<?> setting : module.getSettings()) {
                            if (settingsNode.has(setting.getName())) {
                                JsonElement element = settingsNode.get(setting.getName());
                                loadSettingValue(setting, element);
                            }
                        }
                    }
                }
            }
        }
        HadesLogger.get().info("Loaded config: " + configName);
        com.hades.client.notification.NotificationManager.getInstance().show(
                "Config Manager",
                "Loaded " + configName,
                com.hades.client.notification.Notification.Type.ENABLED);
    }

    public void save(String configName) {
        File configFile = resolveFile(configName);

        try (FileWriter writer = new FileWriter(configFile)) {
            JsonObject root = new JsonObject();
            JsonObject modulesNode = new JsonObject();

            for (Module module : HadesClient.getInstance().getModuleManager().getModules()) {
                JsonObject moduleNode = new JsonObject();
                moduleNode.addProperty("enabled", module.isEnabled());
                moduleNode.addProperty("keyBind", module.getKeyBind());

                JsonObject settingsNode = new JsonObject();
                for (Setting<?> setting : module.getSettings()) {
                    saveSettingValue(setting, settingsNode);
                }

                if (settingsNode.size() > 0) {
                    moduleNode.add("settings", settingsNode);
                }

                modulesNode.add(module.getName(), moduleNode);
            }

            root.add("modules", modulesNode);
            
            JsonObject themeNode = new JsonObject();
            themeNode.addProperty("primary", com.hades.client.gui.clickgui.theme.Theme.ACCENT_PRIMARY);
            themeNode.addProperty("secondary", com.hades.client.gui.clickgui.theme.Theme.ACCENT_SECONDARY);
            themeNode.addProperty("gradientStart", com.hades.client.gui.clickgui.theme.Theme.ACCENT_GRADIENT_START);
            themeNode.addProperty("gradientEnd", com.hades.client.gui.clickgui.theme.Theme.ACCENT_GRADIENT_END);
            root.add("theme", themeNode);
            
            gson.toJson(root, writer);

            HadesLogger.get().info("Saved config: " + configFile.getName());

            // Only notify if it's not the default crash-save hook running during client
            // shutdown.
            // A simple check is to avoid showing it for the default internal saves, but
            // let's just show it.
            com.hades.client.notification.NotificationManager.getInstance().show(
                    "Config Manager",
                    "Saved " + configName,
                    com.hades.client.notification.Notification.Type.ENABLED);
        } catch (IOException e) {
            HadesLogger.get().error("Failed to save config: " + configName, e);
            com.hades.client.notification.NotificationManager.getInstance().show(
                    "Error",
                    "Failed to save " + configName,
                    com.hades.client.notification.Notification.Type.DISABLED);
        }
    }

    public void delete(String configName) {
        File configFile = resolveFile(configName);
        if (configFile.exists()) {
            if (configFile.delete()) {
                HadesLogger.get().info("Deleted config: " + configFile.getName());
                com.hades.client.notification.NotificationManager.getInstance().show(
                        "Config Manager",
                        "Deleted " + configName,
                        com.hades.client.notification.Notification.Type.DISABLED);
            } else {
                HadesLogger.get().error("Failed to delete config: " + configFile.getName());
            }
        }
    }

    private File resolveFile(String configName) {
        return new File(configDir, configName.endsWith(".json") ? configName : configName + ".json");
    }

    @SuppressWarnings("unchecked")
    private void loadSettingValue(Setting<?> setting, JsonElement element) {
        try {
            if (setting instanceof BooleanSetting) {
                ((BooleanSetting) setting).setValue(element.getAsBoolean());
            } else if (setting instanceof NumberSetting) {
                ((NumberSetting) setting).setValue(element.getAsDouble());
            } else if (setting instanceof ModeSetting) {
                ((ModeSetting) setting).setValue(element.getAsString());
            }
        } catch (Exception e) {
            HadesLogger.get().error("Failed to parse setting " + setting.getName(), e);
        }
    }

    private void saveSettingValue(Setting<?> setting, JsonObject node) {
        if (setting instanceof BooleanSetting) {
            node.addProperty(setting.getName(), ((BooleanSetting) setting).getValue());
        } else if (setting instanceof NumberSetting) {
            node.addProperty(setting.getName(), ((NumberSetting) setting).getValue());
        } else if (setting instanceof ModeSetting) {
            node.addProperty(setting.getName(), ((ModeSetting) setting).getValue());
        }
    }
}

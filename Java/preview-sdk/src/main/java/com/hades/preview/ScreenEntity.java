package com.hades.preview;

import java.util.Map;

/**
 * Represents a projected entity in screen space — sent from the Electron renderer
 * after projecting 3D world positions via Three.js camera.
 */
public class ScreenEntity {
    private String id;
    private String type;
    private double x;
    private double y;
    private double width;
    private double height;
    private Map<String, Object> metadata;

    public ScreenEntity() {}

    public ScreenEntity(String id, String type, double x, double y, double width, double height, Map<String, Object> metadata) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public Map<String, Object> getMetadata() { return metadata; }

    public String getMetaString(String key, String defaultVal) {
        if (metadata == null || !metadata.containsKey(key)) return defaultVal;
        return String.valueOf(metadata.get(key));
    }

    public double getMetaDouble(String key, double defaultVal) {
        if (metadata == null || !metadata.containsKey(key)) return defaultVal;
        Object v = metadata.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return defaultVal;
    }
}

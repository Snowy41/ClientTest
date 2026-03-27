package com.hades.client.platform;

/**
 * Represents a detected Minecraft client platform.
 * Version-agnostic — supports any MC version + client combination.
 */
public enum ClientPlatform {
    LABYMOD("LabyMod", "net.labymod.api.LabyAPI"),
    FORGE("Forge", "net.minecraftforge.fml.common.Loader"),
    VANILLA("Vanilla", null);

    private final String displayName;
    private final String markerClass;

    ClientPlatform(String displayName, String markerClass) {
        this.displayName = displayName;
        this.markerClass = markerClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * The class name used to detect this platform.
     * Null for VANILLA (it's the fallback).
     */
    public String getMarkerClass() {
        return markerClass;
    }
}

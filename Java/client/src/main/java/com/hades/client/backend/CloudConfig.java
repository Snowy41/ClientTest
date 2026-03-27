package com.hades.client.backend;

/**
 * Represents a cloud config from the Hades Web Hub.
 * Mirrors the Supabase `configs` table columns returned by launcher-profile.
 */
public class CloudConfig {
    public final String id;
    public final String name;
    public final String description;
    public final String category;
    public final String filePath;
    public final boolean isOfficial;
    public final int downloads;
    public final float rating;

    public CloudConfig(String id, String name, String description, String category,
                       String filePath, boolean isOfficial, int downloads, float rating) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.filePath = filePath;
        this.isOfficial = isOfficial;
        this.downloads = downloads;
        this.rating = rating;
    }

    @Override
    public String toString() {
        return name + " [" + category + "] (" + downloads + " downloads)";
    }
}

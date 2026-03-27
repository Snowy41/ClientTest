package com.hades.client.account;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for the user profile returned by the /launcher-profile endpoint.
 * Maps directly to the JSON response from hades-core-hub.
 */
public class UserProfile {

    private String username;
    private String role; // e.g. "user", "staff", "developer"
    private boolean subscribed;
    private List<String> badges;
    private List<String> ownedConfigs;

    public UserProfile() {
        this.badges = new ArrayList<>();
        this.ownedConfigs = new ArrayList<>();
    }

    // ═══════════════════════════════
    // Getters & Setters
    // ═══════════════════════════════

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public List<String> getBadges() {
        return badges;
    }

    public void setBadges(List<String> badges) {
        this.badges = badges;
    }

    public List<String> getOwnedConfigs() {
        return ownedConfigs;
    }

    public void setOwnedConfigs(List<String> ownedConfigs) {
        this.ownedConfigs = ownedConfigs;
    }
}

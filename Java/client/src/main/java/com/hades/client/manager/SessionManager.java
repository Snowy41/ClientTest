package com.hades.client.manager;

import com.hades.client.api.HadesAPI;
import com.hades.client.util.HadesLogger;

public class SessionManager {

    private static SessionManager instance;

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
            instance.init();
        }
        return instance;
    }

    private void init() {
        // Initialization safely offloaded to the API
    }

    public boolean setCrackedSession(String username) {
        try {
            HadesAPI.mc.setCrackedSession(username);
            HadesLogger.get().info("Successfully pushed session update down the API pipeline for: " + username);
            return true;
        } catch (Exception e) {
            HadesLogger.get().error("Failed to push session update: " + e.getMessage());
            return false;
        }
    }
}

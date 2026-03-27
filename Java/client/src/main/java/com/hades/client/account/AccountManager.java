package com.hades.client.account;

import com.hades.client.util.HadesLogger;

/**
 * AccountManager handles session token storage and API communication.
 * The injector will pass the session token at launch time.
 * Until then, the client runs in "offline" mode with no restrictions.
 */
public class AccountManager {

    private static AccountManager instance;

    private String sessionToken;
    private UserProfile profile;
    private boolean authenticated = false;

    public static AccountManager getInstance() {
        if (instance == null) {
            instance = new AccountManager();
        }
        return instance;
    }

    /**
     * Called by the injector to pass the session token into the client.
     * This is the entry point for authentication.
     */
    public void setSessionToken(String token) {
        this.sessionToken = token;
        HadesLogger.get().info("Session token received.");
    }

    /**
     * Fetches the user's launcher profile from the backend API.
     * Runs asynchronously to avoid blocking the render thread.
     *
     * @param apiBaseUrl The base URL of the hades-core-hub API (e.g.,
     *                   "https://example.com/api")
     */
    public void fetchLauncherProfile(String apiBaseUrl) {
        if (sessionToken == null || sessionToken.isEmpty()) {
            HadesLogger.get().info("No session token set - running in offline mode.");
            return;
        }

        new Thread(() -> {
            try {
                // TODO: Replace with actual HTTP request when injector integration is ready
                // Expected endpoint: GET {apiBaseUrl}/launcher-profile
                // Header: Authorization: Bearer {sessionToken}
                //
                // For now, we mock a successful response:
                HadesLogger.get().info("Fetching launcher profile from: " + apiBaseUrl + "/launcher-profile");

                // Mock profile for development
                profile = new UserProfile();
                profile.setUsername("DevUser");
                profile.setRole("developer");
                profile.setSubscribed(true);
                authenticated = true;

                HadesLogger.get().info("Launcher profile loaded for: " + profile.getUsername());
            } catch (Exception e) {
                HadesLogger.get().error("Failed to fetch launcher profile", e);
            }
        }, "Hades-API").start();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public String getSessionToken() {
        return sessionToken;
    }
}

package com.hades.client.module.impl.render;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches UUID to Role mappings received from LabyConnect or our Backend.
 */
public class RoleManager {
    // Thread-safe map since payloads arrive asynchronously
    private static final Map<UUID, String> roleCache = new ConcurrentHashMap<>();

    public static void setRole(UUID uuid, String role) {
        if (uuid != null && role != null) {
            roleCache.put(uuid, role);
        }
    }

    public static String getRole(UUID uuid) {
        return roleCache.get(uuid);
    }
    
    public static boolean hasRole(UUID uuid) {
        return roleCache.containsKey(uuid);
    }

    public static void clear() {
        roleCache.clear();
    }
}

package com.hades.client.combat;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.api.HadesAPI;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Advanced Centralized Target Management System.
 * Uses heuristic scoring and Dot-Product FOV culling instead of simple distance
 * checks.
 *
 * All modules MUST use this TargetManager to avoid desynchronization between
 * AimAssist, AutoBlock, WTap, and LagRange.
 */
public class TargetManager {

    private static final TargetManager INSTANCE = new TargetManager();

    private com.hades.client.api.interfaces.IEntity target = null;
    private double maxRange = 6.0;
    private float maxFov = 360f;
    private Priority priority = Priority.CLOSEST;

    // Anti-Flicker persistence
    private int ticksWithoutTarget = 0;
    private static final int SWITCH_COOLDOWN = 3;

    // ── NPC/Bot filtering (Intave trap bypass) ──
    // Track when entities first appear to reject freshly-spawned NPC traps
    private final Map<Integer, Long> entitySpawnTimes = new ConcurrentHashMap<>();
    private static final long MIN_ENTITY_AGE_MS = 500; // Ignore entities spawned < 500ms ago

    private TargetManager() {
    }

    public static TargetManager getInstance() {
        return INSTANCE;
    }

    public enum Priority {
        CLOSEST, LOWEST_HEALTH, CROSSHAIR
    }

    // ── Core Update ──

    public void onWorldChange() {
        this.target = null;
        this.ticksWithoutTarget = 0;
        this.entitySpawnTimes.clear();
        com.hades.client.util.HadesLogger.get().info("[TargetManager] World changed — Cleared tracking caches.");
    }

    @EventHandler
    public void onTick(TickEvent event) {
        trackEntitySpawns();
        update();
    }

    /**
     * Tracks newly appeared entities and records their first-seen timestamp.
     * Used to reject freshly-spawned NPC traps that Intave uses.
     */
    private void trackEntitySpawns() {
        if (HadesAPI.world == null)
            return;
        long now = System.currentTimeMillis();
        List<com.hades.client.api.interfaces.IEntity> entities = HadesAPI.world.getLoadedEntities();
        for (com.hades.client.api.interfaces.IEntity e : entities) {
            entitySpawnTimes.putIfAbsent(e.getEntityId(), now);
        }
        // Cleanup: remove entries for entities that no longer exist
        if (entitySpawnTimes.size() > entities.size() * 2) {
            java.util.Set<Integer> currentIds = entities.stream()
                    .map(com.hades.client.api.interfaces.IEntity::getEntityId)
                    .collect(java.util.stream.Collectors.toSet());
            entitySpawnTimes.keySet().removeIf(id -> !currentIds.contains(id));
        }
    }

    public void update() {
        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player == null) {
            target = null;
            return;
        }

        // Pre-calculate local player health once to avoid O(N^2) lookups in LabyMod
        // wrapper
        final float localPlayerHealth = HadesAPI.Player.getHealth();

        // 1. Instant Clear if local player is dead
        if (localPlayerHealth <= 0 || Float.isNaN(localPlayerHealth)) {
            target = null;
            return; // completely halt targeting
        }

        // If we currently have a target, check if it's still valid
        if (target != null) {
            float th = target.getHealth();
            // 2. Instant Clear if target is explicitly dead or invalid object
            if (th <= 0 || Float.isNaN(th) || !target.isLiving() || target.getRaw() == player.getRaw()) {
                target = null;
                ticksWithoutTarget = 0;
            } 
            // 3. Hysteresis (anti-flicker) for FOV/Range dropouts
            else if (!isValid(target, localPlayerHealth)
                    || CombatUtil.getDistanceToBox(HadesAPI.player, target) > maxRange) {
                ticksWithoutTarget++;
                if (ticksWithoutTarget >= SWITCH_COOLDOWN) {
                    target = null;
                }
            } else {
                ticksWithoutTarget = 0;
            }
        }

        // Only search for a new target if we lost the old one or priority demands
        // continuous updates
        List<com.hades.client.api.interfaces.IEntity> validTargets = HadesAPI.world.getLoadedEntities().stream()
                .filter(e -> isValid(e, localPlayerHealth))
                .collect(Collectors.toList());

        if (validTargets.isEmpty()) {
            target = null;
            return;
        }

        com.hades.client.api.interfaces.IEntity bestTarget = validTargets.stream()
                .min(Comparator.comparingDouble(this::getHeuristicScore))
                .orElse(null);

        // Anti-flicker: Only switch targets if the new target is significantly better
        if (target == null || bestTarget != target) {
            target = bestTarget;
            ticksWithoutTarget = 0;
        }
    }

    /**
     * Calculates the heuristic score based on the current Priority Setting.
     * Lower score is better.
     */
    private double getHeuristicScore(com.hades.client.api.interfaces.IEntity entity) {
        double dist = CombatUtil.getDistanceToBox(HadesAPI.player, entity);
        double angleDiff = Math.abs(getAngleDifference(entity));

        switch (priority) {
            case LOWEST_HEALTH:
                return (HadesAPI.Player.getHealth() * 5.0) + dist + (angleDiff * 0.1);
            case CROSSHAIR:
                return angleDiff + (dist * 0.5);
            case CLOSEST:
            default:
                // Weight distance heavily, but slightly penalize extreme angle differences
                return dist + (angleDiff * 0.05);
        }
    }

    // Evaluates all anti-bot, range, and FOV checks.
    private boolean isValid(com.hades.client.api.interfaces.IEntity entity, float localPlayerHealth) {
        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player == null)
            return false;

        // Compare underlying raw objects, NOT wrapper references
        if (entity.getRaw() == player.getRaw() || !entity.isLiving())
            return false;

        // Do not attack if we are dead
        if (localPlayerHealth <= 0 || Float.isNaN(localPlayerHealth))
            return false;

        // Do not attack dead entities
        float targetHealth = entity.getHealth();
        if (targetHealth <= 0 || Float.isNaN(targetHealth))
            return false;

        // Anti-Bot: Ignore invisibles
        if (entity.isInvisible())
            return false;

        // ── Anti-Bot: Only target actual players (Intave NPC trap bypass) ──
        // Intave spawns armor stands, villagers, and fake entities as KillAura traps.
        // A real PvP target is always an EntityPlayer (isPlayer() = true).
        if (!entity.isPlayer())
            return false;

        // ── Anti-Bot: Reject freshly spawned entities (Intave NPC trap bypass) ──
        // Intave spawns invisible/visible NPCs that appear for < 200ms.
        // Real players don't teleport in and immediately get targeted.
        Long spawnTime = entitySpawnTimes.get(entity.getEntityId());
        if (spawnTime != null && (System.currentTimeMillis() - spawnTime) < MIN_ENTITY_AGE_MS) {
            return false;
        }

        // Friends System integration
        if (com.hades.client.module.impl.misc.Friends.isFriend(entity)) {
            return false;
        }

        // Range check
        if (CombatUtil.getDistanceToBox(player, entity) > maxRange)
            return false;

        // FOV check
        if (maxFov < 360f && !CombatUtil.isWithinFOV(entity, maxFov))
            return false;

        return true;
    }

    private float getAngleDifference(com.hades.client.api.interfaces.IEntity entity) {
        double pX = HadesAPI.player.getX();
        double pZ = HadesAPI.player.getZ();
        double eX = entity.getX();
        double eZ = entity.getZ();
        float yawToTarget = (float) Math.toDegrees(Math.atan2(eZ - pZ, eX - pX)) - 90f;
        return CombatUtil.getAngleDifference(HadesAPI.Player.getYaw(), yawToTarget);
    }

    // ── Getters / Setter Overrides ──

    public com.hades.client.api.interfaces.IEntity getTarget() {
        return target;
    }

    public void overrideTarget(com.hades.client.api.interfaces.IEntity t) {
        this.target = t;
    }

    public double getDistance() {
        return target != null ? Math.max(0, CombatUtil.getDistanceToBox(HadesAPI.player, target)) : -1;
    }

    // ── Configuration ──

    public void setConfig(double maxRange, float maxFov, String priorityStr) {
        this.maxRange = maxRange;
        this.maxFov = maxFov;
        try {
            // Replace spaces with underscores for enum parsing ("Lowest Health" ->
            // LOWEST_HEALTH)
            this.priority = Priority.valueOf(priorityStr.toUpperCase().replace(" ", "_"));
        } catch (Exception e) {
            this.priority = Priority.CLOSEST;
        }
    }
}

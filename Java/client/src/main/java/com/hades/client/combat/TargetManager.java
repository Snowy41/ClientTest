package com.hades.client.combat;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.api.HadesAPI;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


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
        if (HadesAPI.world == null) {
            target = null;
            return;
        }
        List<com.hades.client.api.interfaces.IEntity> loadedEntities = HadesAPI.world.getLoadedEntities();
        update(loadedEntities);
    }

    public void update(List<com.hades.client.api.interfaces.IEntity> loadedEntities) {
        com.hades.client.api.interfaces.IEntity player = HadesAPI.player;
        if (player == null) {
            target = null;
            return;
        }

        final float localPlayerHealth = HadesAPI.Player.getHealth();
        if (localPlayerHealth <= 0 || Float.isNaN(localPlayerHealth)) {
            target = null;
            return; 
        }

        boolean currentTargetExists = false;
        com.hades.client.api.interfaces.IEntity bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        long now = System.currentTimeMillis();

        int targetId = target != null ? target.getEntityId() : -1;
        double pX = player.getX();
        double pY = player.getY();
        double pZ = player.getZ();
        double extendedRangeSq = (maxRange + 4.0) * (maxRange + 4.0);

        // ── Single Pass Optimization ──
        for (com.hades.client.api.interfaces.IEntity e : loadedEntities) {
            int id = e.getEntityId();
            
            // 1. Spawn Tracking
            entitySpawnTimes.putIfAbsent(id, now);

            // 2. Target Validation
            if (id == targetId) {
                currentTargetExists = true;
                target = e; 
            }

            // 3. Fast Distance Pre-filter
            double dx = e.getX() - pX;
            double dy = e.getY() - pY;
            double dz = e.getZ() - pZ;
            if ((dx * dx + dy * dy + dz * dz) > extendedRangeSq) {
                continue;
            }

            // 4. Deep Inspection & Scoring
            if (isValid(e, localPlayerHealth)) {
                double score = getHeuristicScore(e);
                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = e;
                }
            }
        }

        // Cleanup spawn times map
        if (entitySpawnTimes.size() > loadedEntities.size() * 2) {
            java.util.Set<Integer> currentIds = new java.util.HashSet<>(loadedEntities.size());
            for (com.hades.client.api.interfaces.IEntity e : loadedEntities) {
                currentIds.add(e.getEntityId());
            }
            entitySpawnTimes.keySet().removeIf(id -> !currentIds.contains(id));
        }

        if (target != null) {
            if (!currentTargetExists) {
                target = null;
                ticksWithoutTarget = 0;
            } else {
                float th = target.getHealth();
                if (th <= 0 || Float.isNaN(th) || !target.isLiving() || target.getRaw() == player.getRaw()) {
                    target = null;
                    ticksWithoutTarget = 0;
                } 
                else if (!isValid(target, localPlayerHealth) || CombatUtil.getDistanceToBox(player, target) > maxRange) {
                    ticksWithoutTarget++;
                    if (ticksWithoutTarget >= SWITCH_COOLDOWN) {
                        target = null;
                    }
                } else {
                    ticksWithoutTarget = 0;
                }
            } 
        }

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

package com.hades.client.combat;

import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;

/**
 * CombatState acts as the global synchronization bus for all combat modules.
 * This prevents impossible packet sequences (e.g. Backtrack and LagRange both
 * trying to hold packets at the same time, or WTap releasing sprint while AutoBlock blocks).
 */
public class CombatState {

    private static final CombatState INSTANCE = new CombatState();

    // Mutex locks for packet manipulation modules
    private boolean isBacktracking = false;
    private boolean isLagging = false;
    
    // Attack state tracking
    private boolean isAttacking = false;
    private int ticksSinceLastHit = 0;
    private Object lastHitEntity = null;

    // Block state tracking
    private boolean isBlocking = false;

    // Transition cooldown — prevents rapid oscillation between Backtrack/LagRange
    // which creates a detectable "ping-pong" pattern for anticheats like Polar
    private int currentTick = 0;
    private int lastReleaseTick = -100; // Start with cooldown already satisfied
    private static final int TRANSITION_COOLDOWN = 8; // ~400ms between module switches

    private CombatState() {}

    public static CombatState getInstance() {
        return INSTANCE;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        currentTick++;
        ticksSinceLastHit++;
        isAttacking = false;
    }

    // ── Getters / Setters ──

    public boolean isBacktracking() { return isBacktracking; }
    public void setBacktracking(boolean backtracking) {
        if (this.isBacktracking && !backtracking) markPacketRelease();
        isBacktracking = backtracking;
    }

    public boolean isLagging() { return isLagging; }
    public void setLagging(boolean lagging) {
        if (this.isLagging && !lagging) markPacketRelease();
        isLagging = lagging;
    }

    public boolean isAttacking() { return isAttacking; }
    public void setAttacking(boolean attacking, Object entity) { 
        this.isAttacking = attacking; 
        if (attacking) {
            this.ticksSinceLastHit = 0;
            this.lastHitEntity = entity;
        }
    }

    public int getTicksSinceLastHit() { return ticksSinceLastHit; }
    public Object getLastHitEntity() { return lastHitEntity; }

    public boolean isBlocking() { return isBlocking; }
    public void setBlocking(boolean blocking) { isBlocking = blocking; }
    
    /** 
     * Determines if a module is allowed to delay/hold packets.
     * Prevents Backtrack and LagRange from conflicting.
     */
    public boolean canDelayPackets(ModuleType type) {
        boolean cooldownReady = (currentTick - lastReleaseTick) >= TRANSITION_COOLDOWN;
        if (type == ModuleType.BACKTRACK) return !isLagging && cooldownReady;
        if (type == ModuleType.LAGRANGE) return !isBacktracking && cooldownReady;
        return true;
    }

    /** Mark that a packet manipulation module just released its held packets. */
    public void markPacketRelease() {
        lastReleaseTick = currentTick;
    }

    public enum ModuleType {
        BACKTRACK, LAGRANGE
    }
}

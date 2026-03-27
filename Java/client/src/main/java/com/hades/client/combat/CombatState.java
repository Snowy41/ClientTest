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

    private CombatState() {}

    public static CombatState getInstance() {
        return INSTANCE;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        ticksSinceLastHit++;
        isAttacking = false;
    }

    // ── Getters / Setters ──

    public boolean isBacktracking() { return isBacktracking; }
    public void setBacktracking(boolean backtracking) { isBacktracking = backtracking; }

    public boolean isLagging() { return isLagging; }
    public void setLagging(boolean lagging) { isLagging = lagging; }

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
        if (type == ModuleType.BACKTRACK) return !isLagging;
        if (type == ModuleType.LAGRANGE) return !isBacktracking;
        return true;
    }

    public enum ModuleType {
        BACKTRACK, LAGRANGE
    }
}

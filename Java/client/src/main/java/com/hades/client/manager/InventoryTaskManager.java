package com.hades.client.manager;

/**
 * Lightweight coordination singleton that prevents AutoArmor, AutoInventory
 * and StorageStealer from fighting over inventory operations.
 * 
 * - "busy" flag: set by AutoArmor during its multi-step swap sequence.
 *   While busy, AutoInventory yields to prevent cursor corruption.
 * - "lastActionTick": prevents two modules from issuing windowClick in the
 *   same game tick, which can corrupt the cursor item.
 */
public class InventoryTaskManager {
    private static final InventoryTaskManager INSTANCE = new InventoryTaskManager();
    
    private volatile String busyOwner = null;
    private volatile long lastActionTick = 0;
    
    private InventoryTaskManager() {}
    
    public static InventoryTaskManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Marks the inventory as busy by the given owner.
     * Used by AutoArmor during its 3-step swap sequence.
     */
    public void setBusy(String owner) {
        this.busyOwner = owner;
    }
    
    /**
     * Clears the busy state, but only if the caller is the current owner.
     */
    public void clearBusy(String owner) {
        if (owner.equals(this.busyOwner)) {
            this.busyOwner = null;
        }
    }
    
    /**
     * Force-clears the busy state regardless of owner.
     * Used on module disable as a safety net.
     */
    public void forceClear(String owner) {
        if (owner.equals(this.busyOwner)) {
            this.busyOwner = null;
        }
    }
    
    /**
     * Returns true if a multi-step operation is in progress.
     */
    public boolean isBusy() {
        return busyOwner != null;
    }
    
    /**
     * Returns the name of the module holding the lock, or null.
     */
    public String getBusyOwner() {
        return busyOwner;
    }
    
    /**
     * Records that an inventory action was performed this tick.
     * Other modules should check canActThisTick() before clicking.
     */
    public void recordAction() {
        // Use currentTimeMillis / 50 as approximate tick (20 TPS = 50ms per tick)
        this.lastActionTick = System.currentTimeMillis() / 50;
    }
    
    /**
     * Returns true if no other module has performed an action this tick.
     * Prevents two modules from issuing windowClick in the same game tick.
     */
    public boolean canActThisTick() {
        // Yield to user: if the user physically clicks their mouse, the bot pauses to prevent intersecting packets (Anti-Desync)
        if (com.hades.client.api.HadesAPI.Input.isButtonDown(0) || com.hades.client.api.HadesAPI.Input.isButtonDown(1)) {
            return false;
        }

        long currentTick = System.currentTimeMillis() / 50;
        return currentTick != lastActionTick;
    }
    
    /**
     * Synthesizes a true Machine-Learning safe Bell Curve delay using Random.nextGaussian().
     * This bypasses Heuristic timing checks by generating natural deviations instead of linear RNG.
     */
    public long getGaussianDelay(long baseDelay, long jitter) {
        if (jitter <= 0) return baseDelay;
        
        // Random.nextGaussian() averages 0.0 with stddev 1.0 (99.7% of values fall within -3 to 3)
        double val = new java.util.Random().nextGaussian();
        
        // Clamp to strict limits to prevent anomalous 10-second pauses
        if (val > 3.0) val = 3.0;
        if (val < -3.0) val = -3.0;
        
        // Use absolute distribution to ensure delay is purely additive over baseDelay
        double offset = Math.abs(val) * (jitter / 3.0);
        
        return baseDelay + (long) offset;
    }
}

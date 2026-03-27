package com.hades.client.api.interfaces;

/**
 * Universal interface for the local player entity.
 * Implementations of this interface (e.g. Player189, Player120) wrap the underlying mapped Minecraft objects.
 */
public interface IPlayer extends IEntity {

    /** Returns true if the player instance is currently null (not in game) */
    boolean isNull();

    void setPrevYaw(float yaw);
    void setPrevPitch(float pitch);

    // Status
    boolean isSprinting();
    void setSprinting(boolean sprinting);
    boolean isSneaking();
    boolean isCollidedHorizontally();
    boolean isOnGround();
    
    // Equipment
    boolean isHoldingWeapon();
    boolean isHoldingSword();
    void swingItem();
    com.hades.client.api.interfaces.IItemStack getHeldItem();
    
    // Movement Input
    float getMoveForward();
    float getMoveStrafing();
    float getMovementInputForward(Object movementInput);
    float getMovementInputStrafe(Object movementInput);
    void overrideMovementInput(Object movementInput, float forward, float strafe);
    
    float getEyeHeight();
    int getItemInUseDuration();
    
    // GUI
    void closeScreen();
}

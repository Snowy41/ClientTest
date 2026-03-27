package com.hades.client.api.interfaces;

/**
 * Universal interface for core game properties (MC instance, display, inputs).
 */
public interface IMinecraft {
    boolean isNull();
    Object getRaw();


    int displayWidth();
    int displayHeight();
    
    /** Returns [scaledWidth, scaledHeight, scaleFactor] */
    int[] scaledResolution();
    
    boolean isInGame();
    boolean isInGui();
    
    void displayScreen(Object screen);
    
    float partialTicks();
    void setTimerSpeed(float speed);
    
    float getGamma();
    void setGamma(float gamma);
    float getMouseSensitivity();
    int getThirdPersonView();
    
    // Input
    boolean isKeyDown(int key);
    boolean isButtonDown(int button);
    int getMouseX();
    int getMouseY();
    
    void performClick();
    void performRightClick();
    void setKeyForwardPressed(boolean pressed);
    boolean isKeyForwardDown();
    void setKeySneakPressed(boolean pressed);
    void setKeySprintPressed(boolean pressed);
    boolean isKeySprintPhysicallyDown();
    void setVisuallyBlocking(boolean blocking);
    
    void setMouseOverBlock(double hitX, double hitY, double hitZ, int blockX, int blockY, int blockZ, int facingId);
    
    boolean isTargetingBlockTop();
    
    /** Returns 0 = MISS, 1 = BLOCK, 2 = ENTITY from MovingObjectType enum */
    int getMouseOverType();
    
    /** Returns the raw Entity object if type is ENTITY */
    Object getMouseOverEntity();
    
    /** Returns the raw BlockPos object if type is BLOCK */
    Object getMouseOverBlockPos();
    
    void attackEntity(com.hades.client.api.interfaces.IPlayer player, com.hades.client.api.interfaces.IEntity target);
}

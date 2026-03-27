package com.hades.client.api.interfaces;

/**
 * Universal interface for an entity in the game world.
 */
public interface IEntity {
    
    /** Returns the underlying raw object for use in packets or reflection when necessary. */
    Object getRaw();

    /** Returns the Minecraft entity ID used in network packets. */
    int getEntityId();

    boolean isPlayer();
    boolean isLiving();
    String getName();
    java.util.UUID getUUID();

    float getDistanceToEntity(IEntity other);

    double getX();
    double getY();
    double getZ();

    double getPrevX();
    double getPrevY();
    double getPrevZ();

    double getLastTickX();
    double getLastTickY();
    double getLastTickZ();

    float getWidth();
    float getHeight();
    float getYaw();
    float getPitch();

    boolean isOnGround();
    boolean isInvisible();

    double getMotionX();
    double getMotionY();
    double getMotionZ();

    float getSwingProgress();
    boolean isSwingInProgress();
    int getHurtTime();
    float getHealth();
    float getMaxHealth();
    
    // Setters for Combat / Movement manipulation
    void setYaw(float yaw);
    void setPitch(float pitch);
    void setMotionX(double x);
    void setMotionY(double y);
    void setMotionZ(double z);
}

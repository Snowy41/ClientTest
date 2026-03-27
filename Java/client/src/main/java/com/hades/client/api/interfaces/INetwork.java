package com.hades.client.api.interfaces;

import io.netty.channel.Channel;

/**
 * Universal interface for network communication.
 */
public interface INetwork {

    Channel getNettyChannel();

    /** Sends a packet through the normal event pipeline */
    void sendPacket(Object packet);
    
    /** Sends a packet directly to the server, bypassing interceptors */
    void sendPacketDirect(Object packet);
    
    // Packet Wrappers (Abstracting specific packet creation/reading)
    void sendInteractPacket(Object targetEntity);
    void sendBlockPacket(Object player);
    void sendUnblockPacket();
    
    Object createC08PacketForBlock(int x, int y, int z, int facing, float hitX, float hitY, float hitZ, Object heldItem);
    
    Object createC0APacket();
    Object createC02Packet(Object targetEntity);
    Object createC05Packet(float yaw, float pitch, boolean onGround);
    
    // Packet Readers
    boolean isS12Packet(Object packet);
    int getS12EntityId(Object packet);
    void scaleS12Velocity(Object packet, double horizontal, double vertical);
    
    boolean isS27Packet(Object packet);
    void scaleS27Velocity(Object packet, double horizontal, double vertical);

    // C03
    boolean isC03Packet(Object packet);
    void setC03Rotations(Object packet, float yaw, float pitch);
    void setC03OnGround(Object packet, boolean onGround);
    void sendAttackPacket(Object targetEntity);

    // BackTrack Packet Readers
    int getPacketEntityId(Object packet);
    double[] getS14EntityMoveDelta(Object packet);
    double[] getS18EntityPos(Object packet);
    
    // C0B
    boolean isC0BPacket(Object packet);
    int getC0BAction(Object packet);

    boolean isC02Packet(Object packet);
    String getC02Action(Object packet);
}

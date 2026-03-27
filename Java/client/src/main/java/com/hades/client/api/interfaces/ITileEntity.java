package com.hades.client.api.interfaces;

/**
 * Universal interface for TileEntities (Chests, Furnaces, Signs, etc) loaded in the world.
 */
public interface ITileEntity {

    /** Returns the raw Minecraft object for ByteBuddy/Reflection interoperation */
    Object getRaw();

    /** Returns the absolute X coordinate of the block */
    int getX();

    /** Returns the absolute Y coordinate of the block */
    int getY();

    /** Returns the absolute Z coordinate of the block */
    int getZ();

    /** Identifies if the TileEntity is a standard Chest */
    boolean isChest();

    /** Identifies if the TileEntity is an EnderChest */
    boolean isEnderChest();
}

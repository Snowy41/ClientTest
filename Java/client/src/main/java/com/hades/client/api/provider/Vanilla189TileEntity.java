package com.hades.client.api.provider;

import com.hades.client.api.interfaces.ITileEntity;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Method;

public class Vanilla189TileEntity implements ITileEntity {

    private final Object rawTileEntity;
    private int posX = 0, posY = 0, posZ = 0;

    // Reflection Cache
    private static Method getPosMethod = null;
    private static Method getXMethod = null;
    private static Method getYMethod = null;
    private static Method getZMethod = null;
    private static boolean initialized = false;

    private static Class<?> tileEntityChestClass = null;
    private static Class<?> tileEntityEnderChestClass = null;

    public Vanilla189TileEntity(Object rawTileEntity) {
        this.rawTileEntity = rawTileEntity;

        if (!initialized) {
            initReflection();
        }

        extractPosition();
    }

    private void initReflection() {
        try {
            Class<?> tileEntityClass = ReflectionUtil.findClass("net.minecraft.tileentity.TileEntity", "akw");
            Class<?> blockPosClass = ReflectionUtil.findClass("net.minecraft.util.BlockPos", "cj", "dt"); // cj for 1.8.9, dt can be Vec3i
            Class<?> vec3iClass = ReflectionUtil.findClass("net.minecraft.util.Vec3i", "df"); // Parent class possessing getX, getY, getZ

            tileEntityChestClass = ReflectionUtil.findClass("net.minecraft.tileentity.TileEntityChest", "aky");
            tileEntityEnderChestClass = ReflectionUtil.findClass("net.minecraft.tileentity.TileEntityEnderChest", "alf");

            // getPos -> returns BlockPos (cj)
            if (tileEntityClass != null) {
                for (Method m : tileEntityClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && blockPosClass != null && m.getReturnType() == blockPosClass && (m.getName().equals("getPos") || m.getName().equals("v"))) {
                        getPosMethod = m;
                        getPosMethod.setAccessible(true);
                        break;
                    }
                }
            }

            // Vec3i (df) has getX, getY, getZ for blockpos coordinates
            if (vec3iClass != null) {
                for (Method m : vec3iClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == int.class) {
                        String name = m.getName();
                        if (name.equals("getX") || name.equals("n")) getXMethod = m;
                        if (name.equals("getY") || name.equals("o")) getYMethod = m;
                        if (name.equals("getZ") || name.equals("p")) getZMethod = m;
                    }
                }
                
                if (getXMethod != null) getXMethod.setAccessible(true);
                if (getYMethod != null) getYMethod.setAccessible(true);
                if (getZMethod != null) getZMethod.setAccessible(true);
            }

        } catch (Exception e) {
            HadesLogger.get().error("Vanilla189TileEntity core reflection initialization failed", e);
        }
        initialized = true;
    }

    private void extractPosition() {
        if (rawTileEntity == null || getPosMethod == null || getXMethod == null || getYMethod == null || getZMethod == null) return;
        
        try {
            // BlockPos pos = tileEntity.getPos()
            Object blockPos = getPosMethod.invoke(rawTileEntity);
            if (blockPos != null) {
                this.posX = (int) getXMethod.invoke(blockPos);
                this.posY = (int) getYMethod.invoke(blockPos);
                this.posZ = (int) getZMethod.invoke(blockPos);
            }
        } catch (Exception ignored) {
            // Fails safe silently, XYZ reverts to 0
        }
    }

    @Override
    public Object getRaw() {
        return rawTileEntity;
    }

    @Override
    public int getX() {
        return posX;
    }

    @Override
    public int getY() {
        return posY;
    }

    @Override
    public int getZ() {
        return posZ;
    }

    @Override
    public boolean isChest() {
        return rawTileEntity != null && tileEntityChestClass != null && tileEntityChestClass.isAssignableFrom(rawTileEntity.getClass());
    }

    @Override
    public boolean isEnderChest() {
        return rawTileEntity != null && tileEntityEnderChestClass != null && tileEntityEnderChestClass.isAssignableFrom(rawTileEntity.getClass());
    }
}

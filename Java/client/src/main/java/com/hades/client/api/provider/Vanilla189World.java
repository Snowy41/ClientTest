package com.hades.client.api.provider;

import com.hades.client.api.interfaces.IEntity;
import com.hades.client.api.interfaces.ITileEntity;
import com.hades.client.api.interfaces.IWorld;
import com.hades.client.api.interfaces.TraceResult;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Constructor;
import com.hades.client.util.HadesLogger;

public class Vanilla189World implements IWorld {

    private final Class<?> minecraftClass;
    private final Method getMinecraftMethod;
    private final Field theWorldField;
    
    private Constructor<?> blockPosConstructor;
    private Constructor<?> mutableBlockPosConstructor;
    private Method setBlockPosMethod;
    private Method getBlockStateMethod;
    private Method getBlockMethod;
    private Method getIdFromBlockMethod;

    // Ray trace / entity intercept reflection caches
    private Class<?> vec3Class;
    private Constructor<?> vec3Ctor;
    private Field vxF, vyF, vzF;
    private Class<?> mopClass;
    private Field hitVecField, sideHitField;
    private Method rayTraceBlocksMethod;
    private Class<?> aabbClass;
    private Method expandMethod, calcInterceptMethod;
    private Method getEntityBBMethod;
    private Class<?> entityLivingClass;

    public Vanilla189World() {
        minecraftClass = ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
        getMinecraftMethod = ReflectionUtil.findMethod(minecraftClass, new String[] { "A", "getMinecraft", "func_71410_x" });
        theWorldField = ReflectionUtil.findField(minecraftClass, "f", "theWorld", "field_71441_e");
        
        Class<?> blockPosClass = ReflectionUtil.findClass("net.minecraft.util.BlockPos", "cj");
        if (blockPosClass != null) {
            try { blockPosConstructor = blockPosClass.getConstructor(int.class, int.class, int.class); } catch (Exception ignored) {}
        }
        
        Class<?> mutableBlockPosClass = ReflectionUtil.findClass("net.minecraft.util.BlockPos$MutableBlockPos", "cj$a");
        if (mutableBlockPosClass != null) {
            try { 
                mutableBlockPosConstructor = mutableBlockPosClass.getConstructor(); 
                setBlockPosMethod = ReflectionUtil.findMethod(mutableBlockPosClass, new String[]{"c", "set", "func_181079_c"}, int.class, int.class, int.class);
            } catch (Exception ignored) {}
        }
        
        Class<?> worldClass = ReflectionUtil.findClass("net.minecraft.world.World", "adm");
        for (Method m : worldClass.getDeclaredMethods()) {
            String mn = m.getName();
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == blockPosClass && (mn.equals("p") || mn.equals("getBlockState") || mn.equals("func_180495_p"))) {
                getBlockStateMethod = m;
                getBlockStateMethod.setAccessible(true);
                break;
            }
        }

        Class<?> blockStateClass = ReflectionUtil.findClass("net.minecraft.block.state.IBlockState", "alz");
        for (Method m : blockStateClass.getDeclaredMethods()) {
            String mn = m.getName();
            if (m.getParameterCount() == 0 && (mn.equals("c") || mn.equals("getBlock") || mn.equals("func_177230_c"))) {
                getBlockMethod = m;
                getBlockMethod.setAccessible(true);
                break;
            }
        }

        Class<?> blockClass = ReflectionUtil.findClass("net.minecraft.block.Block", "afh", "aky");
        for (Method m : blockClass.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == blockClass && m.getReturnType() == int.class) {
                getIdFromBlockMethod = m;
                getIdFromBlockMethod.setAccessible(true);
                break;
            }
        }

        // --- Ray trace / entity intercept reflection ---
        vec3Class = ReflectionUtil.findClass("net.minecraft.util.Vec3", "aui");
        if (vec3Class != null) {
            try { vec3Ctor = vec3Class.getConstructor(double.class, double.class, double.class); } catch (Exception ignored) {}
            vxF = ReflectionUtil.findField(vec3Class, "xCoord", "a", "field_72450_a");
            vyF = ReflectionUtil.findField(vec3Class, "yCoord", "b", "field_72448_b");
            vzF = ReflectionUtil.findField(vec3Class, "zCoord", "c", "field_72449_c");
        }

        mopClass = ReflectionUtil.findClass("net.minecraft.util.MovingObjectPosition", "auh");
        if (mopClass != null) {
            hitVecField = ReflectionUtil.findField(mopClass, "hitVec", "c", "field_72307_f");
            sideHitField = ReflectionUtil.findField(mopClass, "sideHit", "b", "field_178784_b");
        }

        if (worldClass != null && vec3Class != null) {
            // rayTraceBlocks(Vec3, Vec3) — 2 param version
            rayTraceBlocksMethod = ReflectionUtil.findMethod(worldClass, new String[]{"a", "rayTraceBlocks", "func_72933_a"}, vec3Class, vec3Class);
        }

        aabbClass = ReflectionUtil.findClass("net.minecraft.util.AxisAlignedBB", "aug");
        if (aabbClass != null) {
            expandMethod = ReflectionUtil.findMethod(aabbClass, new String[]{"b", "expand", "func_72314_b"}, double.class, double.class, double.class);
            if (vec3Class != null) {
                calcInterceptMethod = ReflectionUtil.findMethod(aabbClass, new String[]{"a", "calculateIntercept", "func_72327_a"}, vec3Class, vec3Class);
            }
        }

        Class<?> entityClass = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");
        if (entityClass != null) {
            getEntityBBMethod = ReflectionUtil.findMethod(entityClass, new String[]{"aB", "getEntityBoundingBox", "func_174813_aQ"});
        }

        entityLivingClass = ReflectionUtil.findClass("net.minecraft.entity.EntityLivingBase", "pr");
    }

    private Object getWorld() {
        try {
            Object mc = getMinecraftMethod != null ? getMinecraftMethod.invoke(null) : null;
            return mc != null && theWorldField != null ? theWorldField.get(mc) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isNull() {
        return getWorld() == null;
    }
    
    @Override
    public Object getRaw() {
        return getWorld();
    }

    @Override
    public com.hades.client.api.interfaces.scoreboard.IScoreboard getScoreboard() {
        Object rawWorld = getWorld();
        if (rawWorld == null) return null;
        try {
            java.lang.reflect.Method getScoreboardMethod = ReflectionUtil.findMethod(rawWorld.getClass(), new String[]{"getScoreboard", "Z", "func_96441_U"});
            if (getScoreboardMethod != null) {
                Object rawScoreboard = getScoreboardMethod.invoke(rawWorld);
                if (rawScoreboard != null) {
                    return new com.hades.client.api.provider.Vanilla189Scoreboard(rawScoreboard);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private final java.util.Map<Object, IEntity> entityCache = new java.util.WeakHashMap<>();
    private final java.util.Map<Object, ITileEntity> tileEntityCache = new java.util.WeakHashMap<>();
    private Object lastRawWorld = null;

    public void clearCaches() {
        entityCache.clear();
        tileEntityCache.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IEntity> getLoadedEntities() {
        try {
            Object w = getWorld();
            if (w == null) return Collections.emptyList();

            if (w != lastRawWorld) {
                clearCaches();
                lastRawWorld = w;
                // Hook to clear TargetManager's cross-world caches too
                com.hades.client.combat.TargetManager.getInstance().onWorldChange();
            }

            Field f = ReflectionUtil.findField(w.getClass(), "j", "loadedEntityList", "field_72996_f");
            if (f == null) return Collections.emptyList();
            List<Object> rawList = (List<Object>) f.get(w);
            if (rawList == null) return Collections.emptyList();
            
            List<IEntity> wrappedList = new ArrayList<>(rawList.size());
            for (Object raw : rawList) {
                IEntity wrapper = entityCache.get(raw);
                if (wrapper == null) {
                    wrapper = new Vanilla189Entity(raw);
                    entityCache.put(raw, wrapper);
                }
                wrappedList.add(wrapper);
            }
            return wrappedList;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ITileEntity> getLoadedTileEntities() {
        try {
            Object w = getWorld();
            if (w == null) return Collections.emptyList();
            Field f = ReflectionUtil.findField(w.getClass(), "h", "loadedTileEntityList", "field_147482_g");
            if (f == null) return Collections.emptyList();
            List<Object> rawList = (List<Object>) f.get(w);
            if (rawList == null) return Collections.emptyList();
            
            List<ITileEntity> wrappedList = new ArrayList<>(rawList.size());
            for (Object raw : rawList) {
                ITileEntity wrapper = tileEntityCache.get(raw);
                if (wrapper == null) {
                    wrapper = new Vanilla189TileEntity(raw);
                    tileEntityCache.put(raw, wrapper);
                }
                wrappedList.add(wrapper);
            }
            return wrappedList;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // MutableBlockPos instance bound to the world object cache
    private Object mutableBlockPosInstance = null;

    private Object getOrCreateMutableBlockPos(int x, int y, int z) throws Exception {
        if (mutableBlockPosConstructor != null && setBlockPosMethod != null) {
            if (mutableBlockPosInstance == null) {
                mutableBlockPosInstance = mutableBlockPosConstructor.newInstance();
            }
            setBlockPosMethod.invoke(mutableBlockPosInstance, x, y, z);
            return mutableBlockPosInstance; // MutableBlockPos extends BlockPos, safely passed to generic mc methods
        }
        return blockPosConstructor.newInstance(x, y, z); // Fallback to creating a new BlockPos
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        if (blockPosConstructor == null || getBlockStateMethod == null || getBlockMethod == null || getIdFromBlockMethod == null) return false;
        try {
            Object rawWorld = getWorld();
            if (rawWorld == null) return false;
            Object blockPos = getOrCreateMutableBlockPos(x, y, z);
            Object blockState = getBlockStateMethod.invoke(rawWorld, blockPos);
            Object block = getBlockMethod.invoke(blockState);
            int blockId = (int) getIdFromBlockMethod.invoke(null, block);
            return blockId == 0;
        } catch (Exception e) {
            HadesLogger.get().error("Vanilla189World math failure in block boundary calculation!", e);
            return false;
        }
    }

    @Override
    public boolean isSolidBlock(int x, int y, int z) {
        if (blockPosConstructor == null || getBlockStateMethod == null || getBlockMethod == null || getIdFromBlockMethod == null) return false;
        try {
            Object rawWorld = getWorld();
            if (rawWorld == null) return false;
            Object blockPos = getOrCreateMutableBlockPos(x, y, z);
            Object blockState = getBlockStateMethod.invoke(rawWorld, blockPos);
            Object block = getBlockMethod.invoke(blockState);
            int id = (int) getIdFromBlockMethod.invoke(null, block);
            // Ignore non-solid / replaceable blocks: Air(0), Water(8,9), Lava(10,11), TallGrass(31), DeadBush(32), Fire(51), Wheat(59), Vines(106), DoublePlant(175)
            return id != 0 && id != 8 && id != 9 && id != 10 && id != 11 && id != 31 && id != 32 && id != 51 && id != 59 && id != 106 && id != 175;
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public Object getBlockAt(Object blockPos) {
        if (blockPos == null || getBlockStateMethod == null || getBlockMethod == null) return null;
        try {
            Object rawWorld = getWorld();
            if (rawWorld == null) return null;
            Object blockState = getBlockStateMethod.invoke(rawWorld, blockPos);
            return getBlockMethod.invoke(blockState);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TraceResult rayTraceBlocks(double x1, double y1, double z1, double x2, double y2, double z2) {
        if (rayTraceBlocksMethod == null || vec3Ctor == null || hitVecField == null || vxF == null) return TraceResult.miss();
        try {
            Object rawWorld = getWorld();
            if (rawWorld == null) return TraceResult.miss();
            Object startVec = vec3Ctor.newInstance(x1, y1, z1);
            Object endVec = vec3Ctor.newInstance(x2, y2, z2);
            Object mop = rayTraceBlocksMethod.invoke(rawWorld, startVec, endVec);
            if (mop == null) return TraceResult.miss();

            Object hitVec = hitVecField.get(mop);
            if (hitVec == null) return TraceResult.miss();
            double hx = vxF.getDouble(hitVec);
            double hy = vyF.getDouble(hitVec);
            double hz = vzF.getDouble(hitVec);

            String side = "NONE";
            if (sideHitField != null) {
                Object sideObj = sideHitField.get(mop);
                if (sideObj != null) side = sideObj.toString().toUpperCase();
            }
            return new TraceResult(true, hx, hy, hz, side, false);
        } catch (Exception e) {
            return TraceResult.miss();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public TraceResult checkEntityIntercept(double x1, double y1, double z1, double x2, double y2, double z2, Object excludeRaw) {
        if (vec3Ctor == null || calcInterceptMethod == null || expandMethod == null || getEntityBBMethod == null || hitVecField == null || vxF == null) {
            return TraceResult.miss();
        }
        try {
            Object rawWorld = getWorld();
            if (rawWorld == null) return TraceResult.miss();

            Object startVec = vec3Ctor.newInstance(x1, y1, z1);
            Object endVec = vec3Ctor.newInstance(x2, y2, z2);

            Field loadedEntityListField = ReflectionUtil.findField(rawWorld.getClass(), "j", "loadedEntityList", "field_72996_f");
            if (loadedEntityListField == null) return TraceResult.miss();
            List<Object> entities = (List<Object>) loadedEntityListField.get(rawWorld);
            if (entities == null) return TraceResult.miss();

            for (Object rawEnt : entities) {
                if (rawEnt == excludeRaw) continue;
                if (entityLivingClass != null && !entityLivingClass.isInstance(rawEnt)) continue;
                try {
                    Object bb = getEntityBBMethod.invoke(rawEnt);
                    if (bb == null) continue;
                    Object expandedBB = expandMethod.invoke(bb, 0.3, 0.3, 0.3);
                    Object intercept = calcInterceptMethod.invoke(expandedBB, startVec, endVec);
                    if (intercept != null) {
                        Object hitVec = hitVecField.get(intercept);
                        if (hitVec != null) {
                            double hx = vxF.getDouble(hitVec);
                            double hy = vyF.getDouble(hitVec);
                            double hz = vzF.getDouble(hitVec);
                            return new TraceResult(true, hx, hy, hz, "NONE", true);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return TraceResult.miss();
    }
}

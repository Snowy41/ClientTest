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
    private static Object mcInstance;
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
    
    private static Field loadedEntityListField;
    private static Field loadedTileEntityListField;
    private static boolean listFieldsCached = false;


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

        if (worldClass != null && !listFieldsCached) {
            loadedEntityListField = ReflectionUtil.findField(worldClass, "j", "loadedEntityList", "field_72996_f");
            loadedTileEntityListField = ReflectionUtil.findField(worldClass, "h", "loadedTileEntityList", "field_147482_g");
            listFieldsCached = true;
        }

    }

    private Object getWorld() {
        try {
            if (mcInstance == null && getMinecraftMethod != null) {
                mcInstance = getMinecraftMethod.invoke(null);
            }
            return mcInstance != null && theWorldField != null ? theWorldField.get(mcInstance) : null;
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

    private final java.util.Map<Object, ITileEntity> tileEntityCache = new java.util.WeakHashMap<>();
    private java.util.Map<Object, IEntity> entityCache = new java.util.IdentityHashMap<>();
    private Object lastRawWorld = null;

    public void clearCaches() {
        tileEntityCache.clear();
        entityCache.clear();
    }

    // Per-tick cache for getLoadedEntities() — avoids rebuilding wrapper list on every call
    private List<IEntity> cachedEntityList = Collections.emptyList();
    private long lastEntityCacheTick = -1;

    @Override
    @SuppressWarnings("unchecked")
    public List<IEntity> getLoadedEntities() {
        try {
            Object w = getWorld();
            if (w == null) return Collections.emptyList();

            if (w != lastRawWorld) {
                clearCaches();
                lastRawWorld = w;
                lastEntityCacheTick = -1; // Force rebuild
                // Hook to clear TargetManager's cross-world caches too
                com.hades.client.combat.TargetManager.getInstance().onWorldChange();
            }

            // Only rebuild wrapper list once per tick (~50ms)
            long currentTick = System.currentTimeMillis() / 50;
            if (currentTick == lastEntityCacheTick) {
                return cachedEntityList;
            }
            lastEntityCacheTick = currentTick;

            if (loadedEntityListField == null) return Collections.emptyList();
            List<Object> rawList = (List<Object>) loadedEntityListField.get(w);
            if (rawList == null) return Collections.emptyList();
            
            java.util.Map<Object, IEntity> nextCache = new java.util.IdentityHashMap<>(rawList.size());
            List<IEntity> wrappedList = new ArrayList<>(rawList.size());
            
            for (Object raw : rawList) {
                IEntity wrapper = entityCache.get(raw);
                if (wrapper == null) {
                    wrapper = new Vanilla189Entity(raw);
                }
                nextCache.put(raw, wrapper);
                wrappedList.add(wrapper);
            }
            
            // Atomically upgrade the cache to strictly only what physically exists this tick
            this.entityCache = nextCache;
            this.cachedEntityList = wrappedList;
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
            if (loadedTileEntityListField == null) return Collections.emptyList();
            List<Object> rawList = (List<Object>) loadedTileEntityListField.get(w);
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
    public TraceResult checkEntityIntercept(double x1, double y1, double z1, double x2, double y2, double z2, Object excludeRaw) {
        TraceResult closest = null;
        double minDistanceSq = Double.MAX_VALUE;

        double dirX = x2 - x1;
        double dirY = y2 - y1;
        double dirZ = z2 - z1;
        double segmentLen = Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
        if (segmentLen == 0) return TraceResult.miss();

        for (com.hades.client.api.interfaces.IEntity entity : getLoadedEntities()) {
            if (entity.getRaw() == null || entity.getRaw() == excludeRaw) continue;

            // Projectile AABB expansion is canonically 0.3f for arrows/snowballs in Vanilla
            double border = 0.3; 
            double w = (entity.getWidth() / 2.0) + border;
            
            double minX = entity.getX() - w;
            double maxX = entity.getX() + w;
            double minY = entity.getY() - border; 
            double maxY = entity.getY() + entity.getHeight() + border;
            double minZ = entity.getZ() - w;
            double maxZ = entity.getZ() + w;

            // Slab Method for Line-AABB intersection
            double tmin = -Double.MAX_VALUE;
            double tmax = Double.MAX_VALUE;

            if (dirX != 0.0) {
                double tx1 = (minX - x1) / dirX;
                double tx2 = (maxX - x1) / dirX;
                tmin = Math.max(tmin, Math.min(tx1, tx2));
                tmax = Math.min(tmax, Math.max(tx1, tx2));
            } else if (x1 < minX || x1 > maxX) continue;

            if (dirY != 0.0) {
                double ty1 = (minY - y1) / dirY;
                double ty2 = (maxY - y1) / dirY;
                tmin = Math.max(tmin, Math.min(ty1, ty2));
                tmax = Math.min(tmax, Math.max(ty1, ty2));
            } else if (y1 < minY || y1 > maxY) continue;

            if (dirZ != 0.0) {
                double tz1 = (minZ - z1) / dirZ;
                double tz2 = (maxZ - z1) / dirZ;
                tmin = Math.max(tmin, Math.min(tz1, tz2));
                tmax = Math.min(tmax, Math.max(tz1, tz2));
            } else if (z1 < minZ || z1 > maxZ) continue;

            // Check if intersection occurs within the bounds of this single tick's line segment [0, 1]
            if (tmax >= tmin && tmin >= 0 && tmin <= 1) {
                double hitX = x1 + dirX * tmin;
                double hitY = y1 + dirY * tmin;
                double hitZ = z1 + dirZ * tmin;

                double dSq = (hitX - x1)*(hitX - x1) + (hitY - y1)*(hitY - y1) + (hitZ - z1)*(hitZ - z1);
                if (dSq < minDistanceSq) {
                    minDistanceSq = dSq;
                    closest = new TraceResult(true, hitX, hitY, hitZ, "NONE", true);
                }
            }
        }

        return closest != null ? closest : TraceResult.miss();
    }
}

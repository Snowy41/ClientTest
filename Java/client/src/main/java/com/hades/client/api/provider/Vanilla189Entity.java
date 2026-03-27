package com.hades.client.api.provider;

import com.hades.client.api.interfaces.IEntity;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Vanilla189Entity implements IEntity {

    private final Object rawEntity;
    
    private static boolean cached = false;
    private static Class<?> entityClass;
    private static Class<?> playerClass;
    private static Class<?> livingClass;
    
    private static Field posXField, posYField, posZField;
    private static Field prevPosXField, prevPosYField, prevPosZField;
    private static Field motionXField, motionYField, motionZField;
    private static Field rotYawField, rotPitchField;
    private static Field onGroundField;
    private static Field lastTickPosXField, lastTickPosYField, lastTickPosZField;
    private static Field entityWidthField;
    private static Field entityHeightField;
    private static Method getNameMethod;
    private static Method isInvisibleMethod;
    
    private static Field swingProgressField;
    private static Field isSwingInProgressField;
    private static Field hurtTimeField;
    private static Method getHealthMethod, getMaxHealthMethod;
    private static Method getEntityIdMethod;
    private static Method getUUIDMethod;

    public Vanilla189Entity(Object rawEntity) {
        this.rawEntity = rawEntity;
        cacheFields();
    }

    private static void cacheFields() {
        if (cached) return;
        entityClass = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");
        if (entityClass != null) {
            posXField = ReflectionUtil.findField(entityClass, "s", "posX", "field_70165_t");
            posYField = ReflectionUtil.findField(entityClass, "t", "posY", "field_70163_u");
            posZField = ReflectionUtil.findField(entityClass, "u", "posZ", "field_70161_v");
            prevPosXField = ReflectionUtil.findField(entityClass, "p", "prevPosX", "field_70169_q");
            prevPosYField = ReflectionUtil.findField(entityClass, "q", "prevPosY", "field_70167_r");
            prevPosZField = ReflectionUtil.findField(entityClass, "r", "prevPosZ", "field_70166_s");
            motionXField = ReflectionUtil.findField(entityClass, "v", "motionX", "field_70159_w");
            motionYField = ReflectionUtil.findField(entityClass, "w", "motionY", "field_70181_x");
            motionZField = ReflectionUtil.findField(entityClass, "x", "motionZ", "field_70179_y");
            rotYawField = ReflectionUtil.findField(entityClass, "y", "rotationYaw", "field_70177_z");
            rotPitchField = ReflectionUtil.findField(entityClass, "z", "rotationPitch", "field_70125_A");
            onGroundField = ReflectionUtil.findField(entityClass, "C", "onGround", "field_70122_E");
            lastTickPosXField = ReflectionUtil.findField(entityClass, "P", "lastTickPosX", "field_70142_S");
            lastTickPosYField = ReflectionUtil.findField(entityClass, "Q", "lastTickPosY", "field_70137_T");
            lastTickPosZField = ReflectionUtil.findField(entityClass, "R", "lastTickPosZ", "field_70136_U");
            entityWidthField = ReflectionUtil.findField(entityClass, "J", "width", "field_70130_N");
            entityHeightField = ReflectionUtil.findField(entityClass, "K", "height", "field_70131_O");
            
            getNameMethod = ReflectionUtil.findMethod(entityClass, new String[]{"e_", "getName", "func_70005_c_"});
            isInvisibleMethod = ReflectionUtil.findMethod(entityClass, new String[]{"ay", "isInvisible", "func_82150_aj"});
            getEntityIdMethod = ReflectionUtil.findMethod(entityClass, new String[]{"F", "getEntityId", "func_145782_y"});
            getUUIDMethod = ReflectionUtil.findMethod(entityClass, new String[]{"aK", "getUniqueID", "func_110124_au"});
        }
        
        playerClass = ReflectionUtil.findClass("net.minecraft.entity.player.EntityPlayer", "wn");
        livingClass = ReflectionUtil.findClass("net.minecraft.entity.EntityLivingBase", "pr");
        if (livingClass != null) {
            swingProgressField = ReflectionUtil.findField(livingClass, "az", "swingProgress", "field_70733_aJ");
            isSwingInProgressField = ReflectionUtil.findField(livingClass, "ar", "isSwingInProgress", "field_82175_bq");
            hurtTimeField = ReflectionUtil.findField(livingClass, "at", "hurtTime", "field_70737_aN");
            getHealthMethod = ReflectionUtil.findMethod(livingClass, new String[]{"bn", "getHealth", "func_110143_aJ"});
            getMaxHealthMethod = ReflectionUtil.findMethod(livingClass, new String[]{"bI", "getMaxHealth", "func_110138_aP"});
        }
        cached = true;
    }

    @Override public Object getRaw() { return rawEntity; }

    @Override
    public int getEntityId() {
        try { return getEntityIdMethod != null ? (int) getEntityIdMethod.invoke(rawEntity) : -1; } catch (Exception e) { return -1; }
    }
    @Override public boolean isPlayer() { return playerClass != null && playerClass.isInstance(rawEntity); }
    @Override public boolean isLiving() { return livingClass != null && livingClass.isInstance(rawEntity); }

    @Override
    public String getName() {
        try { return getNameMethod != null ? (String) getNameMethod.invoke(rawEntity) : ""; } catch (Exception e) { return ""; }
    }

    @Override
    public java.util.UUID getUUID() {
        try { return getUUIDMethod != null ? (java.util.UUID) getUUIDMethod.invoke(rawEntity) : null; } catch (Exception e) { return null; }
    }

    @Override
    public float getDistanceToEntity(IEntity other) {
        if (other == null) return 0f;
        double dx = getX() - other.getX();
        double dy = getY() - other.getY();
        double dz = getZ() - other.getZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override public double getX() { return ReflectionUtil.getDoubleField(rawEntity, posXField); }
    @Override public double getY() { return ReflectionUtil.getDoubleField(rawEntity, posYField); }
    @Override public double getZ() { return ReflectionUtil.getDoubleField(rawEntity, posZField); }
    @Override public double getPrevX() { return ReflectionUtil.getDoubleField(rawEntity, prevPosXField); }
    @Override public double getPrevY() { return ReflectionUtil.getDoubleField(rawEntity, prevPosYField); }
    @Override public double getPrevZ() { return ReflectionUtil.getDoubleField(rawEntity, prevPosZField); }
    @Override public double getLastTickX() { return ReflectionUtil.getDoubleField(rawEntity, lastTickPosXField); }
    @Override public double getLastTickY() { return ReflectionUtil.getDoubleField(rawEntity, lastTickPosYField); }
    @Override public double getLastTickZ() { return ReflectionUtil.getDoubleField(rawEntity, lastTickPosZField); }
    @Override public float getWidth() { return ReflectionUtil.getFloatField(rawEntity, entityWidthField); }
    @Override public float getHeight() { return ReflectionUtil.getFloatField(rawEntity, entityHeightField); }
    @Override public float getYaw() { return ReflectionUtil.getFloatField(rawEntity, rotYawField); }
    @Override public float getPitch() { return ReflectionUtil.getFloatField(rawEntity, rotPitchField); }
    @Override public boolean isOnGround() { return ReflectionUtil.getBoolField(rawEntity, onGroundField); }
    
    @Override
    public boolean isInvisible() {
        try { return isInvisibleMethod != null && (boolean) isInvisibleMethod.invoke(rawEntity); } catch (Exception e) { return false; }
    }

    @Override public double getMotionX() { return ReflectionUtil.getDoubleField(rawEntity, motionXField); }
    @Override public double getMotionY() { return ReflectionUtil.getDoubleField(rawEntity, motionYField); }
    @Override public double getMotionZ() { return ReflectionUtil.getDoubleField(rawEntity, motionZField); }
    @Override public float getSwingProgress() { return ReflectionUtil.getFloatField(rawEntity, swingProgressField); }
    @Override public boolean isSwingInProgress() { return ReflectionUtil.getBoolField(rawEntity, isSwingInProgressField); }
    @Override public int getHurtTime() { return ReflectionUtil.getIntField(rawEntity, hurtTimeField); }

    private Float cachedHealth = null;
    private Float cachedMaxHealth = null;
    private long lastHealthTime = 0;
    private long lastMaxHealthTime = 0;
    
    // Cache the LabyMod LivingEntity reference to avoid O(N) UUID lookup each call
    private Object cachedLabyLivingEntity = null; // net.labymod.api.client.entity.LivingEntity
    private boolean labyEntityLookupDone = false;
    private long lastLabyLookupTime = 0;

    private Object getLabyLivingEntity() {
        long now = System.currentTimeMillis();
        // Re-lookup every 2 seconds to handle entity respawns/world changes
        if (labyEntityLookupDone && (now - lastLabyLookupTime) < 2000) {
            return cachedLabyLivingEntity;
        }
        labyEntityLookupDone = true;
        lastLabyLookupTime = now;
        cachedLabyLivingEntity = null;
        try {
            if (net.labymod.api.Laby.isInitialized() && getUUIDMethod != null) {
                java.util.UUID myUuid = (java.util.UUID) getUUIDMethod.invoke(rawEntity);
                if (myUuid != null) {
                    for (net.labymod.api.client.entity.Entity lEntity : net.labymod.api.Laby.labyAPI().minecraft().clientWorld().getEntities()) {
                        if (myUuid.equals(lEntity.getUniqueId()) && lEntity instanceof net.labymod.api.client.entity.LivingEntity) {
                            cachedLabyLivingEntity = lEntity;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {}
        return cachedLabyLivingEntity;
    }

    @Override
    public float getHealth() {
        long now = System.currentTimeMillis();
        if (cachedHealth != null && (now - lastHealthTime) < 50) return cachedHealth;
        
        // Try cached LabyMod entity reference first (O(1) after first lookup)
        Object labyEntity = getLabyLivingEntity();
        if (labyEntity instanceof net.labymod.api.client.entity.LivingEntity) {
            cachedHealth = ((net.labymod.api.client.entity.LivingEntity) labyEntity).getHealth();
            lastHealthTime = now;
            return cachedHealth;
        }
        
        // Fallback: direct reflection on raw entity
        if (getHealthMethod != null) {
            try { 
                cachedHealth = (float) getHealthMethod.invoke(rawEntity);
                lastHealthTime = now;
                return cachedHealth; 
            } catch (Exception e) {}
        }
        for (Method m : rawEntity.getClass().getMethods()) {
            if (m.getName().equals("getHealth") && m.getParameterCount() == 0 && m.getReturnType() == float.class) {
                getHealthMethod = m;
                getHealthMethod.setAccessible(true);
                try { 
                    cachedHealth = (float) getHealthMethod.invoke(rawEntity); 
                    lastHealthTime = now;
                    return cachedHealth;
                } catch (Exception e) {}
            }
        }
        cachedHealth = 20f;
        lastHealthTime = now;
        return 20f;
    }

    @Override
    public float getMaxHealth() {
        long now = System.currentTimeMillis();
        if (cachedMaxHealth != null && (now - lastMaxHealthTime) < 50) return cachedMaxHealth;
        
        // Try cached LabyMod entity reference first (O(1) after first lookup)
        Object labyEntity = getLabyLivingEntity();
        if (labyEntity instanceof net.labymod.api.client.entity.LivingEntity) {
            cachedMaxHealth = ((net.labymod.api.client.entity.LivingEntity) labyEntity).getMaximalHealth();
            lastMaxHealthTime = now;
            return cachedMaxHealth;
        }

        // Fallback: direct reflection on raw entity
        if (getMaxHealthMethod != null) {
            try { 
                cachedMaxHealth = (float) getMaxHealthMethod.invoke(rawEntity); 
                lastMaxHealthTime = now;
                return cachedMaxHealth;
            } catch (Exception e) {}
        }
        for (Method m : rawEntity.getClass().getMethods()) {
            if (m.getName().equals("getMaxHealth") && m.getParameterCount() == 0 && m.getReturnType() == float.class) {
                getMaxHealthMethod = m;
                getMaxHealthMethod.setAccessible(true);
                try { 
                    cachedMaxHealth = (float) getMaxHealthMethod.invoke(rawEntity); 
                    lastMaxHealthTime = now;
                    return cachedMaxHealth;
                } catch (Exception e) {}
            }
        }
        cachedMaxHealth = 20f;
        lastMaxHealthTime = now;
        return 20f;
    }

    @Override public void setYaw(float yaw) { ReflectionUtil.setFloatField(rawEntity, rotYawField, yaw); }
    @Override public void setPitch(float pitch) { ReflectionUtil.setFloatField(rawEntity, rotPitchField, pitch); }
    @Override public void setMotionX(double x) { ReflectionUtil.setDoubleField(rawEntity, motionXField, x); }
    @Override public void setMotionY(double y) { ReflectionUtil.setDoubleField(rawEntity, motionYField, y); }
    @Override public void setMotionZ(double z) { ReflectionUtil.setDoubleField(rawEntity, motionZField, z); }
}

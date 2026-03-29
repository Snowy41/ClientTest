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
    private static Field fallDistanceField;

    // -- Fast Unsafe Offsets --
    private static long posXOffset = -1L, posYOffset = -1L, posZOffset = -1L;
    private static long prevPosXOffset = -1L, prevPosYOffset = -1L, prevPosZOffset = -1L;
    private static long motionXOffset = -1L, motionYOffset = -1L, motionZOffset = -1L;
    private static long rotYawOffset = -1L, rotPitchOffset = -1L;
    private static long onGroundOffset = -1L;
    private static long lastTickPosXOffset = -1L, lastTickPosYOffset = -1L, lastTickPosZOffset = -1L;
    private static long entityWidthOffset = -1L, entityHeightOffset = -1L;
    private static long swingProgressOffset = -1L;
    private static long isSwingInProgressOffset = -1L;
    private static long hurtTimeOffset = -1L;
    private static long fallDistanceOffset = -1L;
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
            fallDistanceField = ReflectionUtil.findField(entityClass, "O", "fallDistance", "field_70143_R");

            posXOffset = ReflectionUtil.getFieldOffset(posXField);
            posYOffset = ReflectionUtil.getFieldOffset(posYField);
            posZOffset = ReflectionUtil.getFieldOffset(posZField);
            prevPosXOffset = ReflectionUtil.getFieldOffset(prevPosXField);
            prevPosYOffset = ReflectionUtil.getFieldOffset(prevPosYField);
            prevPosZOffset = ReflectionUtil.getFieldOffset(prevPosZField);
            motionXOffset = ReflectionUtil.getFieldOffset(motionXField);
            motionYOffset = ReflectionUtil.getFieldOffset(motionYField);
            motionZOffset = ReflectionUtil.getFieldOffset(motionZField);
            rotYawOffset = ReflectionUtil.getFieldOffset(rotYawField);
            rotPitchOffset = ReflectionUtil.getFieldOffset(rotPitchField);
            onGroundOffset = ReflectionUtil.getFieldOffset(onGroundField);
            lastTickPosXOffset = ReflectionUtil.getFieldOffset(lastTickPosXField);
            lastTickPosYOffset = ReflectionUtil.getFieldOffset(lastTickPosYField);
            lastTickPosZOffset = ReflectionUtil.getFieldOffset(lastTickPosZField);
            entityWidthOffset = ReflectionUtil.getFieldOffset(entityWidthField);
            entityHeightOffset = ReflectionUtil.getFieldOffset(entityHeightField);
            fallDistanceOffset = ReflectionUtil.getFieldOffset(fallDistanceField);
            
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
            getHealthMethod = ReflectionUtil.findMethod(livingClass, new String[]{"getHealth", "func_110143_aJ", "bn"});
            getMaxHealthMethod = ReflectionUtil.findMethod(livingClass, new String[]{"getMaxHealth", "func_110138_aP", "bI"});
            
            swingProgressOffset = ReflectionUtil.getFieldOffset(swingProgressField);
            isSwingInProgressOffset = ReflectionUtil.getFieldOffset(isSwingInProgressField);
            hurtTimeOffset = ReflectionUtil.getFieldOffset(hurtTimeField);
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

    @Override public double getX() { return ReflectionUtil.getDoubleFast(rawEntity, posXOffset, posXField); }
    @Override public double getY() { return ReflectionUtil.getDoubleFast(rawEntity, posYOffset, posYField); }
    @Override public double getZ() { return ReflectionUtil.getDoubleFast(rawEntity, posZOffset, posZField); }
    @Override public double getPrevX() { return ReflectionUtil.getDoubleFast(rawEntity, prevPosXOffset, prevPosXField); }
    @Override public double getPrevY() { return ReflectionUtil.getDoubleFast(rawEntity, prevPosYOffset, prevPosYField); }
    @Override public double getPrevZ() { return ReflectionUtil.getDoubleFast(rawEntity, prevPosZOffset, prevPosZField); }
    @Override public double getLastTickX() { return ReflectionUtil.getDoubleFast(rawEntity, lastTickPosXOffset, lastTickPosXField); }
    @Override public double getLastTickY() { return ReflectionUtil.getDoubleFast(rawEntity, lastTickPosYOffset, lastTickPosYField); }
    @Override public double getLastTickZ() { return ReflectionUtil.getDoubleFast(rawEntity, lastTickPosZOffset, lastTickPosZField); }
    @Override public float getWidth() { return ReflectionUtil.getFloatFast(rawEntity, entityWidthOffset, entityWidthField); }
    @Override public float getHeight() { return ReflectionUtil.getFloatFast(rawEntity, entityHeightOffset, entityHeightField); }
    @Override public float getYaw() { return ReflectionUtil.getFloatFast(rawEntity, rotYawOffset, rotYawField); }
    @Override public float getPitch() { return ReflectionUtil.getFloatFast(rawEntity, rotPitchOffset, rotPitchField); }
    @Override public boolean isOnGround() { return ReflectionUtil.getBoolFast(rawEntity, onGroundOffset, onGroundField); }
    
    @Override
    public boolean isInvisible() {
        try { return isInvisibleMethod != null && (boolean) isInvisibleMethod.invoke(rawEntity); } catch (Exception e) { return false; }
    }

    @Override public double getMotionX() { return ReflectionUtil.getDoubleFast(rawEntity, motionXOffset, motionXField); }
    @Override public double getMotionY() { return ReflectionUtil.getDoubleFast(rawEntity, motionYOffset, motionYField); }
    @Override public double getMotionZ() { return ReflectionUtil.getDoubleFast(rawEntity, motionZOffset, motionZField); }
    @Override public float getSwingProgress() { return ReflectionUtil.getFloatFast(rawEntity, swingProgressOffset, swingProgressField); }
    @Override public boolean isSwingInProgress() { return ReflectionUtil.getBoolFast(rawEntity, isSwingInProgressOffset, isSwingInProgressField); }
    @Override public int getHurtTime() { return ReflectionUtil.getIntFast(rawEntity, hurtTimeOffset, hurtTimeField); }
    @Override public float getFallDistance() { return ReflectionUtil.getFloatFast(rawEntity, fallDistanceOffset, fallDistanceField); }

    @Override
    public float getHealth() {
        if (!isLiving()) return 20f;
        
        if (getHealthMethod != null) {
            try { 
                return (float) getHealthMethod.invoke(rawEntity); 
            } catch (Exception e) {}
        }
        return 20f;
    }

    @Override
    public float getMaxHealth() {
        if (!isLiving()) return 20f;

        if (getMaxHealthMethod != null) {
            try { 
                return (float) getMaxHealthMethod.invoke(rawEntity); 
            } catch (Exception e) {}
        }
        return 20f;
    }

    @Override public void setYaw(float yaw) { ReflectionUtil.setFloatFast(rawEntity, rotYawOffset, rotYawField, yaw); }
    @Override public void setPitch(float pitch) { ReflectionUtil.setFloatFast(rawEntity, rotPitchOffset, rotPitchField, pitch); }
    @Override public void setMotionX(double x) { ReflectionUtil.setDoubleFast(rawEntity, motionXOffset, motionXField, x); }
    @Override public void setMotionY(double y) { ReflectionUtil.setDoubleFast(rawEntity, motionYOffset, motionYField, y); }
    @Override public void setMotionZ(double z) { ReflectionUtil.setDoubleFast(rawEntity, motionZOffset, motionZField, z); }
}

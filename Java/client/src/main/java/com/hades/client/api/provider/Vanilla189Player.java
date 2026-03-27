package com.hades.client.api.provider;

import com.hades.client.api.interfaces.IPlayer;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Vanilla189Player implements IPlayer {

    private final Class<?> minecraftClass;
    private final Method getMinecraftMethod;
    private final Field thePlayerField;

    // Entity fields
    private Field posXField, posYField, posZField;
    private Field prevPosXField, prevPosYField, prevPosZField;
    private Field motionXField, motionYField, motionZField;
    private Field rotYawField, prevRotationYawField;
    private Field rotPitchField, prevRotationPitchField;
    private Field onGroundField;
    private Field lastTickPosXField, lastTickPosYField, lastTickPosZField;
    private Field entityWidthField, entityHeightField;
    private Field hurtTimeField;
    private Field moveForwardField, moveStrafingField;

    private Method getHealthMethod;
    private Method getMaxHealthMethod;
    private Method isInvisibleMethod;
    private Method setAnglesMethod;
    public static Method setSprintingMethod;
    private Method isSprintingMethod;
    private Method getEyeHeightMethod;
    private Method getItemInUseDurationMethod;
    private Method isSneakingMethod; // Added

    private Field swingProgressField;
    private Field isSwingInProgressField;
    private Field collidedHorizontallyField;

    private Method getUUIDMethod;

    private Method getCurrentItemMethod;
    private Class<?> itemSwordClass;
    private Class<?> itemAxeClass;
    private Class<?> itemBowClass;

    private boolean cached = false;

    public Vanilla189Player() {
        minecraftClass = ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
        getMinecraftMethod = ReflectionUtil.findMethod(minecraftClass,
                new String[] { "A", "getMinecraft", "func_71410_x" });
        thePlayerField = ReflectionUtil.findField(minecraftClass, "h", "thePlayer", "field_71439_g");
    }

    private Object getPlayer() {
        try {
            Object mc = getMinecraftMethod != null ? getMinecraftMethod.invoke(null) : null;
            return mc != null && thePlayerField != null ? thePlayerField.get(mc) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void cacheFields() {
        if (cached)
            return;
        Class<?> entityClass = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");
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
            prevRotationYawField = ReflectionUtil.findField(entityClass, "A", "prevRotationYaw", "field_70126_B");
            rotPitchField = ReflectionUtil.findField(entityClass, "z", "rotationPitch", "field_70125_A");
            prevRotationPitchField = ReflectionUtil.findField(entityClass, "B", "prevRotationPitch", "field_70127_C");
            onGroundField = ReflectionUtil.findField(entityClass, "C", "onGround", "field_70122_E");
            lastTickPosXField = ReflectionUtil.findField(entityClass, "m", "lastTickPosX", "field_70142_S");
            lastTickPosYField = ReflectionUtil.findField(entityClass, "n", "lastTickPosY", "field_70137_T");
            lastTickPosZField = ReflectionUtil.findField(entityClass, "o", "lastTickPosZ", "field_70136_U");
            entityWidthField = ReflectionUtil.findField(entityClass, "I", "width", "field_70130_N");
            entityHeightField = ReflectionUtil.findField(entityClass, "J", "height", "field_70131_O");
            isInvisibleMethod = ReflectionUtil.findMethod(entityClass,
                    new String[] { "ay", "isInvisible", "func_82150_aj" });
            setAnglesMethod = ReflectionUtil.findMethod(entityClass, new String[] { "c", "setAngles", "func_70082_c" },
                    float.class, float.class);
            setSprintingMethod = ReflectionUtil.findMethod(entityClass,
                    new String[] { "d", "setSprinting", "func_70031_b" }, boolean.class);
            isSprintingMethod = ReflectionUtil.findMethod(entityClass,
                    new String[] { "aw", "isSprinting", "func_70051_ag" });
            getEyeHeightMethod = ReflectionUtil.findMethod(entityClass,
                    new String[] { "aQ", "getEyeHeight", "func_70047_e" });
            isSneakingMethod = ReflectionUtil.findMethod(entityClass,
                    new String[] { "aw", "isSneaking", "func_70093_af" });
            collidedHorizontallyField = ReflectionUtil.findField(entityClass, "E", "isCollidedHorizontally",
                    "field_70123_F");
            getUUIDMethod = ReflectionUtil.findMethod(entityClass,
                    new String[] { "aK", "getUniqueID", "func_110124_au" });
        }

        Class<?> playerClass = ReflectionUtil.findClass("net.minecraft.entity.player.EntityPlayer", "wn");
        if (playerClass != null) {
            getItemInUseDurationMethod = ReflectionUtil.findMethod(playerClass,
                    new String[] { "bT", "getItemInUseDuration", "func_71052_bv" });
        }

        Class<?> livingClass = ReflectionUtil.findClass("net.minecraft.entity.EntityLivingBase", "pr");
        if (livingClass != null) {
            hurtTimeField = ReflectionUtil.findField(livingClass, "at", "hurtTime", "field_70737_aN");
            moveForwardField = ReflectionUtil.findField(livingClass, "aZ", "moveForward", "field_70701_bs");
            moveStrafingField = ReflectionUtil.findField(livingClass, "aY", "moveStrafing", "field_70702_br");
            getHealthMethod = ReflectionUtil.findMethod(livingClass,
                    new String[] { "bn", "getHealth", "func_110143_aJ" });
            getMaxHealthMethod = ReflectionUtil.findMethod(livingClass,
                    new String[] { "bI", "getMaxHealth", "func_110138_aP" });
            swingProgressField = ReflectionUtil.findField(livingClass, "az", "swingProgress", "field_70733_aJ");
            isSwingInProgressField = ReflectionUtil.findField(livingClass, "ar", "isSwingInProgress", "field_82175_bq");
        }

        itemSwordClass = ReflectionUtil.findClass("net.minecraft.item.ItemSword", "ze", "zw");
        itemAxeClass = ReflectionUtil.findClass("net.minecraft.item.ItemAxe", "zq", "zx");
        itemBowClass = ReflectionUtil.findClass("net.minecraft.item.ItemBow", "zd", "zh");
        cached = true;
    }

    @Override
    public boolean isNull() {
        return getPlayer() == null;
    }

    @Override
    public Object getRaw() {
        return getPlayer();
    }

    @Override
    public int getEntityId() {
        try {
            Object p = getPlayer();
            if (p != null) {
                Method m = ReflectionUtil.findMethod(p.getClass(),
                        new String[] { "F", "getEntityId", "func_145782_y" });
                if (m != null)
                    return (int) m.invoke(p);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public boolean isLiving() {
        return true;
    }

    @Override
    public String getName() {
        cacheFields();
        try {
            Object p = getPlayer();
            if (p != null) {
                Method m = ReflectionUtil.findMethod(p.getClass(), new String[] { "e_", "getName", "func_70005_c_" });
                if (m != null)
                    return (String) m.invoke(p);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    @Override
    public java.util.UUID getUUID() {
        cacheFields();
        try {
            Object p = getPlayer();
            if (p != null && getUUIDMethod != null) {
                return (java.util.UUID) getUUIDMethod.invoke(p);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public float getDistanceToEntity(com.hades.client.api.interfaces.IEntity other) {
        if (other == null)
            return 0f;
        double dx = getX() - other.getX();
        double dy = getY() - other.getY();
        double dz = getZ() - other.getZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public double getX() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), posXField);
    }

    @Override
    public double getY() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), posYField);
    }

    @Override
    public double getZ() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), posZField);
    }

    @Override
    public double getPrevX() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), prevPosXField);
    }

    @Override
    public double getPrevY() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), prevPosYField);
    }

    @Override
    public double getPrevZ() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), prevPosZField);
    }

    @Override
    public double getLastTickX() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), lastTickPosXField);
    }

    @Override
    public double getLastTickY() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), lastTickPosYField);
    }

    @Override
    public double getLastTickZ() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), lastTickPosZField);
    }

    @Override
    public double getMotionX() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), motionXField);
    }

    @Override
    public double getMotionY() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), motionYField);
    }

    @Override
    public double getMotionZ() {
        cacheFields();
        return ReflectionUtil.getDoubleField(getPlayer(), motionZField);
    }

    @Override
    public void setMotionX(double x) {
        cacheFields();
        ReflectionUtil.setDoubleField(getPlayer(), motionXField, x);
    }

    @Override
    public void setMotionY(double y) {
        cacheFields();
        ReflectionUtil.setDoubleField(getPlayer(), motionYField, y);
    }

    @Override
    public void setMotionZ(double z) {
        cacheFields();
        ReflectionUtil.setDoubleField(getPlayer(), motionZField, z);
    }

    @Override
    public float getYaw() {
        cacheFields();
        return ReflectionUtil.getFloatField(getPlayer(), rotYawField);
    }

    @Override
    public float getPitch() {
        cacheFields();
        return ReflectionUtil.getFloatField(getPlayer(), rotPitchField);
    }

    @Override
    public void setYaw(float yaw) {
        cacheFields();
        ReflectionUtil.setFloatField(getPlayer(), rotYawField, yaw);
    }

    @Override
    public void setPitch(float pitch) {
        cacheFields();
        ReflectionUtil.setFloatField(getPlayer(), rotPitchField, pitch);
    }

    @Override
    public void setPrevYaw(float yaw) {
        cacheFields();
        ReflectionUtil.setFloatField(getPlayer(), prevRotationYawField, yaw);
    }

    @Override
    public void setPrevPitch(float pitch) {
        cacheFields();
        ReflectionUtil.setFloatField(getPlayer(), prevRotationPitchField, pitch);
    }

    @Override
    public float getHeight() {
        cacheFields();
        return ReflectionUtil.getFloatField(getPlayer(), entityHeightField);
    }

    @Override
    public float getWidth() {
        cacheFields();
        return ReflectionUtil.getFloatField(getPlayer(), entityWidthField);
    }

    @Override
    public boolean isOnGround() {
        cacheFields();
        return ReflectionUtil.getBoolField(getPlayer(), onGroundField);
    }

    @Override
    public boolean isSprinting() {
        cacheFields();
        try {
            if (isSprintingMethod != null)
                return (boolean) isSprintingMethod.invoke(getPlayer());
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public float getMoveForward() {
        cacheFields();
        return ReflectionUtil.getFloatField(getPlayer(), moveForwardField);
    }

    @Override
    public float getMoveStrafing() {
        cacheFields();
        return ReflectionUtil.getFloatField(getPlayer(), moveStrafingField);
    }

    private Field movementInputStrafeField;
    private Field movementInputForwardField;

    private boolean dumpedMovementInput = false;

    @Override
    public float getMovementInputForward(Object movementInput) {
        if (movementInput == null) return 0f;
        
        if (!dumpedMovementInput) {
            dumpedMovementInput = true;
            com.hades.client.util.HadesLogger.get().info("[HADES-DEBUG] Dumping MovementInput class: " + movementInput.getClass().getName());
            Class<?> curr = movementInput.getClass();
            while (curr != null && curr != Object.class) {
                com.hades.client.util.HadesLogger.get().info("[HADES-DEBUG] Class: " + curr.getName());
                for (Field f : curr.getDeclaredFields()) {
                    com.hades.client.util.HadesLogger.get().info("  -> Field: " + f.getName() + " (" + f.getType().getName() + ")");
                }
                curr = curr.getSuperclass();
            }
        }

        if (movementInputForwardField == null) {
            movementInputForwardField = ReflectionUtil.findField(movementInput.getClass(), "moveForward", "field_78900_b", "b");
            if (movementInputForwardField != null) movementInputForwardField.setAccessible(true);
        }
        return ReflectionUtil.getFloatField(movementInput, movementInputForwardField);
    }

    @Override
    public float getMovementInputStrafe(Object movementInput) {
        if (movementInput == null) return 0f;
        if (movementInputStrafeField == null) {
            movementInputStrafeField = ReflectionUtil.findField(movementInput.getClass(), "moveStrafe", "field_78902_a", "a");
            if (movementInputStrafeField != null) movementInputStrafeField.setAccessible(true);
        }
        return ReflectionUtil.getFloatField(movementInput, movementInputStrafeField);
    }

    @Override
    public void overrideMovementInput(Object movementInput, float forward, float strafe) {
        if (movementInput == null) return;
        if (movementInputStrafeField == null) {
            movementInputStrafeField = ReflectionUtil.findField(movementInput.getClass(), "moveStrafe", "field_78902_a", "a");
            if (movementInputStrafeField != null) movementInputStrafeField.setAccessible(true);
            com.hades.client.util.HadesLogger.get().info("[HADES-DEBUG] Resolved movementInputStrafeField: " + movementInputStrafeField);
        }
        if (movementInputForwardField == null) {
            movementInputForwardField = ReflectionUtil.findField(movementInput.getClass(), "moveForward", "field_78900_b", "b");
            if (movementInputForwardField != null) movementInputForwardField.setAccessible(true);
            com.hades.client.util.HadesLogger.get().info("[HADES-DEBUG] Resolved movementInputForwardField: " + movementInputForwardField);
        }
        
        com.hades.client.util.HadesLogger.get().info("[HADES-DEBUG] overrideMovementInput writing -> fw: " + forward + " st: " + strafe);
        ReflectionUtil.setFloatField(movementInput, movementInputStrafeField, strafe);
        ReflectionUtil.setFloatField(movementInput, movementInputForwardField, forward);
    }

    @Override
    public void setSprinting(boolean sprinting) {
        cacheFields();
        try {
            if (setSprintingMethod != null) {
                setSprintingMethod.invoke(getPlayer(), sprinting);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isInvisible() {
        cacheFields();
        try {
            if (isInvisibleMethod != null)
                return (boolean) isInvisibleMethod.invoke(getPlayer());
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public float getHealth() {
        cacheFields();
        try {
            return getHealthMethod != null ? (float) getHealthMethod.invoke(getPlayer()) : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    @Override
    public float getMaxHealth() {
        cacheFields();
        try {
            return getMaxHealthMethod != null ? (float) getMaxHealthMethod.invoke(getPlayer()) : 20f;
        } catch (Exception e) {
            return 20f;
        }
    }

    @Override
    public int getHurtTime() {
        cacheFields();
        return ReflectionUtil.getIntField(getPlayer(), hurtTimeField);
    }

    @Override
    public float getSwingProgress() {
        cacheFields();
        return ReflectionUtil.getFloatField(getPlayer(), swingProgressField);
    }

    @Override
    public boolean isSwingInProgress() {
        cacheFields();
        return ReflectionUtil.getBoolField(getPlayer(), isSwingInProgressField);
    }

    @Override
    public boolean isHoldingWeapon() {
        cacheFields();
        try {
            Object p = getPlayer();
            if (p == null)
                return false;

            Field inventoryField = ReflectionUtil.findField(p.getClass(), "bi", "inventory", "field_71071_by");
            if (inventoryField == null)
                return false;

            Object inventory = inventoryField.get(p);
            if (inventory == null)
                return false;

            if (getCurrentItemMethod == null) {
                getCurrentItemMethod = ReflectionUtil.findMethod(inventory.getClass(),
                        new String[] { "h", "getCurrentItem", "func_70448_g" });
            }
            if (getCurrentItemMethod != null) {
                Object itemStack = getCurrentItemMethod.invoke(inventory);
                if (itemStack == null)
                    return false;

                Method getItem = ReflectionUtil.findMethod(itemStack.getClass(),
                        new String[] { "b", "getItem", "func_77973_b" });
                if (getItem != null) {
                    Object item = getItem.invoke(itemStack);
                    if (item == null)
                        return false;
                    String className = item.getClass().getSimpleName().toLowerCase();
                    return className.contains("sword") || className.contains("axe") || className.contains("bow")
                            || (itemSwordClass != null && itemSwordClass.isInstance(item))
                            || (itemAxeClass != null && itemAxeClass.isInstance(item))
                            || (itemBowClass != null && itemBowClass.isInstance(item));
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public boolean isHoldingSword() {
        cacheFields();
        try {
            Object p = getPlayer();
            if (p == null)
                return false;

            Field inventoryField = ReflectionUtil.findField(p.getClass(), "bi", "inventory", "field_71071_by");
            if (inventoryField == null)
                return false;

            Object inventory = inventoryField.get(p);
            if (inventory == null)
                return false;

            if (getCurrentItemMethod == null) {
                getCurrentItemMethod = ReflectionUtil.findMethod(inventory.getClass(),
                        new String[] { "h", "getCurrentItem", "func_70448_g" });
            }
            if (getCurrentItemMethod != null) {
                Object itemStack = getCurrentItemMethod.invoke(inventory);
                if (itemStack == null)
                    return false;

                Method getItem = ReflectionUtil.findMethod(itemStack.getClass(),
                        new String[] { "b", "getItem", "func_77973_b" });
                if (getItem != null) {
                    Object item = getItem.invoke(itemStack);
                    if (item == null)
                        return false;
                    String className = item.getClass().getSimpleName().toLowerCase();
                    return className.contains("sword") || (itemSwordClass != null && itemSwordClass.isInstance(item));
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public boolean isSneaking() {
        cacheFields();
        try {
            return isSneakingMethod != null && (boolean) isSneakingMethod.invoke(getPlayer());
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    public boolean isCollidedHorizontally() {
        cacheFields();
        try {
            return collidedHorizontallyField != null && collidedHorizontallyField.getBoolean(getPlayer());
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    public float getEyeHeight() {
        cacheFields();
        try {
            return getEyeHeightMethod != null ? (float) getEyeHeightMethod.invoke(getPlayer()) : 1.62f;
        } catch (Exception e) {
            return 1.62f;
        }
    }

    @Override
    public int getItemInUseDuration() {
        cacheFields();
        try {
            return getItemInUseDurationMethod != null ? (int) getItemInUseDurationMethod.invoke(getPlayer()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void swingItem() {
        cacheFields();
        try {
            Object p = getPlayer();
            if (p != null) {
                Method swingMethod = ReflectionUtil.findMethod(p.getClass(),
                        new String[] { "bw", "swingItem", "func_71038_i" });
                if (swingMethod != null) {
                    swingMethod.invoke(p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void closeScreen() {
        try {
            Object p = getPlayer();
            if (p != null) {
                Method m = ReflectionUtil.findMethod(p.getClass(), new String[] { "m", "closeScreen", "func_71053_j" });
                if (m != null)
                    m.invoke(p);
            }
        } catch (Exception ignored) {
        }
    }
    
    @Override
    public com.hades.client.api.interfaces.IItemStack getHeldItem() {
        try {
            Object rawPlayer = getRaw();
            if (rawPlayer != null) {
                java.lang.reflect.Method method = com.hades.client.util.ReflectionUtil.findMethod(rawPlayer.getClass(), new String[]{"bz", "getCurrentEquippedItem", "func_70694_bm"});
                if (method != null) {
                    Object itemStack = method.invoke(rawPlayer);
                    if (itemStack != null) {
                        return new com.hades.client.api.interfaces.IItemStack(itemStack);
                    }
                }
            }
        } catch (Exception ignored) {}
        return new com.hades.client.api.interfaces.IItemStack(null);
    }
}

package com.hades.client.wrapper;

import com.hades.client.util.HadesLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Clean wrapper around obfuscated Minecraft 1.8.9 classes.
 * Uses direct class access where possible, reflection only where field names
 * vary.
 *
 * Obfuscated class mappings (vanilla 1.8.9):
 * ave = Minecraft
 * axu = GuiScreen
 * bew = EntityPlayerSP
 * bdb = WorldClient
 * avl = Timer
 * avh = GameSettings
 * pk = Entity
 * pr = EntityLivingBase
 * wn = EntityPlayer
 * bfk = EntityRenderer / RenderManager
 * bfv = Tessellator
 * bfw = WorldRenderer
 * bcy = NetHandlerPlayClient
 * ek = NetworkManager
 * ff = Packet
 * avr = ScaledResolution
 */
public class MC {

    private static final HadesLogger LOG = HadesLogger.get();

    // ── Cached reflection for fields that might have different names ──
    // Entity fields
    private static Class<?> entityClass;
    private static Field posXField;
    private static Field posYField;
    private static Field posZField;
    private static Field rotYawField;
    private static Field prevRotationYawField;
    private static Field rotPitchField;
    private static Field prevRotationPitchField;
    private static Field onGroundField;
    private static Field thePlayerField;
    private static Field theWorldField;
    private static Field timerField;
    private static Field gameSettingsField;
    private static Field currentScreenField;
    private static Field framebufferField;
    private static Field displayWidthField;
    private static Field displayHeightField;
    private static Field renderPartialTicksField;
    private static Field timerSpeedField;
    private static Field ingameGUIField;
    private static Field leftClickCounterField; // used by AutoClicker
    private static java.lang.reflect.Method clickMouseMethod; // clickMouse() for AutoClicker

    private static Method getMinecraftMethod;
    private static Class<?> minecraftClass;
    private static Object mcInstance;
    private static boolean initialized = false;

    /**
     * Initialize the wrapper. Must be called after MC is loaded.
     */
    public static void init() {
        try {
            minecraftClass = findClass("net.minecraft.client.Minecraft", "ave");
            if (minecraftClass == null) {
                LOG.error("Cannot find Minecraft class!");
                return;
            }

            // getMinecraft() - static method
            getMinecraftMethod = findMethod(minecraftClass, new String[] { "A", "getMinecraft", "func_71410_x" });
            if (getMinecraftMethod != null) {
                mcInstance = getMinecraftMethod.invoke(null);
            }

            if (mcInstance == null) {
                LOG.error("Minecraft instance is null!");
                return;
            }

            // Cache field references
            thePlayerField = findField(minecraftClass, "h", "thePlayer", "field_71439_g");
            theWorldField = findField(minecraftClass, "f", "theWorld", "field_71441_e");
            timerField = findField(minecraftClass, "Y", "timer", "field_71428_T");
            gameSettingsField = findField(minecraftClass, "t", "gameSettings", "field_71474_y");
            currentScreenField = findField(minecraftClass, "m", "currentScreen", "field_71462_r");
            framebufferField = findField(minecraftClass, "b", "framebuffer", "field_147124_at");
            displayWidthField = findField(minecraftClass, "d", "displayWidth", "field_71443_c");
            displayHeightField = findField(minecraftClass, "e", "displayHeight", "field_71440_d");
            ingameGUIField = findField(minecraftClass, "q", "ingameGUI", "field_71456_v");
            leftClickCounterField = findField(minecraftClass, "ag", "leftClickCounter", "field_71452_i");
            // clickMouse() fires one left-click (attack entity or break block)
            // MCP: clickMouse, obf: various — try known obf names (1.8.9 is "aw")
            clickMouseMethod = findMethod(minecraftClass, new String[] {
                    "aw", "aA", "aB", "aC", "aD", "aE", "aF",
                    "clickMouse", "func_147121_ag"
            });
            if (clickMouseMethod != null) {
                LOG.info("Found clickMouse method: " + clickMouseMethod.getName());
            } else {
                LOG.error("Could not find clickMouse method!");
            }

            // Timer fields
            if (timerField != null) {
                Object timer = timerField.get(mcInstance);
                if (timer != null) {
                    renderPartialTicksField = findField(timer.getClass(), "c", "renderPartialTicks", "field_74281_c");
                    timerSpeedField = findField(timer.getClass(), "d", "timerSpeed", "field_74278_d");
                }
            }

            initialized = true;
            LOG.info("MC wrapper initialized. MC class: " + minecraftClass.getName());
        } catch (Exception e) {
            LOG.error("MC wrapper init failed", e);
        }
    }

    // ══════════════════════════════════════════
    // Core accessors
    // ══════════════════════════════════════════

    /** Get the Minecraft instance */
    public static Object mc() {
        if (mcInstance == null && getMinecraftMethod != null) {
            try {
                mcInstance = getMinecraftMethod.invoke(null);
            } catch (Exception ignored) {
            }
        }
        return mcInstance;
    }

    /** Get the player (EntityPlayerSP) */
    public static Object player() {
        return getFieldValue(mc(), thePlayerField);
    }

    /** Get the world (WorldClient) */
    public static Object world() {
        return getFieldValue(mc(), theWorldField);
    }

    /** Get game settings */
    public static Object gameSettings() {
        return getFieldValue(mc(), gameSettingsField);
    }

    /** Get current screen (null if none) */
    public static Object currentScreen() {
        return getFieldValue(mc(), currentScreenField);
    }

    /** Get the game's Framebuffer */
    public static Object framebuffer() {
        return getFieldValue(mc(), framebufferField);
    }

    /** Check if player is in game */
    public static boolean isInGame() {
        return player() != null && world() != null;
    }

    /** Check if a GUI screen is open */
    public static boolean isInGui() {
        return currentScreen() != null;
    }

    /**
     * Trigger a left-click via MC's native clickMouse() method.
     * We also set leftClickCounter to 0 so the vanilla game loop doesn't block
     * subsequent legit clicks or hold-clicks.
     */
    public static void performClick() {
        try {
            if (leftClickCounterField != null) {
                leftClickCounterField.set(mc(), 0);
            }
            if (clickMouseMethod != null) {
                clickMouseMethod.invoke(mc());
                LOG.info("Invoked clickMouse successfully!");
            } else {
                LOG.error("Failed to click because clickMouseMethod is null!");
            }
        } catch (Exception e) {
            LOG.error("performClick exception", e);
        }
    }

    private static Field keyBindUseItemField;   // GameSettings.ag (keyBindUseItem)
    private static Field keyBindPressedField;   // KeyBinding.h (pressed boolean)
    private static boolean visualBlockActive = false;

    /**
     * Forces the client to visually perform the blocking animation.
     * 
     * LabyMod's SwordOldAnimation checks options.useItemInput().isDown()
     * which reads GameSettings.ag.h (keyBindUseItem.pressed).
     * We toggle that field to trigger/cancel the visual block animation.
     */
    public static void setVisuallyBlocking(boolean blocking) {
        try {
            if (blocking == visualBlockActive) return;
            Object mc = mc();
            if (mc == null) return;

            // Get GameSettings instance: Minecraft.gameSettings (Notch: t)
            if (keyBindUseItemField == null) {
                Field gameSettingsField = findField(mc.getClass(), "t", "gameSettings", "field_71474_y");
                if (gameSettingsField == null) {
                    LOG.error("setVisuallyBlocking: Could not find gameSettings field");
                    return;
                }
                Object gameSettings = gameSettingsField.get(mc);
                if (gameSettings == null) return;

                // GameSettings.ag is keyBindUseItem (avb type = KeyBinding)
                keyBindUseItemField = findField(gameSettings.getClass(), "ag", "keyBindUseItem", "field_74322_I");
                if (keyBindUseItemField == null) {
                    LOG.error("setVisuallyBlocking: Could not find keyBindUseItem field (ag)");
                    return;
                }
            }

            // Get GameSettings again each time (it doesn't change, but be safe)
            Field gameSettingsField = findField(mc.getClass(), "t", "gameSettings", "field_71474_y");
            Object gameSettings = gameSettingsField.get(mc);
            if (gameSettings == null) return;

            // Get the KeyBinding object for useItem
            Object keyBindUseItem = keyBindUseItemField.get(gameSettings);
            if (keyBindUseItem == null) return;

            // KeyBinding.h is the 'pressed' boolean
            if (keyBindPressedField == null) {
                keyBindPressedField = findField(keyBindUseItem.getClass(), "h", "pressed", "field_74513_e");
                if (keyBindPressedField == null) {
                    LOG.error("setVisuallyBlocking: Could not find pressed field (h) in KeyBinding");
                    return;
                }
            }

            keyBindPressedField.setBoolean(keyBindUseItem, blocking);
            visualBlockActive = blocking;
        } catch (Exception e) {
            LOG.error("setVisuallyBlocking exception", e);
        }
    }

    // ══════════════════════════════════════════
    // Key Control (for W-Tap / Sprint Reset)
    // ══════════════════════════════════════════

    private static Field keyBindForwardField;   // GameSettings.Y (keyBindForward)

    /**
     * Sets the pressed state of the forward (W) key.
     * GameSettings.Y is keyBindForward, KeyBinding.h is pressed.
     */
    public static void setKeyForwardPressed(boolean pressed) {
        try {
            Object mc = mc();
            if (mc == null) return;

            if (keyBindForwardField == null) {
                Field gameSettingsField = findField(mc.getClass(), "t", "gameSettings", "field_71474_y");
                if (gameSettingsField == null) return;
                Object gameSettings = gameSettingsField.get(mc);
                if (gameSettings == null) return;
                keyBindForwardField = findField(gameSettings.getClass(), "Y", "keyBindForward", "field_74351_w");
            }

            Field gameSettingsField = findField(mc.getClass(), "t", "gameSettings", "field_71474_y");
            Object gameSettings = gameSettingsField.get(mc);
            if (gameSettings == null) return;

            Object keyBindForward = keyBindForwardField.get(gameSettings);
            if (keyBindForward == null) return;

            // Reuse the cached keyBindPressedField from setVisuallyBlocking
            if (keyBindPressedField == null) {
                keyBindPressedField = findField(keyBindForward.getClass(), "h", "pressed", "field_74513_e");
            }
            if (keyBindPressedField != null) {
                keyBindPressedField.setBoolean(keyBindForward, pressed);
            }
        } catch (Exception e) {
            LOG.error("setKeyForwardPressed exception", e);
        }
    }

    /** Get the ingame GUI (GuiIngame) */
    public static Object ingameGUI() {
        return getFieldValue(mc(), ingameGUIField);
    }

    /** Set the ingame GUI (GuiIngame) */
    public static void setIngameGUI(Object gui) {
        try {
            if (ingameGUIField != null) {
                ingameGUIField.set(mc(), gui);
                LOG.info("Successfully set ingameGUI to " + (gui != null ? gui.getClass().getName() : "null"));
            }
        } catch (Exception e) {
            LOG.error("Failed to set ingameGUI", e);
        }
    }

    // ══════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════

    /** Display a GUI screen (pass null to close) */
    public static void displayScreen(Object screen) {
        try {
            Class<?> guiScreenClass = findClass("net.minecraft.client.gui.GuiScreen", "axu");
            Method m = findMethod(minecraftClass, new String[] { "a", "displayGuiScreen", "func_147108_a" },
                    guiScreenClass);
            if (m != null) {
                m.invoke(mc(), screen);
                LOG.info("displayScreen successfully invoked with screen: "
                        + (screen != null ? screen.getClass().getName() : "null"));
            } else {
                LOG.error("Failed to find displayScreen method in Minecraft class!");
            }
        } catch (Exception e) {
            LOG.error("Failed to display screen", e);
        }
    }

    public static int displayWidth() {
        return getIntField(mc(), displayWidthField);
    }

    public static int displayHeight() {
        return getIntField(mc(), displayHeightField);
    }

    /**
     * Get scaled resolution dimensions.
     * Returns [scaledWidth, scaledHeight, scaleFactor].
     */
    public static int[] scaledResolution() {
        try {
            Class<?> srClass = findClass("net.minecraft.client.gui.ScaledResolution", "avr");
            if (srClass == null)
                return new int[] { 854, 480, 2 };

            Object sr = srClass.getConstructor(minecraftClass).newInstance(mc());

            // Find the width/height/scale getter methods
            int scaledWidth = 0, scaledHeight = 0, scaleFactor = 2;
            for (Method m : srClass.getDeclaredMethods()) {
                if (m.getReturnType() == int.class && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    int val = (int) m.invoke(sr);
                    if (val > 100 && val < 3000) {
                        if (scaledWidth == 0)
                            scaledWidth = val;
                        else if (scaledHeight == 0)
                            scaledHeight = val;
                    } else if (val >= 1 && val <= 4) {
                        scaleFactor = val;
                    }
                }
            }
            if (scaledWidth == 0 || scaledHeight == 0) {
                return new int[] { 854, 480, 2 };
            }
            return new int[] { scaledWidth, scaledHeight, scaleFactor };
        } catch (Exception e) {
            return new int[] { 854, 480, 2 };
        }
    }

    // ══════════════════════════════════════════
    // Timer
    // ══════════════════════════════════════════

    public static float partialTicks() {
        try {
            Object timer = timerField != null ? timerField.get(mc()) : null;
            return timer != null && renderPartialTicksField != null
                    ? renderPartialTicksField.getFloat(timer)
                    : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    public static void setTimerSpeed(float speed) {
        try {
            Object timer = timerField != null ? timerField.get(mc()) : null;
            if (timer != null && timerSpeedField != null) {
                timerSpeedField.setFloat(timer, speed);
            }
        } catch (Exception ignored) {
        }
    }

    private static Class<?> c02PacketClass;
    private static Class<?> c02ActionEnum;

    public static void sendInteractPacket(Object targetEntity) {
        try {
            if (c02PacketClass == null) {
                c02PacketClass = findClass("net.minecraft.network.play.client.C02PacketUseEntity", "io");
                c02ActionEnum = findClass("net.minecraft.network.play.client.C02PacketUseEntity$Action", "io$a");
            }
            if (c02PacketClass != null && c02ActionEnum != null) {
                // INTERACT is usually the first enum (ordinal 0)
                Object interactAction = c02ActionEnum.getEnumConstants()[0]; 
                Class<?> entityClassRef = findClass("net.minecraft.entity.Entity", "pk");
                Constructor<?> c02Const = c02PacketClass.getConstructor(entityClassRef, c02ActionEnum);
                Object packet = c02Const.newInstance(targetEntity, interactAction);
                sendPacketDirect(packet);
            }
        } catch (Exception ignored) {}
    }

    private static Class<?> c08PacketClass;
    private static Class<?> itemStackClass;

    public static void sendBlockPacket(Object player) {
        try {
            if (c08PacketClass == null) {
                c08PacketClass = findClass("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement", "jo");
                itemStackClass = findClass("net.minecraft.item.ItemStack", "zx");
            }
            if (c08PacketClass != null && player != null) {
                if (getCurrentItemMethod == null) {
                    getCurrentItemMethod = findMethod(player.getClass(), new String[]{"bz", "getHeldItem", "func_70694_bm", "getCurrentEquippedItem"});
                }
                Object heldItem = null;
                if (getCurrentItemMethod != null) heldItem = getCurrentItemMethod.invoke(player);

                // C08PacketPlayerBlockPlacement(ItemStack stackIn) is available in 1.8.9
                Constructor<?> c08Const = c08PacketClass.getConstructor(itemStackClass);
                Object packet = c08Const.newInstance(heldItem);
                sendPacketDirect(packet);
            }
        } catch (Exception ignored) {}
    }

    private static Class<?> c07PacketClass;
    private static Class<?> c07ActionEnum;
    private static Class<?> blockPosClass;
    private static Class<?> enumFacingClass;

    public static void sendUnblockPacket() {
        try {
            if (c07PacketClass == null) {
                c07PacketClass = findClass("net.minecraft.network.play.client.C07PacketPlayerDigging", "jn");
                c07ActionEnum = findClass("net.minecraft.network.play.client.C07PacketPlayerDigging$Action", "jn$a");
                blockPosClass = findClass("net.minecraft.util.BlockPos", "cj");
                enumFacingClass = findClass("net.minecraft.util.EnumFacing", "cq");
            }
            if (c07PacketClass != null && c07ActionEnum != null && blockPosClass != null && enumFacingClass != null) {
                // RELEASE_USE_ITEM is usually the 6th enum value (ordinal 5)
                Object releaseAction = c07ActionEnum.getEnumConstants()[5];
                
                // new BlockPos(-1, -1, -1) or BlockPos.ORIGIN
                Constructor<?> bpConst = blockPosClass.getConstructor(int.class, int.class, int.class);
                Object dummyPos = bpConst.newInstance(-1, -1, -1);
                
                // EnumFacing.DOWN (ordinal 0)
                Object dummyFacing = enumFacingClass.getEnumConstants()[0];
                
                Constructor<?> c07Const = c07PacketClass.getConstructor(c07ActionEnum, blockPosClass, enumFacingClass);
                Object packet = c07Const.newInstance(releaseAction, dummyPos, dummyFacing);
                sendPacketDirect(packet);
            }
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════
    // Entity helpers
    // ══════════════════════════════════════════

    private static Field motionXField, motionYField, motionZField;
    private static Field prevPosXField, prevPosYField, prevPosZField;
    private static Field lastTickPosXField, lastTickPosYField, lastTickPosZField;
    private static Field entityHeightField;
    private static Field sprintingField;
    private static Field hurtTimeField;
    private static boolean entityFieldsCached = false;

    private static void cacheEntityFields() {
        if (entityFieldsCached)
            return;
        Class<?> entityClass = findClass("net.minecraft.entity.Entity", "pk");
        if (entityClass != null) {
            posXField = findField(entityClass, "s", "posX", "field_70165_t");
            posYField = findField(entityClass, "t", "posY", "field_70163_u");
            posZField = findField(entityClass, "u", "posZ", "field_70161_v");
            prevPosXField = findField(entityClass, "p", "prevPosX", "field_70169_q");
            prevPosYField = findField(entityClass, "q", "prevPosY", "field_70167_r");
            prevPosZField = findField(entityClass, "r", "prevPosZ", "field_70166_s");
            motionXField = findField(entityClass, "v", "motionX", "field_70159_w");
            motionYField = findField(entityClass, "w", "motionY", "field_70181_x");
            motionZField = findField(entityClass, "x", "motionZ", "field_70179_y");
                rotYawField = findField(entityClass, "y", "rotationYaw", "field_70177_z");
                prevRotationYawField = findField(entityClass, "A", "prevRotationYaw", "field_70126_B");
                rotPitchField = findField(entityClass, "z", "rotationPitch", "field_70125_A");
                prevRotationPitchField = findField(entityClass, "B", "prevRotationPitch", "field_70127_C");
            onGroundField = findField(entityClass, "C", "onGround", "field_70122_E");
            sprintingField = findField(entityClass, "ag", "sprinting", "field_70160_al");
            lastTickPosXField = findField(entityClass, "m", "lastTickPosX", "field_70142_S");
            lastTickPosYField = findField(entityClass, "n", "lastTickPosY", "field_70137_T");
            lastTickPosZField = findField(entityClass, "o", "lastTickPosZ", "field_70136_U");
            entityHeightField = findField(entityClass, "J", "height", "field_70131_O");
        }
        Class<?> livingClass = findClass("net.minecraft.entity.EntityLivingBase", "pr");
        if (livingClass != null) {
            hurtTimeField = findField(livingClass, "at", "hurtTime", "field_70737_aN");
        }
        entityFieldsCached = true;
    }

    // Position
    public static double playerX() {
        cacheEntityFields();
        return getDoubleField(player(), posXField);
    }

    public static double playerY() {
        cacheEntityFields();
        return getDoubleField(player(), posYField);
    }

    public static double playerZ() {
        cacheEntityFields();
        return getDoubleField(player(), posZField);
    }

    public static double entityX(Object e) {
        cacheEntityFields();
        return getDoubleField(e, posXField);
    }

    public static double entityY(Object e) {
        cacheEntityFields();
        return getDoubleField(e, posYField);
    }

    public static double entityZ(Object e) {
        cacheEntityFields();
        return getDoubleField(e, posZField);
    }

    // Previous tick position (for inter-frame interpolation)
    public static double entityPrevX(Object e) {
        cacheEntityFields();
        return getDoubleField(e, prevPosXField);
    }

    public static double entityPrevY(Object e) {
        cacheEntityFields();
        return getDoubleField(e, prevPosYField);
    }

    public static double entityPrevZ(Object e) {
        cacheEntityFields();
        return getDoubleField(e, prevPosZField);
    }

    // lastTickPos — used for render interpolation (different from prevPos!)
    public static double lastTickPosX(Object e) {
        cacheEntityFields();
        return getDoubleField(e, lastTickPosXField);
    }

    public static double lastTickPosY(Object e) {
        cacheEntityFields();
        return getDoubleField(e, lastTickPosYField);
    }

    public static double lastTickPosZ(Object e) {
        cacheEntityFields();
        return getDoubleField(e, lastTickPosZField);
    }

    // Entity height (bounding box height)
    public static float entityHeight(Object e) {
        cacheEntityFields();
        try {
            return entityHeightField != null ? entityHeightField.getFloat(e) : 1.8f;
        } catch (Exception ex) {
            return 1.8f;
        }
    }

    // Entity motion (velocity) — works on any entity, not just player
    public static double entityMotionX(Object e) {
        cacheEntityFields();
        return getDoubleField(e, motionXField);
    }

    public static double entityMotionY(Object e) {
        cacheEntityFields();
        return getDoubleField(e, motionYField);
    }

    public static double entityMotionZ(Object e) {
        cacheEntityFields();
        return getDoubleField(e, motionZField);
    }

    // Motion
    public static double motionX() {
        cacheEntityFields();
        return getDoubleField(player(), motionXField);
    }

    public static double motionY() {
        cacheEntityFields();
        return getDoubleField(player(), motionYField);
    }

    public static double motionZ() {
        cacheEntityFields();
        return getDoubleField(player(), motionZField);
    }

    public static void setMotionX(double v) {
        cacheEntityFields();
        setDoubleField(player(), motionXField, v);
    }

    public static void setMotionY(double v) {
        cacheEntityFields();
        setDoubleField(player(), motionYField, v);
    }

    public static void setMotionZ(double v) {
        cacheEntityFields();
        setDoubleField(player(), motionZField, v);
    }

    // Rotation
    public static float yaw(Object entity) {
        cacheEntityFields();
        return getFloatField(entity, rotYawField);
    }

    public static float yaw() {
        return yaw(player());
    }

    public static float pitch(Object entity) {
        cacheEntityFields();
        return getFloatField(entity, rotPitchField);
    }

    public static float pitch() {
        return pitch(player());
    }

    public static void setYaw(Object entity, float v) {
        cacheEntityFields();
        setFloatField(entity, rotYawField, v);
    }

    public static void setYaw(float v) {
        cacheEntityFields(); // Added cache call
        setFloatField(player(), rotYawField, v);
    }

    public static void setPrevYaw(float yaw) {
        cacheEntityFields(); // Added cache call
        setFloatField(player(), prevRotationYawField, yaw);
    }

    public static void setPitch(Object entity, float v) {
        cacheEntityFields();
        setFloatField(entity, rotPitchField, v);
    }

    public static void setPitch(float v) {
        cacheEntityFields(); // Added cache call
        setFloatField(player(), rotPitchField, v);
    }

    private static Method setAnglesMethod;

    public static void setAngles(Object entity, float yawDelta, float pitchDelta) {
        try {
            if (entity == null)
                return;
            if (setAnglesMethod == null) {
                // MCP mapping: setAngles, Notch: c (in Entity class)
                setAnglesMethod = findMethod(entity.getClass(), new String[]{ "c", "setAngles", "func_70082_c" }, float.class, float.class);
            }
            if (setAnglesMethod != null) {
                setAnglesMethod.invoke(entity, yawDelta, pitchDelta);
            }
        } catch (Exception ignored) {}
    }

    public static void setAngles(float yawDelta, float pitchDelta) {
        setAngles(player(), yawDelta, pitchDelta);
    }

    public static void setPrevPitch(float pitch) {
        cacheEntityFields(); // Added cache call
        setFloatField(player(), prevRotationPitchField, pitch);
    }

    // State
    public static boolean onGround(Object entity) {
        cacheEntityFields();
        return getBoolField(entity, onGroundField);
    }

    private static Method isInvisibleMethod;

    public static boolean isInvisible(Object entity) {
        try {
            if (entity == null) return false;
            if (isInvisibleMethod == null) {
                // MCP: isInvisible, Notch: ay, SRG: func_82150_aj
                isInvisibleMethod = findMethod(entity.getClass(), new String[]{"ay", "isInvisible", "func_82150_aj"});
            }
            if (isInvisibleMethod != null) {
                return (boolean) isInvisibleMethod.invoke(entity);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean onGround() {
        return onGround(player());
    }

    public static boolean sprinting(Object entity) {
        cacheEntityFields();
        return getBoolField(entity, sprintingField);
    }

    public static boolean sprinting() {
        return sprinting(player());
    }

    public static void setSprinting(Object entity, boolean v) {
        cacheEntityFields();
        setBoolField(entity, sprintingField, v);
        try {
            if (entity == null)
                return;
            Method m = findMethod(entity.getClass(), new String[] { "d", "setSprinting", "func_70031_b" },
                    boolean.class);
            if (m != null)
                m.invoke(entity, v);
        } catch (Exception ignored) {
        }
    }

    public static void setSprinting(boolean v) {
        setSprinting(player(), v);
    }

    public static int hurtTime(Object entity) {
        cacheEntityFields();
        return getIntField(entity, hurtTimeField);
    }

    // Health
    private static Method getHealthMethod;

    public static float health(Object entity) {
        try {
            if (entity == null)
                return 0f;
            if (getHealthMethod == null) {
                getHealthMethod = findMethod(entity.getClass(), new String[] { "bn", "getHealth", "func_110143_aJ" });
            }
            return getHealthMethod != null ? (float) getHealthMethod.invoke(entity) : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    // Max Health
    private static Method getMaxHealthMethod;

    public static float maxHealth(Object entity) {
        try {
            if (entity == null) return 0f;
            if (getMaxHealthMethod == null) {
                getMaxHealthMethod = findMethod(entity.getClass(), new String[]{"bI", "getMaxHealth", "func_110138_aP"});
            }
            return getMaxHealthMethod != null ? (float) getMaxHealthMethod.invoke(entity) : 20f;
        } catch (Exception e) {
            return 20f;
        }
    }

    // Entity Name
    private static Method getNameMethod;

    public static String entityName(Object entity) {
        try {
            if (entity == null) return "";
            if (getNameMethod == null) {
                getNameMethod = findMethod(entity.getClass(), new String[]{"e_", "getName", "func_70005_c_"});
            }
            return getNameMethod != null ? (String) getNameMethod.invoke(entity) : "";
        } catch (Exception e) {
            return "";
        }
    }

    // World entities
    @SuppressWarnings("unchecked")
    public static List<Object> loadedEntities() {
        try {
            Object w = world();
            if (w == null)
                return Collections.emptyList();
            Field f = findField(w.getClass(), "j", "loadedEntityList", "field_72996_f");
            return f != null ? (List<Object>) f.get(w) : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static boolean isEntityPlayer(Object entity) {
        Class<?> c = findClass("net.minecraft.entity.player.EntityPlayer", "wn");
        return c != null && c.isInstance(entity);
    }

    public static boolean isEntityLiving(Object entity) {
        Class<?> c = findClass("net.minecraft.entity.EntityLivingBase", "pr");
        return c != null && c.isInstance(entity);
    }

    public static float distanceTo(Object entity) {
        Object p = player();
        if (p == null || entity == null)
            return 0;
        double dx = entityX(p) - entityX(entity);
        double dy = entityY(p) - entityY(entity);
        double dz = entityZ(p) - entityZ(entity);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    private static Method getCurrentItemMethod;
    private static Class<?> itemSwordClass;

    public static boolean isHoldingSword(Object player) {
        try {
            if (player == null) return false;
            
            // 1. Get inventory field (EntityPlayer.inventory -> wm)
            Field inventoryField = findField(player.getClass(), "bi", "inventory", "field_71071_by");
            if (inventoryField == null) {
                HadesLogger.get().info("[AutoBlock DEBUG] inventory field NOT found!");
                return false;
            }
            Object inventory = inventoryField.get(player);
            if (inventory == null) return false;

            // 2. Get getCurrentItem method
            if (getCurrentItemMethod == null) {
                getCurrentItemMethod = findMethod(inventory.getClass(), new String[]{"h", "getCurrentItem", "func_70448_g"});
            }
            if (getCurrentItemMethod != null) {
                Object itemStack = getCurrentItemMethod.invoke(inventory);
                if (itemStack == null) return false;
                
                // Get item from stack -> getItem() -> b()
                Method getItem = findMethod(itemStack.getClass(), new String[]{"b", "getItem", "func_77973_b"});
                if (getItem != null) {
                    Object item = getItem.invoke(itemStack);
                    if (item == null) return false;
                    
                    if (itemSwordClass == null) {
                        itemSwordClass = findClass("net.minecraft.item.ItemSword", "zw");
                    }
                    boolean isSword = itemSwordClass != null && itemSwordClass.isInstance(item);
                    return isSword;
                } else {
                    HadesLogger.get().info("[AutoBlock DEBUG] getItem method NOT found in " + itemStack.getClass().getName());
                }
            } else {
                HadesLogger.get().info("[AutoBlock DEBUG] getCurrentItem method NOT found in " + inventory.getClass().getName());
            }
        } catch (Exception e) {
            HadesLogger.get().info("[AutoBlock DEBUG] isHoldingSword exception: " + e.getMessage());
        }
        return false;
    }

    private static Field swingProgressField;
    private static Field isSwingInProgressField;

    public static float swingProgress(Object entity) {
        try {
            if (entity == null) return 0f;
            if (swingProgressField == null) {
                // MCP: swingProgress, Notch: az (confirmed from pr.java dump)
                swingProgressField = findField(entity.getClass(), "az", "swingProgress", "field_70733_aJ");
            }
            if (swingProgressField != null) {
                return (float) swingProgressField.get(entity);
            }
        } catch (Exception ignored) {}
        return 0f;
    }

    public static boolean isSwingInProgress(Object entity) {
        try {
            if (entity == null) return false;
            if (isSwingInProgressField == null) {
                // MCP: isSwingInProgress, Notch: ar (confirmed from pr.java dump)
                isSwingInProgressField = findField(entity.getClass(), "ar", "isSwingInProgress", "field_82175_bq");
            }
            if (isSwingInProgressField != null) {
                return (boolean) isSwingInProgressField.get(entity);
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ══════════════════════════════════════════
    // Network
    // ══════════════════════════════════════════

    private static Field sendQueueField;
    private static Field networkManagerField;
    private static Field channelField;
    private static Method sendPacketMethod;

    public static Object getSendQueue() {
        try {
            Object p = player();
            if (p == null)
                return null;

            if (sendQueueField == null) {
                Class<?> playerClass = findClass("net.minecraft.client.entity.EntityPlayerSP", "bew");
                if (playerClass != null) {
                    sendQueueField = findField(playerClass, "a", "sendQueue", "field_71174_a");
                }
            }
            if (sendQueueField == null)
                return null;
            return sendQueueField.get(p);
        } catch (Exception e) {
            return null;
        }
    }

    public static io.netty.channel.Channel getNettyChannel() {
        try {
            Object sendQueue = getSendQueue();
            if (sendQueue == null)
                return null;

            if (networkManagerField == null) {
                Class<?> netHandlerClass = findClass("net.minecraft.client.network.NetHandlerPlayClient", "bcy");
                if (netHandlerClass != null) {
                    networkManagerField = findField(netHandlerClass, "c", "netManager", "field_147302_e");
                }
            }
            if (networkManagerField == null)
                return null;

            Object networkManager = networkManagerField.get(sendQueue);
            if (networkManager == null)
                return null;

            // Fallback to Reflection
            if (channelField == null) {
                Class<?> networkManagerClass = findClass("net.minecraft.network.NetworkManager", "ek");
                if (networkManagerClass != null) {
                    channelField = findField(networkManagerClass, "k", "channel", "field_150746_k");
                }
            }
            if (channelField == null)
                return null;

            return (io.netty.channel.Channel) channelField.get(networkManager);
        } catch (Exception e) {
            return null;
        }
    }

    public static void sendPacket(Object packet) {
        try {
            Object sendQueue = getSendQueue();
            if (sendQueue == null)
                return;

            if (sendPacketMethod == null) {
                Class<?> packetClass = findClass("net.minecraft.network.Packet", "ff");
                if (packetClass != null) {
                    sendPacketMethod = findMethod(sendQueue.getClass(),
                            new String[] { "a", "addToSendQueue", "func_147297_a" }, packetClass);
                }
            }
            if (sendPacketMethod != null)
                sendPacketMethod.invoke(sendQueue, packet);
        } catch (Exception e) {
            LOG.error("Failed to send packet", e);
        }
    }

    /**
     * Send a packet DIRECTLY to the server, bypassing HadesNettyHandler.
     * Use this when modules need to send raw packets without triggering
     * our own outbound packet event listeners.
     */
    public static void sendPacketDirect(Object packet) {
        try {
            io.netty.channel.Channel channel = getNettyChannel();
            if (channel != null && channel.isOpen()) {
                io.netty.channel.ChannelHandlerContext ctx = channel.pipeline().context("hades_packet_handler");
                if (ctx != null) {
                    ctx.writeAndFlush(packet);
                } else {
                    channel.writeAndFlush(packet);
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to send packet directly", e);
        }
    }

    // ══════════════════════════════════════════
    // GameSettings helpers
    // ══════════════════════════════════════════

    private static Field gammaField;

    public static float getGamma() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return 0f;
            if (gammaField == null) {
                gammaField = findField(gs.getClass(), "aA", "gammaSetting", "field_74333_Y");
            }
            return gammaField != null ? gammaField.getFloat(gs) : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    public static void setGamma(float gamma) {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return;
            if (gammaField == null) {
                gammaField = findField(gs.getClass(), "aA", "gammaSetting", "field_74333_Y");
            }
            if (gammaField != null)
                gammaField.setFloat(gs, gamma);
        } catch (Exception ignored) {
        }
    }

    private static Field thirdPersonViewField;

    public static int getThirdPersonView() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return 0;
            if (thirdPersonViewField == null) {
                thirdPersonViewField = findField(gs.getClass(), "ap", "thirdPersonView", "field_74320_O");
            }
            return thirdPersonViewField != null ? thirdPersonViewField.getInt(gs) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static Field mouseSensitivityField;

    /**
     * Get the current mouse sensitivity (0.0-1.0 range).
     * Used by RotationUtil.applyGCD() for accurate GCD calculation.
     * GameSettings field: mouseSensitivity / obf: aG / field_74341_c
     */
    public static float getMouseSensitivity() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return 0.5f;
            if (mouseSensitivityField == null) {
                mouseSensitivityField = findField(gs.getClass(), "aG", "mouseSensitivity", "field_74341_c");
            }
            return mouseSensitivityField != null ? mouseSensitivityField.getFloat(gs) : 0.5f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    // ══════════════════════════════════════════
    // Reflection helpers
    // ══════════════════════════════════════════

    public static Class<?> findClass(String... names) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String name : names) {
            try {
                if (cl != null)
                    return Class.forName(name, false, cl);
            } catch (ClassNotFoundException ignored) {
            }
        }
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        // Search thread classloaders
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            ClassLoader tcl = t.getContextClassLoader();
            if (tcl == null)
                continue;
            for (String name : names) {
                try {
                    return Class.forName(name, false, tcl);
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        return null;
    }

    public static Field findField(Class<?> clazz, String... names) {
        Class<?> current = clazz;
        while (current != null) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Method findMethod(Class<?> clazz, String[] names, Class<?>... params) {
        Class<?> current = clazz;
        while (current != null) {
            for (String name : names) {
                try {
                    Method m = current.getDeclaredMethod(name, params);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    // Field access helpers (safe, null-checked)
    public static Object getFieldValue(Object obj, Field f) {
        try {
            return obj != null && f != null ? f.get(obj) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static double getDoubleField(Object obj, Field f) {
        try {
            return obj != null && f != null ? f.getDouble(obj) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static float getFloatField(Object obj, Field f) {
        try {
            return obj != null && f != null ? f.getFloat(obj) : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    public static int getIntField(Object obj, Field field) {
        if (field == null)
            return 0;
        try {
            return field.getInt(obj);
        } catch (Exception e) {
            return 0;
        }
    }

    public static byte getByteField(Object obj, Field field) {
        if (field == null)
            return 0;
        try {
            return field.getByte(obj);
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean getBoolField(Object obj, Field f) {
        try {
            return obj != null && f != null && f.getBoolean(obj);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setDoubleField(Object obj, Field f, double v) {
        try {
            if (obj != null && f != null)
                f.setDouble(obj, v);
        } catch (Exception ignored) {
        }
    }

    public static void setFloatField(Object obj, Field f, float v) {
        try {
            if (obj != null && f != null)
                f.setFloat(obj, v);
        } catch (Exception ignored) {
        }
    }

    public static void setBoolField(Object obj, Field f, boolean v) {
        try {
            if (obj != null && f != null)
                f.setBoolean(obj, v);
        } catch (Exception ignored) {
        }
    }

    // ══════════════════════════════════════════
    // Velocity Packet Helpers (S12 & S27)
    // ══════════════════════════════════════════

    private static Class<?> s12PacketClass;
    private static Field s12EntityIdField;
    private static Field s12MotionXField;
    private static Field s12MotionYField;
    private static Field s12MotionZField;

    public static boolean isS12Packet(Object packet) {
        try {
            if (s12PacketClass == null) {
                s12PacketClass = findClass("net.minecraft.network.play.server.S12PacketEntityVelocity", "iv");
            }
            return s12PacketClass != null && s12PacketClass.isInstance(packet);
        } catch (Exception e) {
            return false;
        }
    }

    public static int getS12EntityId(Object packet) {
        try {
            if (s12EntityIdField == null && s12PacketClass != null) {
                s12EntityIdField = findField(s12PacketClass, "a", "entityID", "field_149417_a");
            }
            return s12EntityIdField != null ? s12EntityIdField.getInt(packet) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static void scaleS12Velocity(Object packet, double horizontal, double vertical) {
        try {
            if (s12MotionXField == null && s12PacketClass != null) {
                s12MotionXField = findField(s12PacketClass, "b", "motionX", "field_149415_b");
                s12MotionYField = findField(s12PacketClass, "c", "motionY", "field_149416_c");
                s12MotionZField = findField(s12PacketClass, "d", "motionZ", "field_149414_d");
            }

            if (s12MotionXField != null) {
                int mx = s12MotionXField.getInt(packet);
                s12MotionXField.setInt(packet, (int) (mx * horizontal));
            }
            if (s12MotionYField != null) {
                int my = s12MotionYField.getInt(packet);
                s12MotionYField.setInt(packet, (int) (my * vertical));
            }
            if (s12MotionZField != null) {
                int mz = s12MotionZField.getInt(packet);
                s12MotionZField.setInt(packet, (int) (mz * horizontal));
            }
        } catch (Exception ignored) {
        }
    }

    private static Class<?> s27PacketClass;
    private static Field s27MotionXField;
    private static Field s27MotionYField;
    private static Field s27MotionZField;

    public static boolean isS27Packet(Object packet) {
        try {
            if (s27PacketClass == null) {
                s27PacketClass = findClass("net.minecraft.network.play.server.S27PacketExplosion", "jz");
            }
            return s27PacketClass != null && s27PacketClass.isInstance(packet);
        } catch (Exception e) {
            return false;
        }
    }

    public static void scaleS27Velocity(Object packet, double horizontal, double vertical) {
        try {
            if (s27MotionXField == null && s27PacketClass != null) {
                s27MotionXField = findField(s27PacketClass, "f", "field_149152_f"); // motionX
                s27MotionYField = findField(s27PacketClass, "g", "field_149153_g"); // motionY
                s27MotionZField = findField(s27PacketClass, "h", "field_149159_h"); // motionZ
            }

            if (s27MotionXField != null) {
                float mx = s27MotionXField.getFloat(packet);
                s27MotionXField.setFloat(packet, (float) (mx * horizontal));
            }
            if (s27MotionYField != null) {
                float my = s27MotionYField.getFloat(packet);
                s27MotionYField.setFloat(packet, (float) (my * vertical));
            }
            if (s27MotionZField != null) {
                float mz = s27MotionZField.getFloat(packet);
                s27MotionZField.setFloat(packet, (float) (mz * horizontal));
            }
        } catch (Exception ignored) {
        }
    }
}

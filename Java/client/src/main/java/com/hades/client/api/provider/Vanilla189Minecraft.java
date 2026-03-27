package com.hades.client.api.provider;

import com.hades.client.api.interfaces.IMinecraft;
import com.hades.client.util.ReflectionUtil;
import com.hades.client.util.HadesLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Vanilla189Minecraft implements IMinecraft {

    private final Class<?> minecraftClass;
    private final Method getMinecraftMethod;
    private Object mcInstance;

    private Field currentScreenField;
    private Field displayWidthField;
    private Field displayHeightField;
    private Field timerField;
    private Field gameSettingsField;
    private Field leftClickCounterField;
    private Field objectMouseOverField;

    // Timer fields
    private Field renderPartialTicksField;
    private Field timerSpeedField;

    // GameSettings fields
    private Field gammaField;
    private Field mouseSensitivityField;
    private Field thirdPersonViewField;
    private Field keyBindUseItemField;
    private Field keyBindForwardField;
    private Field keyBindPressedField;
    private Field keyBindSneakField;
    private Field keyBindSprintField;

    private Method clickMouseMethod;
    private Method rightClickMouseMethod;
    private Method displayGuiScreenMethod;
    private Class<?> guiScreenClass;
    private Class<?> scaledResolutionClass;

    private Field playerControllerField;
    private Method attackEntityMethod;

    private Class<?> keyboardClass;
    private Method isKeyDownMethod;
    private Class<?> mouseClass;
    private Method isButtonDownMethod;
    private Method getXMethod;
    private Method getYMethod;

    public Vanilla189Minecraft() {
        minecraftClass = ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
        getMinecraftMethod = ReflectionUtil.findMethod(minecraftClass,
                new String[] { "A", "getMinecraft", "func_71410_x" });
        if (getMinecraftMethod != null) {
            try {
                mcInstance = getMinecraftMethod.invoke(null);
            } catch (Exception e) {
                HadesLogger.get().error("Vanilla189Minecraft failed to get mc instance");
            }
        }

        currentScreenField = ReflectionUtil.findField(minecraftClass, "m", "currentScreen", "field_71462_r");
        displayWidthField = ReflectionUtil.findField(minecraftClass, "d", "displayWidth", "field_71443_c");
        displayHeightField = ReflectionUtil.findField(minecraftClass, "e", "displayHeight", "field_71440_d");
        timerField = ReflectionUtil.findField(minecraftClass, "Y", "timer", "field_71428_T");
        gameSettingsField = ReflectionUtil.findField(minecraftClass, "t", "gameSettings", "field_71474_y");
        leftClickCounterField = ReflectionUtil.findField(minecraftClass, "ag", "leftClickCounter", "field_71452_i");
        objectMouseOverField = ReflectionUtil.findField(minecraftClass, "s", "objectMouseOver", "field_71476_x");

        clickMouseMethod = ReflectionUtil.findMethod(minecraftClass,
                new String[] { "aw", "clickMouse", "func_147121_ag" });
        rightClickMouseMethod = ReflectionUtil.findMethod(minecraftClass,
                new String[] { "ax", "rightClickMouse", "func_147121_ag" });

        guiScreenClass = ReflectionUtil.findClass("net.minecraft.client.gui.GuiScreen", "axu");
        displayGuiScreenMethod = ReflectionUtil.findMethod(minecraftClass,
                new String[] { "a", "displayGuiScreen", "func_147108_a" }, guiScreenClass);

        scaledResolutionClass = ReflectionUtil.findClass("net.minecraft.client.gui.ScaledResolution", "avr");

        playerControllerField = ReflectionUtil.findField(minecraftClass, "c", "playerController", "field_71442_b");
        Class<?> playerControllerClass = ReflectionUtil.findClass("net.minecraft.client.multiplayer.PlayerControllerMP",
                "bda");
        Class<?> entityPlayerClass = ReflectionUtil.findClass("net.minecraft.entity.player.EntityPlayer", "wn");
        Class<?> entityClass = ReflectionUtil.findClass("net.minecraft.entity.Entity", "pk");
        attackEntityMethod = ReflectionUtil.findMethod(playerControllerClass,
                new String[] { "a", "attackEntity", "func_78764_a" }, entityPlayerClass, entityClass);

        try {
            keyboardClass = Class.forName("org.lwjgl.input.Keyboard", true, minecraftClass.getClassLoader());
            isKeyDownMethod = keyboardClass.getMethod("isKeyDown", int.class);
            mouseClass = Class.forName("org.lwjgl.input.Mouse", true, minecraftClass.getClassLoader());
            isButtonDownMethod = mouseClass.getMethod("isButtonDown", int.class);
            getXMethod = mouseClass.getMethod("getX");
            getYMethod = mouseClass.getMethod("getY");
        } catch (Exception ignored) {
        }
    }

    private Object mc() {
        if (mcInstance == null && getMinecraftMethod != null) {
            try {
                mcInstance = getMinecraftMethod.invoke(null);
            } catch (Exception ignored) {
            }
        }
        return mcInstance;
    }

    private Object gameSettings() {
        return ReflectionUtil.getFieldValue(mc(), gameSettingsField);
    }


    @Override
    public boolean isNull() {
        return mc() == null;
    }

    @Override
    public Object getRaw() {
        return mc();
    }

    @Override
    public int displayWidth() {
        return ReflectionUtil.getIntField(mc(), displayWidthField);
    }

    @Override
    public int displayHeight() {
        return ReflectionUtil.getIntField(mc(), displayHeightField);
    }

    @Override
    public int[] scaledResolution() {
        try {
            if (scaledResolutionClass == null)
                return new int[] { 854, 480, 2 };
            Object sr = scaledResolutionClass.getConstructor(minecraftClass).newInstance(mc());
            int scaledWidth = 0, scaledHeight = 0, scaleFactor = 2;
            for (Method m : scaledResolutionClass.getDeclaredMethods()) {
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
            if (scaledWidth == 0 || scaledHeight == 0)
                return new int[] { 854, 480, 2 };
            return new int[] { scaledWidth, scaledHeight, scaleFactor };
        } catch (Exception e) {
            return new int[] { 854, 480, 2 };
        }
    }

    @Override
    public boolean isInGame() {
        // Fallback for Player vs World check, can be handled loosely here
        Object thePlayer = ReflectionUtil.getFieldValue(mc(),
                ReflectionUtil.findField(minecraftClass, "h", "thePlayer", "field_71439_g"));
        Object theWorld = ReflectionUtil.getFieldValue(mc(),
                ReflectionUtil.findField(minecraftClass, "f", "theWorld", "field_71441_e"));
        return thePlayer != null && theWorld != null;
    }

    @Override
    public boolean isInGui() {
        return ReflectionUtil.getFieldValue(mc(), currentScreenField) != null;
    }

    @Override
    public void displayScreen(Object screen) {
        try {
            if (displayGuiScreenMethod != null) {
                displayGuiScreenMethod.invoke(mc(), screen);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public float partialTicks() {
        try {
            Object timer = timerField != null ? timerField.get(mc()) : null;
            if (timer != null && renderPartialTicksField == null) {
                renderPartialTicksField = ReflectionUtil.findField(timer.getClass(), "c", "renderPartialTicks",
                        "field_74281_c");
            }
            return timer != null && renderPartialTicksField != null ? renderPartialTicksField.getFloat(timer) : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    @Override
    public void setTimerSpeed(float speed) {
        try {
            Object timer = timerField != null ? timerField.get(mc()) : null;
            if (timer != null && timerSpeedField == null) {
                timerSpeedField = ReflectionUtil.findField(timer.getClass(), "d", "timerSpeed", "field_74278_d");
            }
            if (timer != null && timerSpeedField != null) {
                timerSpeedField.setFloat(timer, speed);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public float getGamma() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return 0f;
            if (gammaField == null)
                gammaField = ReflectionUtil.findField(gs.getClass(), "aA", "gammaSetting", "field_74333_Y");
            return gammaField != null ? gammaField.getFloat(gs) : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    @Override
    public void setGamma(float gamma) {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return;
            if (gammaField == null)
                gammaField = ReflectionUtil.findField(gs.getClass(), "aA", "gammaSetting", "field_74333_Y");
            if (gammaField != null)
                gammaField.setFloat(gs, gamma);
        } catch (Exception ignored) {
        }
    }

    @Override
    public float getMouseSensitivity() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return 0.5f;
            if (mouseSensitivityField == null)
                mouseSensitivityField = ReflectionUtil.findField(gs.getClass(), "aG", "mouseSensitivity",
                        "field_74341_c");
            return mouseSensitivityField != null ? mouseSensitivityField.getFloat(gs) : 0.5f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    @Override
    public int getThirdPersonView() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return 0;
            if (thirdPersonViewField == null)
                thirdPersonViewField = ReflectionUtil.findField(gs.getClass(), "ap", "thirdPersonView",
                        "field_74320_O");
            return thirdPersonViewField != null ? thirdPersonViewField.getInt(gs) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean isKeyDown(int key) {
        try {
            if (isKeyDownMethod != null) {
                return (boolean) isKeyDownMethod.invoke(null, key);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public boolean isButtonDown(int button) {
        try {
            if (isButtonDownMethod != null) {
                return (boolean) isButtonDownMethod.invoke(null, button);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public int getMouseX() {
        try {
            if (getXMethod != null) {
                int rawX = (int) getXMethod.invoke(null);
                int[] sr = scaledResolution();
                return rawX * sr[0] / displayWidth();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @Override
    public int getMouseY() {
        try {
            if (getYMethod != null) {
                int rawY = (int) getYMethod.invoke(null);
                int height = displayHeight();
                int[] sr = scaledResolution();
                return sr[1] - (rawY * sr[1] / height) - 1;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @Override
    public void performClick() {
        try {
            if (leftClickCounterField != null)
                leftClickCounterField.set(mc(), 0);
            if (clickMouseMethod != null)
                clickMouseMethod.invoke(mc());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void performRightClick() {
        try {
            if (rightClickMouseMethod != null)
                rightClickMouseMethod.invoke(mc());
        } catch (Exception ignored) {
        }
    }


    @Override
    public void setKeyForwardPressed(boolean pressed) {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return;
            if (keyBindForwardField == null)
                keyBindForwardField = ReflectionUtil.findField(gs.getClass(), "Y", "keyBindForward", "field_74351_w");
            if (keyBindForwardField == null)
                return;

            Object keyBindForward = keyBindForwardField.get(gs);
            if (keyBindForward == null)
                return;

            if (keyBindPressedField == null)
                keyBindPressedField = ReflectionUtil.findField(keyBindForward.getClass(), "i", "h", "pressed",
                        "field_74513_e");
            if (keyBindPressedField != null)
                keyBindPressedField.setBoolean(keyBindForward, pressed);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isKeyForwardDown() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return false;
            if (keyBindForwardField == null)
                keyBindForwardField = ReflectionUtil.findField(gs.getClass(), "Y", "keyBindForward", "field_74351_w");
            if (keyBindForwardField == null)
                return false;

            Object keyBindForward = keyBindForwardField.get(gs);
            if (keyBindForward == null)
                return false;

            if (keyBindPressedField == null)
                keyBindPressedField = ReflectionUtil.findField(keyBindForward.getClass(), "i", "h", "pressed",
                        "field_74513_e");
            if (keyBindPressedField != null)
                return keyBindPressedField.getBoolean(keyBindForward);
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void setMouseOverBlock(double hitX, double hitY, double hitZ, int blockX, int blockY, int blockZ, int facingId) {
        try {
            Class<?> vec3Class = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.util.Vec3", "aui");
            Object vec3Obj = vec3Class.getConstructor(double.class, double.class, double.class).newInstance(hitX, hitY, hitZ);

            Class<?> blockPosClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.util.BlockPos", "cj");
            Object blockPosObj = blockPosClass.getConstructor(double.class, double.class, double.class).newInstance((double)blockX, (double)blockY, (double)blockZ);

            Class<?> enumFacingClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.util.EnumFacing", "cq");
            java.lang.reflect.Method getFrontMethod = com.hades.client.util.ReflectionUtil.findMethod(enumFacingClass, new String[]{"a", "getFront", "func_82600_a"}, int.class);
            Object enumFacingObj = getFrontMethod.invoke(null, facingId);

            Class<?> mopClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.util.MovingObjectPosition", "auh");
            Object mopObj = mopClass.getConstructor(vec3Class, enumFacingClass, blockPosClass).newInstance(vec3Obj, enumFacingObj, blockPosObj);

            objectMouseOverField.set(mc(), mopObj);
        } catch (Exception e) {}
    }

    @Override
    public void setKeySneakPressed(boolean pressed) {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return;
            if (keyBindSneakField == null)
                keyBindSneakField = ReflectionUtil.findField(gs.getClass(), "ad", "keyBindSneak", "field_74311_E");
            if (keyBindSneakField == null)
                return;

            Object keyBindSneak = keyBindSneakField.get(gs);
            if (keyBindSneak == null)
                return;

            // Universal Approach: use KeyBinding.setKeyBindState(int keyCode, boolean pressed)
            // This natively bypasses LabyMod ToggleSneak overrides because it correctly routes through 
            // the intended class pathways rather than modifying underlying dead fields.
            Method getCode = ReflectionUtil.findMethod(keyBindSneak.getClass(), new String[] { "i", "getKeyCode", "func_151463_i" });
            if (getCode != null) {
                int code = (int) getCode.invoke(keyBindSneak);
                Class<?> keyBindingClass = ReflectionUtil.findClass("net.minecraft.client.settings.KeyBinding", "avb");
                Method setBindState = ReflectionUtil.findMethod(keyBindingClass, new String[] { "a", "setKeyBindState", "func_74510_a" }, int.class, boolean.class);
                if (setBindState != null) {
                    setBindState.invoke(null, code, pressed);
                }
            }

            // Fallback to raw field manipulation just in case
            if (keyBindPressedField == null)
                keyBindPressedField = ReflectionUtil.findField(keyBindSneak.getClass(), "i", "h", "pressed",
                        "field_74513_e");
            if (keyBindPressedField != null)
                keyBindPressedField.setBoolean(keyBindSneak, pressed);
        } catch (Exception ignored) {}
    }

    @Override
    public void setKeySprintPressed(boolean pressed) {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return;
            if (keyBindSprintField == null)
                keyBindSprintField = ReflectionUtil.findField(gs.getClass(), "aq", "keyBindSprint", "field_151444_V");
            if (keyBindSprintField == null)
                return;

            Object keyBindSprint = keyBindSprintField.get(gs);
            if (keyBindSprint == null)
                return;

            if (keyBindPressedField == null)
                keyBindPressedField = ReflectionUtil.findField(keyBindSprint.getClass(), "i", "h", "pressed",
                        "field_74513_e");
            if (keyBindPressedField != null)
                keyBindPressedField.setBoolean(keyBindSprint, pressed);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isKeySprintPhysicallyDown() {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return false;
            if (keyBindSprintField == null)
                keyBindSprintField = ReflectionUtil.findField(gs.getClass(), "aq", "keyBindSprint", "field_151444_V");
            if (keyBindSprintField == null)
                return false;

            Object keyBindSprint = keyBindSprintField.get(gs);
            if (keyBindSprint == null)
                return false;

            Method getCode = ReflectionUtil.findMethod(keyBindSprint.getClass(),
                    new String[] { "i", "getKeyCode", "func_151463_i" });
            if (getCode != null) {
                int code = (int) getCode.invoke(keyBindSprint);
                return org.lwjgl.input.Keyboard.isKeyDown(code);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void setVisuallyBlocking(boolean blocking) {
        try {
            Object gs = gameSettings();
            if (gs == null)
                return;
            if (keyBindUseItemField == null)
                keyBindUseItemField = ReflectionUtil.findField(gs.getClass(), "ah", "keyBindUseItem", "field_74313_G");
            if (keyBindUseItemField == null)
                return;
            Object keyBindUseItem = keyBindUseItemField.get(gs);
            if (keyBindUseItem == null)
                return;
            if (keyBindPressedField == null)
                keyBindPressedField = ReflectionUtil.findField(keyBindUseItem.getClass(), "i", "h", "pressed", "field_74513_e");
            if (keyBindPressedField != null)
                keyBindPressedField.setBoolean(keyBindUseItem, blocking);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void attackEntity(com.hades.client.api.interfaces.IPlayer player, com.hades.client.api.interfaces.IEntity target) {
        try {
            if (player == null || target == null) return;
            if (playerControllerField == null || attackEntityMethod == null) return;
            
            Object pc = playerControllerField.get(mc());
            Object rawPlayer = player.getRaw();
            Object rawTarget = target.getRaw();
            
            if (rawPlayer != null && rawTarget != null) {
                attackEntityMethod.invoke(pc, rawPlayer, rawTarget);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int getMouseOverType() {
        try {
            if (objectMouseOverField == null) return 0;
            Object mouseOver = objectMouseOverField.get(mc());
            if (mouseOver == null) return 0;
            
            for (Field f : mouseOver.getClass().getDeclaredFields()) {
                if (f.getType().isEnum() && (f.getType().getSimpleName().contains("MovingObjectType") || f.getType().getSimpleName().equals("a"))) {
                    f.setAccessible(true);
                    Object typeEnum = f.get(mouseOver);
                    if (typeEnum != null) {
                        String name = ((Enum<?>) typeEnum).name();
                        if (name.equals("MISS")) return 0;
                        if (name.equals("BLOCK")) return 1;
                        if (name.equals("ENTITY")) return 2;
                    }
                }
            }
        } catch (Exception e) {}
        return 0;
    }

    @Override
    public Object getMouseOverEntity() {
        try {
            if (objectMouseOverField == null) return null;
            Object mouseOver = objectMouseOverField.get(mc());
            if (mouseOver == null) return null;
            
            for (Field f : mouseOver.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("Entity") || f.getType().getSimpleName().equals("pk")) {
                    f.setAccessible(true);
                    return f.get(mouseOver);
                }
            }
        } catch (Exception e) {}
        return null;
    }

    @Override
    public Object getMouseOverBlockPos() {
        try {
            if (objectMouseOverField == null) return null;
            Object mouseOver = objectMouseOverField.get(mc());
            if (mouseOver == null) return null;
            
            for (Field f : mouseOver.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("BlockPos") || f.getType().getSimpleName().equals("cj")) {
                    f.setAccessible(true);
                    return f.get(mouseOver);
                }
            }
        } catch (Exception e) {}
        return null;
    }

    @Override
    public boolean isTargetingBlockTop() {
        try {
            if (objectMouseOverField == null) return false;
            Object mouseOver = objectMouseOverField.get(mc());
            if (mouseOver == null) return false;

            Field sideHitField = ReflectionUtil.findField(mouseOver.getClass(), "b", "sideHit");
            if (sideHitField == null) return false;
            
            Object sideHit = sideHitField.get(mouseOver);
            if (sideHit == null) return false;
            
            return sideHit.toString().toUpperCase().contains("UP");
        } catch (Exception e) {
            return false;
        }
    }
}
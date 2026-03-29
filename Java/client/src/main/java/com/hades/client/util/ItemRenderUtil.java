package com.hades.client.util;

import com.hades.client.api.HadesAPI;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class ItemRenderUtil {

    private static Class<?> itemClass;
    private static Method getItemById;
    private static Class<?> itemStackClass;
    private static Constructor<?> itemStackConstructor;

    private static Method getRenderItem;
    private static Method renderModel;
    private static Method renderModelFlat;
    private static Class<?> renderHelperClass;
    private static Method enableItemLighting;
    private static Method disableItemLighting;

    private static Object cachedRenderItemObj;
    private static Object cachedMcObj;
    private static boolean initialized = false;

    public static void init() {
        if (initialized)
            return;
        initialized = true; // Set IMMEDIATELY to prevent infinite reflection loops if NPEs occur
        try {
            itemClass = ReflectionUtil.findClass("net.minecraft.item.Item", "zw");
            for (Method m : itemClass.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == int.class && m.getReturnType() == itemClass) {
                    getItemById = m;
                    break;
                }
            }

            itemStackClass = ReflectionUtil.findClass("net.minecraft.item.ItemStack", "zx");
            for (Constructor<?> c : itemStackClass.getConstructors()) {
                if (c.getParameterCount() == 3
                        && c.getParameterTypes()[0] == itemClass
                        && c.getParameterTypes()[1] == int.class
                        && c.getParameterTypes()[2] == int.class) {
                    itemStackConstructor = c;
                    break;
                }
            }

            Object mc = HadesAPI.mc.getRaw();
            for (Method m : mc.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && (m.getReturnType().getSimpleName().equals("bjh")
                        || m.getReturnType().getSimpleName().contains("RenderItem"))) {
                    getRenderItem = m;
                    break;
                }
            }
            if (getRenderItem != null) {
                getRenderItem.setAccessible(true);
                cachedMcObj = HadesAPI.mc.getRaw();
                cachedRenderItemObj = getRenderItem.invoke(cachedMcObj);

                renderModel = ReflectionUtil.findMethod(cachedRenderItemObj.getClass(),
                        new String[] { "a", "renderItemAndEffectIntoGUI", "func_175042_a" }, itemStackClass, int.class,
                        int.class);
                        
                renderModelFlat = ReflectionUtil.findMethod(cachedRenderItemObj.getClass(),
                        new String[] { "b", "renderItemIntoGUI", "func_180450_b" }, itemStackClass, int.class,
                        int.class);

                if (renderModel == null) {
                    renderModel = ReflectionUtil.findMethod(cachedRenderItemObj.getClass(),
                            new String[] { "b", "renderItemIntoGUI", "func_180450_b" }, itemStackClass, int.class,
                            int.class);
                }
            }

            renderHelperClass = ReflectionUtil.findClass("net.minecraft.client.renderer.RenderHelper", "bqs");
            if (renderHelperClass != null) {
                enableItemLighting = ReflectionUtil.findMethod(renderHelperClass,
                        new String[] { "c", "enableGUIStandardItemLighting", "func_74520_c" });
                disableItemLighting = ReflectionUtil.findMethod(renderHelperClass,
                        new String[] { "a", "disableStandardItemLighting", "func_74518_a" });
            }
        } catch (Exception e) {
            System.err.println("[Hades] ItemRenderUtil reflection init error: " + e.getMessage());
        }
    }

    public static Object createItemStack(int id, int meta) {
        init();
        try {
            if (getItemById != null && itemStackConstructor != null) {
                Object item = getItemById.invoke(null, id);
                if (item != null) {
                    return itemStackConstructor.newInstance(item, 1, meta);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void beginItemRender() {
        init();
        try {
            if (enableItemLighting != null)
                enableItemLighting.invoke(null);
        } catch (Exception ignored) {
        }
    }

    public static void beginItemRenderFlat() {
        init();
        try {
            // ONLY use GlStateManager or nothing. Avoid corrupting the GL state cache!
            // Without standard lighting, the items will render completely flat (2D shadows removed).
        } catch (Exception ignored) {
        }
    }

    public static void drawItemIconInner(Object rawItemStack, float x, float y) {
        if (rawItemStack == null || renderModel == null || cachedRenderItemObj == null)
            return;
        try {
            renderModel.invoke(cachedRenderItemObj, rawItemStack, (int) x, (int) y);
        } catch (Exception ignored) {
        }
    }

    public static void drawItemIconFlatInner(Object rawItemStack, float x, float y) {
        if (rawItemStack == null || renderModelFlat == null || cachedRenderItemObj == null)
            return;
        try {
            renderModelFlat.invoke(cachedRenderItemObj, rawItemStack, (int) x, (int) y);
        } catch (Exception ignored) {
        }
    }

    public static void endItemRender() {
        try {
            if (disableItemLighting != null)
                disableItemLighting.invoke(null);
            
            // Do NOT forcefully wipe GL states, it destroys the 3D rendering cache and causes player models to overdraw
        } catch (Exception ignored) {
        }
    }

    public static void drawItemIcon(Object rawItemStack, float x, float y) {
        beginItemRender();
        drawItemIconInner(rawItemStack, x, y);
        endItemRender();
    }

    public static void drawItemIconFlat(Object rawItemStack, float x, float y) {
        beginItemRenderFlat();
        drawItemIconFlatInner(rawItemStack, x, y);
        endItemRender();
    }

}

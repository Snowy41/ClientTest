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
    private static Class<?> renderHelperClass;
    private static Method enableItemLighting;
    private static Method disableItemLighting;

    private static Object cachedRenderItemObj;
    private static Object cachedMcObj;
    private static boolean initialized = false;

    public static void init() {
        if (initialized)
            return;
        try {
            itemClass = ReflectionUtil.findClass("net.minecraft.item.Item", "zw");
            for (Method m : itemClass.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class && m.getReturnType() == itemClass) {
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
                
                for (Method m : cachedRenderItemObj.getClass().getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 3 && params[0] == itemStackClass && params[1] == int.class
                            && params[2] == int.class) {
                        renderModel = m;
                        break;
                    }
                }
            }

            renderHelperClass = ReflectionUtil.findClass("net.minecraft.client.renderer.RenderHelper", "bqs");
            enableItemLighting = ReflectionUtil.findMethod(renderHelperClass,
                    new String[] { "c", "enableGUIStandardItemLighting", "func_74520_c" });
            disableItemLighting = ReflectionUtil.findMethod(renderHelperClass,
                    new String[] { "a", "disableStandardItemLighting", "func_74518_a" });

            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
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
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glEnable(32826); // GL_RESCALE_NORMAL

            if (enableItemLighting != null)
                enableItemLighting.invoke(null);
        } catch (Exception ignored) {}
    }

    public static void drawItemIconInner(Object rawItemStack, float x, float y) {
        if (rawItemStack == null || renderModel == null || cachedRenderItemObj == null)
            return;
        try {
            renderModel.invoke(cachedRenderItemObj, rawItemStack, (int) x, (int) y);
        } catch (Exception ignored) {}
    }

    public static void endItemRender() {
        try {
            if (disableItemLighting != null)
                disableItemLighting.invoke(null);

            org.lwjgl.opengl.GL11.glDisable(32826);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glPopMatrix();
        } catch (Exception ignored) {}
    }

    public static void drawItemIcon(Object rawItemStack, float x, float y) {
        beginItemRender();
        drawItemIconInner(rawItemStack, x, y);
        endItemRender();
    }
}

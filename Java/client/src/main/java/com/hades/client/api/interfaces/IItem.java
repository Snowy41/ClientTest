package com.hades.client.api.interfaces;

import com.hades.client.util.ReflectionUtil;
import java.lang.reflect.Method;

public class IItem {
    private final Object rawItem;
    private static Class<?> itemClass;
    private static Class<?> itemBlockClass;
    private static Method getIdMethod;
    private static Method getUnlocalizedNameMethod;

    public IItem(Object rawItem) {
        this.rawItem = rawItem;
        cacheFields();
    }

    private void cacheFields() {
        if (itemClass == null) {
            itemClass = ReflectionUtil.findClass("net.minecraft.item.Item", "zw");
            itemBlockClass = ReflectionUtil.findClass("net.minecraft.item.ItemBlock", "yo");
            
            if (itemClass != null) {
                // static int getIdFromItem(Item)
                for (Method m : itemClass.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) 
                        && m.getParameterCount() == 1 
                        && m.getParameterTypes()[0] == itemClass 
                        && m.getReturnType() == int.class) {
                        getIdMethod = m;
                        getIdMethod.setAccessible(true);
                        break;
                    }
                }
                
                getUnlocalizedNameMethod = ReflectionUtil.findMethod(itemClass, new String[]{"a", "getUnlocalizedName", "func_77658_a"});
            }
        }
    }

    public boolean isBlock() {
        if (rawItem == null || itemBlockClass == null) return false;
        return itemBlockClass.isInstance(rawItem);
    }

    public boolean isNull() {
        return rawItem == null;
    }

    public int getId() {
        if (rawItem == null || getIdMethod == null) return 0;
        try {
            return (int) getIdMethod.invoke(null, rawItem);
        } catch (Exception e) {}
        return 0;
    }

    public String getUnlocalizedName() {
        if (rawItem == null || getUnlocalizedNameMethod == null) return "unknown";
        try {
            return (String) getUnlocalizedNameMethod.invoke(rawItem);
        } catch (Exception e) {}
        return "unknown";
    }

    public Object getRaw() {
        return rawItem;
    }
}

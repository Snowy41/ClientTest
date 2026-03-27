package com.hades.client.api.interfaces;

import com.hades.client.util.ReflectionUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class IItemStack {
    private final Object rawStack;
    private static Class<?> itemStackClass;
    private static Class<?> itemClass;
    
    private static Field stackSizeField;
    private static Field itemDamageField;
    private static Method getItemMethod;
    private static Method getDisplayNameMethod;
    private static Method getMaxDamageMethod;
    private static Method getMaxStackSizeMethod;
    private static Class<?> enchantmentHelperClass;
    private static Method getEnchantmentLevelMethod;
    
    private static Class<?> blockClass;
    private static Method getStrVsBlockMethod;

    public IItemStack(Object rawStack) {
        this.rawStack = rawStack;
        cacheFields();
    }

    private void cacheFields() {
        if (itemStackClass == null) {
            itemStackClass = ReflectionUtil.findClass("net.minecraft.item.ItemStack", "zx");
            itemClass = ReflectionUtil.findClass("net.minecraft.item.Item", "zw");
            
            if (itemStackClass != null) {
                stackSizeField = ReflectionUtil.findField(itemStackClass, "stackSize", "b", "field_77994_a");
                itemDamageField = ReflectionUtil.findField(itemStackClass, "f", "itemDamage", "field_77991_e");
                
                // Method returning Item
                if (itemClass != null) {
                    for (Method m : itemStackClass.getDeclaredMethods()) {
                        if (m.getParameterCount() == 0 && m.getReturnType() == itemClass) {
                            getItemMethod = m;
                            getItemMethod.setAccessible(true);
                            break;
                        }
                    }
                }
                
                getDisplayNameMethod = ReflectionUtil.findMethod(itemStackClass, new String[]{"q", "getDisplayName", "func_82833_r"});
                if (getDisplayNameMethod == null) {
                    // Fallback string method with 0 args (sometimes q)
                    for (Method m : itemStackClass.getDeclaredMethods()) {
                        if (m.getParameterCount() == 0 && m.getReturnType() == String.class && m.getName().equals("q")) {
                            getDisplayNameMethod = m;
                            getDisplayNameMethod.setAccessible(true);
                            break;
                        }
                    }
                }
                
                getMaxDamageMethod = ReflectionUtil.findMethod(itemStackClass, new String[]{"j", "getMaxDamage", "func_77976_d"});
                getMaxStackSizeMethod = ReflectionUtil.findMethod(itemStackClass, new String[]{"c", "getMaxStackSize", "func_77978_p"});
            }
            
            enchantmentHelperClass = ReflectionUtil.findClass("net.minecraft.enchantment.EnchantmentHelper", "ack");
            if (enchantmentHelperClass != null && itemStackClass != null) {
                getEnchantmentLevelMethod = ReflectionUtil.findMethod(enchantmentHelperClass, new String[]{"getEnchantmentLevel", "a", "func_77506_a"}, int.class, itemStackClass);
            }
            
            blockClass = ReflectionUtil.findClass("net.minecraft.block.Block", "afh", "aky");
            if (blockClass != null && itemStackClass != null) {
                for (Method m : itemStackClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == blockClass && m.getReturnType() == float.class) {
                        getStrVsBlockMethod = m;
                        getStrVsBlockMethod.setAccessible(true);
                        break;
                    }
                }
            }
        }
    }

    public boolean isNull() {
        return rawStack == null;
    }

    public int getStackSize() {
        if (isNull() || stackSizeField == null) return 0;
        try {
            return stackSizeField.getInt(rawStack);
        } catch (Exception e) {}
        return 0;
    }

    public int getEnchantmentLevel(int enchId) {
        if (isNull() || getEnchantmentLevelMethod == null) return 0;
        try {
            return (int) getEnchantmentLevelMethod.invoke(null, enchId, rawStack);
        } catch (Exception e) {}
        return 0;
    }

    public int getDamage() {
        if (isNull() || itemDamageField == null) return 0;
        try {
            return itemDamageField.getInt(rawStack);
        } catch (Exception e) {}
        return 0;
    }

    public int getMaxDamage() {
        if (isNull() || getMaxDamageMethod == null) return 0;
        try {
            return (int) getMaxDamageMethod.invoke(rawStack);
        } catch (Exception e) {}
        return 0;
    }

    public int getMaxStackSize() {
        if (isNull() || getMaxStackSizeMethod == null) return 64; // Default safe assumption
        try {
            return (int) getMaxStackSizeMethod.invoke(rawStack);
        } catch (Exception e) {}
        return 64;
    }

    public IItem getItem() {
        if (isNull() || getItemMethod == null) return new IItem(null);
        try {
            return new IItem(getItemMethod.invoke(rawStack));
        } catch (Exception e) {}
        return new IItem(null);
    }

    public String getDisplayName() {
        if (isNull() || getDisplayNameMethod == null) return "Unknown Item";
        try {
            return (String) getDisplayNameMethod.invoke(rawStack);
        } catch (Exception e) {}
        return "Unknown Item";
    }

    public Object getRaw() {
        return rawStack;
    }

    public float getStrVsBlock(Object block) {
        if (isNull() || getStrVsBlockMethod == null || block == null) return 1.0f;
        try {
            return (float) getStrVsBlockMethod.invoke(rawStack, block);
        } catch (Exception e) {}
        return 1.0f;
    }

    public float getDamageVsEntity() {
        if (isNull()) return 1.0f;
        String name = getDisplayName().toLowerCase();
        if (name.contains("wood") || name.contains("gold")) return 4.0f;
        if (name.contains("stone")) return 5.0f;
        if (name.contains("iron")) return 6.0f;
        if (name.contains("diamond")) return 7.0f;
        return 1.0f;
    }
}

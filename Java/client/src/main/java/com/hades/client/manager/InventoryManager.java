package com.hades.client.manager;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.api.interfaces.IItem;
import com.hades.client.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

public class InventoryManager {
    private static InventoryManager instance;

    // Reflection Caches
    private static Class<?> mcClass;
    private static Class<?> playerClass;
    private static Class<?> playerControllerClass;
    private static Class<?> inventoryPlayerClass;

    private static Field inventoryField;
    private static Field mainInventoryField;
    private static Field armorInventoryField;
    private static Field currentItemField;
    
    private static Field playerControllerField;
    private static Method windowClickMethod;

    private InventoryManager() {
        cacheFields();
    }

    public static InventoryManager getInstance() {
        if (instance == null) {
            instance = new InventoryManager();
        }
        return instance;
    }

    private void cacheFields() {
        if (mcClass != null) return;
        
        mcClass = ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
        playerClass = ReflectionUtil.findClass("net.minecraft.entity.player.EntityPlayer", "wn", "ahd");
        inventoryPlayerClass = ReflectionUtil.findClass("net.minecraft.entity.player.InventoryPlayer", "wm", "ahc");
        playerControllerClass = ReflectionUtil.findClass("net.minecraft.client.multiplayer.PlayerControllerMP", "bda", "bge");

        if (playerClass != null) {
            inventoryField = ReflectionUtil.findField(playerClass, "inventory", "bi", "field_71071_by");
        }

        if (inventoryPlayerClass != null) {
            mainInventoryField = ReflectionUtil.findField(inventoryPlayerClass, "mainInventory", "a", "field_70462_a");
            armorInventoryField = ReflectionUtil.findField(inventoryPlayerClass, "armorInventory", "b", "field_70460_b");
            currentItemField = ReflectionUtil.findField(inventoryPlayerClass, "currentItem", "c", "field_70461_c");
        }

        if (mcClass != null && playerControllerClass != null) {
            playerControllerField = ReflectionUtil.findField(mcClass, "playerController", "c", "field_71442_b");
            // Object windowClick(int windowId, int slotId, int mouseButton, int mode, EntityPlayer playerIn)
            for (Method m : playerControllerClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 5) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == int.class && params[1] == int.class && params[2] == int.class && params[3] == int.class && params[4] == playerClass) {
                        windowClickMethod = m;
                        windowClickMethod.setAccessible(true);
                        break;
                    }
                }
            }
        }
    }

    private long lastInventoryUpdate = -1;
    private IItemStack[] hotbarSnapshot = new IItemStack[9];

    /**
     * Gets the main inventory container array from the player.
     */
    private Object[] getMainInventory() {
        if (HadesAPI.player == null || HadesAPI.player.getRaw() == null || inventoryField == null || mainInventoryField == null) return null;
        try {
            Object rawPlayer = HadesAPI.player.getRaw();
            Object inventory = inventoryField.get(rawPlayer);
            return (Object[]) mainInventoryField.get(inventory);
        } catch (Exception e) {}
        return null;
    }

    /**
     * Retrieves an ItemStack wrapper from the player's main inventory (0-35).
     * Hotbar is 0-8. Main is 9-35.
     * Uses a per-tick snapshot for slots 0-8 to reduce extreme reflection overhead.
     */
    public IItemStack getSlot(int index) {
        if (index >= 0 && index < 9) {
            long currentTick = HadesAPI.mc.isNull() ? 0 : System.currentTimeMillis() / 50; // Approximating ticks safely
            if (currentTick != lastInventoryUpdate) {
                // Re-poll the 9 hotbar slots once per tick (or on respawns / player changes)
                Object[] mainInv = getMainInventory();
                for (int i = 0; i < 9; i++) {
                    if (mainInv != null && i < mainInv.length) {
                        hotbarSnapshot[i] = new IItemStack(mainInv[i]);
                    } else {
                        hotbarSnapshot[i] = new IItemStack(null);
                    }
                }
                lastInventoryUpdate = currentTick;
            }
            return hotbarSnapshot[index];
        } else {
            // Uncached lookup for 9-35 (rarely spam-polled)
            Object[] mainInv = getMainInventory();
            if (mainInv != null && index >= 0 && index < mainInv.length) {
                return new IItemStack(mainInv[index]);
            }
            return new IItemStack(null);
        }
    }

    /**
     * Retrieves an ItemStack wrapper from the player's armor inventory (0-3).
     * 0 = Boots, 1 = Leggings, 2 = Chestplate, 3 = Helmet.
     */
    public IItemStack getArmorSlot(int index) {
        if (HadesAPI.player == null || HadesAPI.player.getRaw() == null || inventoryField == null || armorInventoryField == null) return new IItemStack(null);
        try {
            Object rawPlayer = HadesAPI.player.getRaw();
            Object inventory = inventoryField.get(rawPlayer);
            Object[] armorInv = (Object[]) armorInventoryField.get(inventory);
            if (armorInv != null && index >= 0 && index < armorInv.length) {
                return new IItemStack(armorInv[index]);
            }
        } catch (Exception e) {}
        return new IItemStack(null);
    }

    /**
     * Finds the hotbar slot (0-8) that matches the predicate.
     */
    public int findItemHotbar(Predicate<IItemStack> condition) {
        for (int i = 0; i < 9; i++) {
            IItemStack stack = getSlot(i);
            if (!stack.isNull() && condition.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Set the player's current held hotbar slot (0-8).
     */
    public void setHeldItemSlot(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;
        if (HadesAPI.player == null || HadesAPI.player.getRaw() == null || inventoryField == null || currentItemField == null) return;
        try {
            Object rawPlayer = HadesAPI.player.getRaw();
            Object inventory = inventoryField.get(rawPlayer);
            currentItemField.set(inventory, hotbarSlot);
        } catch (Exception e) {}
    }

    /**
     * Get the currently held hotbar slot.
     */
    public int getHeldItemSlot() {
        if (HadesAPI.player == null || HadesAPI.player.getRaw() == null || inventoryField == null || currentItemField == null) return -1;
        try {
            Object rawPlayer = HadesAPI.player.getRaw();
            Object inventory = inventoryField.get(rawPlayer);
            return currentItemField.getInt(inventory);
        } catch (Exception e) {}
        return -1;
    }

    /**
     * Total block count across the hotbar + inventory limit 36.
     * Essential for Scaffold module readiness queries.
     */
    public int getTotalBlockCount() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            IItemStack stack = getSlot(i);
            if (!stack.isNull() && !stack.getItem().isNull()) {
                if (stack.getItem().isBlock()) {
                    count += stack.getStackSize();
                }
            }
        }
        return count;
    }

    /**
     * Finds a valid hotbar slot containing blocks. Returns -1 if none found.
     */
    public int getBestBlockSlot() {
        return findItemHotbar(stack -> !stack.getItem().isNull() && stack.getItem().isBlock() && stack.getStackSize() > 0);
    }

    /**
     * Advanced inventory interaction for moving items (InvCleaner, AutoArmor).
     * Sends the packet automatically.
     */
    public void windowClick(int windowId, int slotId, int mouseButton, int mode) {
        if (HadesAPI.mc == null || HadesAPI.mc.getRaw() == null || HadesAPI.player == null || HadesAPI.player.getRaw() == null || playerControllerField == null || windowClickMethod == null) return;
        try {
            Object rawMc = HadesAPI.mc.getRaw();
            Object controller = playerControllerField.get(rawMc);
            if (controller != null) {
                Object rawPlayer = HadesAPI.player.getRaw();
                // Object windowClick(int windowId, int slotId, int mouseButton, int mode, EntityPlayer playerIn)
                windowClickMethod.invoke(controller, windowId, slotId, mouseButton, mode, rawPlayer);
            }
        } catch (Exception e) {}
    }
}

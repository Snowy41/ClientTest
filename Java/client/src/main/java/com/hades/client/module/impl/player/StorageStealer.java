package com.hades.client.module.impl.player;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.ReflectionUtil;
import com.hades.client.ai.NeuroFeatures;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class StorageStealer extends Module {

    private final NumberSetting delay = new NumberSetting("Delay (ms)", 100, 0, 1000, 10);
    private final NumberSetting jitter = new NumberSetting("Jitter (ms)", 50, 0, 500, 10);
    private final BooleanSetting onlyUseful = new BooleanSetting("Only Useful (ML)", true);
    private final BooleanSetting autoClose = new BooleanSetting("Auto Close", true);

    private long lastStealTime = 0;
    private long emptyChestTime = -1;

    // Reflection caches
    private Class<?> guiChestClass;
    private Class<?> guiContainerClass;
    private Class<?> containerClass;
    private Class<?> slotClass;
    private Class<?> iInventoryClass;

    private Field currentScreenField;
    private Field inventorySlotsField;
    private Field windowIdField;
    private Field slotListField;
    private Field lowerChestInventoryField;
    
    private Method getStackMethod;
    private Method getSizeInventoryMethod;

    public StorageStealer() {
        super("StorageStealer", "Intelligently logic-steals desired items from chests natively faster than humans.", Category.PLAYER, 0);
        register(delay);
        register(jitter);
        register(onlyUseful);
        register(autoClose);
        
        try {
            Class<?> mcClass = ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
            if (mcClass != null) {
                currentScreenField = ReflectionUtil.findField(mcClass, "m", "currentScreen", "field_71462_r");
            }

            guiChestClass = ReflectionUtil.findClass("net.minecraft.client.gui.inventory.GuiChest", "ayr");
            guiContainerClass = ReflectionUtil.findClass("net.minecraft.client.gui.inventory.GuiContainer", "ayl");
            containerClass = ReflectionUtil.findClass("net.minecraft.inventory.Container", "xi");
            slotClass = ReflectionUtil.findClass("net.minecraft.inventory.Slot", "yg");
            iInventoryClass = ReflectionUtil.findClass("net.minecraft.inventory.IInventory", "og");

            if (guiContainerClass != null) {
                inventorySlotsField = ReflectionUtil.findField(guiContainerClass, "h", "inventorySlots", "field_147002_h");
            }
            if (containerClass != null) {
                windowIdField = ReflectionUtil.findField(containerClass, "d", "windowId", "field_75152_c");
                slotListField = ReflectionUtil.findField(containerClass, "c", "inventorySlots", "field_75151_b");
            }
            if (slotClass != null) {
                getStackMethod = ReflectionUtil.findMethod(slotClass, new String[]{"d", "getStack", "func_75211_c"});
            }
            if (guiChestClass != null) {
                lowerChestInventoryField = ReflectionUtil.findField(guiChestClass, "w", "lowerChestInventory", "field_147015_w");
            }
            if (iInventoryClass != null) {
                getSizeInventoryMethod = ReflectionUtil.findMethod(iInventoryClass, new String[]{"o_", "getSizeInventory", "func_70302_i_"});
            }
        } catch (Exception e) {}
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc == null || HadesAPI.player == null) return;
        if (!HadesAPI.Game.isGuiOpen()) return;

        if (System.currentTimeMillis() - lastStealTime < delay.getValue()) return;

        try {
            Object currentScreen = currentScreenField.get(HadesAPI.mc.getRaw());
            if (currentScreen == null || !guiChestClass.isInstance(currentScreen)) return;

            Object lowerChestInfo = lowerChestInventoryField.get(currentScreen);
            int chestSize = (int) getSizeInventoryMethod.invoke(lowerChestInfo);

            Object container = inventorySlotsField.get(currentScreen);
            int windowId = windowIdField.getInt(container);
            List<?> slots = (List<?>) slotListField.get(container);

            if (isInventoryFull(slots, chestSize)) {
                if (autoClose.getValue()) HadesAPI.player.closeScreen();
                return;
            }

            boolean isEmpty = true;

            // Iterate exclusively through chest boundaries (0 to chestSize - 1)
            for (int i = 0; i < chestSize; i++) {
                Object slotObj = slots.get(i);
                Object rawStack = getStackMethod.invoke(slotObj);

                if (rawStack != null) {
                    IItemStack wrapper = new IItemStack(rawStack);

                    if (onlyUseful.getValue()) {
                        if (!isUseful(wrapper)) continue;
                    }

                    // Shift-click natively extracts directly into player's lowest empty hotbar/backpack
                    InventoryManager.getInstance().windowClick(windowId, i, 0, 1);
                    lastStealTime = System.currentTimeMillis() + (long)(Math.random() * jitter.getValue());
                    isEmpty = false;
                    break; // Wait for delay native Tick execution
                }
            }

            if (isEmpty) {
                if (emptyChestTime == -1) {
                    emptyChestTime = System.currentTimeMillis() + (long)(Math.random() * jitter.getValue());
                } else if (System.currentTimeMillis() - emptyChestTime >= delay.getValue()) {
                    if (autoClose.getValue()) HadesAPI.player.closeScreen();
                    emptyChestTime = -1;
                }
            } else {
                emptyChestTime = -1;
            }

        } catch (Exception e) {}
    }

    /**
     * Failsafe bounding loop to securely verify memory capacity prior to native drag-dropping.
     * Conditionally overrides and proceeds if valid identical ML-filtered items exist offering stack merges natively.
     */
    private boolean isInventoryFull(List<?> chestSlots, int chestSize) {
        InventoryManager inv = InventoryManager.getInstance();
        boolean hasEmpty = false;
        
        for (int i = 0; i < 36; i++) {
            IItemStack stack = inv.getSlot(i);
            if (stack == null || stack.isNull() || stack.getItem().isNull()) {
                hasEmpty = true;
                break;
            }
        }
        
        if (hasEmpty) return false;

        try {
            for (int i = 0; i < chestSize; i++) {
                Object slotObj = chestSlots.get(i);
                Object rawStack = getStackMethod.invoke(slotObj);
                if (rawStack != null) {
                    IItemStack chestItem = new IItemStack(rawStack);
                    if (onlyUseful.getValue() && !isUseful(chestItem)) continue;
                    
                    for (int j = 0; j < 36; j++) {
                        IItemStack invItem = inv.getSlot(j);
                        if (!invItem.isNull() && !invItem.getItem().isNull() &&
                            invItem.getItem().getUnlocalizedName().equals(chestItem.getItem().getUnlocalizedName()) &&
                            invItem.getDamage() == chestItem.getDamage() &&
                            invItem.getStackSize() < invItem.getMaxStackSize()) {
                            return false; // Safely stackable dynamically bridging the full-inventory block!
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        return true;
    }

    /**
     * Determines whether we should loot the item utilizing ML gradients.
     */
    private boolean isUseful(IItemStack stack) {
        if (stack == null || stack.isNull() || stack.getItem().isNull()) return false;
        
        String name = stack.getItem().getUnlocalizedName().toLowerCase();
        
        // Contextual fast-fail layer (Absolute garbage overrides NN)
        if (name.contains("rotten") || name.contains("poisonous") || name.contains("seeds") || name.contains("egg")) {
            return false;
        }

        // Compare with existing gear natively without heavy NN evaluation
        if (isGear(name)) {
            return !hasBetterGear(stack, name);
        }

        // Tensors Extraction
        float[] features = NeuroFeatures.extractFeatures(stack, false);
        
        // Pass through the Multi-Layer Perceptron Predictor
        float keepProbability = NeuroFeatures.predictDesirability(features);
        
        // Extreme Contextual Weights
        if (name.contains("apple") && name.contains("gold")) return true; // Always loot golden apples
        
        // Network evaluation must exceed the threshold (Confidence > 50%)
        return keepProbability >= 0.5f;
    }

    private boolean isGear(String uName) {
        return uName.contains("sword") || uName.contains("pickaxe") || uName.contains("axe") || 
               uName.contains("spade") || uName.contains("helmet") || uName.contains("chestplate") || 
               uName.contains("leggings") || uName.contains("boots");
    }

    private boolean hasBetterGear(IItemStack chestStack, String uName) {
        InventoryManager inv = InventoryManager.getInstance();
        float chestScore = getGearScore(chestStack, uName);
        
        for (int i = 0; i < 36; i++) {
            IItemStack invStack = inv.getSlot(i);
            if (!invStack.isNull() && !invStack.getItem().isNull()) {
                String invName = invStack.getItem().getUnlocalizedName().toLowerCase();
                if (getGearType(invName).equals(getGearType(uName))) {
                    float invScore = getGearScore(invStack, invName);
                    if (invScore >= chestScore) {
                        return true; // We already have a better or equal piece
                    }
                }
            }
        }
        return false;
    }

    private String getGearType(String name) {
        if (name.contains("sword")) return "sword";
        if (name.contains("pickaxe")) return "pickaxe";
        if (name.contains("axe")) return "axe";
        if (name.contains("spade")) return "spade";
        if (name.contains("helmet")) return "helmet";
        if (name.contains("chestplate")) return "chestplate";
        if (name.contains("leggings")) return "leggings";
        if (name.contains("boots")) return "boots";
        return "none";
    }

    private float getGearScore(IItemStack stack, String name) {
        float score = 0;
        if (name.contains("wood") || name.contains("leather")) score = 1;
        else if (name.contains("stone") || name.contains("chainmail")) score = 2;
        else if (name.contains("gold")) score = 1.5f;
        else if (name.contains("iron")) score = 3;
        else if (name.contains("diamond")) score = 4;
        
        // Add enchantment score roughly (sharpness/protection level)
        // Without full ench mapping, just checking if it is enchanted by checking max damage diff or name is an okay heuristic,
        // but for now base material score is sufficient!
        return score;
    }
}

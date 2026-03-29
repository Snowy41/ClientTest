package com.hades.client.module.impl.player;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.manager.InventoryTaskManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.ai.NeuroFeatures;

public class AutoArmor extends Module {

    private final NumberSetting delay = new NumberSetting("Delay (ms)", 150, 0, 1000, 10);
    private final NumberSetting jitter = new NumberSetting("Jitter (ms)", 50, 0, 500, 10);
    private final BooleanSetting openInvOnly = new BooleanSetting("Open Inv Only", false);
    private final BooleanSetting avoidDrop = new BooleanSetting("Avoid Drop", true);

    private long lastActionTime = 0;
    
    private int equipState = 0;
    private int oldArmorGuiSlot = -1;
    private int newArmorGuiSlot = -1;
    private boolean hasOldArmor = false;

    private java.lang.reflect.Field currentScreenField;
    
    // Resolved GUI classes for reliable instanceof checks (works in obfuscated env)
    private Class<?> guiInventoryClass;
    private Class<?> guiContainerCreativeClass;

    public AutoArmor() {
        super("AutoArmor", "Natively analyzes and equips the most optimal defensive plates utilizing ML boundaries.", Category.PLAYER, 0);
        register(delay);
        register(jitter);
        register(openInvOnly);
        register(avoidDrop);

        try {
            Class<?> mcClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
            if (mcClass != null) {
                currentScreenField = com.hades.client.util.ReflectionUtil.findField(mcClass, "m", "currentScreen", "field_71462_r");
            }
            guiInventoryClass = com.hades.client.util.ReflectionUtil.findClass(
                    "net.minecraft.client.gui.inventory.GuiInventory", "azc");
            guiContainerCreativeClass = com.hades.client.util.ReflectionUtil.findClass(
                    "net.minecraft.client.gui.inventory.GuiContainerCreative", "ayu");
        } catch (Exception e) {}
    }

    @Override
    public void onDisable() {
        equipState = 0;
        InventoryTaskManager.getInstance().forceClear("AutoArmor");
        super.onDisable();
    }

    /**
     * Checks if the current GUI screen is the player's inventory (survival or creative).
     * Uses resolved class references instead of name matching — works in obfuscated environments.
     */
    private boolean isPlayerInventoryScreen() {
        if (currentScreenField == null) return false;
        try {
            Object rawMc = HadesAPI.mc.getRaw();
            if (rawMc == null) return false;
            Object screen = currentScreenField.get(rawMc);
            if (screen == null) return false;
            
            if (guiInventoryClass != null && guiInventoryClass.isInstance(screen)) return true;
            if (guiContainerCreativeClass != null && guiContainerCreativeClass.isInstance(screen)) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc == null || HadesAPI.player == null) return;
        
        if (HadesAPI.Game.isGuiOpen()) {
            // A GUI is open — only proceed if it's the player's inventory screen
            if (!isPlayerInventoryScreen()) {
                if (equipState > 0) {
                    equipState = 0;
                    InventoryTaskManager.getInstance().clearBusy("AutoArmor");
                }
                return;
            }
        } else if (openInvOnly.getValue()) {
            // No GUI open and openInvOnly is enabled — skip
            if (equipState > 0) {
                equipState = 0;
                InventoryTaskManager.getInstance().clearBusy("AutoArmor");
            }
            return;
        }

        if (System.currentTimeMillis() - lastActionTime < delay.getValue()) return;
        
        InventoryTaskManager taskMgr = InventoryTaskManager.getInstance();

        InventoryManager inv = InventoryManager.getInstance();

        if (equipState > 0) {
            // Only guard actual click operations against tick coordination — not evaluation
            if (!taskMgr.canActThisTick()) return;

            if (equipState == 1) {
                // Unequip current armor via Shift-Click or Throw
                boolean isFull = true;
                for (int i = 0; i <= 35; i++) {
                    IItemStack stack = inv.getSlot(i);
                    if (stack == null || stack.isNull() || stack.getItem().isNull()) {
                        isFull = false;
                        break;
                    }
                }
                
                if (isFull) {
                    if (avoidDrop.getValue()) {
                        com.hades.client.util.HadesLogger.get().info("[AutoArmor] Aborting plate swap! Inventory is completely full (Avoid Drop natively triggered).");
                        equipState = 0;
                        taskMgr.clearBusy("AutoArmor");
                        return;
                    }
                    inv.windowClick(0, oldArmorGuiSlot, 1, 4); // Drop entire stack implicitly
                } else {
                    inv.windowClick(0, oldArmorGuiSlot, 0, 1); // Shift-click into inventory
                }
                
                equipState = 2;
                lastActionTime = System.currentTimeMillis() + taskMgr.getGaussianDelay(0, jitter.getValue().longValue());
                taskMgr.recordAction();
                return;
            } else if (equipState == 2) {
                // Verify memory payload still accurately reflects target object natively (Server Rollback Defense)
                // Convert GUI slot back to API slot for verification: GUI 36-44 = API 0-8 (hotbar), GUI 9-35 = API 9-35
                int verifyApiSlot = newArmorGuiSlot >= 36 ? newArmorGuiSlot - 36 : newArmorGuiSlot;
                IItemStack verifyStack = inv.getSlot(verifyApiSlot);
                if (verifyStack == null || verifyStack.isNull() || verifyStack.getItem().isNull()) {
                    com.hades.client.util.HadesLogger.get().info("[AutoArmor] Target slot spontaneously emptied (Server Rollback)! Aborting shift-click.");
                    equipState = 0;
                    taskMgr.clearBusy("AutoArmor");
                    return;
                }

                // Equip new armor via Shift-Click
                inv.windowClick(0, newArmorGuiSlot, 0, 1); 
                equipState = 0;
                lastActionTime = System.currentTimeMillis() + taskMgr.getGaussianDelay(0, jitter.getValue().longValue());
                taskMgr.clearBusy("AutoArmor");
                taskMgr.recordAction();
                return;
            }
        }
        
        // Evaluation phase: scanning inventory for better armor is read-only — never block it.
        // Only actual windowClick actions above respect canActThisTick().

        if (routeBestArmor(inv, "helmet", 3, 5)) return;
        if (routeBestArmor(inv, "chestplate", 2, 6)) return;
        if (routeBestArmor(inv, "leggings", 1, 7)) return;
        if (routeBestArmor(inv, "boots", 0, 8)) return;
    }

    /**
     * Finds the absolute best armor plate natively within the bounds and extracts it into the proper slot.
     * @param type "helmet", "chestplate", "leggings", "boots"
     * @param armorIndex The 0-3 logical wrapper index mapping
     * @param guiArmorSlot The 5-8 native container bounds
     */
    private boolean routeBestArmor(InventoryManager inv, String type, int armorIndex, int guiArmorSlot) {
        IItemStack currentEquipped = inv.getArmorSlot(armorIndex);
        float currentScore = 0;
        
        if (currentEquipped != null && !currentEquipped.isNull() && !currentEquipped.getItem().isNull()) {
            String name = currentEquipped.getItem().getUnlocalizedName().toLowerCase();
            if (name.contains(type) || (type.equals("leggings") && name.contains("pants"))) {
                currentScore = NeuroFeatures.getArmorScore(currentEquipped);
            }
        }

        int bestSlot = -1;
        float bestScore = currentScore; // Require newer armor to exclusively be better than exactly what we have

        for (int i = 0; i <= 35; i++) {
            IItemStack stack = inv.getSlot(i);
            if (stack == null || stack.isNull() || stack.getItem().isNull()) continue;
            
            String name = stack.getItem().getUnlocalizedName().toLowerCase();
            if (name.contains(type) || (type.equals("leggings") && name.contains("pants"))) {
                float score = NeuroFeatures.getArmorScore(stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        // If a mathematically superior piece exists natively inside the backpack wrapper
        if (bestSlot != -1) {
            int guiClickSlot = bestSlot >= 9 ? bestSlot : bestSlot + 36;
            
            hasOldArmor = (currentEquipped != null && !currentEquipped.isNull() && !currentEquipped.getItem().isNull());
            
            // Shift-Click equip sequence
            equipState = hasOldArmor ? 1 : 2; // Skip unequip step if slot is empty
            oldArmorGuiSlot = guiArmorSlot;
            newArmorGuiSlot = guiClickSlot;
            InventoryTaskManager.getInstance().setBusy("AutoArmor");
            
            com.hades.client.util.HadesLogger.get().info("[AutoArmor] Natively re-routing mathematically optimal " + type + "!");
            return true;
        }

        return false;
    }
}

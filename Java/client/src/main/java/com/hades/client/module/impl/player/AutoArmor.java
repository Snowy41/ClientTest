package com.hades.client.module.impl.player;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.ai.NeuroFeatures;

public class AutoArmor extends Module {

    private final NumberSetting delay = new NumberSetting("Delay (ms)", 150, 0, 1000, 10);
    private final NumberSetting jitter = new NumberSetting("Jitter (ms)", 50, 0, 500, 10);
    private final BooleanSetting openInvOnly = new BooleanSetting("Open Inv Only", false);

    private long lastActionTime = 0;
    
    private int equipState = 0;
    private int oldArmorGuiSlot = -1;
    private int newArmorGuiSlot = -1;

    private java.lang.reflect.Field currentScreenField;

    public AutoArmor() {
        super("AutoArmor", "Natively analyzes and equips the most optimal defensive plates utilizing ML boundaries.", Category.PLAYER, 0);
        register(delay);
        register(jitter);
        register(openInvOnly);

        try {
            Class<?> mcClass = com.hades.client.util.ReflectionUtil.findClass("net.minecraft.client.Minecraft", "ave");
            if (mcClass != null) {
                currentScreenField = com.hades.client.util.ReflectionUtil.findField(mcClass, "m", "currentScreen", "field_71462_r");
            }
        } catch (Exception e) {}
    }

    @Override
    public void onDisable() {
        equipState = 0;
        super.onDisable();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc == null || HadesAPI.player == null) return;
        
        if (HadesAPI.Game.isGuiOpen()) {
            Object rawMc = HadesAPI.mc.getRaw();
            if (rawMc != null && currentScreenField != null) {
                try {
                    Object currentScreen = currentScreenField.get(rawMc);
                    if (currentScreen != null) {
                        String name = currentScreen.getClass().getSimpleName().toLowerCase();
                        if (!name.contains("inventory") && !name.equals("ayl")) return;
                    }
                } catch (Exception e) {}
            }
        } else if (openInvOnly.getValue()) {
            return;
        }

        if (System.currentTimeMillis() - lastActionTime < delay.getValue()) return;

        InventoryManager inv = InventoryManager.getInstance();

        if (equipState > 0) {
            if (equipState == 1) {
                inv.windowClick(0, newArmorGuiSlot, 0, 0); // Pick up new explicitly
                equipState = 2;
                lastActionTime = System.currentTimeMillis() + (long)(Math.random() * jitter.getValue());
                return;
            } else if (equipState == 2) {
                inv.windowClick(0, oldArmorGuiSlot, 0, 0); // Drop directly into armor explicitly swapping Native cursor
                equipState = 3;
                lastActionTime = System.currentTimeMillis() + (long)(Math.random() * jitter.getValue());
                return;
            } else if (equipState == 3) {
                inv.windowClick(0, newArmorGuiSlot, 0, 0); // Drop old cursor parameter natively replacing the gap
                equipState = 0;
                lastActionTime = System.currentTimeMillis() + (long)(Math.random() * jitter.getValue());
                return;
            }
        }

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
            if (name.contains(type)) {
                currentScore = NeuroFeatures.getArmorScore(currentEquipped);
            }
        }

        int bestSlot = -1;
        float bestScore = currentScore; // Require newer armor to exclusively be better than exactly what we have

        for (int i = 0; i <= 35; i++) {
            IItemStack stack = inv.getSlot(i);
            if (stack == null || stack.isNull() || stack.getItem().isNull()) continue;
            
            String name = stack.getItem().getUnlocalizedName().toLowerCase();
            if (name.contains(type)) {
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
            // Uniform explicitly 3-state Drag and Drop routing!
            equipState = 1;
            oldArmorGuiSlot = guiArmorSlot;
            newArmorGuiSlot = guiClickSlot;
            
            com.hades.client.util.HadesLogger.get().info("[AutoArmor] Natively re-routing mathematically optimal " + type + "!");
            return true;
        }

        return false;
    }
}

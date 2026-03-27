package com.hades.client.module.impl.player;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IItemStack;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;

public class AutoTool extends Module {

    private final BooleanSetting swords = new BooleanSetting("Auto Sword", true);
    private final BooleanSetting tools = new BooleanSetting("Auto Tools", true);
    
    private int returnSlot = -1;
    private long lastSwapTime = 0;
    
    // Caches to prevent re-evaluating the whole inventory every tick for the same block/entity
    private Object lastBlock = null;
    private int lastBestBlockSlot = -1;
    private int lastBestWeaponSlot = -1;

    public AutoTool() {
        super("AutoTool", "Automatically switches to the best tool or weapon", Category.PLAYER, 0);
        register(swords);
        register(tools);
    }

    @Override
    public void onEnable() {
        returnSlot = -1;
        lastBlock = null;
        lastBestBlockSlot = -1;
        lastBestWeaponSlot = -1;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc.isNull() || HadesAPI.player.isNull() || HadesAPI.world.isNull()) return;

        // We'll just assume left click is mouse button 0 using LWJGL if needed.
        boolean isClicking = org.lwjgl.input.Mouse.isButtonDown(0);
        InventoryManager im = InventoryManager.getInstance();

        if (!isClicking) {
            // Swap back if we stopped clicking
            if (returnSlot != -1 && System.currentTimeMillis() - lastSwapTime > 150) {
                if (returnSlot != im.getHeldItemSlot() && returnSlot >= 0 && returnSlot < 9) {
                    im.setHeldItemSlot(returnSlot);
                }
                returnSlot = -1;
            }
            return;
        }

        int targetType = HadesAPI.mc.getMouseOverType();
        
        if (targetType == 1 && tools.getValue()) { // BLOCK
            Object blockPos = HadesAPI.mc.getMouseOverBlockPos();
            if (blockPos != null) {
                Object block = HadesAPI.world.getBlockAt(blockPos);
                if (block != null) {
                    if (block != lastBlock) {
                        lastBlock = block;
                        lastBestBlockSlot = evaluateBestTool(block);
                    }
                    swapToSlot(lastBestBlockSlot);
                }
            }
        }  
        else if (targetType == 2 && swords.getValue()) { // ENTITY
            if (lastBestWeaponSlot == -1) {
                lastBestWeaponSlot = evaluateBestWeapon();
            }
            swapToSlot(lastBestWeaponSlot);
        } else {
            // Reset state if not looking at anything
            lastBlock = null;
            lastBestWeaponSlot = -1;
        }
    }

    /** Returns the inventory index (0-8) of the best tool for the given block. */
    private int evaluateBestTool(Object block) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        
        InventoryManager im = InventoryManager.getInstance();
        IItemStack current = im.getSlot(im.getHeldItemSlot());
        if (current != null && !current.isNull()) {
            bestSpeed = current.getStrVsBlock(block);
        }

        for (int i = 0; i < 9; i++) {
            IItemStack stack = im.getSlot(i);
            if (stack != null && !stack.isNull()) {
                float speed = stack.getStrVsBlock(block);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private void swapToSlot(int slot) {
        InventoryManager im = InventoryManager.getInstance();
        if (slot != -1 && slot != im.getHeldItemSlot()) {
            if (returnSlot == -1) {
                returnSlot = im.getHeldItemSlot();
            }
            im.setHeldItemSlot(slot);
            lastSwapTime = System.currentTimeMillis();
        } else if (slot != -1) {
            lastSwapTime = System.currentTimeMillis();
        }
    }

    /** Returns the inventory index (0-8) of the best weapon. */
    private int evaluateBestWeapon() {
        int bestSlot = -1;
        float bestDamage = 1.0f;
        
        InventoryManager im = InventoryManager.getInstance();
        IItemStack current = im.getSlot(im.getHeldItemSlot());
        if (current != null && !current.isNull()) {
            bestDamage = current.getDamageVsEntity();
        }

        for (int i = 0; i < 9; i++) {
            IItemStack stack = im.getSlot(i);
            if (stack != null && !stack.isNull()) {
                float damage = stack.getDamageVsEntity();
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }
}

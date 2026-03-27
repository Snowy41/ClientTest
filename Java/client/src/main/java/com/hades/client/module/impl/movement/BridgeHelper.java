package com.hades.client.module.impl.movement;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

public class BridgeHelper extends Module {

    private final BooleanSetting autoPlace = register(new BooleanSetting("Auto Place", true));
    private final BooleanSetting autoSwitch = register(new BooleanSetting("Auto Switch Base", true));
    private final BooleanSetting onlyBlocks = register(new BooleanSetting("Only With Blocks", true));
    private final NumberSetting minDrop = register(new NumberSetting("Min Drop Blocks", 3, 1, 10, 1));
    private final NumberSetting sneakDelay = register(new NumberSetting("Sneak Delay Ticks", 2, 0, 10, 1));

    private int delayTicks = 0;
    private boolean sneakingLocked = false;
    private boolean autoPlacedThisEdge = false;
    private long lastSneakTime = 0L;

    public BridgeHelper() {
        super("BridgeHelper", "Legit Eagle. Automatisch sneaken an Kanten und blockieren.", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    public boolean isActivelyBridging() {
        return sneakingLocked || delayTicks > 0 || (System.currentTimeMillis() - lastSneakTime < 1000L);
    }

    @Override
    protected void onDisable() {
        HadesAPI.mc.setKeySneakPressed(false);
        sneakingLocked = false;
        autoPlacedThisEdge = false;
        delayTicks = 0;
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (!isEnabled() || HadesAPI.player == null || HadesAPI.player.getRaw() == null) return;

        // Eagle Logic only triggers if player is on the ground
        if (!HadesAPI.player.isOnGround()) {
            delayTicks = 0;
            return;
        }

        if (sneakingLocked) {
            lastSneakTime = System.currentTimeMillis();
        }

        // AutoSwitch using InventoryManager
        if (autoSwitch.getValue()) {
            int currentSlot = InventoryManager.getInstance().getHeldItemSlot();
            if (currentSlot != -1) {
                com.hades.client.api.interfaces.IItemStack held = InventoryManager.getInstance().getSlot(currentSlot);
                if (held == null || held.isNull() || !held.getItem().isBlock()) {
                    int blockSlot = InventoryManager.getInstance().getBestBlockSlot();
                    if (blockSlot != -1 && blockSlot != currentSlot) {
                        InventoryManager.getInstance().setHeldItemSlot(blockSlot);
                    }
                }
            }
        }

        // Logic check delay to mimic human un-sneak delay
        if (delayTicks > 0) {
            delayTicks--;
            return; // Wait out the sneak lock
        }

        // Contextual Edge Case Filters
        if (onlyBlocks.getValue()) {
            int slot = InventoryManager.getInstance().getHeldItemSlot();
            if (slot == -1) return;
            com.hades.client.api.interfaces.IItemStack held = InventoryManager.getInstance().getSlot(slot);
            if (held == null || held.isNull() || !held.getItem().isBlock()) {
                if (sneakingLocked) {
                    HadesAPI.mc.setKeySneakPressed(false);
                    sneakingLocked = false;
                }
                return;
            }
        }

        // Calculate Block under player based on projected position
        int bpX = (int) Math.floor(HadesAPI.player.getX());
        int bpY = (int) Math.floor(HadesAPI.player.getY());
        int bpZ = (int) Math.floor(HadesAPI.player.getZ());

        // Vertical Raycast: Assess if the drop is just a staircase or an actual bridge edge
        boolean isSafeDrop = false;
        int maxDrop = minDrop.getValue().intValue();
        for (int i = 1; i <= maxDrop; i++) {
            if (!HadesAPI.world.isAirBlock(bpX, bpY - i, bpZ)) {
                isSafeDrop = true;
                break;
            }
        }

        boolean OverAir = !isSafeDrop; // True if ALL blocks downwards up to maxDrop are Air

        if (OverAir) {
            // Reached the edge
            if (!sneakingLocked) {
                HadesLogger.get().info("[BridgeHelper] Reached Edge! Air block strictly underneath " + bpX + ", " + bpY + ", " + bpZ + ". Sneaking...");
                HadesAPI.mc.setKeySneakPressed(true);
                sneakingLocked = true;
                autoPlacedThisEdge = false;
            }
        } else {
            // Safely on block
            if (sneakingLocked) {
                HadesLogger.get().info("[BridgeHelper] Solid ground detected at " + bpX + ", " + bpY + ", " + bpZ + ". Releasing Sneak.");
                HadesAPI.mc.setKeySneakPressed(false);
                sneakingLocked = false;
                delayTicks = autoPlace.getValue() ? sneakDelay.getValue().intValue() : 0; // Artificial delay before re-scanning
            }
        }

        // Handle Auto-Place explicitly gated by the raytrace dropping off the block's top face
        if (sneakingLocked && autoPlace.getValue() && !autoPlacedThisEdge) {
            if (!HadesAPI.mc.isTargetingBlockTop()) {
                HadesAPI.mc.performRightClick();
                autoPlacedThisEdge = true;
            }
        }
    }
}

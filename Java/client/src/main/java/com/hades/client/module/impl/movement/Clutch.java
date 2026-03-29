package com.hades.client.module.impl.movement;

import com.hades.client.HadesClient;
import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.manager.InventoryManager;
import com.hades.client.util.HadesLogger;
import com.hades.client.util.RotationUtil;



public class Clutch extends Module {

    private final ModeSetting mode = new ModeSetting("Mode", "Scaffold", "Scaffold", "Pearl");
    private final NumberSetting fallDist = new NumberSetting("Fall Distance", 3.0, 1.0, 10.0, 0.5);

    private long lastHitTime = 0;
    private boolean isClutching = false;
    private int clutchDelay = 0;

    // Singleton link for Scaffold to check if Clutch is actively asking it to bridge out
    public static boolean isClutchActive() {
        Module clutch = HadesClient.getInstance().getModuleManager().getModule("Clutch");
        if (clutch instanceof Clutch) {
            return clutch.isEnabled() && ((Clutch) clutch).isClutching && ((Clutch) clutch).mode.getValue().equals("Scaffold");
        }
        return false;
    }

    public Clutch() {
        super("Clutch", "Saves you from falling into the void or dying to fall damage.", Category.MOVEMENT, 0);
        register(mode);
        register(fallDist);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        isClutching = false;
        lastHitTime = 0;
        clutchDelay = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (isClutching && mode.getValue().equals("Scaffold")) {
            Module scaffold = HadesClient.getInstance().getModuleManager().getModule("Scaffold");
            if (scaffold != null && scaffold.isEnabled()) {
                scaffold.setEnabled(false);
            }
        }
        isClutching = false;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled() || HadesAPI.mc.isInGui() || HadesAPI.player == null || HadesAPI.world == null) return;

        // Record hit times (User requested: "if the player is hit (so it does not spamm rays down)")
        if (HadesAPI.player.getHurtTime() > 0) {
            lastHitTime = System.currentTimeMillis();
        }

        if (isClutching) {
            // Once we land safely, disable clutching
            if (HadesAPI.player.isOnGround()) {
                isClutching = false;
                if (mode.getValue().equals("Scaffold")) {
                    Module scaffold = HadesClient.getInstance().getModuleManager().getModule("Scaffold");
                    if (scaffold != null && scaffold.isEnabled()) {
                        scaffold.setEnabled(false);
                        HadesLogger.get().info("[Clutch] Saved successfully! Disabling Scaffold.");
                    }
                }
            }
            
            // Timeout delay for pearl mode to give it time to land before re-triggering
            if (clutchDelay > 0) clutchDelay--;
            return;
        }

        // We only verify void if falling past the trigger threshold and hit recently (within last 3 seconds)
        boolean hitRecently = (System.currentTimeMillis() - lastHitTime) < 3000;
        if (hitRecently && HadesAPI.player.getFallDistance() >= fallDist.getValue()) {
            if (isOverVoid()) {
                activateClutch();
            }
        }
    }

    private boolean isOverVoid() {
        double px = HadesAPI.player.getX();
        double py = HadesAPI.player.getY();
        double pz = HadesAPI.player.getZ();

        int bpx = (int) Math.floor(px);
        int bpz = (int) Math.floor(pz);

        // Raytrace straight down. If we find any solid block within 30 blocks, we are not over the void.
        // We only care about saving them from immediate death/void
        int searchDepth = Math.min((int) py, 30);
        for (int y = (int) py - 1; y > Math.max(0, py - searchDepth); y--) {
            if (HadesAPI.world.isSolidBlock(bpx, y, bpz)) {
                return false;
            }
        }
        return true; 
    }

    private void activateClutch() {
        isClutching = true;
        HadesLogger.get().info("[Clutch] Void/Death fall detected! Activating " + mode.getValue() + " clutch!");

        if (mode.getValue().equals("Scaffold")) {
            Module scaffold = HadesClient.getInstance().getModuleManager().getModule("Scaffold");
            if (scaffold != null && !scaffold.isEnabled()) {
                scaffold.setEnabled(true);
            }
        } else if (mode.getValue().equals("Pearl")) {
            if (clutchDelay > 0) return; // Prevent spamming pearls mid-air
            executePearlClutch();
            clutchDelay = 40; // 2 seconds delay before trying another pearl
        }
    }

    private void executePearlClutch() {
        InventoryManager im = InventoryManager.getInstance();
        int pearlSlot = im.findItemHotbar(stack -> {
            if (stack == null || stack.isNull() || stack.getItem().isNull()) return false;
            String name = stack.getItem().getUnlocalizedName().toLowerCase();
            return name.contains("enderpearl") || name.contains("ender_pearl");
        });

        if (pearlSlot == -1) {
            HadesLogger.get().info("[Clutch] No EnderPearl in hotbar!");
            isClutching = false; // Cannot pearl, abort
            return;
        }

        // Search for a safe landing pad (Flat isolated island, safe blocks)
        double px = HadesAPI.player.getX();
        double py = HadesAPI.player.getY();
        double pz = HadesAPI.player.getZ();
        
        // Find best block in 20 block radius
        int[] bestBlock = null;
        double bestScore = Double.MAX_VALUE;

        int radius = 20;
        for (int x = -radius; x <= radius; x += 3) {
            for (int z = -radius; z <= radius; z += 3) {
                // Heuristics: search from Y-5 to Y+10
                for (int y = (int) py - 5; y <= py + 10; y++) {
                    int checkX = (int) px + x;
                    int checkZ = (int) pz + z;
                    
                    if (HadesAPI.world.isSolidBlock(checkX, y, checkZ)) {
                        // Needs 2 blocks of air above to safely land
                        if (!HadesAPI.world.isSolidBlock(checkX, y + 1, checkZ) && !HadesAPI.world.isSolidBlock(checkX, y + 2, checkZ)) {
                            double distSq = (x * x) + ((y - py) * (y - py)) + (z * z);
                            
                            // Score is purely distance right now, but we penalize Y differences slightly
                            double score = distSq + Math.abs(y - py) * 10.0; 
                            
                            if (score < bestScore && distSq > 9.0) { // Don't throw at ourselves (distSq > 9)
                                bestScore = score;
                                bestBlock = new int[]{checkX, y, checkZ};
                            }
                        }
                    }
                }
            }
        }

        if (bestBlock != null) {
            double targetX = bestBlock[0] + 0.5;
            double targetY = bestBlock[1] + 1.0;
            double targetZ = bestBlock[2] + 0.5;

            // Calculate rotations
            float[] rots = RotationUtil.getRotations(targetX, targetY, targetZ);
            
            // Adjust Pitch to aim slightly upward to account for EnderPearl gravity arc
            double dist = Math.sqrt(Math.pow(targetX - px, 2) + Math.pow(targetZ - pz, 2));
            float pitchOffset = (float) (dist * 0.5); // Add 0.5 deg upward pitch per block distance
            float throwPitch = Math.max(-90f, rots[1] - pitchOffset);

            int oldSlot = im.getHeldItemSlot();

            // 1. Swap
            im.setHeldItemSlot(pearlSlot);
            
            // 2. Spoof C03 Look
            HadesAPI.network.sendPacket(HadesAPI.network.createC05Packet(rots[0], throwPitch, HadesAPI.player.isOnGround()));
            
            // 3. Throw right click
            HadesAPI.mc.performRightClick();
            
            // 4. Swap back
            im.setHeldItemSlot(oldSlot);

            HadesLogger.get().info("[Clutch] Threw Pearl at targeted safe coordinates.");
        } else {
            HadesLogger.get().info("[Clutch] Could not find safe adjacent island/block to Pearl to!");
            isClutching = false;
        }
    }
}

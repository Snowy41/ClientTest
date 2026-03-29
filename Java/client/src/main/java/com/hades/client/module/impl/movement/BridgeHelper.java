package com.hades.client.module.impl.movement;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.manager.InventoryManager;
import com.hades.client.module.Module;
import com.hades.client.module.setting.BooleanSetting;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import com.hades.client.util.HadesLogger;
import org.lwjgl.input.Keyboard;

public class BridgeHelper extends Module {

    private final ModeSetting mode = register(new ModeSetting("Mode", "Eagle", "Eagle", "GodBridge", "Telly"));
    private final BooleanSetting lockCamera = register(new BooleanSetting("Lock Camera", true));

    private final BooleanSetting autoPlace = register(new BooleanSetting("Auto Place", true));
    private final BooleanSetting autoSwitch = register(new BooleanSetting("Auto Switch Base", true));
    private final BooleanSetting onlyBlocks = register(new BooleanSetting("Only With Blocks", true));
    private final NumberSetting minDrop = register(new NumberSetting("Min Drop Blocks", 3, 1, 10, 1));
    private final NumberSetting sneakDelay = register(new NumberSetting("Sneak Delay Ticks", 2, 0, 10, 1));

    private int delayTicks = 0;
    private boolean sneakingLocked = false;
    private boolean autoPlacedThisEdge = false;
    private long lastSneakTime = 0L;

    // Telly state
    private int tellyTicks = 0;
    private boolean inTellyJump = false;

    // Spoofed Rotation State
    private Float spoofYaw = null;
    private Float spoofPitch = null;

    public BridgeHelper() {
        super("BridgeHelper", "Legit Macro. Eagle, GodBridge, and Telly for bypass generation.", Category.MOVEMENT,
                Keyboard.KEY_NONE);
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
        tellyTicks = 0;
        inTellyJump = false;
        spoofYaw = null;
        spoofPitch = null;
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (!isEnabled() || HadesAPI.player == null || HadesAPI.player.getRaw() == null)
            return;

        // AutoSwitch using InventoryManager (applies to all modes)
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

        // Contextual Edge Case Filters (must hold block)
        if (onlyBlocks.getValue()) {
            int slot = InventoryManager.getInstance().getHeldItemSlot();
            if (slot == -1)
                return;
            com.hades.client.api.interfaces.IItemStack held = InventoryManager.getInstance().getSlot(slot);
            if (held == null || held.isNull() || !held.getItem().isBlock()) {
                if (sneakingLocked) {
                    HadesAPI.mc.setKeySneakPressed(false);
                    sneakingLocked = false;
                }
                spoofYaw = null;
                spoofPitch = null;
                return;
            }
        }

        switch (mode.getValue()) {
            case "Eagle":
                handleEagleMode();
                break;
            case "GodBridge":
                handleGodBridgeMode(event);
                break;
            case "Telly":
                handleTellyMode(event);
                break;
        }

        // Apply Spoofed Rotations to MotionEvent if Camera Lock is disabled
        if (!lockCamera.getValue() && spoofYaw != null && spoofPitch != null) {
            event.setYaw(spoofYaw);
            event.setPitch(spoofPitch);
        }
    }

    private void handleEagleMode() {
        if (!HadesAPI.player.isOnGround()) {
            delayTicks = 0;
            return;
        }

        if (sneakingLocked) {
            lastSneakTime = System.currentTimeMillis();
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        int bpX = (int) Math.floor(HadesAPI.player.getX());
        int bpY = (int) Math.floor(HadesAPI.player.getY());
        int bpZ = (int) Math.floor(HadesAPI.player.getZ());

        boolean isSafeDrop = false;
        int maxDrop = minDrop.getValue().intValue();
        for (int i = 1; i <= maxDrop; i++) {
            if (!HadesAPI.world.isAirBlock(bpX, bpY - i, bpZ)) {
                isSafeDrop = true;
                break;
            }
        }

        boolean overAir = !isSafeDrop;

        if (overAir) {
            if (!sneakingLocked) {
                HadesAPI.mc.setKeySneakPressed(true);
                sneakingLocked = true;
                autoPlacedThisEdge = false;
            }
        } else {
            if (sneakingLocked) {
                HadesAPI.mc.setKeySneakPressed(false);
                sneakingLocked = false;
                delayTicks = autoPlace.getValue() ? sneakDelay.getValue().intValue() : 0;
            }
        }

        if (sneakingLocked && autoPlace.getValue() && !autoPlacedThisEdge) {
            if (scaffoldPlace()) {
                autoPlacedThisEdge = true;
            }
        }
    }

    private void handleGodBridgeMode(MotionEvent event) {
        // God bridge requires backward/zigzag movement.
        // We lock pitch to ~76.5 for optimal fast placement and align yaw backward.

        float perfectPitch = 76.5f;
        float currentYaw = HadesAPI.player.getYaw();

        // Find nearest 45 degree angle backwards to user's movement (zigzag path
        // offset)
        float perfectYaw = Math.round(currentYaw / 45.0f) * 45.0f;

        boolean isMoving = HadesAPI.player.getMotionX() != 0 || HadesAPI.player.getMotionZ() != 0;
        if (!isMoving && HadesAPI.player.isOnGround()) {
            spoofYaw = null;
            spoofPitch = null;
            return;
        }

        // Auto Strafe to dead-center of block to prevent falling off
        if (HadesAPI.player.isOnGround() && mode.getValue().equals("GodBridge")) {
            double diffX = (Math.floor(HadesAPI.player.getX()) + 0.5) - HadesAPI.player.getX();
            double diffZ = (Math.floor(HadesAPI.player.getZ()) + 0.5) - HadesAPI.player.getZ();

            float yaw = ((HadesAPI.player.getYaw() % 360) + 360) % 360;
            if ((yaw >= 315 || yaw < 45) || (yaw >= 135 && yaw < 225)) {
                HadesAPI.player.setMotionX(diffX * 0.15); // Correct horizontal drift
            } else {
                HadesAPI.player.setMotionZ(diffZ * 0.15);
            }
        }

        // Apply Rotations
        applyRotations(perfectYaw, perfectPitch);

        // Auto-place geometrically
        if (autoPlace.getValue()) {
            scaffoldPlace();
        }
    }

    private void handleTellyMode(MotionEvent event) {
        // Telly relies on detecting a sprint jump off an edge
        if (HadesAPI.player.isOnGround()) {
            inTellyJump = false;
            tellyTicks = 0;
            spoofYaw = null;
            spoofPitch = null;
            return;
        }

        // If we just jumped and we are sprinting, start telly routine
        if (!inTellyJump && HadesAPI.player.isSprinting() && HadesAPI.player.getMotionY() > 0.1) {
            inTellyJump = true;
            tellyTicks = 0;
        }

        if (inTellyJump) {
            tellyTicks++;
            float tellyPitch = 79.5f;
            float backwardYaw = HadoopYawBackwards(HadesAPI.player.getYaw());

            if (tellyTicks >= 3 && tellyTicks <= 14) {
                // Snap camera 180 backwards and place
                applyRotations(backwardYaw, tellyPitch);
                if (autoPlace.getValue()) {
                    scaffoldPlace();
                }
            } else if (tellyTicks > 14) {
                // Snap camera back to legit forward yaw instantly before landing
                // Nullifying allows the legit client yaw to take over natively immediately
                spoofYaw = null;
                spoofPitch = null;
                // If camera was locked, manually snap it back (approx original yaw)
                if (lockCamera.getValue()) {
                    HadesAPI.player.setYaw(HadoopYawBackwards(backwardYaw));
                }
            }
        }
    }

    private void applyRotations(float yaw, float pitch) {
        if (lockCamera.getValue()) {
            HadesAPI.player.setYaw(yaw);
            HadesAPI.player.setPitch(pitch);
            spoofYaw = null;
            spoofPitch = null;
        } else {
            spoofYaw = yaw;
            spoofPitch = pitch;
        }
    }

    private float HadoopYawBackwards(float yaw) {
        return (yaw + 180f) % 360f;
    }

    // Prevents placing a block if the player's bounding box intersects it!
    private boolean isSafeToPlace(int x, int y, int z) {
        if (HadesAPI.player == null)
            return false;
        double minX = HadesAPI.player.getX() - 0.3;
        double maxX = HadesAPI.player.getX() + 0.3;
        double minY = HadesAPI.player.getY();
        double maxY = HadesAPI.player.getY() + 1.8;
        double minZ = HadesAPI.player.getZ() - 0.3;
        double maxZ = HadesAPI.player.getZ() + 0.3;

        double bMinX = x;
        double bMaxX = x + 1.0;
        double bMinY = y;
        double bMaxY = y + 1.0;
        double bMinZ = z;
        double bMaxZ = z + 1.0;

        // Return false if they intersect
        return !(minX < bMaxX && maxX > bMinX && minY < bMaxY && maxY > bMinY && minZ < bMaxZ && maxZ > bMinZ);
    }

    private boolean scaffoldPlace() {
        BlockData target = searchBlockData();
        if (target != null) {
            int placeX = target.x;
            int placeY = target.y;
            int placeZ = target.z;
            if (target.facing == 0)
                placeY -= 1;
            if (target.facing == 1)
                placeY += 1;
            if (target.facing == 2)
                placeZ -= 1;
            if (target.facing == 3)
                placeZ += 1;
            if (target.facing == 4)
                placeX -= 1;
            if (target.facing == 5)
                placeX += 1;

            if (!isSafeToPlace(placeX, placeY, placeZ)) {
                return false; // Wait patiently natively until we slip completely off the voxel edge
            }

            double faceX = target.x + 0.5;
            double faceY = target.y + 0.5;
            double faceZ = target.z + 0.5;

            if (target.facing == 1)
                faceY += 0.5;
            if (target.facing == 0)
                faceY -= 0.5;
            if (target.facing == 2)
                faceZ -= 0.5;
            if (target.facing == 3)
                faceZ += 0.5;
            if (target.facing == 4)
                faceX -= 0.5;
            if (target.facing == 5)
                faceX += 0.5;

            HadesAPI.mc.setMouseOverBlock(faceX, faceY, faceZ, target.x, target.y, target.z, target.facing);
            HadesAPI.mc.performRightClick();
            return true;
        }
        return false;
    }

    private BlockData searchBlockData() {
        double px = HadesAPI.player.getX();
        double py = HadesAPI.player.getY();
        double pz = HadesAPI.player.getZ();

        int bpx = (int) Math.floor(px);
        int bpz = (int) Math.floor(pz);

        float yaw = spoofYaw != null ? spoofYaw : HadesAPI.player.getYaw();
        yaw = ((yaw % 360) + 360) % 360;

        // Iterate starting from the exact foot level downwards 4 blocks (Critical for
        // Telly jumps)
        for (int yOffset = 1; yOffset <= 4; yOffset++) {
            int bpy = (int) Math.floor(py) - yOffset;

            // If standing ON the block before dropping (Godbridge), click the correct
            // backing face
            if (HadesAPI.world.isSolidBlock(bpx, bpy, bpz)) {
                if (yaw >= 315 || yaw < 45)
                    return new BlockData(bpx, bpy, bpz, 2);
                else if (yaw >= 45 && yaw < 135)
                    return new BlockData(bpx, bpy, bpz, 5);
                else if (yaw >= 135 && yaw < 225)
                    return new BlockData(bpx, bpy, bpz, 3);
                else
                    return new BlockData(bpx, bpy, bpz, 4);
            }

            // If dropped into air (bpy is air), search strictly straight-backward offsets
            java.util.List<int[]> offsets = new java.util.ArrayList<>();
            offsets.add(new int[] { 0, -1, 0 });
            if (yaw >= 315 || yaw < 45) {
                offsets.add(new int[] { 0, 0, 1 });
                offsets.add(new int[] { 0, -1, 1 });
            } else if (yaw >= 45 && yaw < 135) {
                offsets.add(new int[] { -1, 0, 0 });
                offsets.add(new int[] { -1, -1, 0 });
            } else if (yaw >= 135 && yaw < 225) {
                offsets.add(new int[] { 0, 0, -1 });
                offsets.add(new int[] { 0, -1, -1 });
            } else {
                offsets.add(new int[] { 1, 0, 0 });
                offsets.add(new int[] { 1, -1, 0 });
            }

            for (int[] offset : offsets) {
                int targetAirX = bpx + offset[0];
                int targetAirY = bpy + offset[1];
                int targetAirZ = bpz + offset[2];

                if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY, targetAirZ))
                    continue;

                if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY - 1, targetAirZ))
                    return new BlockData(targetAirX, targetAirY - 1, targetAirZ, 1);
                if (HadesAPI.world.isSolidBlock(targetAirX - 1, targetAirY, targetAirZ))
                    return new BlockData(targetAirX - 1, targetAirY, targetAirZ, 5);
                if (HadesAPI.world.isSolidBlock(targetAirX + 1, targetAirY, targetAirZ))
                    return new BlockData(targetAirX + 1, targetAirY, targetAirZ, 4);
                if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY, targetAirZ - 1))
                    return new BlockData(targetAirX, targetAirY, targetAirZ - 1, 3);
                if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY, targetAirZ + 1))
                    return new BlockData(targetAirX, targetAirY, targetAirZ + 1, 2);
            }
        }

        return null;
    }

    private static class BlockData {
        public final int x, y, z, facing;

        public BlockData(int x, int y, int z, int facing) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
        }
    }
}

package com.hades.client.module.impl.movement;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.module.Module;

public class Scaffold extends Module {
    private BlockData currentTarget;

    // Per-tick micro-jitter to guarantee Vanilla always sends C06 (rot=true) not C04
    // Vanilla sends C04 when rotationYaw == lastReportedYaw && rotationPitch == lastReportedPitch
    // By alternating pitch by 0.001° each tick, d4 != 0 → flag3=true → C06 always
    private int tickCounter = 0;

    private static Float activeScaffoldYaw = null;
    private static Float activeScaffoldPitch = null;
    private boolean isSpoofing = false;
    
    // Tracks the exact X/Y/Z coords last sent to the server in a C03.
    // Vital for GrimAC PositionPlace bounds simulation.
    private double serverX = 0, serverY = 0, serverZ = 0;

    public static Float getActiveScaffoldYaw() {
        Module scaffold = com.hades.client.HadesClient.getInstance().getModuleManager().getModule("Scaffold");
        if (scaffold != null && scaffold.isEnabled() && activeScaffoldYaw != null) {
            return activeScaffoldYaw;
        }
        return null;
    }

    public static Float getActiveScaffoldPitch() {
        Module scaffold = com.hades.client.HadesClient.getInstance().getModuleManager().getModule("Scaffold");
        if (scaffold != null && scaffold.isEnabled() && activeScaffoldPitch != null) {
            return activeScaffoldPitch;
        }
        return null;
    }

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under your feet", Module.Category.MOVEMENT, 0);
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        currentTarget = null;
        tickCounter = 0;
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
        activeScaffoldYaw = null;
        activeScaffoldPitch = null;
        isSpoofing = false;
        HadesAPI.mc.setKeySneakPressed(false);
    }

    private double faceX, faceY, faceZ;

    @EventHandler
    public void onTick(TickEvent event) {
        if (HadesAPI.mc.isInGui() || HadesAPI.player == null || HadesAPI.world == null) return;

        // AutoTool Integration
        com.hades.client.module.Module autoTool = com.hades.client.HadesClient.getInstance().getModuleManager().getModule("AutoTool");
        if (autoTool != null && autoTool.isEnabled()) {
            com.hades.client.manager.InventoryManager im = com.hades.client.manager.InventoryManager.getInstance();
            com.hades.client.api.interfaces.IItemStack currentHeld = im.getSlot(im.getHeldItemSlot());
            if (currentHeld == null || currentHeld.isNull() || !currentHeld.getItem().isBlock() || currentHeld.getStackSize() <= 0) {
                int bestSlot = im.getBestBlockSlot();
                if (bestSlot != -1 && bestSlot != im.getHeldItemSlot()) {
                    im.setHeldItemSlot(bestSlot);
                }
            }
        }

        tickCounter++;
        
        // Search using CURRENT position
        currentTarget = searchBlockData();
        
        if (currentTarget != null) {
            if (HadesAPI.player.getHeldItem() == null) {
                decayRotation();
                return;
            }

            faceX = currentTarget.x + 0.5;
            faceY = currentTarget.y + 0.5;
            faceZ = currentTarget.z + 0.5;

            // Adjust hit vector towards the center to prevent precision floating-point misses in GrimAC
            switch (currentTarget.facing) {
                case 0: faceY = currentTarget.y;       break; 
                case 1: faceY = currentTarget.y + 1.0; break; 
                case 2: faceZ = currentTarget.z;       break; 
                case 3: faceZ = currentTarget.z + 1.0; break; 
                case 4: faceX = currentTarget.x;       break; 
                case 5: faceX = currentTarget.x + 1.0; break; 
            }

            // Add 0.05 inset offset for raytrace floating point collision buffer
            if (currentTarget.facing == 2) faceZ += 0.05;
            if (currentTarget.facing == 3) faceZ -= 0.05;
            if (currentTarget.facing == 4) faceX += 0.05;
            if (currentTarget.facing == 5) faceX -= 0.05;
            if (currentTarget.facing == 0) faceY += 0.05;
            if (currentTarget.facing == 1) faceY -= 0.05;

            double eyeX = HadesAPI.player.getX();
            double eyeY = HadesAPI.player.getY() + HadesAPI.player.getEyeHeight();
            double eyeZ = HadesAPI.player.getZ();

            double diffX = faceX - eyeX;
            double diffZ = faceZ - eyeZ;
            double diffY = faceY - eyeY;

            double horizDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
            float traceYaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f;
            float tracePitch = (float) -(Math.atan2(diffY, horizDist) * 180.0 / Math.PI);
            
            tracePitch = Math.max(-90.0f, Math.min(90.0f, tracePitch));
            tracePitch += ((tickCounter % 2 == 0) ? 0.001f : -0.001f);

            // Expose to MovementInputHook for MovementFix
            activeScaffoldYaw = traceYaw;
            activeScaffoldPitch = tracePitch;
            isSpoofing = true;

            // Vanilla rightClickMouse handles the native C08 BlockPlacement + Animation creation natively
            // Executing in TickEvent ensures C08 is dispatched before C03 is added to the Netty queue!
            // This cleanly bypasses the 'Post' flag without gambling on asynchronous S32 latency.
            HadesAPI.mc.setMouseOverBlock(faceX, faceY, faceZ, currentTarget.x, currentTarget.y, currentTarget.z, currentTarget.facing);
            HadesAPI.mc.performRightClick();
        } else {
            decayRotation();
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (HadesAPI.mc.isInGui() || HadesAPI.player == null) return;
        
        if (event.isPre()) {
            if (isSpoofing && activeScaffoldYaw != null && activeScaffoldPitch != null) {
                // Sync C03 packet perfectly with our block placement
                event.setYaw(activeScaffoldYaw);
                event.setPitch(activeScaffoldPitch);
            }
        } else if (event.isPost()) {
            // After C03 is sent, the server knows our new exact coordinates.
            serverX = HadesAPI.player.getX();
            serverY = HadesAPI.player.getY();
            serverZ = HadesAPI.player.getZ();
        }
    }

    private void decayRotation() {
        if (isSpoofing && activeScaffoldYaw != null && activeScaffoldPitch != null) {
            float playerYaw = HadesAPI.player.getYaw();
            float playerPitch = HadesAPI.player.getPitch();

            // Shift physical camera to Aura's coordinate space without breaking native GCD
            float diff = playerYaw - activeScaffoldYaw;
            while (diff <= -180f) { playerYaw += 360f; diff += 360f; }
            while (diff > 180f) { playerYaw -= 360f; diff -= 360f; }

            float[] decayed = com.hades.client.util.RotationUtil.smoothRotation(
                    activeScaffoldYaw, activeScaffoldPitch, playerYaw, playerPitch, 60.0f);
            
            decayed = com.hades.client.util.RotationUtil.applyGCD(decayed[0], decayed[1], activeScaffoldYaw, activeScaffoldPitch);
            activeScaffoldYaw = decayed[0];
            activeScaffoldPitch = decayed[1];

            if (Math.abs(activeScaffoldYaw - playerYaw) <= 3.0f) {
                isSpoofing = false;
                activeScaffoldYaw = null;
                activeScaffoldPitch = null;
                
                // Allow sprint native restoration inside hook if it was flipped backwards
                if (com.hades.client.hook.hooks.MoveEntityWithHeadingHook.droppedSprintForSideways) {
                    HadesAPI.player.setSprinting(true);
                    com.hades.client.hook.hooks.MoveEntityWithHeadingHook.droppedSprintForSideways = false;
                }
            } else {
                // Must ensure event rotation is sent during decay to prevent snappy desync
                if (com.hades.client.hook.hooks.UpdateWalkingPlayerHook.currentEvent != null) {
                    com.hades.client.hook.hooks.UpdateWalkingPlayerHook.currentEvent.setYaw(activeScaffoldYaw);
                    com.hades.client.hook.hooks.UpdateWalkingPlayerHook.currentEvent.setPitch(activeScaffoldPitch);
                }
            }
        } else {
            activeScaffoldYaw = null;
            activeScaffoldPitch = null;
        }
    }
    
    /**
     * Finds a block to place against. The target is always the space directly below
     * the player's feet. If that space is already solid, no placement needed.
     * 
     * The anchor block search validates GrimAC PositionPlace geometry:
     * the clicked face must be geometrically visible from the player's eye position.
     *
     * PositionPlace checks (from GrimAC source):
     *   UP    face: player.maxY < block.maxY  (player eyes BELOW block top — impossible for UP)
     *   DOWN  face: player.minY > block.minY
     *   NORTH face: player.minZ > block.minZ  (player SOUTH of block's north face)
     *   SOUTH face: player.maxZ < block.maxZ  (player NORTH of block's south face)  
     *   WEST  face: player.minX > block.minX  (player EAST of block's west face)
     *   EAST  face: player.maxX < block.maxX  (player WEST of block's east face)
     * 
     * Wait — PositionPlace flags when the condition IS true (face hidden).
     * So for NORTH (Z-): flags if eyePos.minZ > combined.minZ → we need eyePos.minZ <= combined.minZ
     * i.e., player Z must be <= block Z (north of the block) to click its NORTH face.
     * 
     * Actually re-reading PositionPlace source:
     *   NORTH → eyePositions.minZ > combined.minZ  → flag
     *   SOUTH → eyePositions.maxZ < combined.maxZ  → flag
     *   EAST  → eyePositions.maxX < combined.maxX  → flag
     *   WEST  → eyePositions.minX > combined.minX  → flag
     *   UP    → eyePositions.maxY < combined.maxY  → flag
     *   DOWN  → eyePositions.minY > combined.minY  → flag
     * 
     * So to NOT flag:
     *   NORTH (2): need eyePos.minZ <= combined.minZ  → playerZ <= blockZ
     *   SOUTH (3): need eyePos.maxZ >= combined.maxZ  → playerZ >= blockZ + 1
     *   WEST  (4): need eyePos.minX <= combined.minX  → playerX <= blockX
     *   EAST  (5): need eyePos.maxX >= combined.maxX  → playerX >= blockX + 1
     *   UP    (1): need eyePos.maxY >= combined.maxY  → eyeY >= blockY + 1
     *   DOWN  (0): need eyePos.minY <= combined.minY  → eyeY <= blockY
     */
    private BlockData searchBlockData() {
        double px = HadesAPI.player.getX();
        double py = HadesAPI.player.getY();
        double pz = HadesAPI.player.getZ();
        
        // Target: the air block directly below feet
        int bpx = (int) Math.floor(px);
        int bpy = (int) Math.floor(py) - 1;
        int bpz = (int) Math.floor(pz);
        
        // If already solid, nothing to place
        if (HadesAPI.world.isSolidBlock(bpx, bpy, bpz)) return null;
        
        // Include movementThreshold lenience (0.03 blocks for 1.8 idle packet)
        double threshold = 0.03;

        // PositionPlace bounds validation MUST use the SERVER'S perspective of our location.
        // Because we place the block in isPre() BEFORE the current tick's C03 arrives at the server,
        // processing uses our PREVIOUS tick's exact coordinates.
        if (HadesAPI.world.isSolidBlock(bpx, bpy - 1, bpz) && (serverY + threshold) >= bpy) {
            return new BlockData(bpx, bpy - 1, bpz, 1); // UP face
        }
        
        // EAST neighbor (bpx+1) → click WEST face (4)
        if (HadesAPI.world.isSolidBlock(bpx + 1, bpy, bpz) && (serverX - threshold) <= (bpx + 1)) {
            return new BlockData(bpx + 1, bpy, bpz, 4);
        }
        
        // WEST neighbor (bpx-1) → click EAST face (5)
        if (HadesAPI.world.isSolidBlock(bpx - 1, bpy, bpz) && (serverX + threshold) >= bpx) {
            return new BlockData(bpx - 1, bpy, bpz, 5);
        }
        
        // SOUTH neighbor (bpz+1) → click NORTH face (2)
        if (HadesAPI.world.isSolidBlock(bpx, bpy, bpz + 1) && (serverZ - threshold) <= (bpz + 1)) {
            return new BlockData(bpx, bpy, bpz + 1, 2);
        }
        
        // NORTH neighbor (bpz-1) → click SOUTH face (3) 
        if (HadesAPI.world.isSolidBlock(bpx, bpy, bpz - 1) && (serverZ + threshold) >= bpz) {
            return new BlockData(bpx, bpy, bpz - 1, 3);
        }
        
        return null;
    }
    
    private static class BlockData {
        public final int x, y, z, facing;
        public BlockData(int x, int y, int z, int facing) {
            this.x = x; this.y = y; this.z = z; this.facing = facing;
        }
    }
}

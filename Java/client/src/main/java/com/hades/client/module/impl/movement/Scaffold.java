package com.hades.client.module.impl.movement;

import com.hades.client.api.HadesAPI;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.TickEvent;
import com.hades.client.event.events.MotionEvent;
import com.hades.client.module.Module;

public class Scaffold extends Module {
    private BlockData currentTarget;

    // Per-tick micro-jitter to guarantee Vanilla always sends C06 (rot=true) not
    // C04
    // Vanilla sends C04 when rotationYaw == lastReportedYaw && rotationPitch ==
    // lastReportedPitch
    // By alternating pitch by 0.001° each tick, d4 != 0 → flag3=true → C06 always
    private int tickCounter = 0;
    private float toweringYawDrift = 0.0f;
    private float toweringPitchDrift = 0.0f;

    private static Float activeScaffoldYaw = null;
    private static Float activeScaffoldPitch = null;
    private boolean isSpoofing = false;
    private boolean safeWalkSneaking = false;

    // Polar mode: sprint suppression state
    private boolean didSuppressSprint = false;

    // Polar mode: placement delay to break inhuman consistency
    private int placeDelay = 0;
    private int nextPlaceDelay = 0;

    // --- Pre-computed block scan offsets to eliminate per-tick array allocations
    // and sorting ---
    private static final java.util.List<int[]> CLUTCH_OFFSETS = new java.util.ArrayList<>();
    private static final java.util.List<int[]> NORMAL_OFFSETS = new java.util.ArrayList<>();

    static {
        for (int dy = 3; dy >= -3; dy--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    CLUTCH_OFFSETS.add(new int[] { dx, dy, dz });
                }
            }
        }
        CLUTCH_OFFSETS
                .sort(java.util.Comparator.comparingDouble(o -> Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2])));
        NORMAL_OFFSETS.add(new int[] { 0, 0, 0 });
    }

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

    private final com.hades.client.module.setting.BooleanSetting safeWalk = new com.hades.client.module.setting.BooleanSetting("Safewalk", false);
    private final com.hades.client.module.setting.ModeSetting mode = new com.hades.client.module.setting.ModeSetting("Mode", "GrimAC", "GrimAC", "Intave", "Polar");

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under your feet", Module.Category.MOVEMENT, 0);
        register(mode);
        register(safeWalk);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        currentTarget = null;
        tickCounter = 0;
        toweringYawDrift = 0.0f;
        toweringPitchDrift = 0.0f;
        didSuppressSprint = false;
        placeDelay = 0;
        nextPlaceDelay = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
        activeScaffoldYaw = null;
        activeScaffoldPitch = null;
        isSpoofing = false;
        safeWalkSneaking = false;
        HadesAPI.mc.setKeySneakPressed(false);

        // Polar: restore sprint if we suppressed it
        if (didSuppressSprint) {
            HadesAPI.player.setSprinting(true);
            didSuppressSprint = false;
        }
    }

    private double faceX, faceY, faceZ;

    @EventHandler
    public void onTick(TickEvent event) {
        if (HadesAPI.mc.isInGui() || HadesAPI.player == null || HadesAPI.world == null)
            return;

        if (safeWalk.getValue()) {
            if (HadesAPI.player.isOnGround()) {
                int bpX = (int) Math.floor(HadesAPI.player.getX());
                int bpY = (int) Math.floor(HadesAPI.player.getY());
                int bpZ = (int) Math.floor(HadesAPI.player.getZ());
                
                if (HadesAPI.world.isAirBlock(bpX, bpY - 1, bpZ)) {
                    if (!safeWalkSneaking) {
                        HadesAPI.mc.setKeySneakPressed(true);
                        safeWalkSneaking = true;
                    }
                } else {
                    if (safeWalkSneaking) {
                        HadesAPI.mc.setKeySneakPressed(false);
                        safeWalkSneaking = false;
                    }
                }
            } else {
                if (safeWalkSneaking) {
                    HadesAPI.mc.setKeySneakPressed(false);
                    safeWalkSneaking = false;
                }
            }
        } else if (safeWalkSneaking) {
            HadesAPI.mc.setKeySneakPressed(false);
            safeWalkSneaking = false;
        }

        // AutoTool Integration
        com.hades.client.module.Module autoTool = com.hades.client.HadesClient.getInstance().getModuleManager()
                .getModule("AutoTool");
        if (autoTool != null && autoTool.isEnabled()) {
            com.hades.client.manager.InventoryManager im = com.hades.client.manager.InventoryManager.getInstance();
            com.hades.client.api.interfaces.IItemStack currentHeld = im.getSlot(im.getHeldItemSlot());
            if (currentHeld == null || currentHeld.isNull() || !currentHeld.getItem().isBlock()
                    || currentHeld.getStackSize() <= 0) {
                int bestSlot = im.getBestBlockSlot();
                if (bestSlot != -1 && bestSlot != im.getHeldItemSlot()) {
                    im.setHeldItemSlot(bestSlot);
                }
            }
        }

        com.hades.client.manager.InventoryManager im = com.hades.client.manager.InventoryManager.getInstance();
        com.hades.client.api.interfaces.IItemStack currentHeld = im.getSlot(im.getHeldItemSlot());
        if (currentHeld == null || currentHeld.isNull() || !currentHeld.getItem().isBlock()
                || currentHeld.getStackSize() <= 0) {
            currentTarget = null;
            decayRotation();
            return;
        }

        tickCounter++;

        // Calculate dimensional movement deltas
        double dX = HadesAPI.player.getX() - HadesAPI.player.getPrevX();
        double dY = HadesAPI.player.getY() - HadesAPI.player.getPrevY();
        double dZ = HadesAPI.player.getZ() - HadesAPI.player.getPrevZ();

        // If the player is perfectly still (or only visually rotating their camera), do
        // not begin scaffolding
        // This prevents instantly placing blocks when standing motionless on the very
        // edge of a collision box
        if (Math.abs(dX) < 0.001 && Math.abs(dY) < 0.001 && Math.abs(dZ) < 0.001) {
            currentTarget = null;
            decayRotation();
            return;
        }

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

            // Adjust hit vector towards the center to prevent precision floating-point
            // misses in GrimAC
            switch (currentTarget.facing) {
                case 0:
                    faceY = currentTarget.y;
                    break;
                case 1:
                    faceY = currentTarget.y + 1.0;
                    break;
                case 2:
                    faceZ = currentTarget.z;
                    break;
                case 3:
                    faceZ = currentTarget.z + 1.0;
                    break;
                case 4:
                    faceX = currentTarget.x;
                    break;
                case 5:
                    faceX = currentTarget.x + 1.0;
                    break;
            }

            // Add 0.05 inset offset for raytrace floating point collision buffer
            if (currentTarget.facing == 2)
                faceZ += 0.05;
            if (currentTarget.facing == 3)
                faceZ -= 0.05;
            if (currentTarget.facing == 4)
                faceX += 0.05;
            if (currentTarget.facing == 5)
                faceX -= 0.05;
            if (currentTarget.facing == 0)
                faceY += 0.05;
            if (currentTarget.facing == 1)
                faceY -= 0.05;

            double eyeX = HadesAPI.player.getX();
            double eyeY = HadesAPI.player.getY() + HadesAPI.player.getEyeHeight();
            double eyeZ = HadesAPI.player.getZ();

            double diffX = faceX - eyeX;
            double diffZ = faceZ - eyeZ;
            double diffY = faceY - eyeY;

            double horizDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
            float traceYaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f;
            float tracePitch = (float) -(Math.atan2(diffY, horizDist) * 180.0 / Math.PI);

            // Towering rotation override (jumping straight up without horizontal movement)
            if (Math.abs(dX) < 0.05 && Math.abs(dZ) < 0.05 && Math.abs(dY) > 0.001) {
                tracePitch = 90.0f;
                
                // Drift by a random amount each tick, guaranteeing a minimum integer mouse delta pixel footprint
                float yawDriftDelta = (float) (Math.random() * 2.0 - 1.0) * 1.5f;
                // Minimum 0.6f offset guarantees it overcomes the GCD mouse step floor flawlessly
                if (Math.abs(yawDriftDelta) < 0.6f) yawDriftDelta = Math.signum(yawDriftDelta == 0 ? 1 : yawDriftDelta) * 0.6f;
                
                float pitchDriftDelta = (float) (Math.random() * 2.0 - 1.0) * 1.5f;
                if (Math.abs(pitchDriftDelta) < 0.6f) pitchDriftDelta = Math.signum(pitchDriftDelta == 0 ? 1 : pitchDriftDelta) * 0.6f;
                
                toweringYawDrift += yawDriftDelta;
                toweringPitchDrift += pitchDriftDelta;
                
                // Prevent the vibration offset from physically leaving the block surface boundary 
                if (toweringYawDrift > 20.0f) toweringYawDrift = 20.0f;
                if (toweringYawDrift < -20.0f) toweringYawDrift = -20.0f;
                if (toweringPitchDrift > 0.0f) toweringPitchDrift = 0.0f; // Cannot look past straight-down (90.0)
                if (toweringPitchDrift < -12.0f) toweringPitchDrift = -12.0f;
                
                traceYaw += toweringYawDrift;
                tracePitch += toweringPitchDrift;
            }

            tracePitch = Math.max(-90.0f, Math.min(90.0f, tracePitch));
            
            // Provide a fluid visual float jitter that surpasses typical GCD zeroes even when static
            if (Math.abs(dY) <= 0.001) {
                traceYaw += ((tickCounter % 2 == 0) ? 0.38f : -0.38f);
                tracePitch += ((tickCounter % 2 == 0) ? 0.38f : -0.38f);
            }

            // ALWAYS apply GCD to the rotations so AimModulo360 does not flag from raw Float casting
            float prevYaw = activeScaffoldYaw != null ? activeScaffoldYaw : HadesAPI.player.getYaw();
            float prevPitch = activeScaffoldPitch != null ? activeScaffoldPitch : HadesAPI.player.getPitch();

            float[] gcdFixed = com.hades.client.util.RotationUtil.applyGCD(traceYaw, tracePitch, prevYaw, prevPitch);
            traceYaw = gcdFixed[0];
            tracePitch = gcdFixed[1];

            // Vanilla clamps Pitch AFTER GCD is applied
            tracePitch = Math.max(-90.0f, Math.min(90.0f, tracePitch));

            // Expose to MovementInputHook for MovementFix
            activeScaffoldYaw = traceYaw;
            activeScaffoldPitch = tracePitch;
            isSpoofing = true;

            // ── Polar: Sprint suppression ──
            // A movement simulation engine validates that sprint is only possible when moveForward > 0
            // relative to the SERVER-SIDE yaw. If we're sending a yaw that faces backwards (>90° diff
            // from our actual movement direction), sprinting is physically impossible and must be dropped.
            // This sends C0B STOP_SPRINTING before the C03, which is exactly what vanilla does.
            if (mode.getValue().equals("Polar")) {
                float playerYaw = HadesAPI.player.getYaw();
                float yawDiff = Math.abs(com.hades.client.util.RotationUtil.getAngleDifference(traceYaw, playerYaw));
                
                if (yawDiff > 90.0f && HadesAPI.player.isSprinting()) {
                    // Our spoofed yaw faces away from movement direction — sprint is impossible
                    HadesAPI.player.setSprinting(false);
                    didSuppressSprint = true;
                } else if (didSuppressSprint && yawDiff <= 90.0f) {
                    // Yaw returned to forward — safe to re-enable sprint
                    HadesAPI.player.setSprinting(true);
                    didSuppressSprint = false;
                }
            }

            // ── Mode-specific block placement ──
            // GrimAC: C08 must fire BEFORE C03 (vanilla rightClickMouse in TickEvent)
            // Intave/Polar: C03 must fire BEFORE C08 (placed in onMotion POST)
            if (mode.getValue().equals("GrimAC")) {
                HadesAPI.mc.setMouseOverBlock(faceX, faceY, faceZ, currentTarget.x, currentTarget.y, currentTarget.z, currentTarget.facing);
                HadesAPI.mc.performRightClick();
            }
            // Polar placement happens in onMotion(POST) with delay gating
        } else {
            decayRotation();

            // Polar: restore sprint when not actively scaffolding
            if (mode.getValue().equals("Polar") && didSuppressSprint) {
                HadesAPI.player.setSprinting(true);
                didSuppressSprint = false;
            }
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (HadesAPI.mc.isInGui() || HadesAPI.player == null)
            return;

        if (event.isPre()) {
            if (isSpoofing && activeScaffoldYaw != null && activeScaffoldPitch != null) {
                // Sync C03 packet perfectly with our block placement
                event.setYaw(activeScaffoldYaw);
                event.setPitch(activeScaffoldPitch);
            }
        } else if (event.isPost()) {
            if (mode.getValue().equals("Intave") && isSpoofing && currentTarget != null) {
                // Intave specifically validates RayTrace boundaries relative to the exact
                // Player coordinates/rotations the server *last securely mapped*.
                // Pushing rightClick into the Post boundary ensures the C03 dispatch happened FIRST
                HadesAPI.mc.setMouseOverBlock(faceX, faceY, faceZ, currentTarget.x, currentTarget.y, currentTarget.z, currentTarget.facing);
                HadesAPI.mc.performRightClick();
            } else if (mode.getValue().equals("Polar") && isSpoofing && currentTarget != null) {
                // Polar: Post-C03 placement with delay gating
                // C03 has already been sent during this tick — the server knows our position+rotation.
                // Now we place the block, but only if the placement delay has elapsed.
                if (placeDelay <= 0) {
                    HadesAPI.mc.setMouseOverBlock(faceX, faceY, faceZ, currentTarget.x, currentTarget.y, currentTarget.z, currentTarget.facing);
                    HadesAPI.mc.performRightClick();
                    // Randomized 1-2 tick delay to break inhuman consistency
                    nextPlaceDelay = 1 + (int)(Math.random() * 2); // 1 or 2 ticks
                    placeDelay = nextPlaceDelay;
                } else {
                    placeDelay--;
                }
            }
        }
    }

    private void decayRotation() {
        if (isSpoofing && activeScaffoldYaw != null && activeScaffoldPitch != null) {
            float playerYaw = HadesAPI.player.getYaw();
            float playerPitch = HadesAPI.player.getPitch();

            // Shift physical camera to Aura's coordinate space without breaking native GCD
            float diff = playerYaw - activeScaffoldYaw;
            while (diff <= -180f) {
                playerYaw += 360f;
                diff += 360f;
            }
            while (diff > 180f) {
                playerYaw -= 360f;
                diff -= 360f;
            }

            float speed = mode.getValue().equals("Intave") ? 15.0f : (mode.getValue().equals("Polar") ? 30.0f : 60.0f);
            float[] decayed = com.hades.client.util.RotationUtil.smoothRotation(
                    activeScaffoldYaw, activeScaffoldPitch, playerYaw, playerPitch, speed);

            decayed = com.hades.client.util.RotationUtil.applyGCD(decayed[0], decayed[1], activeScaffoldYaw,
                    activeScaffoldPitch);
            activeScaffoldYaw = decayed[0];
            activeScaffoldPitch = decayed[1];

            // Vanilla strictly clamps Pitch after GCD grid evaluation
            activeScaffoldPitch = Math.max(-90.0f, Math.min(90.0f, activeScaffoldPitch));

            if (Math.abs(activeScaffoldYaw - playerYaw) <= 3.0f) {
                // Instantly snap the physical camera to the final GCD-aligned spoofed rotation.
                // This guarantees the very next genuine mouse tick natively inherits the mathematical grid,
                // seamlessly bypassing AimModulo360 disconnects.
                HadesAPI.player.setYaw(activeScaffoldYaw);
                HadesAPI.player.setPitch(activeScaffoldPitch);

                isSpoofing = false;
                activeScaffoldYaw = null;
                activeScaffoldPitch = null;
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
     * the clicked face must be geometrically visible from the player's eye
     * position.
     *
     * PositionPlace checks (from GrimAC source):
     * UP face: player.maxY < block.maxY (player eyes BELOW block top — impossible
     * for UP)
     * DOWN face: player.minY > block.minY
     * NORTH face: player.minZ > block.minZ (player SOUTH of block's north face)
     * SOUTH face: player.maxZ < block.maxZ (player NORTH of block's south face)
     * WEST face: player.minX > block.minX (player EAST of block's west face)
     * EAST face: player.maxX < block.maxX (player WEST of block's east face)
     * 
     * Wait — PositionPlace flags when the condition IS true (face hidden).
     * So for NORTH (Z-): flags if eyePos.minZ > combined.minZ → we need eyePos.minZ
     * <= combined.minZ
     * i.e., player Z must be <= block Z (north of the block) to click its NORTH
     * face.
     * 
     * Actually re-reading PositionPlace source:
     * NORTH → eyePositions.minZ > combined.minZ → flag
     * SOUTH → eyePositions.maxZ < combined.maxZ → flag
     * EAST → eyePositions.maxX < combined.maxX → flag
     * WEST → eyePositions.minX > combined.minX → flag
     * UP → eyePositions.maxY < combined.maxY → flag
     * DOWN → eyePositions.minY > combined.minY → flag
     * 
     * So to NOT flag:
     * NORTH (2): need eyePos.minZ <= combined.minZ → playerZ <= blockZ
     * SOUTH (3): need eyePos.maxZ >= combined.maxZ → playerZ >= blockZ + 1
     * WEST (4): need eyePos.minX <= combined.minX → playerX <= blockX
     * EAST (5): need eyePos.maxX >= combined.maxX → playerX >= blockX + 1
     * UP (1): need eyePos.maxY >= combined.maxY → eyeY >= blockY + 1
     * DOWN (0): need eyePos.minY <= combined.minY → eyeY <= blockY
     */
    private BlockData searchBlockData() {
        double px = HadesAPI.player.getX();
        double py = HadesAPI.player.getY();
        double pz = HadesAPI.player.getZ();

        int bpx = (int) Math.floor(px);
        int bpy = (int) Math.floor(py) - 1;
        int bpz = (int) Math.floor(pz);

        if (HadesAPI.world.isSolidBlock(bpx, bpy, bpz))
            return null;

        boolean clutchMode = com.hades.client.module.impl.movement.Clutch.isClutchActive();
        java.util.List<int[]> offsets = clutchMode ? CLUTCH_OFFSETS : NORMAL_OFFSETS;

        // Now search efficiently and exit immediately when the closest valid block is
        // found
        for (int[] offset : offsets) {
            int dx = offset[0];
            int dy = offset[1];
            int dz = offset[2];

            int targetAirX = bpx + dx;
            int targetAirY = bpy + dy;
            int targetAirZ = bpz + dz;

            if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY, targetAirZ))
                continue;

            // UP face
            if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY - 1, targetAirZ)) {
                return new BlockData(targetAirX, targetAirY - 1, targetAirZ, 1);
            }
            // EAST face (neighbor is West)
            if (HadesAPI.world.isSolidBlock(targetAirX - 1, targetAirY, targetAirZ)) {
                return new BlockData(targetAirX - 1, targetAirY, targetAirZ, 5);
            }
            // WEST face (neighbor is East)
            if (HadesAPI.world.isSolidBlock(targetAirX + 1, targetAirY, targetAirZ)) {
                return new BlockData(targetAirX + 1, targetAirY, targetAirZ, 4);
            }
            // SOUTH face (neighbor is North)
            if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY, targetAirZ - 1)) {
                return new BlockData(targetAirX, targetAirY, targetAirZ - 1, 3);
            }
            // NORTH face (neighbor is South)
            if (HadesAPI.world.isSolidBlock(targetAirX, targetAirY, targetAirZ + 1)) {
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

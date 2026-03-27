package com.hades.client.util;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;

import java.lang.reflect.Method;

public class RayTraceUtil {

    private static Class<?> vec3Class;
    private static Method rayTraceBlocksMethod;

    static {
        try {
            vec3Class = ReflectionUtil.findClass("net.minecraft.util.Vec3", "aui");
            Class<?> worldClass = ReflectionUtil.findClass("net.minecraft.world.World", "adm");

            if (worldClass != null && vec3Class != null) {
                // rayTraceBlocks(Vec3, Vec3, boolean, boolean, boolean)
                rayTraceBlocksMethod = ReflectionUtil.findMethod(worldClass, new String[] {
                    "a", "rayTraceBlocks", "func_147447_a"
                }, vec3Class, vec3Class, boolean.class, boolean.class, boolean.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if there is a clear line of sight (no blocks in the way) between the player's eyes 
     * and a specific point on the target entity.
     * 
     * @param target The target entity to test.
     * @param YOffset The vertical offset on the target entity to check (e.g. 0.0 for feet, target.getHeight() / 2.0 for chest).
     * @return true if the ray trace hits nothing (clean line of sight), false if blocked.
     */
    public static boolean canSeeEntity(IEntity target, double YOffset) {
        // Remove IMinecraft dependency since we use world wrapper
        if (HadesAPI.player == null || HadesAPI.world == null || HadesAPI.world.getRaw() == null || target == null || rayTraceBlocksMethod == null || vec3Class == null) {
            return false;
        }

        try {
            Object world = HadesAPI.world.getRaw();
            if (world == null) return false;
            
            // Create Vec3 for player eye position
            double px = HadesAPI.player.getX();
            // Fallback to 1.62 eye height if reflection fails
            double eyeHeight = 1.62;
            try {
                Object rawPlayer = HadesAPI.player.getRaw();
                Method getEyeHeight = ReflectionUtil.findMethod(rawPlayer.getClass(), new String[]{"aQ", "getEyeHeight", "func_70047_e"});
                if (getEyeHeight != null) {
                    eyeHeight = (float) getEyeHeight.invoke(rawPlayer);
                }
            } catch (Exception ignored) {}
            double py = HadesAPI.player.getY() + eyeHeight;
            double pz = HadesAPI.player.getZ();
            Object startVec = vec3Class.getConstructor(double.class, double.class, double.class).newInstance(px, py, pz);

            // Create Vec3 for target position + offset
            double tx = target.getX();
            double ty = target.getY() + YOffset;
            double tz = target.getZ();
            Object endVec = vec3Class.getConstructor(double.class, double.class, double.class).newInstance(tx, ty, tz);

            // rayTraceBlocks(start, end, stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock)
            Object hitResult = rayTraceBlocksMethod.invoke(world, startVec, endVec, false, true, false);

            // Return true if hitResult is NULL (line of sight is clear)
            return hitResult == null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper to get rotations to a specific point in space.
     */
    public static float[] getRotationsTo(double px, double py, double pz, double tx, double ty, double tz) {
        double dx = tx - px;
        double dy = ty - py;
        double dz = tz - pz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        
        return new float[] { yaw, pitch };
    }
}

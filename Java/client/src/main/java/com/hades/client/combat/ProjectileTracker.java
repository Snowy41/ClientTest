package com.hades.client.combat;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.util.MathUtil;

public class ProjectileTracker {

    /**
     * Calculates the best {yaw, pitch} to shoot an arrow at the target.
     * @param target The target entity
     * @param charge The bow charge ratio (0.0 to 1.0)
     * @param predict Whether to predict target movement over flight time
     * @return float array containing yaw and pitch
     */
    public static float[] calculateBowAim(IEntity target, float charge, boolean predict) {
        // Arrow velocity formula from Vanilla MC
        float velocity = charge * 3.0f; 
        if (velocity < 0.1f) velocity = 0.1f;
        
        // Gravity for arrow is 0.05
        float gravity = 0.05f;
        
        double pX = HadesAPI.player.getX();
        double pY = HadesAPI.player.getY() + HadesAPI.Player.getEyeHeight(); 
        double pZ = HadesAPI.player.getZ();

        double tX = target.getX();
        double tY = target.getY() + (target.getHeight() / 2.0); // aim at center
        double tZ = target.getZ();

        double predictedX = tX;
        double predictedY = tY;
        double predictedZ = tZ;

        if (predict) {
            double vX = target.getX() - target.getPrevX();
            // double vY = target.getY() - target.getPrevY(); // Omitted for prediction stability
            double vZ = target.getZ() - target.getPrevZ();

            double distance = MathUtil.distance3D(pX, pY, pZ, predictedX, predictedY, predictedZ);
            double travelTime = distance / velocity; // Approximate travel time in ticks

            for (int i = 0; i < (int) travelTime; i++) {
                predictedX += vX;
                // Exclude Y from prediction to avoid shooting into the ground/air if they jumped
                predictedZ += vZ;
            }
        }

        double diffX = predictedX - pX;
        double diffY = predictedY - pY;
        double diffZ = predictedZ - pZ;

        double distXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        // Physics calculation for required pitch
        // v^4 - g*(g*x^2 + 2*y*v^2)
        double v2 = velocity * velocity;
        double v4 = v2 * v2;
        double root = v4 - gravity * (gravity * distXZ * distXZ + 2.0 * diffY * v2);

        float pitch;
        if (root < 0) {
            // Target is out of range or unreachable with current charge, just aim highest
            pitch = (float) -(Math.atan2(diffY, distXZ) * 180.0 / Math.PI);
        } else {
            // High arc vs Low arc; we choose low arc (subtracting the root)
            pitch = (float) -(Math.atan((v2 - Math.sqrt(root)) / (gravity * distXZ)) * 180.0 / Math.PI);
        }

        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI - 90.0);

        return new float[]{yaw, pitch};
    }
}

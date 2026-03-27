package com.hades.client.util;

import java.util.concurrent.ThreadLocalRandom;
import java.security.SecureRandom;

public class MathUtil {

    public static double randomDouble(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static float randomFloat(float min, float max) {
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static float nextSecureFloat(double min, double max) {
        return (float) (min + (new SecureRandom().nextFloat() * (max - min)));
    }

    public static int nextSecureInt(int min, int max) {
        return min + new SecureRandom().nextInt(max - min + 1);
    }

    public static double randomSin() {
        return Math.sin(System.currentTimeMillis() / 1000.0) * ThreadLocalRandom.current().nextDouble(0.01, 0.05);
    }

    public static double nextGaussian() {
        return ThreadLocalRandom.current().nextGaussian();
    }

    public static long nextLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Calculates distance between two 3D points.
     */
    public static double distance3D(double x1, double y1, double z1,
                                    double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
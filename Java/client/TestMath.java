public class TestMath {
    public static void main(String[] args) {
        test(1, 0, 0, 90, "W (0) -> West (90)");
        test(0, 1, 0, 90, "A (0) -> West (90)");
        test(-1, 0, 0, 90, "S (0) -> West (90)");
        test(0, -1, 0, 90, "D (0) -> West (90)");
        
        test(1, 1, 0, 90, "WA (0) -> West (90)");
        test(1, -1, 0, 90, "WD (0) -> West (90)");
    }
    
    static void test(float forward, float strafe, float playerYaw, float targetYaw, String name) {
        double moveAngle = Math.atan2(strafe, forward);
        
        // This math is exactly correct to map physical inputs
        double absoluteAngle = playerYaw - Math.toDegrees(moveAngle);
        double targetAngle = Math.toRadians(absoluteAngle - targetYaw);
        
        float newForward = (float) Math.round(Math.cos(targetAngle));
        // sin needs to be inverted because strafe is inverted
        float newStrafe = (float) Math.round(-Math.sin(targetAngle));
        
        System.out.printf("%s: Output(F=%.0f, S=%.0f)\n", name, newForward, newStrafe);
    }
}

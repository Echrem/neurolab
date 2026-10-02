package lab.neurolab.brain;

/** Small deterministic policies for sensory guidance, separated for repeatable tests. */
public final class FlyNavigation {
    public enum LightMode { BALANCED, SEEK_LIGHT, SEEK_SHADE, OFF }

    private FlyNavigation() {}

    /** Positive is a right turn; bilateral rays provide the light gradient. */
    public static double phototaxisTurn(double left, double right, double ambient,
                                        LightMode mode, double strength) {
        left = unit(left);
        right = unit(right);
        ambient = unit(ambient);
        strength = unit(strength);
        double preference = switch (mode) {
            case OFF -> 0;
            case SEEK_LIGHT -> 1;
            case SEEK_SHADE -> -1;
            case BALANCED -> ambient < 0.35 ? 1 : ambient > 0.82 ? -0.55 : 0.15;
        };
        return clamp((right - left) * preference * strength, -1, 1);
    }

    public static boolean mayPerch(boolean enabled, boolean threat, boolean raining,
                                   boolean onCooldown, double distance) {
        return enabled && !threat && !raining && !onCooldown && Double.isFinite(distance)
                && distance >= 0 && distance <= 1.25;
    }

    /** Hostile/player bodies are threats at range; passive bodies loom only when rapidly closing and near. */
    public static double loomingStrength(double distance, boolean closing, boolean dangerous) {
        if (!Double.isFinite(distance) || distance < 0 || distance >= 3.5) return 0;
        if (!dangerous && (!closing || distance > 2.0)) return 0;
        return (1 - distance / 3.5) * (closing ? 1.0 : 0.35);
    }

    /** Reduce approach speed continuously with distance, preserving the decoded throttle ceiling. */
    public static double approachThrottle(double decodedThrottle, double distance, double cueStrength) {
        double throttle = unit(decodedThrottle);
        if (!Double.isFinite(distance) || distance <= 0) return 0;
        distance = Math.min(16, distance);
        cueStrength = unit(cueStrength);
        double cueLimit = 0.20 + cueStrength * 0.60;
        double contactLimit = distance * 0.18;
        return Math.min(throttle, Math.min(cueLimit, contactLimit));
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? clamp(value, 0, 1) : 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

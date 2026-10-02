package lab.neurolab.brain;

/** Keeps game-body fallback behavior separate from neural motor activity. */
public final class EmbodimentDecoder {
    public record Command(double forward, double turn, double lift, boolean escape, boolean reflex) {}
    /** Hand-built world-to-body guidance, kept separate from connectome motor output. */
    public record WorldCue(double targetTurn, double targetStrength, double targetDistance,
                           double obstacle, double avoidanceTurn, double threatTurn, double threatStrength) {
        public static final WorldCue NONE = new WorldCue(0, 0, 16, 0, 0, 0, 0);
        public WorldCue(double targetTurn, double targetStrength, double targetDistance,
                        double obstacle, double avoidanceTurn) {
            this(targetTurn, targetStrength, targetDistance, obstacle, avoidanceTurn, 0, 0);
        }
        public WorldCue {
            targetTurn = clamp(targetTurn, -1, 1);
            targetStrength = clamp(targetStrength, 0, 1);
            targetDistance = clamp(targetDistance, 0, 16);
            obstacle = clamp(obstacle, 0, 1);
            avoidanceTurn = clamp(avoidanceTurn, -1, 1);
            threatTurn = clamp(threatTurn, -1, 1);
            threatStrength = clamp(threatStrength, 0, 1);
        }
        private static double clamp(double value, double min, double max) {
            return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : 0;
        }
    }

    private EmbodimentDecoder() {}

    public static Command decode(FlyBrain.Snapshot neural, FlyBrain.Drive senses, long tick, long identitySeed) {
        return decode(neural, senses, tick, identitySeed, ControlMode.ASSISTED);
    }

    public static Command decode(FlyBrain.Snapshot neural, FlyBrain.Drive senses, long tick,
                                 long identitySeed, ControlMode mode) {
        return decode(neural, senses, tick, identitySeed, mode, WorldCue.NONE);
    }

    public static Command decode(FlyBrain.Snapshot neural, FlyBrain.Drive senses, long tick,
                                 long identitySeed, ControlMode mode, WorldCue world) {
        if (!mode.controlsBody()) return new Command(0, 0, 0, false, false);
        if (mode == ControlMode.NEURAL)
            return new Command(neural.forward(), neural.turn(), neural.lift(), neural.escape(), false);
        Command base;
        double threat = Math.max(senses.looming(), Math.max(senses.pain(),
                senses.tactile() > 0.5f && world.threatStrength() > 0.25 ? 0.75 : 0));
        if (threat > 0.28) {
            double escapeTurn = world.threatStrength() > 0.05 ? world.threatTurn()
                    : ((identitySeed & 1L) == 0 ? 1 : -1);
            base = new Command(1.0, escapeTurn, 0.9, true, true);
        } else if (neural.forward() > 0 || Math.abs(neural.turn()) > 0 || neural.lift() > 0 || neural.escape()) {
            base = new Command(neural.forward(), neural.turn(), neural.lift(), neural.escape(), false);
        } else {
            double handedness = (identitySeed & 1L) == 0 ? 1 : -1;
            if (senses.looming() > 0.25f || senses.tactile() > 0.5f) {
                base = new Command(0.65, handedness * 0.7, 0.9, true, true);
            } else {
                double phase = tick * 0.035 + (identitySeed & 255) * 0.07;
                double forward = senses.odor() > 0.05f || senses.taste() > 0.05f ? 0.36 : 0.48;
                base = new Command(forward, Math.sin(phase) * 0.32, 0, false, true);
            }
        }
        return applyWorldCue(base, world);
    }

    private static Command applyWorldCue(Command base, WorldCue world) {
        double target = world.targetTurn() * world.targetStrength();
        double turn = clamp(base.turn() + target * 0.55 + world.avoidanceTurn() * world.obstacle() * 0.85
                        + world.threatTurn() * world.threatStrength() * 0.45,
                -1, 1);
        double forward = base.forward();
        if (world.targetStrength() > 0.05) {
            double approachSpeed = (0.12 + world.targetStrength() * 0.36)
                    * Math.min(1, Math.max(0, (world.targetDistance() - 1.0) / 2.5));
            forward = Math.max(base.forward() * 0.65, approachSpeed);
            if (world.targetDistance() < 1.1) forward = Math.min(forward, 0.04);
        }
        forward *= 1 - world.obstacle() * (base.escape() ? 0.30 : 0.96);
        if (base.escape()) forward = Math.max(forward, 0.8);
        double lift = Math.max(base.lift(), world.obstacle() > 0.78 ? 0.42 : 0);
        boolean guided = world.targetStrength() > 0.05 || world.obstacle() > 0.08;
        return new Command(forward, turn, lift, base.escape(), base.reflex() || guided);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

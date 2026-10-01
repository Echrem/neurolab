package lab.neurolab.brain;

/** Keeps game-body fallback behavior separate from neural motor activity. */
public final class EmbodimentDecoder {
    public record Command(double forward, double turn, double lift, boolean escape, boolean reflex) {}

    private EmbodimentDecoder() {}

    public static Command decode(FlyBrain.Snapshot neural, FlyBrain.Drive senses, long tick, long identitySeed) {
        if (neural.forward() > 0 || Math.abs(neural.turn()) > 0 || neural.lift() > 0 || neural.escape())
            return new Command(neural.forward(), neural.turn(), neural.lift(), neural.escape(), false);

        double handedness = (identitySeed & 1L) == 0 ? 1 : -1;
        if (senses.looming() > 0.25f || senses.tactile() > 0.5f)
            return new Command(0.65, handedness * 0.7, 0.9, true, true);

        double phase = tick * 0.035 + (identitySeed & 255) * 0.07;
        double forward = senses.odor() > 0.05f || senses.taste() > 0.05f ? 0.24 : 0.14;
        return new Command(forward, Math.sin(phase) * 0.18, 0, false, true);
    }
}

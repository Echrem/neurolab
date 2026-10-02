package lab.neurolab.brain;

import java.util.EnumMap;

/** Server-tick pulse schedule. Test input augments natural senses, never motor channels. */
public final class StimulusSchedule {
    public enum Sense { EYE, LEFT_EYE, RIGHT_EYE, LOOMING, TOUCH, ODOR, TASTE }
    private record Pulse(float strength, long expiresAt) {}
    private final EnumMap<Sense, Pulse> active = new EnumMap<>(Sense.class);

    public void start(Sense sense, float strength, int durationTicks, long tick) {
        if (!Float.isFinite(strength) || strength < 0 || strength > 1)
            throw new IllegalArgumentException("Stimulus strength must be between 0 and 1.");
        if (durationTicks < 1 || durationTicks > 1200)
            throw new IllegalArgumentException("Stimulus duration must be between 1 and 1200 ticks.");
        if (strength == 0) active.remove(sense);
        else active.put(sense, new Pulse(strength, Math.addExact(tick, durationTicks)));
    }

    public int clear() { int count = active.size(); active.clear(); return count; }
    public int activeCount(long tick) { expire(tick); return active.size(); }
    private void expire(long tick) { active.entrySet().removeIf(e -> e.getValue().expiresAt() <= tick); }

    public FlyBrain.Drive apply(FlyBrain.Drive natural, long tick) {
        expire(tick);
        float eye = strength(Sense.EYE);
        return new FlyBrain.Drive(Math.max(natural.light(), eye),
                Math.max(natural.leftEye(), Math.max(eye, strength(Sense.LEFT_EYE))),
                Math.max(natural.rightEye(), Math.max(eye, strength(Sense.RIGHT_EYE))),
                Math.max(natural.looming(), strength(Sense.LOOMING)),
                Math.max(natural.tactile(), strength(Sense.TOUCH)),
                Math.max(natural.odor(), strength(Sense.ODOR)),
                Math.max(natural.taste(), strength(Sense.TASTE)), natural.pain(), natural.reward());
    }

    private float strength(Sense sense) {
        Pulse pulse = active.get(sense);
        return pulse == null ? 0 : pulse.strength();
    }
}

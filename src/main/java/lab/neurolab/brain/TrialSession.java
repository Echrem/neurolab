package lab.neurolab.brain;

/** A named, bounded interval for grouping telemetry within one mob's continuous brain run. */
public final class TrialSession {
    public record Snapshot(String label, long startedAtTick, long endsAtTick) {
        public boolean activeAt(long tick) { return tick >= startedAtTick && tick < endsAtTick; }
        public long elapsedTicks(long tick) { return Math.max(0, tick - startedAtTick); }
        public long durationTicks() { return endsAtTick - startedAtTick; }
    }

    private Snapshot current;

    public Snapshot start(String label, long tick, int durationTicks) {
        if (label == null || !label.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,47}"))
            throw new IllegalArgumentException("Trial labels must be 1-48 letters, numbers, dots, underscores, or hyphens.");
        if (tick < 0 || durationTicks < 1 || durationTicks > 12000)
            throw new IllegalArgumentException("Trial duration must be between 1 and 12000 world ticks.");
        current = new Snapshot(label, tick, Math.addExact(tick, durationTicks));
        return current;
    }

    public Snapshot current(long tick) {
        return current != null && current.activeAt(tick) ? current : null;
    }

    public Snapshot expire(long tick) {
        if (current != null && tick >= current.endsAtTick()) return end();
        return null;
    }

    public Snapshot end() {
        Snapshot ended = current;
        current = null;
        return ended;
    }
}

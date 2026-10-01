package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Small bounded client-side ring history for the dashboard plots. */
public final class TelemetryHistory {
    public static final int LIMIT = 180;
    private static final long STALE_AFTER_NANOS = 10_000_000_000L;
    private static final ConcurrentHashMap<Integer, Series> DATA = new ConcurrentHashMap<>();
    private TelemetryHistory() {}
    public static void accept(BrainTelemetryPayload packet) {
        DATA.computeIfAbsent(packet.entityId(), id -> new Series()).push(packet);
        prune();
    }
    public static List<SeriesView> snapshot() {
        prune();
        List<SeriesView> result = new ArrayList<>();
        DATA.forEach((id, series) -> result.add(series.view(id)));
        return result;
    }
    public static void clear() { DATA.clear(); }

    private static void prune() {
        long now = System.nanoTime();
        DATA.entrySet().removeIf(entry -> now - entry.getValue().lastUpdatedNanos() > STALE_AFTER_NANOS);
    }

    private static final class Series {
        private final ArrayDeque<BrainTelemetryPayload> samples = new ArrayDeque<>(LIMIT);
        private volatile long updatedAt = System.nanoTime();
        synchronized void push(BrainTelemetryPayload p) {
            if (samples.size() == LIMIT) samples.removeFirst();
            samples.addLast(p);
            updatedAt = System.nanoTime();
        }
        long lastUpdatedNanos() { return updatedAt; }
        synchronized SeriesView view(int id) { return new SeriesView(id, List.copyOf(samples)); }
    }
    public record SeriesView(int entityId, List<BrainTelemetryPayload> samples) {}
}

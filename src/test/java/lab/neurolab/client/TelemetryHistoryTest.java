package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryHistoryTest {
    @AfterEach
    void clearHistory() {
        TelemetryHistory.clear();
    }

    @Test
    void keepsOnlyTheMostRecentSamplesForEachMob() {
        for (int sample = 0; sample < TelemetryHistory.LIMIT + 25; sample++) {
            TelemetryHistory.accept(packet(7, sample));
        }

        var series = TelemetryHistory.snapshot().getFirst();
        assertEquals(TelemetryHistory.LIMIT, series.samples().size());
        assertEquals(25, series.samples().getFirst().spikes());
        assertEquals(TelemetryHistory.LIMIT + 24, series.samples().getLast().spikes());
    }

    @Test
    void keepsConcurrentMobSeriesBeyondTheFormerEightMobLimit() {
        for (int id = 0; id < 12; id++) TelemetryHistory.accept(packet(id, id));

        assertEquals(12, TelemetryHistory.snapshot().size());
    }

    @Test
    void frozenSnapshotsRemainStableWhileLiveTelemetryContinues() {
        TelemetryHistory.accept(packet(7, 10));
        var frozen = TelemetryHistory.snapshot();
        TelemetryHistory.accept(packet(7, 20));
        TelemetryHistory.clear();
        assertEquals(1, frozen.getFirst().samples().size());
        assertEquals(10, frozen.getFirst().samples().getFirst().spikes());
        assertEquals(0, TelemetryHistory.snapshot().size());
    }

    private static BrainTelemetryPayload packet(int entityId, int spikes) {
        return new BrainTelemetryPayload(entityId, spikes, 42, 0.25f, -0.4f,
                0.1f, 0.8f, false);
    }
}

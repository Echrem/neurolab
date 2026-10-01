package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void boundsTheNumberOfMobSeries() {
        for (int id = 0; id < 12; id++) TelemetryHistory.accept(packet(id, id));

        assertTrue(TelemetryHistory.snapshot().size() <= 8);
    }

    private static BrainTelemetryPayload packet(int entityId, int spikes) {
        return new BrainTelemetryPayload(entityId, spikes, 42, 0.25f, -0.4f,
                0.1f, 0.8f, false);
    }
}

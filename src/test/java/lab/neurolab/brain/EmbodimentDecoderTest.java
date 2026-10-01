package lab.neurolab.brain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class EmbodimentDecoderTest {
    private static final FlyBrain.Snapshot SILENT = new FlyBrain.Snapshot(1, 0, 0, 0, 0, 0, false, Map.of());

    @Test
    void silentNeuralMotorChannelsUseLabeledExplorationFallback() {
        var command = EmbodimentDecoder.decode(SILENT, new FlyBrain.Drive(0, 0, 0, 0, 0), 100, 12);

        assertTrue(command.reflex());
        assertTrue(command.forward() > 0);
        assertFalse(command.escape());
    }

    @Test
    void loomingAndTouchActivateTheFallbackEscapeResponse() {
        var command = EmbodimentDecoder.decode(SILENT, new FlyBrain.Drive(0, 0, 0.8f, 0, 0), 100, 12);

        assertTrue(command.reflex());
        assertTrue(command.escape());
        assertTrue(command.lift() > 0);
    }

    @Test
    void activeNeuralMotorOutputTakesPriorityOverFallback() {
        var neural = new FlyBrain.Snapshot(2, 5, 3, 0.25, -0.5, 0.1, false, Map.of());
        var command = EmbodimentDecoder.decode(neural, new FlyBrain.Drive(0, 1, 1, 1, 1), 100, 12);

        assertFalse(command.reflex());
        assertEquals(0.25, command.forward());
        assertEquals(-0.5, command.turn());
    }
}

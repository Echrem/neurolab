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
        var command = EmbodimentDecoder.decode(neural, new FlyBrain.Drive(0, 0, 0, 0, 0), 100, 12);

        assertFalse(command.reflex());
        assertEquals(0.25, command.forward());
        assertEquals(-0.5, command.turn());
    }

    @Test
    void assistedThreatForcesFastDirectionalEscapeEvenWithOtherNeuralOutput() {
        var neural = new FlyBrain.Snapshot(2, 5, 3, 0.1, 0, 0.2, false, Map.of());
        var world = new EmbodimentDecoder.WorldCue(0, 0, 16, 0, 0, -1, 0.9);
        var command = EmbodimentDecoder.decode(neural, new FlyBrain.Drive(0, 0, 0.7f, 0, 0),
                100, 12, ControlMode.ASSISTED, world);

        assertTrue(command.escape());
        assertTrue(command.forward() >= 0.8);
        assertTrue(command.turn() < -0.8);
        assertTrue(command.lift() > 0.8);
    }

    @Test
    void neuralOnlyModeDoesNotInventMovementEvenWhenThreatened() {
        var command = EmbodimentDecoder.decode(SILENT, new FlyBrain.Drive(1, 1, 1, 1, 1),
                100, 12, ControlMode.NEURAL);
        assertEquals(new EmbodimentDecoder.Command(0, 0, 0, false, false), command);
    }

    @Test
    void observationNeverAppliesEvenStrongNeuralOutput() {
        var neural = new FlyBrain.Snapshot(2, 5, 3, 1, -1, 1, true, Map.of());
        var command = EmbodimentDecoder.decode(neural, new FlyBrain.Drive(1, 1, 1, 1, 1),
                100, 12, ControlMode.OBSERVE);
        assertEquals(new EmbodimentDecoder.Command(0, 0, 0, false, false), command);
        assertFalse(ControlMode.OBSERVE.controlsBody());
    }

    @Test
    void assistedBrainTurnsTowardNearbyWorldTargets() {
        var cue = new EmbodimentDecoder.WorldCue(1, 0.8, 5, 0, 0);
        var command = EmbodimentDecoder.decode(SILENT, new FlyBrain.Drive(0, 0, 0, 0, 0),
                0, 12, ControlMode.ASSISTED, cue);

        assertTrue(command.turn() > 0.25);
        assertTrue(command.forward() > 0.2);
        assertTrue(command.reflex());
    }

    @Test
    void assistedBrainSlowsAndSteersIntoClearanceAtObstacles() {
        var cue = new EmbodimentDecoder.WorldCue(0, 0, 16, 1, -1);
        var command = EmbodimentDecoder.decode(SILENT, new FlyBrain.Drive(0, 0, 0, 0, 0),
                100, 12, ControlMode.ASSISTED, cue);

        assertTrue(command.turn() < -0.5);
        assertTrue(command.forward() < 0.06);
        assertTrue(command.lift() > 0);
    }

    @Test
    void neuralModeIgnoresHandBuiltWorldGuidance() {
        var cue = new EmbodimentDecoder.WorldCue(-1, 1, 2, 1, 1);
        var expected = new EmbodimentDecoder.Command(0, 0, 0, false, false);
        assertEquals(expected, EmbodimentDecoder.decode(SILENT,
                new FlyBrain.Drive(0, 0, 0, 0, 0), 100, 12, ControlMode.NEURAL, cue));
    }

    @Test
    void modesPreserveAnOriginallyDisabledAiWhenObserving() {
        assertFalse(ControlMode.OBSERVE.noAi(false));
        assertTrue(ControlMode.OBSERVE.noAi(true));
        assertTrue(ControlMode.NEURAL.noAi(false));
        assertTrue(ControlMode.ASSISTED.noAi(false));
    }
}

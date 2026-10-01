package lab.neurolab.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static lab.neurolab.brain.StimulusSchedule.Sense.*;

class StimulusScheduleTest {
    private static final FlyBrain.Drive DARK = new FlyBrain.Drive(0, 0, 0, 0, 0, 0, 0);

    @Test void pulseExpiresExactlyAtTheDeadline() {
        var schedule = new StimulusSchedule();
        schedule.start(LOOMING, 0.8f, 40, 100);
        assertEquals(0.8f, schedule.apply(DARK, 139).looming());
        assertEquals(DARK, schedule.apply(DARK, 140));
        assertEquals(0, schedule.activeCount(140));
    }

    @Test void eyePulsesComposeWithoutCrossingSidesOrSuppressingNaturalInput() {
        var schedule = new StimulusSchedule();
        schedule.start(EYE, 0.3f, 40, 100);
        schedule.start(LEFT_EYE, 0.9f, 10, 100);
        var natural = new FlyBrain.Drive(0.7f, 0.2f, 0.6f, 0, 0, 0, 0);
        var mixed = schedule.apply(natural, 101);
        assertEquals(0.7f, mixed.light());
        assertEquals(0.9f, mixed.leftEye());
        assertEquals(0.6f, mixed.rightEye());
        assertEquals(0.3f, schedule.apply(natural, 110).leftEye());
    }

    @Test void clearingPulsesPreservesWorldSensesAndZeroCancelsOneSense() {
        var schedule = new StimulusSchedule();
        schedule.start(TOUCH, 1, 40, 1);
        schedule.start(ODOR, 1, 40, 1);
        schedule.start(TOUCH, 0, 40, 2);
        assertEquals(1, schedule.activeCount(2));
        assertEquals(0, schedule.apply(DARK, 2).tactile());
        assertEquals(1, schedule.clear());
        var natural = new FlyBrain.Drive(0.4f, 0.3f, 0.2f, 0.1f, 0.5f);
        assertEquals(natural, schedule.apply(natural, 3));
    }

    @Test void repeatedPulseReplacesItsDeadlineAndHandlesLongWorldTimes() {
        var schedule = new StimulusSchedule();
        long tick = (long) Integer.MAX_VALUE + 100;
        schedule.start(TASTE, 0.3f, 5, tick);
        schedule.start(TASTE, 0.7f, 40, tick + 1);
        assertEquals(0.7f, schedule.apply(DARK, tick + 40).taste());
        assertEquals(DARK, schedule.apply(DARK, tick + 41));
    }

    @Test void invalidPulseDoesNotAlterExistingSchedule() {
        var schedule = new StimulusSchedule();
        schedule.start(TOUCH, 1, 40, 1);
        for (float strength : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f})
            assertThrows(IllegalArgumentException.class, () -> schedule.start(TOUCH, strength, 40, 2));
        for (int duration : new int[]{0, -1, 1201})
            assertThrows(IllegalArgumentException.class, () -> schedule.start(TOUCH, 1, duration, 2));
        assertEquals(1, schedule.apply(DARK, 3).tactile());
    }
}

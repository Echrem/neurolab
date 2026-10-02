package lab.neurolab.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FlyNavigationTest {
    @Test
    void lightGradientTurnsTowardTheBrighterSideAtLowLight() {
        double turn = FlyNavigation.phototaxisTurn(0.9, 0.2, 0.1,
                FlyNavigation.LightMode.BALANCED, 0.8);
        assertTrue(turn < -0.5);
    }

    @Test
    void balancedModeMovesTowardShadeWhenAlreadyInBrightLight() {
        double turn = FlyNavigation.phototaxisTurn(0.9, 0.2, 0.95,
                FlyNavigation.LightMode.BALANCED, 1);
        assertTrue(turn > 0);
    }

    @Test
    void lightModesCanSeekShadeOrDisablePhototaxis() {
        assertTrue(FlyNavigation.phototaxisTurn(0.9, 0.2, 0.3,
                FlyNavigation.LightMode.SEEK_SHADE, 1) > 0);
        assertEquals(0.0, FlyNavigation.phototaxisTurn(0.9, 0.2, 0.3,
                FlyNavigation.LightMode.OFF, 1), 1.0e-9);
    }

    @Test
    void unsafeOrRainExposedFliesNeverPerchOnAHost() {
        assertTrue(FlyNavigation.mayPerch(true, false, false, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, true, false, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, false, true, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, false, false, true, 1.0));
        assertFalse(FlyNavigation.mayPerch(false, false, false, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, false, false, false, Double.NaN));
    }

    @Test
    void passiveAnimalsAreNotThreatsUnlessTheyCloseFastAtShortRange() {
        assertEquals(0, FlyNavigation.loomingStrength(1.0, false, false));
        assertEquals(0, FlyNavigation.loomingStrength(2.5, true, false));
        assertTrue(FlyNavigation.loomingStrength(1.5, true, false) > 0.5);
        assertTrue(FlyNavigation.loomingStrength(3.0, false, true) > 0);
        assertEquals(0, FlyNavigation.loomingStrength(Double.POSITIVE_INFINITY, true, true));
    }

    @Test
    void approachSpeedBrakesSmoothlyAsTheLandingSurfaceGetsNearer() {
        double distant = FlyNavigation.approachThrottle(0.7, 4.0, 0.9);
        double near = FlyNavigation.approachThrottle(0.7, 1.0, 0.9);
        double contact = FlyNavigation.approachThrottle(0.7, 0.2, 0.9);
        assertTrue(distant > near);
        assertTrue(near > contact);
        assertTrue(contact < 0.05);
        assertEquals(0, FlyNavigation.approachThrottle(0.7, Double.NaN, 0.9));
    }
}

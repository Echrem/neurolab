package lab.neurolab.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.electronwill.nightconfig.core.CommentedConfig;

final class NeuroFlyConfigTest {
    @Test
    void environmentConfigLoadsSafeTunableDefaults() {
        NeuroFlyConfig.SPEC.acceptConfig(CommentedConfig.inMemory());
        assertEquals(NeuroFlyConfig.LightMode.BALANCED, NeuroFlyConfig.LIGHT_MODE.get());
        assertEquals(0.55, NeuroFlyConfig.LIGHT_STEERING.get());
        assertEquals(0.62, NeuroFlyConfig.CRUISE_THROTTLE.get());
        assertEquals(8, NeuroFlyConfig.FLOWER_RADIUS.get());
        assertEquals(8, NeuroFlyConfig.HOST_RADIUS.get());
        assertEquals(120, NeuroFlyConfig.PERCH_TICKS.get());
        assertEquals(40, NeuroFlyConfig.RAIN_SCAN_INTERVAL.get());
        assertTrue(NeuroFlyConfig.SEEK_SHELTER_IN_RAIN.get());
        assertTrue(NeuroFlyConfig.PERCH_ON_ANIMALS_AND_VILLAGERS.get());
    }
}

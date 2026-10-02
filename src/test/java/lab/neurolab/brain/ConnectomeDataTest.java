package lab.neurolab.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ConnectomeDataTest {
    @Test void bundledDatasetHasExpectedDimensionsAndValidCsr() throws Exception {
        ConnectomeData data = ConnectomeData.readBundled();
        assertEquals("male-cns:v1.0", data.dataset);
        assertEquals(176_422, data.neurons());
        assertEquals(6_287_749, data.connections());
        assertEquals(data.neurons() + 1, data.rowOffsets.length);
        assertEquals(data.connections(), data.rowOffsets[data.neurons()]);
        assertEquals(9_619, data.visualReceptors.length);
        assertEquals(data.visualReceptors.length,
                data.visualLeftReceptors.length + data.visualRightReceptors.length);
        assertTrue(data.visualLeftReceptors.length > 0);
        assertTrue(data.visualRightReceptors.length > 0);
        for (byte side : data.visualSides) assertTrue(side == 1 || side == 3);
        assertTrue(data.motorNeurons.length > 0);
        assertTrue(data.olfactoryNeurons.length > 0);
        assertTrue(data.gustatoryNeurons.length > 0);
        assertTrue(data.tactileNeurons.length > 0);
        for (int i = 0; i < 2_000; i++) assertTrue(data.rowOffsets[i] <= data.rowOffsets[i + 1]);
    }

    @Test void silentNetworkHasNoSpontaneousSpikes() throws Exception {
        FlyBrain brain = new FlyBrain(ConnectomeData.readBundled());
        for (int tick = 0; tick < 4; tick++) {
            FlyBrain.Snapshot snapshot = brain.advance(new FlyBrain.Drive(0, 0, 0, 0, 0));
            assertEquals(0, snapshot.spikes());
            assertEquals(0, snapshot.synapticEvents());
            assertEquals(0, snapshot.forward());
            assertFalse(snapshot.escape());
        }
    }

    @Test void visualInputEntersThroughTheConnectomesRetinaNeurons() throws Exception {
        FlyBrain brain = new FlyBrain(ConnectomeData.readBundled());
        FlyBrain.Snapshot snapshot = brain.advance(new FlyBrain.Drive(1, 0, 0, 0, 0));
        assertTrue(snapshot.spikes() > 0);
        assertTrue(snapshot.synapticEvents() > 0);
        assertTrue(snapshot.activeNeurons() > 0);
    }

    @Test void separateEyeDrivesReachTheSideAnnotatedRetina() throws Exception {
        FlyBrain leftBrain = new FlyBrain(ConnectomeData.readBundled());
        FlyBrain rightBrain = new FlyBrain(ConnectomeData.readBundled());
        FlyBrain.Snapshot left = leftBrain.advance(new FlyBrain.Drive(0, 1, 0, 0, 0, 0, 0));
        FlyBrain.Snapshot right = rightBrain.advance(new FlyBrain.Drive(0, 0, 1, 0, 0, 0, 0));
        assertTrue(left.spikes() > 0);
        assertTrue(right.spikes() > 0);
    }
}

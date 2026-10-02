package lab.neurolab.minecraft;

import io.netty.buffer.Unpooled;
import lab.neurolab.brain.ControlMode;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrainTelemetryPayloadTest {
    @Test void wireRoundTripPreservesEverySignalAndMode() {
        for (ControlMode mode : ControlMode.values()) {
            var packet = new BrainTelemetryPayload(123, 456, 9_876_543_210L, 789, 0.1f, -0.2f, 0.3f,
                    0.4f, true, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1, 0.2f,
                    0.45f, 0.55f, 0.3f, -0.4f, 0.5f, false, true, true, mode);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                BrainTelemetryPayload.CODEC.encode(buffer, packet);
                assertEquals(packet, BrainTelemetryPayload.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
}

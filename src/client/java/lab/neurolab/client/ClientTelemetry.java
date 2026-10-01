package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;

public final class ClientTelemetry {
    private ClientTelemetry() {}

    public static void accept(BrainTelemetryPayload payload) {
        TelemetryHistory.accept(payload);
    }
}

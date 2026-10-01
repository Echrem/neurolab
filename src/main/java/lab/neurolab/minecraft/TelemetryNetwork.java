package lab.neurolab.minecraft;

import lab.neurolab.client.ClientTelemetry;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadConnection;

public final class TelemetryNetwork {
    public static final Channel<CustomPacketPayload> CHANNEL = createChannel();

    private TelemetryNetwork() {}

    private static Channel<CustomPacketPayload> createChannel() {
        PayloadConnection<CustomPacketPayload> connection = ChannelBuilder
                .named(Identifier.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "main"))
                .networkProtocolVersion(3)
                .optional()
                .payloadChannel();
        return connection.play()
                .flow(PacketFlow.CLIENTBOUND)
                .addMain(BrainTelemetryPayload.TYPE, BrainTelemetryPayload.CODEC,
                        (payload, context) -> {
                            if (context.isClientSide()) {
                                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                                        () -> () -> ClientTelemetry.accept(payload));
                            }
                        })
                .build();
    }

    public static void initialize() {
    }
}

package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact observation packet; neural internals stay on the server. */
public record BrainTelemetryPayload(int entityId, int spikes, int active, float forward, float turn, float lift,
                                    float realTimeFactor, boolean escape)
        implements CustomPacketPayload {
    public static final Type<BrainTelemetryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("neurolab", "telemetry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrainTelemetryPayload> CODEC =
            StreamCodec.of(BrainTelemetryPayload::encode, BrainTelemetryPayload::decode);

    public BrainTelemetryPayload(RegistryFriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean());
    }

    private static BrainTelemetryPayload decode(RegistryFriendlyByteBuf b) {
        return new BrainTelemetryPayload(b);
    }

    private static void encode(RegistryFriendlyByteBuf b, BrainTelemetryPayload p) {
        b.writeVarInt(p.entityId); b.writeVarInt(p.spikes); b.writeVarInt(p.active);
        b.writeFloat(p.forward); b.writeFloat(p.turn); b.writeFloat(p.lift); b.writeFloat(p.realTimeFactor); b.writeBoolean(p.escape);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

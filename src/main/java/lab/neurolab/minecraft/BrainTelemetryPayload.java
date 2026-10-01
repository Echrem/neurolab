package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import lab.neurolab.brain.ControlMode;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Compact observation packet; neural internals stay on the server. */
public record BrainTelemetryPayload(int entityId, int spikes, int active, float forward, float turn, float lift,
                                    float realTimeFactor, boolean escape, float light, float leftEye,
                                    float rightEye, float looming, float touch, float odor, float taste,
                                    float bodyForward, float bodyTurn, float bodyLift, boolean bodyEscape,
                                    boolean reflex, boolean testStimulus, ControlMode mode)
        implements CustomPacketPayload {
    public static final Type<BrainTelemetryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neurolab", "telemetry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrainTelemetryPayload> CODEC =
            StreamCodec.of(BrainTelemetryPayload::encode, BrainTelemetryPayload::decode);

    public BrainTelemetryPayload(int entityId, int spikes, int active, float forward, float turn, float lift,
                                 float realTimeFactor, boolean escape) {
        this(entityId, spikes, active, forward, turn, lift, realTimeFactor, escape,
                0, 0, 0, 0, 0, 0, 0, forward, turn, lift, escape, false, false, ControlMode.ASSISTED);
    }

    private static BrainTelemetryPayload decode(RegistryFriendlyByteBuf b) {
        int entityId = b.readVarInt();
        int spikes = b.readVarInt();
        int active = b.readVarInt();
        float forward = b.readFloat();
        float turn = b.readFloat();
        float lift = b.readFloat();
        float realTimeFactor = b.readFloat();
        boolean escape = b.readBoolean();
        float light = b.readFloat();
        float leftEye = b.readFloat();
        float rightEye = b.readFloat();
        float looming = b.readFloat();
        float touch = b.readFloat();
        float odor = b.readFloat();
        float taste = b.readFloat();
        float bodyForward = b.readFloat();
        float bodyTurn = b.readFloat();
        float bodyLift = b.readFloat();
        boolean bodyEscape = b.readBoolean();
        boolean reflex = b.readBoolean();
        boolean testStimulus = b.readBoolean();
        return new BrainTelemetryPayload(entityId, spikes, active, forward, turn, lift, realTimeFactor, escape,
                light, leftEye, rightEye, looming, touch, odor, taste, bodyForward, bodyTurn, bodyLift,
                bodyEscape, reflex, testStimulus, b.readEnum(ControlMode.class));
    }

    private static void encode(RegistryFriendlyByteBuf b, BrainTelemetryPayload p) {
        b.writeVarInt(p.entityId); b.writeVarInt(p.spikes); b.writeVarInt(p.active);
        b.writeFloat(p.forward); b.writeFloat(p.turn); b.writeFloat(p.lift); b.writeFloat(p.realTimeFactor); b.writeBoolean(p.escape);
        b.writeFloat(p.light); b.writeFloat(p.leftEye); b.writeFloat(p.rightEye); b.writeFloat(p.looming);
        b.writeFloat(p.touch); b.writeFloat(p.odor); b.writeFloat(p.taste);
        b.writeFloat(p.bodyForward); b.writeFloat(p.bodyTurn); b.writeFloat(p.bodyLift);
        b.writeBoolean(p.bodyEscape); b.writeBoolean(p.reflex); b.writeBoolean(p.testStimulus); b.writeEnum(p.mode);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

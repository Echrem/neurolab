package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to view an attached mob, or return to the player's own camera (-1). */
public record NeuroViewRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<NeuroViewRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "view_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeuroViewRequestPayload> CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeVarInt(payload.entityId()),
                    buffer -> new NeuroViewRequestPayload(buffer.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

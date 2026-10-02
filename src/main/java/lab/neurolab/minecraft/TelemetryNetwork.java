package lab.neurolab.minecraft;

import lab.neurolab.client.ClientTelemetry;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadConnection;

public final class TelemetryNetwork {
    public static final Channel<CustomPacketPayload> CHANNEL = createChannel();
    private static final Channel<CustomPacketPayload> VIEW_CHANNEL = createViewChannel();

    private TelemetryNetwork() {}

    private static Channel<CustomPacketPayload> createChannel() {
        PayloadConnection<CustomPacketPayload> connection = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "main"))
                .networkProtocolVersion(5)
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

    private static Channel<CustomPacketPayload> createViewChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "view"))
                .networkProtocolVersion(1).optional().payloadChannel().play()
                .flow(PacketFlow.SERVERBOUND)
                .addMain(NeuroViewRequestPayload.TYPE, NeuroViewRequestPayload.CODEC, (payload, context) -> {
                    var player = context.getSender();
                    if (player == null) return;
                    if (payload.entityId() < 0) {
                        player.setCamera(player);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Neuro view closed."), true);
                        return;
                    }
                    var entity = player.level().getEntity(payload.entityId());
                    if (entity instanceof net.minecraft.world.entity.Mob mob
                            && BrainAttachmentService.isAttached(mob) && player.distanceToSqr(mob) <= 48 * 48) {
                        player.setCamera(mob);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "Viewing " + mob.getName().getString() + " · V returns to your own view."), true);
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "Aim at a brain-attached mob within 48 blocks."), true);
                    }
                }).build();
    }

    public static void requestView(int entityId) {
        VIEW_CHANNEL.send(new NeuroViewRequestPayload(entityId), PacketDistributor.SERVER.noArg());
    }

    public static void initialize() {
    }
}

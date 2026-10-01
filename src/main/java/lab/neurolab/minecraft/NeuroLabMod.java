package lab.neurolab.minecraft;

import lab.neurolab.brain.FlyBrain;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

@Mod(NeuroLabMod.MOD_ID)
public final class NeuroLabMod {
    public static final String MOD_ID = "neurolab";

    public NeuroLabMod(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        NeuroLabEntities.ENTITY_TYPES.register(modBus);
        NeuroLabEntities.ITEMS.register(modBus);
        TelemetryNetwork.initialize();
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("neurolab")
                    .then(Commands.literal("attach").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> {
                                Entity entity = EntityArgument.getEntity(ctx, "mob");
                                if (!(entity instanceof Mob mob)) {
                                    ctx.getSource().sendFailure(Component.literal("Choose a living mob entity."));
                                    return 0;
                                }
                                if (BrainAttachmentService.attach(mob)) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("Fly connectome attached to "
                                            + mob.getName().getString() + ". Vanilla AI paused."), false);
                                    return 1;
                                }
                                boolean loading = !BrainAttachmentService.connectomeStatus().isDone();
                                ctx.getSource().sendFailure(Component.literal(loading
                                        ? "Connectome still loading; retry in a moment."
                                        : "Could not attach: already attached, failed dataset load, or 4-brain limit reached."));
                                return 0;
                            })))
                    .then(Commands.literal("detach").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> {
                                Entity entity = EntityArgument.getEntity(ctx, "mob");
                                if (!(entity instanceof Mob mob) || !BrainAttachmentService.detach(mob)) {
                                    ctx.getSource().sendFailure(Component.literal("That mob has no NeuroLab brain."));
                                    return 0;
                                }
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Neural control detached; the previous AI state was restored."), false);
                                return 1;
                            })))
                    .then(Commands.literal("status").executes(ctx -> {
                        var future = BrainAttachmentService.connectomeStatus();
                        String msg = future.isCompletedExceptionally() ? "Dataset failed to load"
                                : !future.isDone() ? "Dataset loading"
                                : "Dataset " + future.join().dataset + " · " + future.join().neurons()
                                + " neurons · " + future.join().connections() + " edges";
                        ctx.getSource().sendSuccess(() -> Component.literal(msg + " · attached brains "
                                + BrainAttachmentService.attachedCount() + "/4"), false);
                        return 1;
                    })));
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
            BrainAttachmentService.tick();
        }

        @SubscribeEvent
        public static void onEntityUnload(EntityLeaveLevelEvent event) {
            if (event.getEntity() instanceof Mob mob) BrainAttachmentService.detach(mob);
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            BrainAttachmentService.startup();
            TelemetryLog.start(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("neurolab/events.jsonl"));
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            BrainAttachmentService.shutdown();
            TelemetryLog.stop();
        }
    }

    static void sendTelemetry(Mob mob, FlyBrain.Snapshot snapshot) {
        BrainTelemetryPayload packet = new BrainTelemetryPayload(mob.getId(), (int) snapshot.spikes(),
                snapshot.activeNeurons(), (float) snapshot.forward(), (float) snapshot.turn(),
                (float) snapshot.lift(), (float) BrainAttachmentService.realTimeFactor(mob), snapshot.escape());
        TelemetryLog.record(packet);
        TelemetryNetwork.CHANNEL.send(packet, PacketDistributor.TRACKING_ENTITY.with(mob));
    }
}

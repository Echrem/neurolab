package lab.neurolab.minecraft;

import lab.neurolab.brain.FlyBrain;
import lab.neurolab.brain.EmbodimentDecoder;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
            event.getDispatcher().register(Commands.literal("neurolab").requires(source -> source.hasPermission(2))
                    .then(Commands.literal("attach").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> {
                                Entity entity = EntityArgument.getEntity(ctx, "mob");
                                if (!(entity instanceof Mob mob)) {
                                    ctx.getSource().sendFailure(Component.literal("Choose a living mob entity."));
                                    return 0;
                                }
                                if (BrainAttachmentService.isAttached(mob)) {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            mob.getName().getString() + " already has a NeuroLab brain."), false);
                                    return 1;
                                }
                                String failure = BrainAttachmentService.attachFailure(mob);
                                if (failure == null && BrainAttachmentService.attach(mob)) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("NeuroLab connectome attached to "
                                            + mob.getName().getString() + ". Vanilla AI paused."), false);
                                    return 1;
                                }
                                String reason = failure == null ? BrainAttachmentService.attachFailure(mob) : failure;
                                ctx.getSource().sendFailure(Component.literal(reason == null
                                        ? "Could not attach this mob; check the server log for details." : reason));
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
                                + BrainAttachmentService.attachedCount() + " · shared simulation workers "
                                + BrainAttachmentService.workerCount()), false);
                        return 1;
                    }))
                    .then(Commands.literal("stimulate")
                            .then(Commands.argument("mob", EntityArgument.entity())
                                    .then(Commands.argument("sense", StringArgumentType.word())
                                            .then(Commands.argument("strength", FloatArgumentType.floatArg(0, 1))
                                                    .executes(ctx -> stimulate(ctx, 40))
                                                    .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                                            .executes(ctx -> stimulate(ctx,
                                                                    IntegerArgumentType.getInteger(ctx, "ticks")))))))));
        }

        private static int stimulate(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                     int ticks) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            Entity entity = EntityArgument.getEntity(ctx, "mob");
            if (!(entity instanceof Mob mob)) {
                ctx.getSource().sendFailure(Component.literal("Choose a living mob entity."));
                return 0;
            }
            String failure = BrainAttachmentService.stimulate(mob,
                    StringArgumentType.getString(ctx, "sense"),
                    FloatArgumentType.getFloat(ctx, "strength"), ticks);
            if (failure != null) {
                ctx.getSource().sendFailure(Component.literal(failure));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Applied "
                    + StringArgumentType.getString(ctx, "sense") + " stimulus for " + ticks + " ticks."), false);
            return 1;
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
        public static void onEntityJoin(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide()) BrainAttachmentService.queueAutoAttach(event.getEntity());
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

    static void sendTelemetry(Mob mob, FlyBrain.Snapshot snapshot, FlyBrain.Drive senses,
                              EmbodimentDecoder.Command body, boolean testStimulus) {
        BrainTelemetryPayload packet = new BrainTelemetryPayload(mob.getId(), (int) snapshot.spikes(),
                snapshot.activeNeurons(), (float) snapshot.forward(), (float) snapshot.turn(),
                (float) snapshot.lift(), (float) BrainAttachmentService.realTimeFactor(mob), snapshot.escape(),
                senses.light(), senses.leftEye(), senses.rightEye(), senses.looming(), senses.tactile(),
                senses.odor(), senses.taste(), (float) body.forward(), (float) body.turn(),
                (float) body.lift(), body.escape(), body.reflex(), testStimulus);
        TelemetryLog.record(packet);
        TelemetryNetwork.CHANNEL.send(packet, PacketDistributor.TRACKING_ENTITY.with(mob));
    }
}

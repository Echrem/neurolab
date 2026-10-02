package lab.neurolab.minecraft;

import lab.neurolab.brain.BrainWorker;
import lab.neurolab.brain.ConnectomeData;
import lab.neurolab.brain.FlyBrain;
import lab.neurolab.brain.EmbodimentDecoder;
import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import lab.neurolab.brain.ControlMode;
import lab.neurolab.brain.StimulusSchedule;
import lab.neurolab.brain.StimulusSchedule.Sense;
import lab.neurolab.brain.TrialSession;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Server-thread-owned bindings between Minecraft mobs and independent neural workers. */
public final class BrainAttachmentService {
    private static final String LEARNED_SYNAPSES_TAG = "NeuroLabLearnedSynapses";
    private static final Map<UUID, Attachment> ATTACHED = new ConcurrentHashMap<>();
    private static final Map<UUID, Mob> AUTO_ATTACH_PENDING = new ConcurrentHashMap<>();
    private static volatile CompletableFuture<ConnectomeData> data;

    static { startup(); }

    private BrainAttachmentService() {}

    public static synchronized void startup() {
        if (data == null || data.isCompletedExceptionally()) {
            data = CompletableFuture.supplyAsync(() -> {
                try { return ConnectomeData.readBundled(); }
                catch (IOException e) { throw new IllegalStateException("Could not load male-cns connectome", e); }
            });
        }
    }

    public static boolean attach(Mob mob) {
        if (attachFailure(mob) != null) return false;
        BrainWorker worker = new BrainWorker(data.join(), mob.getPersistentData().getLongArray(LEARNED_SYNAPSES_TAG));
        Attachment state = new Attachment(mob, worker, mob.isNoAi());
        mob.setNoAi(true);
        ATTACHED.put(mob.getUUID(), state);
        return true;
    }

    public static String attachFailure(Mob mob) {
        if (!(mob.level() instanceof ServerLevel)) return "Mob must be in a server-side world.";
        if (ATTACHED.containsKey(mob.getUUID())) return "This mob already has a NeuroLab brain attached.";
        if (!data.isDone()) return "Connectome is still loading; try again in a moment.";
        if (data.isCompletedExceptionally()) return "Connectome failed to load; check latest.log for the NeuroLab error.";
        return null;
    }

    public static boolean isAttached(Mob mob) { return ATTACHED.containsKey(mob.getUUID()); }

    public static String stimulate(Mob mob, String senseName, float strength, int durationTicks) {
        Attachment attachment = ATTACHED.get(mob.getUUID());
        if (attachment == null) return "Attach a brain to this mob before stimulating it.";
        if (!Float.isFinite(strength) || strength < 0 || strength > 1)
            return "Stimulus strength must be between 0 and 1.";
        if (durationTicks < 1 || durationTicks > 1200)
            return "Stimulus duration must be between 1 and 1200 ticks.";
        Sense sense;
        try { sense = Sense.valueOf(senseName.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) {
            return "Unknown sense. Use eye, left_eye, right_eye, looming, touch, odor, or taste.";
        }
        attachment.stimuli.start(sense, strength, durationTicks, mob.level().getGameTime());
        return null;
    }

    public static void queueAutoAttach(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof NeuroFlyEntity fly && fly.level() instanceof ServerLevel)
            AUTO_ATTACH_PENDING.putIfAbsent(fly.getUUID(), fly);
    }

    public static boolean detach(Mob mob) {
        AUTO_ATTACH_PENDING.remove(mob.getUUID());
        Attachment a = ATTACHED.remove(mob.getUUID());
        if (a == null) return false;
        persistLearning(a);
        a.worker.close();
        TrialSession.Snapshot trial = a.trial.end();
        if (trial != null) TelemetryLog.trialEvent("trial_end", mob, a.mode, trial, mob.level().getGameTime());
        mob.setNoAi(a.originalNoAi);
        return true;
    }

    public static void tick() {
        if (data.isDone() && !data.isCompletedExceptionally()) {
            for (var entry : AUTO_ATTACH_PENDING.entrySet()) {
                Mob fly = entry.getValue();
                if (fly.isRemoved() || attach(fly) || ATTACHED.containsKey(entry.getKey()))
                    AUTO_ATTACH_PENDING.remove(entry.getKey(), fly);
            }
        }
        for (Attachment a : ATTACHED.values()) {
            Mob mob = a.mob;
            if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
                persistLearning(a);
                mob.setNoAi(a.originalNoAi);
                a.worker.close(); ATTACHED.remove(mob.getUUID(), a); continue;
            }
            if (a.worker.failure() != null) {
                java.util.logging.Logger.getLogger("neurolab").log(java.util.logging.Level.SEVERE,
                        "Brain failed for entity " + mob.getId() + "; restoring its AI", a.worker.failure());
                detach(mob);
                continue;
            }
            WorldSample world = sample(mob, level, a);
            FlyBrain.Drive senses = a.stimuli.apply(world.senses(), level.getGameTime());
            a.worker.accept(senses);
            FlyBrain.Snapshot neural = a.worker.snapshot();
            EmbodimentDecoder.Command body = EmbodimentDecoder.decode(neural, senses, mob.tickCount,
                    mob.getUUID().getLeastSignificantBits(), a.mode, world.cue());
            long gameTick = level.getGameTime();
            if (gameTick % 200 == 0) persistLearning(a);
            TrialSession.Snapshot endedTrial = a.trial.expire(gameTick);
            if (endedTrial != null) TelemetryLog.trialEvent("trial_end", mob, a.mode,
                    endedTrial, endedTrial.endsAtTick());
            if (mob.tickCount % 2 == 0) {
                TrialSession.Snapshot trial = a.trial.current(gameTick);
                NeuroLabMod.sendTelemetry(mob, neural, senses, body,
                        a.stimuli.activeCount(gameTick) > 0, a.mode, trial);
            }
            if (!a.mode.controlsBody()) continue;
            double turn = body.turn();
            mob.setYRot(mob.getYRot() + (float) (turn * 6));
            mob.yBodyRot = mob.getYRot();
            Vec3 velocity = mob.getDeltaMovement();
            var flyingAttribute = net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED;
            boolean canFly = mob instanceof net.minecraft.world.entity.FlyingMob
                    || mob instanceof NeuroFlyEntity
                    || mob instanceof net.minecraft.world.entity.monster.Ghast
                    || mob instanceof net.minecraft.world.entity.monster.Phantom
                    || mob.getAttribute(flyingAttribute) != null;
            double movement = mob.getAttributeValue(canFly && mob.getAttribute(flyingAttribute) != null
                    ? flyingAttribute : net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            double throttle = Math.max(0, Math.min(1, body.forward()));
            double forward = throttle * Math.max(0, movement);
            double newYaw = Math.toRadians(mob.getYRot());
            double vx = -Math.sin(newYaw) * forward;
            double vz = Math.cos(newYaw) * forward;
            double vy = velocity.y;
            if (canFly) {
                double maxVerticalSpeed = Math.max(0, movement * 0.75);
                double liftCommand = Math.max(0, Math.min(1, body.lift()));
                if (mob instanceof NeuroFlyEntity && a.mode == ControlMode.ASSISTED) {
                    int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            mob.blockPosition().getX(), mob.blockPosition().getZ());
                    double altitudeError = ground + 1.35 - mob.getY();
                    double hoverCommand = Math.max(-0.65, Math.min(0.65, altitudeError / 1.6));
                    if (body.lift() <= 0.05) liftCommand = hoverCommand;
                }
                double lift = liftCommand * maxVerticalSpeed;
                vy = Math.max(-maxVerticalSpeed, Math.min(maxVerticalSpeed, velocity.y * 0.8 + lift));
            } else if (body.escape() && mob.onGround()) {
                var jumpAttribute = net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH;
                double jumpStrength = mob.getAttribute(jumpAttribute) == null ? 0.42
                        : mob.getAttributeValue(jumpAttribute);
                vy = Math.max(velocity.y, jumpStrength);
            }
            mob.setDeltaMovement(0, vy, 0);
            mob.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(vx, 0, vz));
            mob.hasImpulse = true;
        }
    }

    private static WorldSample sample(Mob mob, ServerLevel level, Attachment attachment) {
        Vec3 eye = mob.getEyePosition();
        double yaw = Math.toRadians(mob.getYRot());
        float leftEye = sampleEye(level, mob, eye, yaw - Math.toRadians(28));
        float light = sampleEye(level, mob, eye, yaw);
        float rightEye = sampleEye(level, mob, eye, yaw + Math.toRadians(28));
        float looming = 0;
        double nearestItem = Double.POSITIVE_INFINITY;
        Entity target = null;
        double targetDistance = Double.POSITIVE_INFINITY;
        for (Entity nearby : level.getEntities(mob, mob.getBoundingBox().inflate(10), e -> e != mob && e.isAlive())) {
            double distance = nearby.distanceTo(mob);
            if (nearby instanceof ItemEntity item && !item.getItem().isEmpty()) {
                nearestItem = Math.min(nearestItem, distance);
                if (distance < targetDistance) { target = nearby; targetDistance = distance; }
            } else if (nearby instanceof Player player && !player.isSpectator()
                    && distance < targetDistance) {
                target = nearby;
                targetDistance = distance;
            }
            if (nearby instanceof ItemEntity) continue;
            Vec3 relative = nearby.getBoundingBox().getCenter().subtract(eye);
            double centerDistance = relative.length();
            if (centerDistance < 3.5) {
                boolean closing = nearby.getDeltaMovement().subtract(mob.getDeltaMovement()).dot(relative) < 0;
                double intensity = (1 - centerDistance / 3.5) * (closing ? 1.0 : 0.35);
                looming = Math.max(looming, (float) intensity);
            }
        }
        float tactile = mob.horizontalCollision || mob.verticalCollision || mob.hurtTime > 0 ? 1 : 0;
        float odor = Double.isFinite(nearestItem) ? (float) Math.max(0, Math.min(1, 1 - nearestItem / 8)) : 0;
        net.minecraft.world.level.block.state.BlockState underfoot = level.getBlockState(mob.blockPosition());
        float taste = underfoot.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK)
                || underfoot.is(net.minecraft.world.level.block.Blocks.CAKE)
                || underfoot.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) ? 1 : 0;
        float health = mob.getHealth();
        float maxHealth = Math.max(1, mob.getMaxHealth());
        float pain = mob.hurtTime > 0 || mob.isOnFire() || mob.isInLava() ? 1 : 0;
        pain = Math.max(pain, Math.min(1, Math.max(0, attachment.lastHealth - health) / maxHealth));
        float reward = Math.min(1, Math.max(0, health - attachment.lastHealth) / maxHealth);
        attachment.lastHealth = health;
        boolean harmfulGround = underfoot.is(net.minecraft.world.level.block.Blocks.CACTUS)
                || underfoot.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || underfoot.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                || underfoot.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);
        if (harmfulGround || mob.fallDistance > 3) pain = Math.max(pain, 0.5f);
        if (looming > 0.75f) pain = Math.max(pain, looming * 0.25f);
        if (Double.isFinite(nearestItem) && Double.isFinite(attachment.lastItemDistance)) {
            double progress = Math.max(0, attachment.lastItemDistance - nearestItem);
            reward = Math.max(reward, (float) Math.min(0.25, progress * 0.2));
        }
        attachment.lastItemDistance = nearestItem;
        FlyBrain.Drive senses = new FlyBrain.Drive(light, leftEye, rightEye, looming, tactile, odor, taste,
                pain, reward);

        EmbodimentDecoder.WorldCue cue = EmbodimentDecoder.WorldCue.NONE;
        if (target != null) {
            Vec3 towardTarget = target.position().subtract(mob.position());
            double targetYaw = Math.toDegrees(Math.atan2(-towardTarget.x, towardTarget.z));
            double relativeYaw = net.minecraft.util.Mth.wrapDegrees((float) targetYaw - mob.getYRot());
            double strength = Math.max(0, Math.min(1, (10 - targetDistance) / 6));
            double targetTurn = Math.max(-1, Math.min(1, relativeYaw / 75));
            cue = new EmbodimentDecoder.WorldCue(targetTurn, strength,
                    Math.min(16, targetDistance), 0, 0);
        }

        double leftClear = blockClearance(level, mob, eye, yaw - Math.toRadians(38), 4.5);
        double rightClear = blockClearance(level, mob, eye, yaw + Math.toRadians(38), 4.5);
        double frontClear = blockClearance(level, mob, eye, yaw, 4.5);
        double obstacle = Math.max(0, Math.min(1, (3.5 - frontClear) / 3.0));
        if (obstacle > cue.obstacle()) {
            double avoidance = Math.max(-1, Math.min(1, (rightClear - leftClear) / 3.0));
            if (Math.abs(avoidance) < 0.08)
                avoidance = (mob.getUUID().getLeastSignificantBits() & 1L) == 0 ? -0.75 : 0.75;
            cue = new EmbodimentDecoder.WorldCue(cue.targetTurn(), cue.targetStrength(),
                    cue.targetDistance(), obstacle, avoidance);
        }
        return new WorldSample(senses, cue);
    }

    private static double blockClearance(ServerLevel level, Mob mob, Vec3 origin, double yaw, double distance) {
        Vec3 direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        HitResult hit = level.clip(new ClipContext(origin, origin.add(direction.scale(distance)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return Math.min(distance, Math.sqrt(hit.getLocation().distanceToSqr(origin)));
    }

    private static float sampleEye(ServerLevel level, Mob mob, Vec3 origin, double yaw) {
        Vec3 direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        HitResult sight = level.clip(new ClipContext(origin, origin.add(direction.scale(10)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return (float) level.getMaxLocalRawBrightness(BlockPos.containing(sight.getLocation())) / 15f;
    }

    public static String setMode(Mob mob, ControlMode mode) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        a.mode = mode;
        mob.setNoAi(mode.noAi(a.originalNoAi));
        return null;
    }

    public static String startTrial(Mob mob, String label, int durationTicks) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        long tick = mob.level().getGameTime();
        TrialSession candidate = new TrialSession();
        TrialSession.Snapshot started;
        try { started = candidate.start(label, tick, durationTicks); }
        catch (IllegalArgumentException | ArithmeticException invalid) { return invalid.getMessage(); }
        TrialSession.Snapshot previous = a.trial.end();
        if (previous != null) TelemetryLog.trialEvent("trial_end", mob, a.mode, previous, tick);
        a.trial.start(started.label(), started.startedAtTick(), Math.toIntExact(started.durationTicks()));
        TelemetryLog.trialEvent("trial_start", mob, a.mode, started, tick);
        return null;
    }

    public static String endTrial(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        TrialSession.Snapshot ended = a.trial.end();
        if (ended == null) return "No trial is active.";
        TelemetryLog.trialEvent("trial_end", mob, a.mode, ended, mob.level().getGameTime());
        return null;
    }

    public static String clearStimuli(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        a.stimuli.clear();
        return null;
    }

    public static String describe(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return mob.getName().getString() + ": no NeuroLab brain attached.";
        FlyBrain.Snapshot snapshot = a.worker.snapshot();
        long gameTick = mob.level().getGameTime();
        TrialSession.Snapshot trial = a.trial.current(gameTick);
        return String.format(Locale.ROOT,
                "%s · mode %s · neural tick %d · spikes %d · active %d · real-time %.2fx · test pulses %d · trial %s · original AI %s",
                mob.getName().getString(), a.mode.id(), snapshot.tick(), snapshot.spikes(),
                snapshot.activeNeurons(), a.worker.realTimeFactor(),
                a.stimuli.activeCount(gameTick), trial == null ? "none" : trial.label() + " (" + trial.elapsedTicks(gameTick) + "/" + trial.durationTicks() + " ticks)", a.originalNoAi ? "disabled" : "enabled");
    }

    public static CompletableFuture<ConnectomeData> connectomeStatus() { return data; }
    public static int attachedCount() { return ATTACHED.size(); }
    public static int workerCount() { return BrainWorker.workerCount(); }
    public static double realTimeFactor(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        return a == null ? 0 : a.worker.realTimeFactor();
    }

    public static void shutdown() {
        for (Attachment a : ATTACHED.values()) {
            persistLearning(a);
            a.worker.close();
            TrialSession.Snapshot trial = a.trial.end();
            if (trial != null) TelemetryLog.trialEvent("trial_end", a.mob, a.mode, trial, a.mob.level().getGameTime());
            a.mob.setNoAi(a.originalNoAi);
        }
        ATTACHED.clear();
        AUTO_ATTACH_PENDING.clear();
    }


    private static final class Attachment {
        private final Mob mob;
        private final BrainWorker worker;
        private final boolean originalNoAi;
        private final StimulusSchedule stimuli = new StimulusSchedule();
        private ControlMode mode = ControlMode.ASSISTED;
        private final TrialSession trial = new TrialSession();
        private int persistedLearningRevision;
        private float lastHealth;
        private double lastItemDistance = Double.POSITIVE_INFINITY;

        private Attachment(Mob mob, BrainWorker worker, boolean originalNoAi) {
            this.mob = mob;
            this.worker = worker;
            this.originalNoAi = originalNoAi;
            this.lastHealth = mob.getHealth();
        }
    }

    private static void persistLearning(Attachment attachment) {
        int revision = attachment.worker.learningRevision();
        if (revision <= attachment.persistedLearningRevision) return;
        attachment.mob.getPersistentData().putLongArray(LEARNED_SYNAPSES_TAG,
                attachment.worker.learnedSynapses());
        attachment.persistedLearningRevision = revision;
    }

    private record WorldSample(FlyBrain.Drive senses, EmbodimentDecoder.WorldCue cue) {}

}

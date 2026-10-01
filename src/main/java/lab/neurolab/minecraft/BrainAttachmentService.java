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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Server-thread-owned bindings between Minecraft mobs and independent neural workers. */
public final class BrainAttachmentService {
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
        BrainWorker worker = new BrainWorker(data.join());
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
        attachment.stimuli.put(sense, new Pulse(strength, mob.tickCount + durationTicks));
        return null;
    }

    public static void queueAutoAttach(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof NeuroFlyEntity fly && fly.level() instanceof ServerLevel)
            AUTO_ATTACH_PENDING.putIfAbsent(fly.getUUID(), fly);
    }

    public static boolean detach(Mob mob) {
        Attachment a = ATTACHED.remove(mob.getUUID());
        if (a == null) return false;
        a.worker.close();
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
                mob.setNoAi(a.originalNoAi);
                a.worker.close(); ATTACHED.remove(mob.getUUID(), a); continue;
            }
            if (a.worker.failure() != null) {
                java.util.logging.Logger.getLogger("neurolab").log(java.util.logging.Level.SEVERE,
                        "Brain failed for entity " + mob.getId() + "; restoring its AI", a.worker.failure());
                detach(mob);
                continue;
            }
            FlyBrain.Drive senses = a.stimuli.apply(sample(mob, level), mob.tickCount);
            a.worker.accept(senses);
            FlyBrain.Snapshot neural = a.worker.snapshot();
            EmbodimentDecoder.Command body = EmbodimentDecoder.decode(neural, senses, mob.tickCount,
                    mob.getUUID().getLeastSignificantBits());
            if (mob.tickCount % 2 == 0)
                NeuroLabMod.sendTelemetry(mob, neural, senses, body, a.stimuli.isActive());
            double turn = body.turn();
            mob.setYRot(mob.getYRot() + (float) (turn * 6));
            mob.yBodyRot = mob.getYRot();
            Vec3 velocity = mob.getDeltaMovement();
            double movement = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            double forward = body.forward() * movement * (body.escape() ? 1.8 : 1.0);
            double newYaw = Math.toRadians(mob.getYRot());
            double vx = -Math.sin(newYaw) * forward;
            double vz = Math.cos(newYaw) * forward;
            double vy = velocity.y;
            if (mob instanceof net.minecraft.world.entity.FlyingMob ||
                    mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED) != null) {
                double lift = Math.max(0, body.lift() * 0.16 + (body.escape() ? 0.08 : 0));
                vy = Math.max(-0.12, Math.min(0.16, velocity.y * 0.8 + lift));
            } else if (body.escape() && mob.onGround()) vy = Math.max(velocity.y, 0.42);
            mob.setDeltaMovement(vx, vy, vz);
            mob.hasImpulse = true;
        }
    }

    private static FlyBrain.Drive sample(Mob mob, ServerLevel level) {
        Vec3 eye = mob.getEyePosition();
        double yaw = Math.toRadians(mob.getYRot());
        float leftEye = sampleEye(level, mob, eye, yaw - Math.toRadians(28));
        float light = sampleEye(level, mob, eye, yaw);
        float rightEye = sampleEye(level, mob, eye, yaw + Math.toRadians(28));
        float looming = 0;
        for (Entity nearby : level.getEntities(mob, mob.getBoundingBox().inflate(4), e -> e != mob && e.isAlive())) {
            Vec3 relative = nearby.getBoundingBox().getCenter().subtract(eye);
            double distance = relative.length();
            if (distance < 3 && nearby.getDeltaMovement().subtract(mob.getDeltaMovement()).dot(relative) < 0)
                looming = Math.max(looming, (float) (1 - distance / 3));
        }
        float tactile = mob.horizontalCollision || mob.verticalCollision || mob.hurtTime > 0 ? 1 : 0;
        float odor = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                mob.getBoundingBox().inflate(5), item -> !item.getItem().isEmpty()).isEmpty() ? 0 : 0.7f;
        net.minecraft.world.level.block.state.BlockState underfoot = level.getBlockState(mob.blockPosition());
        float taste = underfoot.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK)
                || underfoot.is(net.minecraft.world.level.block.Blocks.CAKE)
                || underfoot.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) ? 1 : 0;
        return new FlyBrain.Drive(light, leftEye, rightEye, looming, tactile, odor, taste);
    }

    private static float sampleEye(ServerLevel level, Mob mob, Vec3 origin, double yaw) {
        Vec3 direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        HitResult sight = level.clip(new ClipContext(origin, origin.add(direction.scale(10)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return (float) level.getMaxLocalRawBrightness(BlockPos.containing(sight.getLocation())) / 15f;
    }

    public static CompletableFuture<ConnectomeData> connectomeStatus() { return data; }
    public static int attachedCount() { return ATTACHED.size(); }
    public static int workerCount() { return BrainWorker.workerCount(); }
    public static double realTimeFactor(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        return a == null ? 0 : a.worker.realTimeFactor();
    }

    public static void shutdown() {
        for (Attachment a : ATTACHED.values()) { a.worker.close(); a.mob.setNoAi(a.originalNoAi); }
        ATTACHED.clear();
        AUTO_ATTACH_PENDING.clear();
    }

    private enum Sense { EYE, LEFT_EYE, RIGHT_EYE, LOOMING, TOUCH, ODOR, TASTE }
    private record Pulse(float strength, int expiresAt) {}

    private static final class Attachment {
        private final Mob mob;
        private final BrainWorker worker;
        private final boolean originalNoAi;
        private final Stimuli stimuli = new Stimuli();

        private Attachment(Mob mob, BrainWorker worker, boolean originalNoAi) {
            this.mob = mob;
            this.worker = worker;
            this.originalNoAi = originalNoAi;
        }
    }

    private static final class Stimuli {
        private final EnumMap<Sense, Pulse> active = new EnumMap<>(Sense.class);

        private void put(Sense sense, Pulse pulse) { active.put(sense, pulse); }

        private boolean isActive() { return !active.isEmpty(); }

        private FlyBrain.Drive apply(FlyBrain.Drive natural, int tick) {
            active.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= tick);
            float light = Math.max(natural.light(), strength(Sense.EYE));
            float left = Math.max(natural.leftEye(), Math.max(strength(Sense.EYE), strength(Sense.LEFT_EYE)));
            float right = Math.max(natural.rightEye(), Math.max(strength(Sense.EYE), strength(Sense.RIGHT_EYE)));
            return new FlyBrain.Drive(light, left, right,
                    Math.max(natural.looming(), strength(Sense.LOOMING)),
                    Math.max(natural.tactile(), strength(Sense.TOUCH)),
                    Math.max(natural.odor(), strength(Sense.ODOR)),
                    Math.max(natural.taste(), strength(Sense.TASTE)));
        }

        private float strength(Sense sense) {
            Pulse pulse = active.get(sense);
            return pulse == null ? 0 : pulse.strength();
        }
    }
}

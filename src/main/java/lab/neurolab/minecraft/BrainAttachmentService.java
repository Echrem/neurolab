package lab.neurolab.minecraft;

import lab.neurolab.brain.BrainWorker;
import lab.neurolab.brain.ConnectomeData;
import lab.neurolab.brain.FlyBrain;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Server-thread-owned bindings between Minecraft mobs and independent neural workers. */
public final class BrainAttachmentService {
    private static final int MAX_BRAINS = 4;
    private static final Map<UUID, Attachment> ATTACHED = new ConcurrentHashMap<>();
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
        if (!(mob.level() instanceof ServerLevel) || ATTACHED.containsKey(mob.getUUID())) return false;
        if (!data.isDone() || data.isCompletedExceptionally() || ATTACHED.size() >= MAX_BRAINS) return false;
        BrainWorker worker = new BrainWorker(data.join(), mob.getUUID().toString().substring(0, 8));
        Attachment state = new Attachment(mob, worker, mob.isNoAi());
        mob.setNoAi(true);
        ATTACHED.put(mob.getUUID(), state);
        return true;
    }

    public static boolean detach(Mob mob) {
        Attachment a = ATTACHED.remove(mob.getUUID());
        if (a == null) return false;
        a.worker.close();
        mob.setNoAi(a.originalNoAi);
        return true;
    }

    public static void tick() {
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
            a.worker.accept(sample(mob, level));
            FlyBrain.Snapshot neural = a.worker.snapshot();
            if (mob.tickCount % 2 == 0) NeuroLabMod.sendTelemetry(mob, neural);
            double turn = Math.max(-1, Math.min(1, neural.turn()));
            mob.setYRot(mob.getYRot() + (float) (turn * 6));
            mob.yBodyRot = mob.getYRot();
            Vec3 velocity = mob.getDeltaMovement();
            double movement = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            double forward = neural.forward() * movement * (neural.escape() ? 1.8 : 1.0);
            double newYaw = Math.toRadians(mob.getYRot());
            double vx = -Math.sin(newYaw) * forward;
            double vz = Math.cos(newYaw) * forward;
            double vy = velocity.y;
            if (mob instanceof net.minecraft.world.entity.FlyingMob ||
                    mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED) != null) {
                double lift = Math.max(0, neural.lift() * 0.16 + (neural.escape() ? 0.08 : 0));
                vy = Math.max(-0.12, Math.min(0.16, velocity.y * 0.8 + lift));
            } else if (neural.escape() && mob.onGround()) vy = Math.max(velocity.y, 0.42);
            mob.setDeltaMovement(vx, vy, vz);
            mob.hasImpulse = true;
        }
    }

    private static FlyBrain.Drive sample(Mob mob, ServerLevel level) {
        Vec3 eye = mob.getEyePosition();
        double yaw = Math.toRadians(mob.getYRot());
        Vec3 front = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        HitResult sight = level.clip(new ClipContext(eye, eye.add(front.scale(10)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob));
        float light = (float) level.getMaxLocalRawBrightness(BlockPos.containing(sight.getLocation())) / 15f;
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
        return new FlyBrain.Drive(light, looming, tactile, odor, taste);
    }

    public static CompletableFuture<ConnectomeData> connectomeStatus() { return data; }
    public static int attachedCount() { return ATTACHED.size(); }
    public static double realTimeFactor(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        return a == null ? 0 : a.worker.realTimeFactor();
    }

    public static void shutdown() {
        for (Attachment a : ATTACHED.values()) { a.worker.close(); a.mob.setNoAi(a.originalNoAi); }
        ATTACHED.clear();
    }

    private record Attachment(Mob mob, BrainWorker worker, boolean originalNoAi) {}
}

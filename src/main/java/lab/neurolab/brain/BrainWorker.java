package lab.neurolab.brain;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Keeps neural integration off the server tick and publishes immutable observations. */
public final class BrainWorker implements AutoCloseable {
    private static final int WORKER_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    private static final ScheduledThreadPoolExecutor EXECUTOR = createExecutor();
    private final FlyBrain brain;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile FlyBrain.Drive drive = new FlyBrain.Drive(0, 0, 0, 0, 0);
    private volatile Throwable failure;
    private volatile double realTimeFactor = 1.0;
    private final ScheduledFuture<?> task;
    private long nextDueNanos;

    public BrainWorker(ConnectomeData data) {
        this(data, new long[0]);
    }

    public BrainWorker(ConnectomeData data, long[] learnedSynapses) {
        brain = new FlyBrain(data, learnedSynapses);
        nextDueNanos = System.nanoTime();
        task = EXECUTOR.scheduleAtFixedRate(this::run, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void accept(FlyBrain.Drive next) { drive = next; }
    public FlyBrain.Snapshot snapshot() { return brain.latest(); }
    public Throwable failure() { return failure; }
    public double realTimeFactor() { return realTimeFactor; }
    public long[] learnedSynapses() { return brain.learnedSynapses(); }
    public int learningRevision() { return brain.learningRevision(); }
    public void copyLearnedSynapses(long[] state) { brain.copyLearnedSynapses(state); }

    private void run() {
        if (running.get()) {
            long started = System.nanoTime();
            long scheduledAt = nextDueNanos;
            nextDueNanos = scheduledAt + TimeUnit.MILLISECONDS.toNanos(50);
            double queueDelayMs = Math.max(0, started - scheduledAt) / 1e6;
            try { brain.advance(drive); }
            catch (Throwable problem) {
                failure = problem;
                running.set(false);
                throw new IllegalStateException("NeuroLab brain task failed", problem);
            }
            double elapsedMs = (System.nanoTime() - started) / 1e6;
            double sample = Math.min(1, 50.0 / Math.max(0.01, elapsedMs + queueDelayMs));
            realTimeFactor = realTimeFactor * 0.85 + sample * 0.15;
        }
    }

    private static ScheduledThreadPoolExecutor createExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private int sequence;
            @Override public synchronized Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "neurolab-brain-pool-" + ++sequence);
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(WORKER_COUNT, factory);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    public static int workerCount() { return WORKER_COUNT; }
    public FlyBrain.Drive latestDrive() { return drive; }
    @Override public void close() { running.set(false); task.cancel(false); }
}

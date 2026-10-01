package lab.neurolab.brain;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** Keeps neural integration off the server tick and publishes immutable observations. */
public final class BrainWorker implements AutoCloseable {
    private final FlyBrain brain;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile FlyBrain.Drive drive = new FlyBrain.Drive(0, 0, 0, 0, 0);
    private volatile Throwable failure;
    private volatile double realTimeFactor = 1.0;
    private final Thread thread;

    public BrainWorker(ConnectomeData data, String label) {
        brain = new FlyBrain(data);
        thread = new Thread(this::run, "neurolab-brain-" + label);
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    public void accept(FlyBrain.Drive next) { drive = next; }
    public FlyBrain.Snapshot snapshot() { return brain.latest(); }
    public Throwable failure() { return failure; }
    public double realTimeFactor() { return realTimeFactor; }

    private void run() {
        long deadline = System.nanoTime();
        while (running.get()) {
            long started = System.nanoTime();
            try { brain.advance(drive); }
            catch (Throwable problem) { failure = problem; running.set(false); break; }
            double elapsedMs = (System.nanoTime() - started) / 1e6;
            double sample = Math.min(1, 50.0 / Math.max(0.01, elapsedMs));
            realTimeFactor = realTimeFactor * 0.85 + sample * 0.15;
            deadline += 50_000_000L;
            long wait = deadline - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
            else if (wait < -500_000_000L) deadline = System.nanoTime();
        }
    }

    @Override public void close() { running.set(false); thread.interrupt(); }
}

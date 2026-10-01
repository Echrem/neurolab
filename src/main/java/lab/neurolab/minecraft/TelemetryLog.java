package lab.neurolab.minecraft;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Non-blocking JSONL telemetry writer; neural observations never perform disk IO on a game tick. */
public final class TelemetryLog {
    private static final Logger LOG = Logger.getLogger("neurolab");
    private static final ArrayBlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(4096);
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final LongAdder DROPPED = new LongAdder();
    private static Thread writer;
    private static Path file;

    private TelemetryLog() {}

    public static synchronized void start(Path path) {
        if (RUNNING.get()) return;
        file = path;
        RUNNING.set(true);
        writer = new Thread(TelemetryLog::writeLoop, "neurolab-telemetry-writer");
        writer.setDaemon(true);
        writer.start();
    }

    public static void record(BrainTelemetryPayload p) {
        String line = "{\"time\":\"" + Instant.now() + "\",\"entityId\":" + p.entityId()
                + ",\"spikes\":" + p.spikes() + ",\"activeNeurons\":" + p.active()
                + ",\"forward\":" + p.forward() + ",\"turn\":" + p.turn()
                + ",\"lift\":" + p.lift() + ",\"realTimeFactor\":" + p.realTimeFactor()
                + ",\"escape\":" + p.escape() + ",\"light\":" + p.light()
                + ",\"leftEye\":" + p.leftEye() + ",\"rightEye\":" + p.rightEye()
                + ",\"looming\":" + p.looming() + ",\"touch\":" + p.touch()
                + ",\"odor\":" + p.odor() + ",\"taste\":" + p.taste()
                + ",\"bodyForward\":" + p.bodyForward() + ",\"bodyTurn\":" + p.bodyTurn()
                + ",\"bodyLift\":" + p.bodyLift() + ",\"bodyEscape\":" + p.bodyEscape()
                + ",\"reflex\":" + p.reflex() + ",\"testStimulus\":" + p.testStimulus() + "}";
        if (!QUEUE.offer(line)) DROPPED.increment();
    }

    private static void writeLoop() {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                while (RUNNING.get() || !QUEUE.isEmpty()) {
                    String line = QUEUE.poll(250, TimeUnit.MILLISECONDS);
                    if (line != null) { out.write(line); out.newLine(); }
                }
                out.flush();
            }
        } catch (IOException e) { LOG.log(Level.SEVERE, "Could not write NeuroLab event log " + file, e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { RUNNING.set(false); }
    }

    public static synchronized void stop() {
        RUNNING.set(false);
        if (writer != null) {
            try { writer.join(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (DROPPED.sum() > 0) LOG.warning("NeuroLab dropped " + DROPPED.sum() + " telemetry records because the queue filled");
        writer = null;
    }
}

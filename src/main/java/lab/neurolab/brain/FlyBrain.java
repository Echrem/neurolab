package lab.neurolab.brain;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/** Sparse event-driven leaky integrate-and-fire simulation over an immutable adult-fly connectome. */
public final class FlyBrain {
    public record Drive(float light, float leftEye, float rightEye, float looming, float tactile, float odor,
                        float taste, float pain, float reward) {
        public Drive(float light, float leftEye, float rightEye, float looming, float tactile, float odor,
                     float taste) {
            this(light, leftEye, rightEye, looming, tactile, odor, taste, 0, 0);
        }
        public Drive(float light, float looming, float tactile, float odor, float taste) {
            this(light, light, light, looming, tactile, odor, taste);
        }
        public Drive {
            light = clamp(light); leftEye = clamp(leftEye); rightEye = clamp(rightEye);
            looming = clamp(looming); tactile = clamp(tactile); odor = clamp(odor); taste = clamp(taste);
            pain = clamp(pain); reward = clamp(reward);
        }
        private static float clamp(float v) { return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0; }
    }
    public record Snapshot(long tick, long spikes, long synapticEvents, int activeNeurons, double forward, double turn, double lift,
                           boolean escape, Map<String, Double> populations) {
        public Snapshot(long tick, long spikes, int activeNeurons, double forward, double turn, double lift,
                        boolean escape, Map<String, Double> populations) {
            this(tick, spikes, 0, activeNeurons, forward, turn, lift, escape, populations);
        }
    }

    private static final double DT_MS = 0.5, TAU_MS = 20, SYNAPSE_TAU_MS = 5;
    private static final double REST_MV = -52, THRESHOLD_MV = -45, RESET_MV = -52;
    private static final double SYNAPSE_SCALE_MV = 0.0008;
    private final ConnectomeData graph;
    private final SynapticPlasticity plasticity;
    private volatile long[] pendingMindCopy;
    private final double[] voltage, current;
    private final byte[] refractory;
    private final boolean[] queued;
    private int[] active, next;
    private final int[] sensory, leftEye, rightEye;
    private final int[] olfactory, gustatory, tactile;
    private final String[] types;
    private final byte[] signs;
    private final SplittableRandom random = new SplittableRandom(0x4e6575726fL);
    private volatile Snapshot latest = new Snapshot(0, 0, 0, 0, 0, 0, 0, false, Map.of());
    private long tick;

    public FlyBrain(ConnectomeData graph) {
        this(graph, new long[0]);
    }

    public FlyBrain(ConnectomeData graph, long[] learnedSynapses) {
        this.graph = graph;
        plasticity = new SynapticPlasticity(learnedSynapses, graph.connections());
        int n = graph.neurons();
        voltage = new double[n]; current = new double[n]; refractory = new byte[n]; queued = new boolean[n];
        active = new int[Math.max(256, n / 32)]; next = new int[active.length];
        for (int i = 0; i < n; i++) voltage[i] = REST_MV;
        sensory = graph.visualReceptors;
        leftEye = graph.visualLeftReceptors;
        rightEye = graph.visualRightReceptors;
        olfactory = graph.olfactoryNeurons;
        gustatory = graph.gustatoryNeurons;
        tactile = graph.tactileNeurons;
        types = graph.neuronTypes;
        signs = graph.transmitterSigns;
    }

    public Snapshot latest() { return latest; }
    public long[] learnedSynapses() { return plasticity.snapshot(); }
    public int learningRevision() { return plasticity.revision(); }

    /** Queue a copied learned-connectome imprint; applied at the next neural step. */
    public void copyLearnedSynapses(long[] state) {
        pendingMindCopy = java.util.Arrays.copyOf(state, state.length);
    }

    /** Advance 50 ms of neural time. The call is intended for a dedicated worker thread, never the Minecraft tick. */
    public Snapshot advance(Drive drive) {
        long[] copiedMind = pendingMindCopy;
        if (copiedMind != null) {
            pendingMindCopy = null;
            plasticity.replace(copiedMind, graph.connections());
        }
        long spikes = 0;
        long synapticEvents = 0;
        Map<String, Integer> counts = new HashMap<>();
        for (int sub = 0; sub < 100; sub++) {
            int nextCount = 0;
            if (leftEye.length > 0 && rightEye.length > 0) {
                nextCount = inject(leftEye, (drive.light + drive.leftEye) * 35 + drive.looming * 45, next, nextCount);
                nextCount = inject(rightEye, (drive.light + drive.rightEye) * 35 + drive.looming * 45, next, nextCount);
            } else {
                nextCount = inject(sensory, drive.light * 70 + drive.looming * 90, next, nextCount);
            }
            nextCount = inject(olfactory, drive.odor * 80, next, nextCount);
            nextCount = inject(gustatory, drive.taste * 100 + drive.reward * 200, next, nextCount);
            nextCount = inject(tactile, drive.tactile * 80 + drive.pain * 200, next, nextCount);
            for (int k = 0; k < activeCount; k++) {
                int id = active[k];
                queued[id] = false;
                if (refractory[id] > 0) {
                    refractory[id]--;
                    nextCount = enqueue(id, next, nextCount);
                    continue;
                }
                double v = voltage[id] + (REST_MV - voltage[id] + current[id]) * DT_MS / TAU_MS;
                current[id] *= Math.exp(-DT_MS / SYNAPSE_TAU_MS);
                if (v >= THRESHOLD_MV) {
                    voltage[id] = RESET_MV;
                    refractory[id] = 4;
                    spikes++;
                    String type = types[id];
                    if (type.startsWith("DN") || type.startsWith("MN") || type.startsWith("BDN")) counts.merge(type, 1, Integer::sum);
                    for (int edge = graph.rowOffsets[id]; edge < graph.rowOffsets[id + 1]; edge++) {
                        int to = graph.targets[edge];
                        double weight = Short.toUnsignedInt(graph.connectionWeights[edge])
                                * plasticity.weightScale(edge) * SYNAPSE_SCALE_MV;
                        current[to] += weight * (signs[id] < 0 ? -1 : 1);
                        nextCount = enqueue(to, next, nextCount);
                        if (drive.reward > 0 || drive.pain > 0) plasticity.record(edge, tick + 1);
                    }
                    synapticEvents += graph.rowOffsets[id + 1] - graph.rowOffsets[id];
                } else {
                    voltage[id] = v;
                    if (Math.abs(v - REST_MV) > 0.03 || Math.abs(current[id]) > 0.03)
                        nextCount = enqueue(id, next, nextCount);
                }
            }
            int[] swap = active; active = next; next = swap; activeCount = nextCount;
        }
        double fwd = 0, left = 0, right = 0, lift = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String type = e.getKey();
            if (type.startsWith("DNp09") || type.startsWith("BDN") || type.startsWith("oDN1")) fwd += e.getValue();
            if (type.startsWith("DNa02") && (type.endsWith("L") || type.contains("_L"))) left += e.getValue();
            if (type.startsWith("DNa02") && (type.endsWith("R") || type.contains("_R"))) right += e.getValue();
            if (type.startsWith("DNg02")) lift += e.getValue();
        }
        double driveForward = Math.min(1, fwd / 8.0);
        double turn = Math.max(-1, Math.min(1, (right - left) / 5.0));
        double liftDrive = Math.min(1, lift / 8.0);
        boolean escape = counts.entrySet().stream().anyMatch(e -> e.getKey().startsWith("DNp01") && e.getValue() > 0);
        Map<String, Double> rates = Map.of("DNp09/BDN-forward", driveForward, "DNa02-turn", turn, "DNg02-lift", liftDrive,
                "DNp01-escape", escape ? 1.0 : 0.0);
        plasticity.applyFeedback(drive.reward, drive.pain, tick + 1);
        latest = new Snapshot(++tick, spikes, synapticEvents, activeCount, driveForward, turn, liftDrive, escape, rates);
        return latest;
    }

    private int activeCount;
    private int inject(int[] ids, double hz, int[] target, int count) {
        double chance = hz * DT_MS / 1000.0;
        if (chance <= 0) return count;
        for (int id : ids) if (random.nextDouble() < chance) {
            // Sensory events enter only through annotated sensory populations; no motor neuron is stimulated directly.
            voltage[id] = THRESHOLD_MV + 1.0;
            count = enqueue(id, target, count);
        }
        return count;
    }

    private int enqueue(int id, int[] buffer, int count) {
        if (queued[id]) return count;
        boolean isActive = buffer == active, isNext = buffer == next;
        if (count >= buffer.length) {
            buffer = grow(buffer);
            if (isActive) active = buffer;
            if (isNext) next = buffer;
        }
        buffer[count++] = id;
        queued[id] = true;
        return count;
    }

    private static int[] grow(int[] a) { return java.util.Arrays.copyOf(a, Math.min(Integer.MAX_VALUE - 8, a.length * 2)); }
}

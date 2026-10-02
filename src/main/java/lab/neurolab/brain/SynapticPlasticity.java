package lab.neurolab.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded reward-modulated eligibility traces keyed by directed CSR edge index. */
final class SynapticPlasticity {
    private static final int TRACE_CAPACITY = 4096;
    private static final int LEARNED_EDGE_CAPACITY = 8192;
    private static final int ELIGIBILITY_WINDOW = 12;
    private static final float LEARNING_RATE = 0.0001f;
    private static final float MAX_WEIGHT_CHANGE = 0.25f;

    private final int[] recentEdges = new int[TRACE_CAPACITY];
    private final long[] recentSteps = new long[TRACE_CAPACITY];
    private int cursor;
    private volatile int revision;
    private volatile Map<Integer, Float> weights = Map.of();
    private final LinkedHashMap<Integer, Float> learned = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
            return size() > LEARNED_EDGE_CAPACITY;
        }
    };

    SynapticPlasticity(long[] savedState, int edgeCount) {
        restore(savedState, edgeCount);
    }

    void replace(long[] savedState, int edgeCount) {
        learned.clear();
        restore(savedState, edgeCount);
        revision++;
    }

    private void restore(long[] savedState, int edgeCount) {
        for (long entry : savedState) {
            int edge = (int) (entry >>> 32);
            float change = Float.intBitsToFloat((int) entry);
            if (edge >= 0 && edge < edgeCount && Float.isFinite(change)
                    && Math.abs(change) <= MAX_WEIGHT_CHANGE && change != 0) learned.put(edge, change);
        }
        weights = Map.copyOf(learned);
    }

    void record(int directedEdge, long step) {
        recentEdges[cursor] = directedEdge;
        recentSteps[cursor] = step;
        cursor = (cursor + 1) % TRACE_CAPACITY;
    }

    void applyFeedback(float reward, float pain, long step) {
        double feedback = Math.max(-1, Math.min(1, (double) reward - pain));
        if (Math.abs(feedback) < 0.001) return;
        boolean changed = false;
        for (int i = 0; i < TRACE_CAPACITY; i++) {
            long age = step - recentSteps[i];
            if (age < 0 || age > ELIGIBILITY_WINDOW || recentSteps[i] == 0) continue;
            int edge = recentEdges[i];
            float before = learned.getOrDefault(edge, 0f);
            float after = (float) Math.max(-MAX_WEIGHT_CHANGE,
                    Math.min(MAX_WEIGHT_CHANGE, before + feedback * LEARNING_RATE));
            if (after != before) {
                learned.put(edge, after);
                changed = true;
            }
        }
        if (changed) {
            weights = Map.copyOf(learned);
            revision++;
        }
    }

    double weightScale(int directedEdge) {
        Map<Integer, Float> activeWeights = weights;
        return activeWeights.isEmpty() ? 1 : 1 + activeWeights.getOrDefault(directedEdge, 0f);
    }

    int revision() { return revision; }

    long[] snapshot() {
        Map<Integer, Float> stable = weights;
        long[] state = new long[stable.size()];
        int index = 0;
        for (Map.Entry<Integer, Float> entry : stable.entrySet()) {
            state[index++] = ((long) entry.getKey() << 32) | (Float.floatToIntBits(entry.getValue()) & 0xffffffffL);
        }
        return state;
    }
}

package lab.neurolab.brain;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Minimal reader for the documented FLYB v1 interchange layout; this implementation is authored for NeuroLab. */
public final class ConnectomeData {
    public final String dataset;
    public final String[] neuronTypes;
    public final byte[] transmitterSigns;
    public final int[] olfactoryNeurons, gustatoryNeurons, tactileNeurons;
    public final int[] rowOffsets;
    public final int[] targets;
    public final short[] connectionWeights;
    public final int[] visualReceptors;
    public final int[] motorNeurons;

    private ConnectomeData(String dataset, String[] types, byte[] signs, int[] olfactory, int[] gustatory,
                           int[] tactile, int[] rows, int[] targets, short[] weights, int[] visual, int[] motors) {
        this.dataset = dataset;
        this.neuronTypes = types;
        this.transmitterSigns = signs;
        this.olfactoryNeurons = olfactory;
        this.gustatoryNeurons = gustatory;
        this.tactileNeurons = tactile;
        this.rowOffsets = rows;
        this.targets = targets;
        this.connectionWeights = weights;
        this.visualReceptors = visual;
        this.motorNeurons = motors;
    }

    public int neurons() { return neuronTypes.length; }
    public int connections() { return targets.length; }

    public static ConnectomeData readBundled() throws IOException {
        InputStream raw = ConnectomeData.class.getResourceAsStream("/connectome/male-cns-v1.0.flyb.gz");
        if (raw == null) throw new IOException("Bundled male-cns connectome not found");
        try (LEInput in = new LEInput(new BufferedInputStream(new GZIPInputStream(raw)))) {
            String magic = new String(in.bytes(4), StandardCharsets.US_ASCII);
            if (!"FLYB".equals(magic)) throw new IOException("Not a FLYB connectome: " + magic);
            int version = in.i32();
            if (version != 1) throw new IOException("Unsupported FLYB version: " + version);
            int n = in.i32(), m = in.i32(), retinaCount = in.i32();
            if (n < 1 || n > 2_000_000 || m < 1 || m > 100_000_000 || retinaCount < 0 || retinaCount > n * 4)
                throw new IOException("Implausible connectome dimensions: neurons=" + n + " edges=" + m);
            String dataset = in.str16();
            in.skipFully(in.i32()); // descriptive metadata JSON
            String[] typesTable = in.table16();
            in.table16(); // superclass labels, retained in the file but not used by this decoder
            String[] classTable = in.table16();
            for (int i = 0; i < 7; i++) in.table16(); // remaining neuPrint label tables

            in.skipFully((long) n * 8); // neuPrint body ids
            int[] typeIds = new int[n];
            for (int i = 0; i < n; i++) typeIds[i] = in.i32();
            in.skipFully(n);
            byte[] classIds = in.bytes(n);
            in.skipFully((long) n * 2 + n); // subclass and transmitter label indexes
            byte[] signs = in.bytes(n);
            in.skipFully((long) n * 7); // side, hex axes, dimorphism, sex markers, neuromere, nerve
            in.skipFully((long) n * 12 + (long) n * 8);

            int[] rows = new int[n + 1];
            for (int i = 0; i <= n; i++) rows[i] = in.i32();
            if (rows[n] != m) throw new IOException("CSR edge count does not match header");
            int[] targets = new int[m];
            for (int i = 0; i < m; i++) targets[i] = in.i32();
            short[] weights = new short[m];
            for (int i = 0; i < m; i++) weights[i] = (short) in.u16();

            int[] visualTmp = new int[retinaCount];
            for (int i = 0; i < retinaCount; i++) {
                visualTmp[i] = in.i32();
                in.skipFully(4); // side, hex1, hex2, receptor kind
            }

            String[] neuronTypes = new String[n];
            int motorN = 0, odorN = 0, tasteN = 0, touchN = 0;
            for (int i = 0; i < n; i++) {
                int ti = typeIds[i];
                neuronTypes[i] = ti >= 0 && ti < typesTable.length ? typesTable[ti] : "";
                if (neuronTypes[i].startsWith("MN") || neuronTypes[i].startsWith("DN")) motorN++;
                String cl = Byte.toUnsignedInt(classIds[i]) < classTable.length ? classTable[Byte.toUnsignedInt(classIds[i])] : "";
                if (neuronTypes[i].startsWith("ORN_") || "olfactory".equals(cl)) odorN++;
                if ("gustatory".equals(cl)) tasteN++;
                if (isTactile(cl)) touchN++;
            }
            int[] motors = new int[motorN], odor = new int[odorN], taste = new int[tasteN], touch = new int[touchN];
            for (int i = 0, mi = 0, oi = 0, gi = 0, xi = 0; i < n; i++) {
                String type = neuronTypes[i];
                if (type.startsWith("MN") || type.startsWith("DN")) motors[mi++] = i;
                String cl = Byte.toUnsignedInt(classIds[i]) < classTable.length ? classTable[Byte.toUnsignedInt(classIds[i])] : "";
                if (type.startsWith("ORN_") || "olfactory".equals(cl)) odor[oi++] = i;
                if ("gustatory".equals(cl)) taste[gi++] = i;
                if (isTactile(cl)) touch[xi++] = i;
            }
            for (int id : visualTmp) if (id < 0 || id >= n) throw new IOException("Retina neuron index outside connectome");
            return new ConnectomeData(dataset, neuronTypes, signs, odor, taste, touch, rows, targets, weights, visualTmp, motors);
        }
    }

    private static final class LEInput extends InputStream implements AutoCloseable {
        private final InputStream source;
        LEInput(InputStream source) { this.source = source; }
        int u8() throws IOException { int b = source.read(); if (b < 0) throw new EOFException(); return b; }
        int u16() throws IOException { return u8() | (u8() << 8); }
        int i32() throws IOException { return u8() | (u8() << 8) | (u8() << 16) | (u8() << 24); }
        String str16() throws IOException { return new String(bytes(u16()), StandardCharsets.UTF_8); }
        String[] table16() throws IOException { String[] a = new String[u16()]; for (int i = 0; i < a.length; i++) a[i] = str16(); return a; }
        byte[] bytes(int n) throws IOException { byte[] b = source.readNBytes(n); if (b.length != n) throw new EOFException(); return b; }
        void skipFully(long n) throws IOException { while (n > 0) { long k = source.skip(n); if (k == 0) { if (source.read() < 0) throw new EOFException(); k = 1; } n -= k; } }
        @Override public int read() throws IOException { return source.read(); }
        @Override public void close() throws IOException { source.close(); }
    }

    private static boolean isTactile(String neuronClass) {
        return neuronClass.startsWith("mechanosensory") || neuronClass.contains("proprioceptive");
    }
}

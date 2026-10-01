package lab.neurolab.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persistent local preferences for the telemetry dashboard, not simulation/server settings. */
public final class DashboardPreferences {
    public static final String[] CHART_NAMES = {
            "Spikes per sample", "Active neurons", "Forward drive", "Turn bias", "Lift drive",
            "Real-time factor", "Center-eye light", "Left-eye light", "Right-eye light", "Looming",
            "Touch", "Odor", "Taste", "Body forward", "Body turn", "Body lift"
    };
    private static final boolean[] CHARTS = new boolean[CHART_NAMES.length];
    private static boolean leftPanel = true;
    private static boolean rightPanel = true;

    static {
        java.util.Arrays.fill(CHARTS, true);
        load();
    }

    private DashboardPreferences() {}

    public static synchronized boolean showLeftPanel() { return leftPanel; }
    public static synchronized boolean showRightPanel() { return rightPanel; }
    public static synchronized boolean showChart(int index) { return CHARTS[index]; }
    public static synchronized void toggleLeftPanel() { leftPanel = !leftPanel; save(); }
    public static synchronized void toggleRightPanel() { rightPanel = !rightPanel; save(); }
    public static synchronized void toggleChart(int index) { CHARTS[index] = !CHARTS[index]; save(); }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/neurolab-dashboard.properties");
    }

    private static void load() {
        Path file = path();
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (var in = Files.newInputStream(file)) {
            properties.load(in);
            leftPanel = Boolean.parseBoolean(properties.getProperty("panel.left", "true"));
            rightPanel = Boolean.parseBoolean(properties.getProperty("panel.right", "true"));
            for (int i = 0; i < CHARTS.length; i++)
                CHARTS[i] = Boolean.parseBoolean(properties.getProperty("chart." + i, "true"));
        } catch (IOException ignored) {
            // Defaults remain active if the local preference file is unavailable.
        }
    }

    private static synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("panel.left", Boolean.toString(leftPanel));
        properties.setProperty("panel.right", Boolean.toString(rightPanel));
        for (int i = 0; i < CHARTS.length; i++) properties.setProperty("chart." + i, Boolean.toString(CHARTS[i]));
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) { properties.store(out, "NeuroLab dashboard preferences"); }
        } catch (IOException ignored) {
            // Dashboard settings still apply for this session if they cannot be persisted.
        }
    }
}

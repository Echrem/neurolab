package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Selectable, multi-signal telemetry dashboard for attached mobs. */
public final class NeuroAnalysisScreen extends Screen {
    private static final int PANEL = 0xD90B1118;
    private static final int PANEL_INNER = 0xC216232F;
    private int selectedEntityId = -1;
    private List<TelemetryHistory.SeriesView> frozenStreams;
    private Button freezeButton;
    private int chartScroll;
    private int maxChartScroll;
    private int chartViewportTop;
    private int chartViewportBottom;

    public NeuroAnalysisScreen() {
        super(Component.translatable("screen.neurolab.analysis"));
    }

    @Override
    protected void init() {
        int panelW = Math.max(0, Math.min(1040, width - 48));
        int panelX = (width - panelW) / 2;
        int panelY = popupTop();
        freezeButton = addRenderableWidget(Button.builder(Component.literal(frozenStreams == null ? "FREEZE" : "RESUME"),
                        b -> toggleFreeze()).bounds(panelX + panelW - 190, panelY + 8, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.neurolab.guide.short"), b ->
                        Minecraft.getInstance().setScreen(new NeuroGuideScreen(this)))
                .bounds(panelX + panelW - 276, panelY + 8, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b ->
                        Minecraft.getInstance().setScreen(new NeuroSettingsScreen(this)))
                .bounds(panelX + panelW - 102, panelY + 8, 84, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x78070B10);
        int panelW = Math.max(0, Math.min(1040, width - 48));
        int panelX = (width - panelW) / 2;
        int panelY = popupTop();
        int panelBottom = panelY + popupHeight();
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelBottom + 1, 0xFF486174);
        g.fill(panelX, panelY, panelX + panelW, panelBottom, PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF49BCE4);

        int left = panelX + 18;
        int contentW = panelW - 36;
        g.drawString(font, "NEUROLAB", left, panelY + 12, 0xFFB9ECFF, true);
        g.drawString(font, font.plainSubstrByWidth(
                (frozenStreams == null ? "LIVE" : "FROZEN · display only") + " · server simulation continues",
                Math.max(0, contentW)), left, panelY + 29, 0xFFE0EAF2, true);

        List<TelemetryHistory.SeriesView> streams = streams().stream()
                .filter(s -> !s.samples().isEmpty())
                .sorted(Comparator.comparingInt(TelemetryHistory.SeriesView::entityId)).toList();
        if (streams.isEmpty()) {
            g.fill(left, panelY + 58, left + contentW, panelBottom - 39, PANEL_INNER);
            g.drawCenteredString(font, "No brain telemetry received", width / 2, height / 2 - 12, 0xFFFFD48A);
            g.drawCenteredString(font, "Neuro Fly attaches on spawn; use /neurolab attach for another mob.",
                    width / 2, height / 2 + 4, 0xFFD4E0E9);
            drawFooter(g, left, panelBottom - 23, contentW, "Esc  close    ·    Ctrl + N  reopen");
            renderWidgets(g, mouseX, mouseY, partialTick);
            return;
        }

        int selectedIndex = indexOf(streams, selectedEntityId);
        if (selectedIndex < 0) selectedIndex = 0;
        TelemetryHistory.SeriesView selected = streams.get(selectedIndex);
        selectedEntityId = selected.entityId();
        BrainTelemetryPayload latest = selected.samples().getLast();

        drawSignalFlow(g, left, panelY + 50, contentW, latest);

        int mobY = panelY + 125;
        String mobName = "Mob #" + selected.entityId();
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(selected.entityId()) != null) {
            mobName = level.getEntity(selected.entityId()).getName().getString() + "  (#" + selected.entityId() + ")";
        }
        String status = String.format(Locale.ROOT,
                "%s     sample %d/%d     spikes %d     active neurons %d     forward %.0f%%     turn %+.2f     lift %.0f%%     real-time %.2fx%s",
                mobName, selected.samples().size(), TelemetryHistory.LIMIT, latest.spikes(), latest.active(),
                latest.forward() * 100, latest.turn(), latest.lift() * 100, latest.realTimeFactor(),
                latest.escape() ? "     ESCAPE" : "");
        g.fill(left, mobY, left + contentW, mobY + 22, PANEL_INNER);
        g.drawString(font, font.plainSubstrByWidth(status, contentW - 16), left + 8, mobY + 7,
                latest.escape() ? 0xFFFF9F7A : 0xFFF0F6FB, false);

        int chartTop = mobY + 30;
        int chartBottom = panelBottom - 35;
        int gap = 8;
        boolean showSensory = DashboardPreferences.showLeftPanel();
        boolean showMotor = DashboardPreferences.showRightPanel();
        int sideW = contentW >= 760 ? 150 : contentW >= 460 ? 90 : 0;
        boolean leftPanel = sideW > 0 && showSensory;
        boolean rightPanel = sideW > 0 && showMotor;
        int centerX = left + (leftPanel ? sideW + gap : 0);
        int centerW = contentW - (leftPanel ? sideW + gap : 0) - (rightPanel ? sideW + gap : 0);
        List<Metric> metrics = java.util.Arrays.stream(Metric.values())
                .filter(metric -> DashboardPreferences.showChart(metric.ordinal())).toList();

        if (leftPanel) {
            drawSidePanel(g, left, chartTop, sideW, chartBottom - chartTop, "SENSORY INPUT", new String[][]{
                    {"EYE · CENTER", percent(latest.light())},
                    {"EYE · LEFT", percent(latest.leftEye())},
                    {"EYE · RIGHT", percent(latest.rightEye())},
                    {"LOOMING", percent(latest.looming())},
                    {"TOUCH", percent(latest.touch())},
                    {"ODOR", percent(latest.odor())},
                    {"TASTE", percent(latest.taste())},
                    {"PAIN", percent(latest.pain())},
                    {"REWARD", percent(latest.reward())},
                    {"SOURCE", latest.testStimulus() ? "TEST PULSE" : "WORLD"}
            }, 0xFF79D6F2);
        }
        if (rightPanel) {
            drawSidePanel(g, left + contentW - sideW, chartTop, sideW, chartBottom - chartTop,
                    "MOTOR OUTPUT", new String[][]{
                            {"NEURAL FWD", percent(latest.forward())},
                            {"NEURAL TURN", String.format(Locale.ROOT, "%+.2f", latest.turn())},
                            {"NEURAL LIFT", percent(latest.lift())},
                            {"BODY FWD", percent(latest.bodyForward())},
                            {"BODY TURN", String.format(Locale.ROOT, "%+.2f", latest.bodyTurn())},
                            {"BODY LIFT", percent(latest.bodyLift())},
                            {"MODE", latest.mode().name()},
                            {"CONTROL", controlLabel(latest)},
                            {"NEURAL ESCAPE", latest.escape() ? "ACTIVE" : "idle"},
                            {"BODY ESCAPE", latest.bodyEscape() ? "ACTIVE" : "idle"},
                            {"SPIKES", Integer.toString(latest.spikes())},
                            {"ACTIVE CELLS", Integer.toString(latest.active())},
                            {"REAL-TIME", String.format(Locale.ROOT, "%.2fx", latest.realTimeFactor())}
                    }, latest.escape() ? 0xFFFF9F7A : 0xFFFFC16D);
        }
        chartViewportTop = chartTop;
        chartViewportBottom = chartBottom;
        maxChartScroll = 0;
        if (metrics.isEmpty()) {
            g.drawCenteredString(font, "Enable at least one plot in SETTINGS", centerX + centerW / 2,
                    chartTop + 12, 0xFFFFD48A);
        } else {
            int cols = centerW >= 400 ? 2 : 1;
            int rows = (metrics.size() + cols - 1) / cols;
            int chartW = Math.max(1, (centerW - gap * (cols - 1)) / cols);
            int chartH = Math.max(64, (chartBottom - chartTop - gap * (rows - 1)) / rows);
            maxChartScroll = Math.max(0, rows * (chartH + gap) - gap - (chartBottom - chartTop));
            chartScroll = Math.min(chartScroll, maxChartScroll);
            g.enableScissor(centerX, chartTop, centerX + centerW, Math.max(chartTop, chartBottom));
            for (int i = 0; i < metrics.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int chartX = centerX + col * (chartW + gap);
                int chartY = chartTop + row * (chartH + gap) - chartScroll;
                drawChart(g, chartX, chartY, chartW, chartH, selected.samples(), metrics.get(i));
            }
            g.disableScissor();
        }

        String nav = streams.size() > 1
                ? "← / → select mob  ·  " + (selectedIndex + 1) + " of " + streams.size() + " tracked"
                : "1 tracked mob";
        drawFooter(g, left, panelBottom - 23, contentW, nav + " · Space freeze/resume · Wheel scroll · Esc close");
        renderWidgets(g, mouseX, mouseY, partialTick);
    }

    private int popupHeight() { return Math.max(1, Math.min(700, height - 32)); }
    private int popupTop() { return Math.max(0, (height - popupHeight()) / 2); }

    private void renderWidgets(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (net.minecraft.client.gui.components.Renderable renderable : renderables)
            renderable.render(g, mouseX, mouseY, partialTick);
    }

    private List<TelemetryHistory.SeriesView> streams() {
        return frozenStreams == null ? TelemetryHistory.snapshot() : frozenStreams;
    }

    private void toggleFreeze() {
        frozenStreams = frozenStreams == null ? List.copyOf(TelemetryHistory.snapshot()) : null;
        freezeButton.setMessage(Component.literal(frozenStreams == null ? "FREEZE" : "RESUME"));
    }

    private static String controlLabel(BrainTelemetryPayload sample) {
        return !sample.mode().controlsBody() ? "OBSERVATION ONLY"
                : sample.reflex() ? "REFLEX LAYER" : "NEURAL OUTPUT";
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= chartViewportTop && y < chartViewportBottom && maxChartScroll > 0) {
            chartScroll = Math.max(0, Math.min(maxChartScroll, chartScroll - (int) (vertical * 36)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void drawSignalFlow(GuiGraphics g, int x, int y, int w, BrainTelemetryPayload sample) {
        int h = 67;
        g.fill(x, y, x + w, y + h, PANEL_INNER);
        g.drawString(font, "CONTROL PATH  /  GAME INPUT → CONNECTOME → MOTOR DECODER", x + 8, y + 5,
                0xFF91DDF8, false);

        int gap = 8;
        int cardW = Math.max(1, (w - gap * 2 - 16) / 3);
        int cardY = y + 21;
        int sensorX = x + 8;
        int brainX = sensorX + cardW + gap;
        int motorX = brainX + cardW + gap;
        flowCard(g, sensorX, cardY, cardW, 38, "WORLD SENSES",
                String.format(Locale.ROOT, "pain %.0f%% · reward %.0f%%", sample.pain() * 100, sample.reward() * 100),
                0xFF65B8D5);
        flowCard(g, brainX, cardY, cardW, 38, "FLY CONNECTOME",
                sample.active() + " active · " + sample.spikes() + " spikes", 0xFFAF8BE8);
        flowCard(g, motorX, cardY, cardW, 38, sample.mode().name(),
                !sample.mode().controlsBody() ? "observation only; AI restored" : sample.reflex() ? "reflex fallback active" : String.format(Locale.ROOT,
                        "fwd %.0f%% · turn %+.2f", sample.forward() * 100, sample.turn()),
                0xFFFFB968);
        int mid = cardY + 19;
        g.fill(sensorX + cardW, mid, brainX, mid + 2, 0xFF65B8D5);
        g.fill(brainX + cardW, mid, motorX, mid + 2, 0xFFFFB968);
    }

    private void flowCard(GuiGraphics g, int x, int y, int w, int h, String title, String subtitle, int accent) {
        g.fill(x, y, x + w, y + h, 0xFF202F3D);
        g.fill(x, y, x + 2, y + h, accent);
        g.drawString(font, font.plainSubstrByWidth(title, Math.max(0, w - 12)), x + 7, y + 4, accent, true);
        g.drawString(font, font.plainSubstrByWidth(subtitle, Math.max(0, w - 12)), x + 7, y + 18,
                0xFFF0F5F9, true);
    }

    private void drawSidePanel(GuiGraphics g, int x, int y, int w, int h, String title,
                               String[][] rows, int accent) {
        g.fill(x, y, x + w, y + h, PANEL_INNER);
        g.fill(x, y, x + 2, y + h, accent);
        g.drawString(font, title, x + 8, y + 8, accent, true);
        g.fill(x + 8, y + 23, x + w - 8, y + 24, 0xFF486174);
        int rowY = y + 34;
        for (String[] row : rows) {
            if (rowY + 20 >= y + h) break;
            g.drawString(font, row[0], x + 8, rowY, 0xFFAFBFCC, true);
            String value = font.plainSubstrByWidth(row[1], w - 16);
            g.drawString(font, value, x + 8, rowY + 10, 0xFFF2F7FA, true);
            rowY += 35;
        }
    }

    private static String percent(float value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private void drawChart(GuiGraphics g, int x, int y, int w, int h,
                           List<BrainTelemetryPayload> samples, Metric metric) {
        g.fill(x, y, x + w, y + h, PANEL_INNER);
        g.drawString(font, metric.label, x + 7, y + 4, metric.color, true);
        BrainTelemetryPayload latest = samples.getLast();
        String value = metric.format(metric.value(latest));
        g.drawString(font, value, x + w - font.width(value) - 7, y + 4, 0xFFFFFFFF, true);

        int plotLeft = x + 7;
        int plotRight = x + w - 7;
        int plotTop = y + 19;
        int plotBottom = y + h - 13;
        int plotW = plotRight - plotLeft;
        int plotH = plotBottom - plotTop;
        if (plotW <= 0 || plotH <= 1) return;

        float scale = metric.fixedRange;
        int visible = Math.min(samples.size(), plotW);
        int first = samples.size() - visible;
        for (int i = first; i < samples.size(); i++) {
            float v = metric.value(samples.get(i));
            scale = Math.max(scale, metric.signed ? Math.abs(v) : v);
        }
        if (scale <= 0f) scale = 1f;

        for (int grid = 0; grid < 3; grid++) {
            int gy = plotTop + grid * plotH / 2;
            g.fill(plotLeft, gy, plotRight, gy + 1, 0x994C6072);
        }
        int baseline = metric.signed ? plotTop + plotH / 2 : plotBottom;
        if (metric.signed) g.fill(plotLeft, baseline, plotRight, baseline + 1, 0xFF8DA1B0);

        for (int i = 0; i < visible; i++) {
            float v = metric.value(samples.get(first + i));
            if (metric.signed && v == 0f) continue;
            float normalized = Math.min(1f, Math.abs(v) / scale);
            int sampleX = plotLeft + i;
            int endY;
            if (metric.signed) {
                int amplitude = Math.max(1, Math.round((plotH / 2f - 1) * normalized));
                endY = v >= 0 ? baseline - amplitude : baseline + amplitude;
                int top = Math.min(baseline, endY);
                int bottom = Math.max(baseline + 1, endY + 1);
                g.fill(sampleX, top, sampleX + 1, bottom, metric.color);
            } else {
                int amplitude = Math.max(v == 0 ? 0 : 1, Math.round((plotH - 1) * normalized));
                endY = plotBottom - amplitude;
                g.fill(sampleX, endY, sampleX + 1, plotBottom, metric.color);
            }
        }
        g.drawString(font, "n=" + samples.size(), plotLeft, y + h - 10, 0xFFC4D0DA, true);
        g.drawString(font, "newest →", plotRight - font.width("newest →"), y + h - 10, 0xFFC4D0DA, true);
    }

    private void drawFooter(GuiGraphics g, int x, int y, int w, String text) {
        g.drawString(font, font.plainSubstrByWidth(text, w), x + 2, y, 0xFFD6E0E8, true);
    }

    private static int indexOf(List<TelemetryHistory.SeriesView> streams, int entityId) {
        for (int i = 0; i < streams.size(); i++) {
            if (streams.get(i).entityId() == entityId) return i;
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) { toggleFreeze(); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            List<TelemetryHistory.SeriesView> streams = streams().stream()
                    .filter(s -> !s.samples().isEmpty())
                    .sorted(Comparator.comparingInt(TelemetryHistory.SeriesView::entityId)).toList();
            if (streams.size() > 1) {
                int current = Math.max(0, indexOf(streams, selectedEntityId));
                int direction = keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
                selectedEntityId = streams.get(Math.floorMod(current + direction, streams.size())).entityId();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Metric {
        SPIKES("SPIKES / SAMPLE", 0xFF43C6EF, 0, false),
        ACTIVE("ACTIVE NEURONS", 0xFFB78DF2, 0, false),
        FORWARD("FORWARD DRIVE", 0xFF72D69A, 1, false),
        TURN("TURN BIAS", 0xFFFFB75E, 1, true),
        LIFT("LIFT DRIVE", 0xFF58C9F3, 1, false),
        REALTIME("REAL-TIME FACTOR", 0xFFEE8497, 1, false),
        LIGHT("CENTER EYE LIGHT", 0xFF78D6F2, 1, false),
        LEFT_EYE("LEFT EYE LIGHT", 0xFF63B8E8, 1, false),
        RIGHT_EYE("RIGHT EYE LIGHT", 0xFF9C9AF2, 1, false),
        LOOMING("LOOMING", 0xFFFF9F7A, 1, false),
        TOUCH("TOUCH", 0xFFFFC16D, 1, false),
        ODOR("ODOR", 0xFFBAE07A, 1, false),
        TASTE("TASTE", 0xFFE79BD2, 1, false),
        BODY_FORWARD("BODY FORWARD", 0xFF72D69A, 1, false),
        BODY_TURN("BODY TURN", 0xFFFFB75E, 1, true),
        BODY_LIFT("BODY LIFT", 0xFF58C9F3, 1, false);

        private final String label;
        private final int color;
        private final float fixedRange;
        private final boolean signed;

        Metric(String label, int color, float fixedRange, boolean signed) {
            this.label = label;
            this.color = color;
            this.fixedRange = fixedRange;
            this.signed = signed;
        }

        private float value(BrainTelemetryPayload p) {
            return switch (this) {
                case SPIKES -> p.spikes();
                case ACTIVE -> p.active();
                case FORWARD -> p.forward();
                case TURN -> p.turn();
                case LIFT -> p.lift();
                case REALTIME -> p.realTimeFactor();
                case LIGHT -> p.light();
                case LEFT_EYE -> p.leftEye();
                case RIGHT_EYE -> p.rightEye();
                case LOOMING -> p.looming();
                case TOUCH -> p.touch();
                case ODOR -> p.odor();
                case TASTE -> p.taste();
                case BODY_FORWARD -> p.bodyForward();
                case BODY_TURN -> p.bodyTurn();
                case BODY_LIFT -> p.bodyLift();
            };
        }

        private String format(float v) {
            return switch (this) {
                case SPIKES, ACTIVE -> String.format(Locale.ROOT, "%.0f", v);
                case FORWARD, LIFT, LIGHT, LEFT_EYE, RIGHT_EYE, LOOMING, TOUCH, ODOR, TASTE,
                     BODY_FORWARD, BODY_LIFT ->
                        String.format(Locale.ROOT, "%.0f%%", v * 100);
                case TURN, BODY_TURN -> String.format(Locale.ROOT, "%+.2f", v);
                case REALTIME -> String.format(Locale.ROOT, "%.2fx", v);
            };
        }
    }
}

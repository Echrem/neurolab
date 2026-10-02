package lab.neurolab.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local, persistent controls for dashboard panels and visible plots. */
public final class NeuroSettingsScreen extends Screen {
    private final Screen parent;
    private int chartPage;
    private Button previousPage;
    private Button nextPage;

    public NeuroSettingsScreen(Screen parent) {
        super(Component.literal("NeuroLab Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boolean compact = height < 360;
        int panelW = Math.min(720, Math.max(0, width - 32));
        int x = (width - panelW) / 2;
        int buttonW = (panelW - 12) / 2;
        int top = compact ? 62 : Math.max(82, height / 2 - 116);
        addRenderableWidget(toggle("Left sensory panel", DashboardPreferences.showLeftPanel(),
                x, top, buttonW, DashboardPreferences::toggleLeftPanel));
        addRenderableWidget(toggle("Right motor panel", DashboardPreferences.showRightPanel(),
                x + buttonW + 12, top, buttonW, DashboardPreferences::toggleRightPanel));

        int chartTop = top + (compact ? 28 : 36);
        int rowCount = compact ? 4 : 8;
        int visibleCount = compact ? 8 : DashboardPreferences.CHART_NAMES.length;
        int rowH = compact ? 20 : Math.min(22, Math.max(18, (height - chartTop - 66) / rowCount));
        int rowGap = compact ? 3 : 4;
        for (int i = 0; i < visibleCount; i++) {
            int chartIndex = compact ? chartPage * visibleCount + i : i;
            int col = compact ? i % 2 : i / 8;
            int row = compact ? i / 2 : i % 8;
            int bx = x + col * (buttonW + 12);
            int by = chartTop + row * (rowH + rowGap);
            String name = DashboardPreferences.CHART_NAMES[chartIndex];
            addRenderableWidget(toggle(name, DashboardPreferences.showChart(chartIndex), bx, by,
                    buttonW, () -> DashboardPreferences.toggleChart(chartIndex)));
        }
        if (compact) {
            int footerY = height - 28;
            previousPage = addRenderableWidget(Button.builder(Component.literal("‹ PLOTS"), b -> changePage(-1))
                    .bounds(width / 2 - 112, footerY, 70, 20).build());
            nextPage = addRenderableWidget(Button.builder(Component.literal("PLOTS ›"), b -> changePage(1))
                    .bounds(width / 2 + 42, footerY, 70, 20).build());
            updatePageButtons();
        }
        addRenderableWidget(Button.builder(Component.literal("DONE"), b -> onClose())
                .bounds(width / 2 - 35, height - 28, 70, 20).build());
    }

    private void changePage(int delta) {
        chartPage = Math.floorMod(chartPage + delta, 2);
        rebuildWidgets();
    }

    private void updatePageButtons() {
        if (previousPage != null) previousPage.setMessage(Component.literal("‹ " + (chartPage == 0 ? "1/2" : "2/2")));
        if (nextPage != null) nextPage.setMessage(Component.literal((chartPage == 0 ? "1/2" : "2/2") + " ›"));
    }

    private Button toggle(String name, boolean enabled, int x, int y, int w, Runnable action) {
        Button button = Button.builder(label(name, enabled), b -> {
            action.run();
            boolean nowEnabled = name.equals("Left sensory panel") ? DashboardPreferences.showLeftPanel()
                    : name.equals("Right motor panel") ? DashboardPreferences.showRightPanel()
                    : DashboardPreferences.showChart(java.util.Arrays.asList(DashboardPreferences.CHART_NAMES).indexOf(name));
            b.setMessage(label(name, nowEnabled));
        }).bounds(x, y, w, 20).build();
        return button;
    }

    private static Component label(String name, boolean enabled) {
        return Component.literal((enabled ? "ON  ·  " : "OFF ·  ") + name);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF070B10);
        int panelW = Math.min(720, Math.max(0, width - 32));
        int x = (width - panelW) / 2;
        int y = 22;
        g.fill(x - 1, y - 1, x + panelW + 1, height - 22, 0xFF486174);
        g.fill(x, y, x + panelW, height - 23, 0xFF0B1118);
        g.fill(x, y, x + panelW, y + 2, 0xFF49BCE4);
        g.drawCenteredString(font, "NEUROLAB  /  DASHBOARD SETTINGS", width / 2, 38, 0xFFB9ECFF);
        g.drawCenteredString(font, "These preferences change the local analysis view, not the server simulation.",
                width / 2, 56, 0xFFD6E0E8);
        for (net.minecraft.client.gui.components.Renderable renderable : renderables)
            renderable.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

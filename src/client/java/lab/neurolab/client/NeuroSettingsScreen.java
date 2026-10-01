package lab.neurolab.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local, persistent controls for dashboard panels and visible plots. */
public final class NeuroSettingsScreen extends Screen {
    private final Screen parent;

    public NeuroSettingsScreen(Screen parent) {
        super(Component.literal("NeuroLab Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelW = Math.min(720, width - 32);
        int x = (width - panelW) / 2;
        int buttonW = (panelW - 12) / 2;
        int top = Math.max(82, height / 2 - 116);
        addRenderableWidget(toggle("Left sensory panel", DashboardPreferences.showLeftPanel(),
                x, top, buttonW, DashboardPreferences::toggleLeftPanel));
        addRenderableWidget(toggle("Right motor panel", DashboardPreferences.showRightPanel(),
                x + buttonW + 12, top, buttonW, DashboardPreferences::toggleRightPanel));

        int rowH = Math.min(23, Math.max(20, (height - top - 72) / 8));
        int chartTop = top + 36;
        for (int i = 0; i < DashboardPreferences.CHART_NAMES.length; i++) {
            final int chartIndex = i;
            int col = i / 8;
            int row = i % 8;
            int bx = x + col * (buttonW + 12);
            int by = chartTop + row * (rowH + 4);
            String name = DashboardPreferences.CHART_NAMES[i];
            addRenderableWidget(toggle(name, DashboardPreferences.showChart(i), bx, by, buttonW,
                    () -> DashboardPreferences.toggleChart(chartIndex)));
        }
        addRenderableWidget(Button.builder(Component.literal("DONE"), b -> onClose())
                .bounds(width / 2 - 55, height - 34, 110, 20).build());
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
        g.fill(0, 0, width, height, 0xE9081118);
        int panelW = Math.min(720, width - 32);
        int x = (width - panelW) / 2;
        int y = 22;
        g.fill(x - 1, y - 1, x + panelW + 1, height - 22, 0xFF486174);
        g.fill(x, y, x + panelW, height - 23, 0xFF0B1118);
        g.fill(x, y, x + panelW, y + 2, 0xFF49BCE4);
        g.drawCenteredString(font, "NEUROLAB  /  DASHBOARD SETTINGS", width / 2, 38, 0xFFB9ECFF);
        g.drawCenteredString(font, "These preferences change the local analysis view, not the server simulation.",
                width / 2, 56, 0xFFD6E0E8);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

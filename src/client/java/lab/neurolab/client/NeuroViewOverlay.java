package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Compact telemetry HUD shown while the camera follows an attached mob. */
final class NeuroViewOverlay {
    private NeuroViewOverlay() {}

    static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DashboardPreferences.showViewOverlay() || minecraft.player == null
                || minecraft.getCameraEntity() == null || minecraft.getCameraEntity() == minecraft.player) return;

        var camera = minecraft.getCameraEntity();
        BrainTelemetryPayload sample = TelemetryHistory.latest(camera.getId());
        int width = 184;
        int x = graphics.guiWidth() - width - 12;
        int y = 12;
        graphics.fill(x - 1, y - 1, x + width + 1, y + 91, 0xC84B7D91);
        graphics.fill(x, y, x + width, y + 90, 0xC80A121B);
        graphics.fill(x, y, x + 2, y + 90, 0xFF49BCE4);
        graphics.drawString(minecraft.font, "NEURO VIEW · " + camera.getName().getString(),
                x + 9, y + 8, 0xFFB9ECFF, true);
        if (sample == null) {
            graphics.drawString(minecraft.font, "Waiting for live brain telemetry…", x + 9, y + 29,
                    0xFFD4E0E9, false);
            graphics.drawString(minecraft.font, "V  return to your own view", x + 9, y + 70,
                    0xFFAFBFCC, false);
            return;
        }
        graphics.drawString(minecraft.font, "SPIKES " + sample.spikes() + "   ACTIVE " + sample.active(),
                x + 9, y + 27, 0xFFE6EDF3, false);
        graphics.drawString(minecraft.font, "SYNAPSE FLOW " + compact(sample.synapticEvents()) + " / 50 ms",
                x + 9, y + 42, 0xFF75F0D1, false);
        graphics.drawString(minecraft.font, String.format(java.util.Locale.ROOT,
                        "PAIN %.0f%%   REWARD %.0f%%", sample.pain() * 100, sample.reward() * 100),
                x + 9, y + 57, 0xFFFFBE84, false);
        graphics.drawString(minecraft.font, String.format(java.util.Locale.ROOT,
                        "FWD %.0f%%   TURN %+.2f   LIFT %.0f%%", sample.bodyForward() * 100,
                        sample.bodyTurn(), sample.bodyLift() * 100),
                x + 9, y + 72, 0xFFD4E0E9, false);
    }

    private static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        if (value < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }
}

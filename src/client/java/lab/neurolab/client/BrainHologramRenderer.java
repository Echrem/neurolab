package lab.neurolab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import net.minecraftforge.client.event.RenderLivingEvent;

/** Renders a compact, camera-facing neural activity hologram above mobs with a live brain feed. */
final class BrainHologramRenderer {
    private static final double MAX_DISTANCE_SQR = 32.0 * 32.0;
    private BrainHologramRenderer() {}

    static void render(RenderLivingEvent.Post<?, ?> event) {
        var entity = event.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || minecraft.player.distanceToSqr(entity) > MAX_DISTANCE_SQR) return;
        BrainTelemetryPayload sample = TelemetryHistory.latest(entity.getId());
        if (sample == null) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, entity.getBbHeight() + 0.42, 0.0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);

        Font font = minecraft.font;
        var buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        drawCentered(font, buffers, pose, " .-o-o-. ", -20, 0x75F6FF, light);
        drawCentered(font, buffers, pose, "(o-*+-o)", -10, 0xA5C8FF, light);
        drawCentered(font, buffers, pose, " `-o-o-' ", 0, 0x75F6FF, light);

        long events = Math.max(0, sample.synapticEvents());
        String flow = flowFrame(events);
        drawCentered(font, buffers, pose, flow, 11, 0x65F3C4, light);
        drawCentered(font, buffers, pose, "SYN " + compact(events) + " / 50 ms", 21, 0xD7F7FF, light);
        pose.popPose();
    }

    private static void drawCentered(Font font, net.minecraft.client.renderer.MultiBufferSource buffers,
                                     PoseStack pose, String text, int y, int color, int light) {
        font.drawInBatch(Component.literal(text), -font.width(text) / 2.0f, y, color, false,
                pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x50001828, light);
    }

    private static String flowFrame(long events) {
        String[] frames = {"o..>..*..>..o", "o.>. .*.>. .o", "o...>*..>..o", "o.. .*.>. .o"};
        int phase = (int) ((System.nanoTime() / 140_000_000L + events) & 3);
        return frames[phase];
    }

    private static String compact(long count) {
        if (count < 1_000) return Long.toString(count);
        if (count < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", count / 1_000.0);
        return String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000.0);
    }
}

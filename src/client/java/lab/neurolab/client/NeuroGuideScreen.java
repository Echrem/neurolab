package lab.neurolab.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Scrollable in-game Q&A for controls and experimental behavior. */
public final class NeuroGuideScreen extends Screen {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("screen.neurolab.guide.q1", "screen.neurolab.guide.a1"),
            new Entry("screen.neurolab.guide.q2", "screen.neurolab.guide.a2"),
            new Entry("screen.neurolab.guide.q3", "screen.neurolab.guide.a3"),
            new Entry("screen.neurolab.guide.q4", "screen.neurolab.guide.a4"),
            new Entry("screen.neurolab.guide.q5", "screen.neurolab.guide.a5"),
            new Entry("screen.neurolab.guide.q6", "screen.neurolab.guide.a6"),
            new Entry("screen.neurolab.guide.q7", "screen.neurolab.guide.a7"),
            new Entry("screen.neurolab.guide.q8", "screen.neurolab.guide.a8"),
            new Entry("screen.neurolab.guide.q9", "screen.neurolab.guide.a9"),
            new Entry("screen.neurolab.guide.q10", "screen.neurolab.guide.a10"),
            new Entry("screen.neurolab.guide.q11", "screen.neurolab.guide.a11")
    );

    private final Screen parent;
    private int scroll;
    private int maxScroll;

    public NeuroGuideScreen(Screen parent) {
        super(Component.translatable("screen.neurolab.guide.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("screen.neurolab.guide.close"), b -> onClose())
                .bounds(width / 2 - 52, height - 34, 104, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x78070B10);
        int panelWidth = Math.min(840, width - 36);
        int panelHeight = Math.min(640, height - 24);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF486174);
        graphics.fill(left, top, right, bottom, 0xE10B1118);
        graphics.fill(left, top, right, top + 2, 0xFF49BCE4);
        graphics.drawString(font, Component.translatable("screen.neurolab.guide.title"),
                left + 18, top + 12, 0xFFB9ECFF, true);
        graphics.drawString(font, Component.translatable("screen.neurolab.guide.subtitle"),
                left + 18, top + 29, 0xFFD4E0E9, false);

        int contentTop = top + 52;
        int contentBottom = bottom - 45;
        int contentWidth = panelWidth - 36;
        maxScroll = Math.max(0, totalContentHeight(contentWidth) - (contentBottom - contentTop));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        graphics.enableScissor(left + 14, contentTop, right - 14, contentBottom);
        int y = contentTop - scroll;
        for (Entry entry : ENTRIES) {
            var lines = font.split(Component.translatable(entry.answer), contentWidth - 24);
            int cardHeight = 25 + lines.size() * 10;
            graphics.fill(left + 14, y, right - 14, y + cardHeight - 3, 0xB916232F);
            graphics.drawString(font, Component.translatable(entry.question), left + 25, y + 7,
                    0xFF75D9F5, true);
            int answerY = y + 21;
            for (var line : lines) {
                graphics.drawString(font, line, left + 25, answerY, 0xFFE0EAF2, false);
                answerY += 10;
            }
            y += cardHeight;
        }
        graphics.disableScissor();
        graphics.fill(left + 14, bottom - 40, right - 14, bottom - 39, 0x665B7384);
        graphics.drawCenteredString(font, Component.translatable("screen.neurolab.guide.scroll"),
                width / 2, bottom - 29, 0xFFAFBFCC);
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
    }

    private int totalContentHeight(int contentWidth) {
        int height = 0;
        for (Entry entry : ENTRIES) {
            int lines = font.split(Component.translatable(entry.answer), contentWidth - 24).size();
            height += 25 + lines * 10;
        }
        return height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY) * 28));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record Entry(String question, String answer) {}
}

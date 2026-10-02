package lab.neurolab.client;

import com.mojang.blaze3d.platform.InputConstants;
import lab.neurolab.client.render.NeuroFlyModel;
import lab.neurolab.client.render.NeuroFlyRenderer;
import lab.neurolab.minecraft.NeuroLabEntities;
import lab.neurolab.minecraft.TelemetryNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "neurolab", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NeuroLabClient {
    private static final KeyMapping DASHBOARD = new KeyMapping("key.neurolab.analysis",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.neurolab");
    private static final KeyMapping VIEW_TARGET = new KeyMapping("key.neurolab.view",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.neurolab");
    private static final KeyMapping TOGGLE_VIEW_HUD = new KeyMapping("key.neurolab.view_hud",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.neurolab");
    private static final KeyMapping GUIDE = new KeyMapping("key.neurolab.guide",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.neurolab");
    private static boolean dashboardChordDown;

    private NeuroLabClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DASHBOARD);
        event.register(VIEW_TARGET);
        event.register(TOGGLE_VIEW_HUD);
        event.register(GUIDE);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NeuroFlyModel.LAYER, NeuroFlyModel::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NeuroLabEntities.NEURO_FLY.get(), NeuroFlyRenderer::new);
    }

    @SubscribeEvent
    public static void registerViewOverlay(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.VANILLA_ROOT,
                ResourceLocation.fromNamespaceAndPath("neurolab", "neuro_view"), ForgeLayeredDraw.HOTBAR,
                (graphics, deltaTracker) -> NeuroViewOverlay.render(graphics));
    }

    @Mod.EventBusSubscriber(modid = "neurolab", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.getWindow() == null) return;
            long window = client.getWindow().getWindow();
            boolean control = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            boolean chordDown = control && DASHBOARD.isDown();
            if (chordDown && !dashboardChordDown && client.screen == null) {
                client.setScreen(new NeuroAnalysisScreen());
            }
            dashboardChordDown = chordDown;
            while (GUIDE.consumeClick()) {
                if (client.screen == null) client.setScreen(new NeuroGuideScreen(null));
            }
            while (VIEW_TARGET.consumeClick()) {
                if (client.screen != null || client.player == null) continue;
                if (client.getCameraEntity() != null && client.getCameraEntity() != client.player) {
                    TelemetryNetwork.requestView(-1);
                } else if (client.hitResult instanceof EntityHitResult hit) {
                    TelemetryNetwork.requestView(hit.getEntity().getId());
                } else {
                    client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Aim at a brain-attached mob and press V to view it."), true);
                }
            }
            while (TOGGLE_VIEW_HUD.consumeClick()) {
                DashboardPreferences.toggleViewOverlay();
                if (client.player != null) client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Neuro View panel "
                                + (DashboardPreferences.showViewOverlay() ? "enabled" : "hidden") + "."), true);
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            TelemetryHistory.clear();
        }

        @SubscribeEvent
        public static void renderBrainHologram(RenderLivingEvent.Post<?, ?> event) {
            BrainHologramRenderer.render(event);
        }

    }
}

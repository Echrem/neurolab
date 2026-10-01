package lab.neurolab.client;

import com.mojang.blaze3d.platform.InputConstants;
import lab.neurolab.client.render.NeuroFlyModel;
import lab.neurolab.client.render.NeuroFlyRenderer;
import lab.neurolab.minecraft.NeuroLabEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "neurolab", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NeuroLabClient {
    private static final KeyMapping DASHBOARD = new KeyMapping("key.neurolab.analysis",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, KeyMapping.Category.register(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("neurolab", "main")));
    private static boolean dashboardChordDown;

    private NeuroLabClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DASHBOARD);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NeuroFlyModel.LAYER, NeuroFlyModel::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NeuroLabEntities.NEURO_FLY.get(), NeuroFlyRenderer::new);
    }

    @Mod.EventBusSubscriber(modid = "neurolab", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.getWindow() == null) return;
            var window = client.getWindow();
            boolean control = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            boolean chordDown = control && DASHBOARD.isDown();
            if (chordDown && !dashboardChordDown && client.screen == null) {
                client.setScreen(new NeuroAnalysisScreen());
            }
            dashboardChordDown = chordDown;
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            TelemetryHistory.clear();
        }
    }
}

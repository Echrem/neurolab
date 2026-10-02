package lab.neurolab.client.render;

import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class NeuroFlyRenderer extends MobRenderer<NeuroFlyEntity, NeuroFlyModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("neurolab", "textures/entity/neuro_fly.png");

    public NeuroFlyRenderer(EntityRendererProvider.Context context) {
        super(context, new NeuroFlyModel(context.bakeLayer(NeuroFlyModel.LAYER)), 0.18f);
    }

    @Override public ResourceLocation getTextureLocation(NeuroFlyEntity entity) { return TEXTURE; }
}

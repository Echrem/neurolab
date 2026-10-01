package lab.neurolab.client.render;

import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public final class NeuroFlyRenderer extends MobRenderer<NeuroFlyEntity, LivingEntityRenderState, NeuroFlyModel> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("neurolab", "textures/entity/neuro_fly.png");

    public NeuroFlyRenderer(EntityRendererProvider.Context context) {
        super(context, new NeuroFlyModel(context.bakeLayer(NeuroFlyModel.LAYER)), 0.18f);
    }

    @Override public Identifier getTextureLocation(LivingEntityRenderState state) { return TEXTURE; }
    @Override public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }
}

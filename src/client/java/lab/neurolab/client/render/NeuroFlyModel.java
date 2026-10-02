package lab.neurolab.client.render;

import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;

public final class NeuroFlyModel extends HierarchicalModel<NeuroFlyEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("neurolab", "neuro_fly"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftMiddleLeg;
    private final ModelPart rightMiddleLeg;
    private final ModelPart leftBackLeg;
    private final ModelPart rightBackLeg;

    public NeuroFlyModel(ModelPart root) {
        super(net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        this.root = root;
        head = root.getChild("head");
        leftWing = root.getChild("left_wing");
        rightWing = root.getChild("right_wing");
        leftFrontLeg = root.getChild("left_front_leg");
        rightFrontLeg = root.getChild("right_front_leg");
        leftMiddleLeg = root.getChild("left_middle_leg");
        rightMiddleLeg = root.getChild("right_middle_leg");
        leftBackLeg = root.getChild("left_back_leg");
        rightBackLeg = root.getChild("right_back_leg");
    }

    @Override public ModelPart root() { return root; }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(8, 8)
                .addBox(-2.0f, -1.5f, -1.5f, 4.0f, 3.0f, 4.0f, none), PartPose.ZERO);
        root.addOrReplaceChild("abdomen_base", CubeListBuilder.create().texOffs(8, 22)
                .addBox(-1.7f, -1.2f, 1.0f, 3.4f, 2.4f, 3.0f, none), PartPose.ZERO);
        root.addOrReplaceChild("abdomen_tip", CubeListBuilder.create().texOffs(19, 22)
                .addBox(-1.2f, -0.8f, 3.5f, 2.4f, 1.6f, 2.0f, none), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(2, 2)
                .addBox(-1.25f, -1.2f, -2.4f, 2.5f, 2.4f, 2.0f, none)
                .texOffs(42, 4).addBox(-2.25f, -1.5f, -2.15f, 1.5f, 1.8f, 1.4f, none)
                .texOffs(52, 4).addBox(0.75f, -1.5f, -2.15f, 1.5f, 1.8f, 1.4f, none)
                .texOffs(34, 35).addBox(-0.45f, 0.6f, -2.8f, 0.35f, 1.2f, 0.35f, none)
                .texOffs(34, 35).addBox(0.1f, 0.6f, -2.8f, 0.35f, 1.2f, 0.35f, none), PartPose.ZERO);

        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(2, 39)
                .addBox(-7.0f, -0.15f, -2.0f, 7.0f, 0.25f, 4.0f, none), PartPose.offset(-1.4f, -1.0f, -0.2f));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(2, 52)
                .addBox(0.0f, -0.15f, -2.0f, 7.0f, 0.25f, 4.0f, none), PartPose.offset(1.4f, -1.0f, -0.2f));

        addLeg(root, "left_front_leg", -1.5f, -0.6f, -1.0f, -0.48f, -0.2f);
        addLeg(root, "right_front_leg", 1.5f, -0.6f, -1.0f, 0.48f, -0.2f);
        addLeg(root, "left_middle_leg", -1.6f, -0.5f, 0.2f, -0.2f, 0.1f);
        addLeg(root, "right_middle_leg", 1.6f, -0.5f, 0.2f, 0.2f, 0.1f);
        addLeg(root, "left_back_leg", -1.4f, -0.4f, 1.4f, -0.42f, 0.35f);
        addLeg(root, "right_back_leg", 1.4f, -0.4f, 1.4f, 0.42f, 0.35f);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addLeg(PartDefinition root, String name, float x, float y, float z, float roll, float pitch) {
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(35, 39)
                .addBox(-0.28f, 0.0f, -0.25f, 0.56f, 3.0f, 0.5f, CubeDeformation.NONE),
                PartPose.offsetAndRotation(x, y, z, pitch, 0.0f, roll));
    }

    @Override public void setupAnim(NeuroFlyEntity entity, float limbSwing, float limbSwingAmount,
                                    float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * ((float) Math.PI / 180.0f) * 0.35f;
        head.xRot = headPitch * ((float) Math.PI / 180.0f) * 0.35f;
        float beat = (float) Math.sin(ageInTicks * 2.2f) * 0.42f;
        leftWing.zRot = -0.18f - beat;
        rightWing.zRot = 0.18f + beat;
        float step = (float) Math.cos(limbSwing * 0.8f) * limbSwingAmount * 0.35f;
        leftFrontLeg.xRot = step;
        rightBackLeg.xRot = step;
        rightFrontLeg.xRot = -step;
        leftBackLeg.xRot = -step;
        leftMiddleLeg.xRot = -step * 0.6f;
        rightMiddleLeg.xRot = step * 0.6f;
    }
}

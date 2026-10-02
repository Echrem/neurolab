package lab.neurolab.minecraft;

import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = NeuroLabMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NeuroLabEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NeuroLabMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NeuroLabMod.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, NeuroLabMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, NeuroLabMod.MOD_ID);

    public static final RegistryObject<EntityType<NeuroFlyEntity>> NEURO_FLY = ENTITY_TYPES.register("neuro_fly",
            () -> EntityType.Builder.of(NeuroFlyEntity::new, MobCategory.AMBIENT)
                .sized(0.7f, 0.45f)
                    .clientTrackingRange(8)
                    .build("neuro_fly"));

    public static final RegistryObject<Item> NEURO_FLY_SPAWN_EGG = ITEMS.register("neuro_fly_spawn_egg",
            () -> new ForgeSpawnEggItem(NEURO_FLY, 0x34343B, 0xC76D32, new Item.Properties()));
    public static final RegistryObject<Item> NEURO_VIEWER = ITEMS.register("neuro_viewer",
            () -> new NeuroViewerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_MIND_IMPRINTER = ITEMS.register("neuro_mind_imprinter",
            () -> new NeuroMindImprinterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Block> NEURO_MONITOR = BLOCKS.register("neuro_monitor",
            () -> new NeuroMonitorBlock(BlockBehaviour.Properties.of().strength(2.5f)
                    .sound(SoundType.COPPER).noOcclusion()));
    public static final RegistryObject<BlockEntityType<NeuroMonitorBlockEntity>> NEURO_MONITOR_ENTITY =
            BLOCK_ENTITY_TYPES.register("neuro_monitor", () -> BlockEntityType.Builder
                    .of(NeuroMonitorBlockEntity::new, NEURO_MONITOR.get()).build(null));
    public static final RegistryObject<Item> NEURO_MONITOR_ITEM = ITEMS.register("neuro_monitor",
            () -> new net.minecraft.world.item.BlockItem(NEURO_MONITOR.get(), new Item.Properties()));

    private NeuroLabEntities() {}

    @SubscribeEvent
    public static void addAttributes(EntityAttributeCreationEvent event) {
        event.put(NEURO_FLY.get(), NeuroFlyEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FLYING_SPEED, 0.48)
                .build());
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(NEURO_FLY_SPAWN_EGG.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_VIEWER.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_MIND_IMPRINTER.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_MONITOR_ITEM.get());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, path);
    }
}

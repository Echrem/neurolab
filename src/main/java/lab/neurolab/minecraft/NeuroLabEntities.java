package lab.neurolab.minecraft;

import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

    public static final RegistryObject<EntityType<NeuroFlyEntity>> NEURO_FLY = ENTITY_TYPES.register("neuro_fly",
            () -> EntityType.Builder.of(NeuroFlyEntity::new, MobCategory.AMBIENT)
                    .sized(0.7f, 0.45f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("neuro_fly"))));

    public static final RegistryObject<Item> NEURO_FLY_SPAWN_EGG = ITEMS.register("neuro_fly_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id("neuro_fly_spawn_egg")))
                    .spawnEgg(NEURO_FLY.get())));

    private NeuroLabEntities() {}

    @SubscribeEvent
    public static void addAttributes(EntityAttributeCreationEvent event) {
        event.put(NEURO_FLY.get(), NeuroFlyEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.16)
                .add(Attributes.FLYING_SPEED, 0.22)
                .build());
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(NEURO_FLY_SPAWN_EGG.get());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeuroLabMod.MOD_ID, path);
    }
}

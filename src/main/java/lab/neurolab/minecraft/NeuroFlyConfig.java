package lab.neurolab.minecraft;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative tuning for the game-world fly embodiment. */
public final class NeuroFlyConfig {
    public enum LightMode { BALANCED, SEEK_LIGHT, SEEK_SHADE, OFF }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<LightMode> LIGHT_MODE;
    public static final ForgeConfigSpec.DoubleValue LIGHT_STEERING;
    public static final ForgeConfigSpec.DoubleValue CRUISE_THROTTLE;
    public static final ForgeConfigSpec.IntValue FLOWER_RADIUS;
    public static final ForgeConfigSpec.IntValue HOST_RADIUS;
    public static final ForgeConfigSpec.IntValue PERCH_TICKS;
    public static final ForgeConfigSpec.IntValue RAIN_SCAN_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue SEEK_SHELTER_IN_RAIN;
    public static final ForgeConfigSpec.BooleanValue PERCH_ON_ANIMALS_AND_VILLAGERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("neuro_fly_environment");
        LIGHT_MODE = builder.comment("Light-gradient response: BALANCED seeks dim light and avoids very bright spots; SEEK_LIGHT and SEEK_SHADE force one direction.")
                .defineEnum("light_mode", LightMode.BALANCED);
        LIGHT_STEERING = builder.comment("Strength of steering from the left/right light difference.")
                .defineInRange("light_steering", 0.55, 0.0, 1.0);
        CRUISE_THROTTLE = builder.comment("Minimum assisted cruise throttle for Neuro Fly; movement remains bounded by its flying-speed attribute.")
                .defineInRange("cruise_throttle", 0.62, 0.15, 1.0);
        FLOWER_RADIUS = builder.comment("Loaded-block search radius for flower odor and nectar-like reward cues.")
                .defineInRange("flower_radius", 8, 3, 16);
        HOST_RADIUS = builder.comment("Search radius for occasional close approaches and perching on animals or villagers.")
                .defineInRange("host_radius", 8, 0, 16);
        PERCH_TICKS = builder.comment("Approximate duration of an animal/villager rest perch in game ticks.")
                .defineInRange("perch_ticks", 120, 20, 600);
        RAIN_SCAN_INTERVAL = builder.comment("Minimum ticks between local shelter searches while exposed to rain.")
                .defineInRange("rain_scan_interval", 40, 10, 200);
        SEEK_SHELTER_IN_RAIN = builder.define("seek_shelter_in_rain", true);
        PERCH_ON_ANIMALS_AND_VILLAGERS = builder.define("perch_on_animals_and_villagers", true);
        builder.pop();
        SPEC = builder.build();
    }

    private NeuroFlyConfig() {}
}

package net.shadowwalker;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ShadowWalkerConfig {
    public static final ForgeConfigSpec COMMON_CONFIG;

    public static final ForgeConfigSpec.BooleanValue ENABLE_STEALTH_SYSTEM;
    public static final ForgeConfigSpec.BooleanValue SHOW_STEALTH_CROSSHAIR;
    public static final ForgeConfigSpec.BooleanValue SHOW_DETECTION_EXCLAMATION_MARKS;
    public static final ForgeConfigSpec.BooleanValue SHOW_SNEAK_ATTACK_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue REMOVE_INVISIBILITY_ON_ATTACK;
    public static final ForgeConfigSpec.DoubleValue MAX_SCAN_RANGE;
    public static final ForgeConfigSpec.DoubleValue CROSSHAIR_DETECTION_RANGE;
    public static final ForgeConfigSpec.DoubleValue DETECTION_EXCLAMATION_MARK_SCALE;
    public static final ForgeConfigSpec.DoubleValue SNEAK_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ARMOR_NOISE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue OBSERVER_MOB_MOVEMENT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MELEE_SNEAK_ATTACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ONE_HANDED_SNEAK_ATTACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue DAGGER_SNEAK_ATTACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RANGED_SNEAK_ATTACK_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Shadow Walker stealth and sneak attack settings").push("stealth");
        ENABLE_STEALTH_SYSTEM = builder.comment("Set to false to completely disable stealth awareness, stealth targeting suppression, and sneak attacks.")
                .define("enableStealthSystem", true);
        SHOW_STEALTH_CROSSHAIR = builder.comment("Render the stealth awareness indicator over the vanilla crosshair while sneaking.")
                .define("showStealthCrosshair", true);
        SHOW_DETECTION_EXCLAMATION_MARKS = builder.comment("Render a small red exclamation mark above mobs that have detected the sneaking player.")
                .define("showDetectionExclamationMarks", true);
        SHOW_SNEAK_ATTACK_MESSAGE = builder.comment("Show a short chat message when a sneak attack multiplier applies.")
                .define("showSneakAttackMessage", true);
        REMOVE_INVISIBILITY_ON_ATTACK = builder.comment("Remove the vanilla invisibility effect from players after they damage another entity.")
                .define("removeInvisibilityOnAttack", true);
        MAX_SCAN_RANGE = builder.comment("Maximum range mobs can use for stealth awareness scans.")
                .defineInRange("maxScanRange", 36.0D, 4.0D, 128.0D);
        CROSSHAIR_DETECTION_RANGE = builder.comment("Maximum range at which detected mobs can change the stealth crosshair. This only affects the visual indicator.")
                .defineInRange("crosshairDetectionRange", 24.0D, 0.0D, 128.0D);
        DETECTION_EXCLAMATION_MARK_SCALE = builder.comment("World-space scale for the red exclamation mark above detected mobs. Smaller values make it smaller.")
                .defineInRange("detectionExclamationMarkScale", 0.014D, 0.004D, 0.05D);
        SNEAK_LEVEL = builder.comment("Global sneak proficiency from 0 to 100. Higher values make players harder to detect without requiring a skill tree.")
                .defineInRange("sneakLevel", 0.0D, 0.0D, 100.0D);
        ARMOR_NOISE_MULTIPLIER = builder.comment("Multiplier applied to armor noise while sneaking.")
                .defineInRange("armorNoiseMultiplier", 1.0D, 0.0D, 10.0D);
        OBSERVER_MOB_MOVEMENT_MULTIPLIER = builder.comment("Movement multiplier for idle hostile mobs while they are observing nearby sneaking players. 0 stops wandering, 1 keeps vanilla movement.")
                .defineInRange("observerMobMovementMultiplier", 0.25D, 0.0D, 1.0D);
        MELEE_SNEAK_ATTACK_MULTIPLIER = builder.comment("Fallback melee sneak attack damage multiplier.")
                .defineInRange("meleeSneakAttackMultiplier", 2.0D, 1.0D, 100.0D);
        ONE_HANDED_SNEAK_ATTACK_MULTIPLIER = builder.comment("Sneak attack multiplier for common one-handed melee weapons such as swords, axes, and tridents.")
                .defineInRange("oneHandedSneakAttackMultiplier", 6.0D, 1.0D, 100.0D);
        DAGGER_SNEAK_ATTACK_MULTIPLIER = builder.comment("Sneak attack multiplier for items whose registry path contains 'dagger'.")
                .defineInRange("daggerSneakAttackMultiplier", 15.0D, 1.0D, 100.0D);
        RANGED_SNEAK_ATTACK_MULTIPLIER = builder.comment("Sneak attack multiplier for projectile damage caused by the player.")
                .defineInRange("rangedSneakAttackMultiplier", 3.0D, 1.0D, 100.0D);
        builder.pop();

        COMMON_CONFIG = builder.build();
    }

    private ShadowWalkerConfig() {
    }
}

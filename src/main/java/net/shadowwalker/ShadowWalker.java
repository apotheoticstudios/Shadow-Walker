package net.shadowwalker;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.shadowwalker.network.ModMessages;

@Mod(ShadowWalker.MOD_ID)
@Mod.EventBusSubscriber(modid = ShadowWalker.MOD_ID)
public final class ShadowWalker {
    public static final String MOD_ID = "shadow_walker";
    private static final String LEGACY_MOD_ID = "apotheotics_shadow_walker";

    public ShadowWalker(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModEnchantments.ENCHANTMENTS.register(modEventBus);
        ModMessages.register();

        context.registerConfig(ModConfig.Type.COMMON, ShadowWalkerConfig.COMMON_CONFIG);
    }

    @SubscribeEvent
    public static void remapLegacyIds(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Attribute> mapping : event.getMappings(Registries.ATTRIBUTE, LEGACY_MOD_ID)) {
            if ("sneak".equals(mapping.getKey().getPath())) {
                mapping.remap(ModAttributes.SNEAK.get());
            }
        }

        for (MissingMappingsEvent.Mapping<Enchantment> mapping : event.getMappings(Registries.ENCHANTMENT, LEGACY_MOD_ID)) {
            if ("shadow_walker".equals(mapping.getKey().getPath())) {
                mapping.remap(ModEnchantments.SHADOW_WALKER.get());
            }
        }
    }
}

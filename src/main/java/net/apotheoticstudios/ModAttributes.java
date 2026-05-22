package net.apotheoticstudios;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ShadowWalker.MOD_ID);

    public static final RegistryObject<Attribute> SNEAK = ATTRIBUTES.register("sneak",
            () -> new RangedAttribute("attribute.name.shadow_walker.sneak",
                    0.0D, 0.0D, 100.0D).setSyncable(true));

    private ModAttributes() {
    }
}

package net.shadowwalker;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, ShadowWalker.MOD_ID);

    public static final RegistryObject<Enchantment> SHADOW_WALKER = ENCHANTMENTS.register("shadow_walker",
            () -> new Enchantment(Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                @Override
                public int getMaxLevel() {
                    return 1;
                }

                @Override
                public int getMinCost(int level) {
                    return 18;
                }

                @Override
                public int getMaxCost(int level) {
                    return 50;
                }
            });

    private ModEnchantments() {
    }
}

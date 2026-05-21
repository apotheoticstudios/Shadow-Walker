package net.apotheoticstudios.shadowwalker.enchantment;

import net.apotheoticstudios.shadowwalker.ModAttributes;
import net.apotheoticstudios.shadowwalker.ModEnchantments;
import net.apotheoticstudios.shadowwalker.ShadowWalker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ShadowWalker.MOD_ID)
public final class ShadowWalkerEnchantmentEvents {
    private static final double SNEAK_BONUS_PER_LEVEL = 20.0D;
    private static final UUID SHADOW_WALKER_SNEAK_MODIFIER_ID = UUID.fromString("ab65c2d2-50e2-4a1d-9cf2-3151e8f17142");
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private ShadowWalkerEnchantmentEvents() {
    }

    @SubscribeEvent
    public static void updateOnEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot().isArmor() && event.getEntity() instanceof ServerPlayer player) {
            syncSneakBonus(player);
        }
    }

    @SubscribeEvent
    public static void updateOnPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0) {
            syncSneakBonus(player);
        }
    }

    @SubscribeEvent
    public static void updateOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncSneakBonus(player);
        }
    }

    @SubscribeEvent
    public static void updateOnRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncSneakBonus(player);
        }
    }

    private static void syncSneakBonus(ServerPlayer player) {
        AttributeInstance sneakAttribute = player.getAttribute(ModAttributes.SNEAK.get());
        if (sneakAttribute == null) {
            return;
        }

        double bonus = getShadowWalkerSneakBonus(player);
        AttributeModifier existingModifier = sneakAttribute.getModifier(SHADOW_WALKER_SNEAK_MODIFIER_ID);
        if (existingModifier != null && existingModifier.getAmount() == bonus) {
            return;
        }

        sneakAttribute.removeModifier(SHADOW_WALKER_SNEAK_MODIFIER_ID);
        if (bonus > 0.0D) {
            sneakAttribute.addTransientModifier(new AttributeModifier(SHADOW_WALKER_SNEAK_MODIFIER_ID,
                    "Shadow Walker enchantment", bonus, AttributeModifier.Operation.ADDITION));
        }
    }

    private static double getShadowWalkerSneakBonus(ServerPlayer player) {
        int levels = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            levels += stack.getEnchantmentLevel(ModEnchantments.SHADOW_WALKER.get());
        }
        return levels * SNEAK_BONUS_PER_LEVEL;
    }
}

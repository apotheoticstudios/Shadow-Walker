package net.shadowwalker.stealth;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public final class StealthMode {
    private StealthMode() {
    }

    public static boolean isTryingToSneak(Player player) {
        return (player.isCrouching() || player.isShiftKeyDown()) && canEnter(player);
    }

    private static boolean canEnter(Player player) {
        if (!player.onGround()
                || player.isInWaterOrBubble()
                || player.isInLava()
                || player.isSwimming()
                || player.isFallFlying()
                || player.getAbilities().flying) {
            return false;
        }

        BlockPos groundPos = player.getOnPos();
        BlockState groundState = player.level().getBlockState(groundPos);
        return !groundState.getCollisionShape(player.level(), groundPos).isEmpty();
    }
}

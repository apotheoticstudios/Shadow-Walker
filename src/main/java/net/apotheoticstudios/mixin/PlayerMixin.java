package net.apotheoticstudios.mixin;

import net.apotheoticstudios.ShadowWalkerConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void shadowWalker$allowSneakingOverBlockEdges(CallbackInfoReturnable<Boolean> callback) {
        if (ShadowWalkerConfig.ALLOW_SNEAKING_OVER_BLOCK_EDGES.get()) {
            callback.setReturnValue(false);
        }
    }
}

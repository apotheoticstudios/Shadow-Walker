package net.shadowwalker.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.shadowwalker.ShadowWalker;
import net.shadowwalker.ShadowWalkerConfig;

@Mod.EventBusSubscriber(modid = ShadowWalker.MOD_ID, value = Dist.CLIENT)
public final class DetectionMarkerClientEvents {
    private static final String MARKER_TEXT = "!";
    private static final int MARKER_COLOR = 0xFFFF3030;
    private static final double MARKER_VERTICAL_OFFSET = 0.35D;

    private DetectionMarkerClientEvents() {
    }

    @SubscribeEvent
    public static void renderDetectionMarker(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Mob)
                || !ShadowWalkerConfig.ENABLE_STEALTH_SYSTEM.get()
                || !ShadowWalkerConfig.SHOW_DETECTION_EXCLAMATION_MARKS.get()
                || !ClientSneakAwarenessState.isDetectedObserver(entity.getId())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entity.isInvisibleTo(minecraft.player)) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        Font font = minecraft.font;
        float scale = ShadowWalkerConfig.DETECTION_EXCLAMATION_MARK_SCALE.get().floatValue();

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + MARKER_VERTICAL_OFFSET, 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);

        float x = -font.width(MARKER_TEXT) / 2.0F;
        font.drawInBatch(MARKER_TEXT, x, 0.0F, MARKER_COLOR, true, poseStack.last().pose(),
                bufferSource, Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        poseStack.popPose();
    }
}

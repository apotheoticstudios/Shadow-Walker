package net.apotheoticstudios.shadowwalker.client;

import net.apotheoticstudios.shadowwalker.ShadowWalker;
import net.apotheoticstudios.shadowwalker.ShadowWalkerConfig;
import net.apotheoticstudios.shadowwalker.stealth.StealthMode;
import net.apotheoticstudios.shadowwalker.stealth.SneakAwareness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ShadowWalker.MOD_ID, value = Dist.CLIENT)
public final class StealthCrosshairClientEvents {
    private static final int COLOR_SUSPICIOUS = 0xFFECCB61;
    private static final int COLOR_SEARCHING = 0xFFFF9B47;
    private static final int COLOR_DETECTED = 0xFFFF4A4A;
    private static final int COLOR_BACKGROUND = 0xAA050609;

    private StealthCrosshairClientEvents() {
    }

    @SubscribeEvent
    public static void renderStealthCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (!VanillaGuiOverlay.CROSSHAIR.id().equals(event.getOverlay().id())
                || !ShadowWalkerConfig.ENABLE_STEALTH_SYSTEM.get()
                || !ShadowWalkerConfig.SHOW_STEALTH_CROSSHAIR.get()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null
                || minecraft.level == null
                || minecraft.options.hideGui
                || minecraft.options.renderDebug
                || minecraft.screen != null
                || player.isSpectator()
                || minecraft.options.getCameraType().isMirrored()
                || !StealthMode.isTryingToSneak(player)
                || isAiming(player)) {
            return;
        }

        SneakAwareness awareness = ClientSneakAwarenessState.awareness();
        if (awareness == SneakAwareness.DISABLED) {
            return;
        }

        event.setCanceled(true);
        renderSprite(event.getGuiGraphics(), CrosshairSprite.forAwareness(awareness));
        renderProgressBar(event.getGuiGraphics(), awareness, ClientSneakAwarenessState.progress());
    }

    private static boolean isAiming(Player player) {
        ItemStack active = player.getUseItem();
        if (player.isUsingItem() && isAimingItem(active.isEmpty() ? player.getMainHandItem() : active)) {
            return true;
        }
        return Minecraft.getInstance().options.keyUse.isDown()
                && (isAimingItem(player.getMainHandItem()) || isAimingItem(player.getOffhandItem()));
    }

    private static boolean isAimingItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        UseAnim useAnimation = stack.getUseAnimation();
        return item instanceof ProjectileWeaponItem
                || item instanceof TridentItem
                || useAnimation == UseAnim.BOW
                || useAnimation == UseAnim.CROSSBOW
                || useAnimation == UseAnim.SPEAR;
    }

    private static void renderSprite(GuiGraphics guiGraphics, CrosshairSprite sprite) {
        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2;
        int x = centerX - sprite.width() / 2;
        int y = centerY - sprite.height() / 2;
        guiGraphics.blit(sprite.texture(), x, y, 0, 0, sprite.width(), sprite.height(),
                sprite.textureWidth(), sprite.textureHeight());
    }

    private static void renderProgressBar(GuiGraphics guiGraphics, SneakAwareness awareness, float progress) {
        if (progress <= 0.0F) {
            return;
        }

        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2;
        int width = 24;
        int filled = Math.round(width * Math.max(0.0F, Math.min(1.0F, progress)));
        int barY = centerY + 9;
        guiGraphics.fill(centerX - width / 2 - 1, barY - 1, centerX + width / 2 + 1, barY + 2, COLOR_BACKGROUND);
        guiGraphics.fill(centerX - width / 2, barY, centerX - width / 2 + filled, barY + 1, colorFor(awareness));
    }

    private static int colorFor(SneakAwareness awareness) {
        return switch (awareness) {
            case DETECTED -> COLOR_DETECTED;
            case SEARCHING -> COLOR_SEARCHING;
            case SUSPICIOUS -> COLOR_SUSPICIOUS;
            case DISABLED, HIDDEN -> 0xFFB8C2CC;
        };
    }

    private enum CrosshairSprite {
        SNEAK_HIDDEN("sneak_hidden"),
        SNEAK_SUSPICIOUS("sneak_suspicious"),
        SNEAK_SEARCHING("sneak_searching"),
        SNEAK_DETECTED("sneak_detected");

        private static final int WIDTH = 32;
        private static final int HEIGHT = 16;

        private final ResourceLocation texture;

        CrosshairSprite(String name) {
            this.texture = ResourceLocation.fromNamespaceAndPath(ShadowWalker.MOD_ID,
                    "textures/gui/crosshair/" + name + ".png");
        }

        private static CrosshairSprite forAwareness(SneakAwareness awareness) {
            return switch (awareness) {
                case DETECTED -> SNEAK_DETECTED;
                case SEARCHING -> SNEAK_SEARCHING;
                case SUSPICIOUS -> SNEAK_SUSPICIOUS;
                case DISABLED, HIDDEN -> SNEAK_HIDDEN;
            };
        }

        private ResourceLocation texture() {
            return texture;
        }

        private int width() {
            return WIDTH;
        }

        private int height() {
            return HEIGHT;
        }

        private int textureWidth() {
            return WIDTH;
        }

        private int textureHeight() {
            return HEIGHT;
        }
    }
}

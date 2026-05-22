package net.apotheoticstudios.stealth;

import net.apotheoticstudios.ShadowWalker;
import net.apotheoticstudios.ShadowWalkerConfig;
import net.apotheoticstudios.ModAttributes;
import net.apotheoticstudios.network.ClientboundSneakAwarenessPacket;
import net.apotheoticstudios.network.ModMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ShadowWalker.MOD_ID)
public final class SneakAwarenessEvents {
    private static final int UPDATE_INTERVAL_TICKS = 5;
    private static final int STALE_OBSERVER_TICKS = 120;
    private static final int FORCE_SYNC_TICKS = 20;
    private static final int OBSERVER_MOVEMENT_THROTTLE_TICKS = UPDATE_INTERVAL_TICKS + 2;
    private static final double FACING_DOT_THRESHOLD = 0.25D;
    private static final double MAX_SNEAK_DETECTION_REDUCTION = 0.8D;
    private static final double MIN_SNEAK_DETECTION_MULTIPLIER = 0.2D;
    private static final double MAX_SNEAK_DETECTION_MULTIPLIER = 1.5D;
    private static final double GUARANTEED_SNEAK_DETECTION_RANGE = 1.75D;
    private static final double MIN_SNEAK_TARGET_DETECTION_RANGE = 2.5D;
    private static final double DETECTED_DISPLAY_RANGE = 20.0D;
    private static final double SEARCHING_DISPLAY_RANGE = 40.0D;
    private static final double DETECTED_DISPLAY_RANGE_SQR = DETECTED_DISPLAY_RANGE * DETECTED_DISPLAY_RANGE;
    private static final double SEARCHING_DISPLAY_RANGE_SQR = SEARCHING_DISPLAY_RANGE * SEARCHING_DISPLAY_RANGE;
    private static final double SHARED_DETECTION_RANGE = 10.0D;
    private static final double SHARED_DETECTION_RANGE_SQR = SHARED_DETECTION_RANGE * SHARED_DETECTION_RANGE;
    private static final double SOUND_DISRUPTION_NOISE_THRESHOLD = 0.5D;
    private static final double MIN_SNEAK_ATTACK_MULTIPLIER = 1.0D;
    private static final float BOW_ATTACK_NOISE = 0.25F;
    private static final float DAGGER_ATTACK_NOISE = 0.25F;
    private static final float ONE_HANDED_ATTACK_NOISE = 0.85F;
    private static final float HEAVY_ATTACK_NOISE = 1.2F;
    private static final float UNCLASSIFIED_ATTACK_NOISE = 0.7F;
    private static final int SNEAK_ATTACK_CRIT_PARTICLES = 24;
    private static final int SNEAK_ATTACK_ENCHANTED_HIT_PARTICLES = 12;
    private static final float DETECTION_PROGRESS_STEP = 0.22F;
    private static final TagKey<Item> DAGGER_WEAPONS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ShadowWalker.MOD_ID, "daggers"));
    private static final Map<UUID, PlayerAwarenessData> PLAYER_AWARENESS = new HashMap<>();
    private static final Map<UUID, Integer> DISABLED_SYNC_TICKS = new HashMap<>();
    private static final Map<UUID, Long> OBSERVER_MOVEMENT_THROTTLE_UNTIL = new HashMap<>();

    private SneakAwarenessEvents() {
    }

    @SubscribeEvent
    public static void tickPlayerAwareness(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (!isStealthSystemEnabled()) {
            disableForPlayer(player);
            return;
        }

        UUID playerId = player.getUUID();
        DISABLED_SYNC_TICKS.remove(playerId);

        PlayerAwarenessData data = PLAYER_AWARENESS.get(playerId);
        boolean undetectable = isUndetectable(player);
        if (player.isSpectator() || player.isCreative() || !player.isAlive() || undetectable) {
            if (undetectable && player.tickCount % UPDATE_INTERVAL_TICKS == 0) {
                clearNearbyObserverTargets(player);
            }
            clearAwareness(player, data);
            return;
        }

        if (!StealthMode.isTryingToSneak(player)) {
            clearAwareness(player, data);
            return;
        }

        if (player.tickCount % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        data = PLAYER_AWARENESS.computeIfAbsent(playerId, uuid -> new PlayerAwarenessData());
        updatePlayerAwareness(player, data);
    }

    @SubscribeEvent
    public static void recordPlayerSound(PlayLevelSoundEvent.AtEntity event) {
        Entity entity = event.getEntity();
        if (!isStealthSystemEnabled()
                || !(entity instanceof ServerPlayer player)
                || event.getLevel().isClientSide()
                || !StealthMode.isTryingToSneak(player)
                || isUndetectable(player)) {
            return;
        }

        float noise = event.getOriginalVolume();
        if (event.getSource() == SoundSource.PLAYERS) {
            noise *= 1.35F;
        }
        if (noise <= 0.0F) {
            return;
        }

        PLAYER_AWARENESS.computeIfAbsent(player.getUUID(), uuid -> new PlayerAwarenessData())
                .recordNoise(Mth.clamp(noise, 0.0F, 2.0F));
    }

    @SubscribeEvent
    public static void throttleObserverMovement(LivingEvent.LivingTickEvent event) {
        if (!isStealthSystemEnabled() || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }

        double movementMultiplier = observerMobMovementMultiplier();
        if (movementMultiplier >= 1.0D) {
            return;
        }

        long gameTime = mob.level().getGameTime();
        Long throttleUntil = OBSERVER_MOVEMENT_THROTTLE_UNTIL.get(mob.getUUID());
        if (throttleUntil == null) {
            return;
        }
        if (gameTime > throttleUntil || !canThrottleObserverMovement(mob)) {
            OBSERVER_MOVEMENT_THROTTLE_UNTIL.remove(mob.getUUID());
            return;
        }

        if (movementMultiplier <= 0.0D) {
            mob.getNavigation().stop();
        }

        Vec3 movement = mob.getDeltaMovement();
        mob.setDeltaMovement(movement.x * movementMultiplier, movement.y, movement.z * movementMultiplier);
    }

    @SubscribeEvent
    public static void preventUndetectedPlayerTargeting(LivingChangeTargetEvent event) {
        if (!isStealthSystemEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof Mob observer) || !(event.getNewTarget() instanceof ServerPlayer player)) {
            return;
        }

        if (isUndetectable(player) || shouldSuppressStealthTarget(observer, player)) {
            event.setNewTarget(null);
        }
    }

    @SubscribeEvent
    public static void applySneakAttackDamage(LivingHurtEvent event) {
        if (!isStealthSystemEnabled()
                || event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof Mob observer)) {
            return;
        }

        SneakAttackType sneakAttackType = getSneakAttackType(event.getSource());
        if (sneakAttackType == SneakAttackType.NONE || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!canSneakAttack(player, observer)) {
            return;
        }

        double multiplier = getSneakAttackMultiplier(player, sneakAttackType);
        event.setAmount((float) (event.getAmount() * multiplier));
        if (multiplier > MIN_SNEAK_ATTACK_MULTIPLIER) {
            spawnSneakAttackCritParticles(observer, sneakAttackType);
            sendSneakAttackCritMessage(player, multiplier);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void breakInvisibilityOnAttack(LivingHurtEvent event) {
        if (!ShadowWalkerConfig.REMOVE_INVISIBILITY_ON_ATTACK.get()
                || event.getEntity().level().isClientSide()
                || event.getAmount() <= 0.0F
                || event.getEntity() == event.getSource().getEntity()
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        player.removeEffect(MobEffects.INVISIBILITY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void recordSneakAttackNoise(LivingHurtEvent event) {
        if (!isStealthSystemEnabled()
                || event.getEntity().level().isClientSide()
                || event.getAmount() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getEntity() == player
                || !StealthMode.isTryingToSneak(player)
                || isUndetectable(player)) {
            return;
        }

        float noise = getAttackNoise(player, getSneakAttackType(event.getSource()));
        if (noise <= 0.0F) {
            return;
        }

        PLAYER_AWARENESS.computeIfAbsent(player.getUUID(), uuid -> new PlayerAwarenessData())
                .recordNoise(noise);
    }

    @SubscribeEvent
    public static void clearPlayer(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_AWARENESS.remove(event.getEntity().getUUID());
        DISABLED_SYNC_TICKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void clearServer(ServerStoppingEvent event) {
        PLAYER_AWARENESS.clear();
        DISABLED_SYNC_TICKS.clear();
        OBSERVER_MOVEMENT_THROTTLE_UNTIL.clear();
    }

    private static boolean isStealthSystemEnabled() {
        return ShadowWalkerConfig.ENABLE_STEALTH_SYSTEM.get();
    }

    private static void disableForPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerAwarenessData data = PLAYER_AWARENESS.remove(playerId);
        if (data != null) {
            data.clear(player);
        }

        Integer lastSyncTick = DISABLED_SYNC_TICKS.get(playerId);
        if (lastSyncTick == null || player.tickCount - lastSyncTick >= FORCE_SYNC_TICKS) {
            DISABLED_SYNC_TICKS.put(playerId, player.tickCount);
            ModMessages.sendToPlayer(new ClientboundSneakAwarenessPacket(SneakAwareness.DISABLED, 0.0F, -1), player);
        }
    }

    private static void clearAwareness(ServerPlayer player, PlayerAwarenessData data) {
        if (data != null) {
            data.clear(player);
            PLAYER_AWARENESS.remove(player.getUUID());
        }
    }

    private static void updatePlayerAwareness(ServerPlayer player, PlayerAwarenessData data) {
        int tick = player.tickCount;
        data.decayRecentNoise();

        ServerLevel level = player.serverLevel();
        AABB scanArea = player.getBoundingBox().inflate(maxScanRange());
        List<Mob> observers = level.getEntitiesOfClass(Mob.class, scanArea, observer -> canObserve(observer, player));
        if (!observers.isEmpty()) {
            double normalizedSneak = getNormalizedSneak(player);
            double playerNoise = getSneakingPlayerNoise(player, data.recentNoise);
            int playerLight = getActualLightLevel(player.level(), player.blockPosition());
            List<Mob> detectedObservers = new ArrayList<>();

            for (Mob observer : observers) {
                markObserverMovementThrottle(observer);

                ObserverAwareness observerAwareness = data.observer(observer.getId());
                boolean alreadyTracked = observerAwareness.hasBeenChecked();
                observerAwareness.lastCheckedTick = tick;
                double distanceSqr = observer.distanceToSqr(player);
                observerAwareness.lastDistanceSqr = distanceSqr;

                if (observer.getTarget() == player || observer.getLastHurtByMob() == player) {
                    if (alreadyTracked) {
                        observerAwareness.advance(DETECTION_PROGRESS_STEP);
                    } else {
                        observerAwareness.detectImmediately();
                    }
                    observerAwareness.lastSensedTick = tick;
                    if (observerAwareness.isDetected()) {
                        detectedObservers.add(observer);
                    }
                    continue;
                }

                if (canDetectSneakingTarget(observer, player, distanceSqr, playerNoise, normalizedSneak, playerLight)) {
                    observerAwareness.advance(DETECTION_PROGRESS_STEP);
                    observerAwareness.lastSensedTick = tick;
                    if (observerAwareness.isDetected()) {
                        detectedObservers.add(observer);
                    }
                    continue;
                }

                double signal = getDetectionSignal(player, observer, distanceSqr, playerNoise, normalizedSneak, playerLight);
                if (signal > 0.0D) {
                    observerAwareness.lastSensedTick = tick;
                    observerAwareness.progress += (float) (signal * 0.17D);
                } else {
                    observerAwareness.progress -= 0.04F;
                }
                observerAwareness.progress = Mth.clamp(observerAwareness.progress, 0.0F, 1.0F);
                if (observerAwareness.isDetected() && observerAwareness.lastSensedTick == tick) {
                    detectedObservers.add(observer);
                }
            }

            for (Mob detectedObserver : detectedObservers) {
                targetDetectedObserver(detectedObserver, player);
                alertNearbyObservers(level, player, data, detectedObserver, tick);
            }
        }

        data.pruneObservers(tick);
        data.sync(player, tick, crosshairDetectionRangeSqr());
    }

    private static boolean canObserve(Mob observer, Player player) {
        return observer.isAlive()
                && !observer.isSpectator()
                && observer.getId() != player.getId()
                && (observer instanceof Enemy || observer.getTarget() == player || observer.getLastHurtByMob() == player);
    }

    private static void markObserverMovementThrottle(Mob observer) {
        if (observerMobMovementMultiplier() >= 1.0D || !canThrottleObserverMovement(observer)) {
            return;
        }

        OBSERVER_MOVEMENT_THROTTLE_UNTIL.put(observer.getUUID(),
                observer.level().getGameTime() + OBSERVER_MOVEMENT_THROTTLE_TICKS);
    }

    private static boolean canThrottleObserverMovement(Mob observer) {
        return observer.isAlive()
                && !observer.isSpectator()
                && observer.getTarget() == null
                && observer.getLastHurtByMob() == null;
    }

    private static boolean shouldSuppressStealthTarget(Mob observer, ServerPlayer player) {
        if (player.isSpectator() || player.isCreative() || !player.isAlive() || !StealthMode.isTryingToSneak(player)) {
            return false;
        }
        if (observer.getTarget() == player || observer.getLastHurtByMob() == player) {
            return false;
        }
        if (hasDetectedAwareness(player, observer)) {
            return false;
        }
        if (!canObserve(observer, player)) {
            return false;
        }

        return !canDetectSneakingTarget(observer, player);
    }

    private static boolean canSneakAttack(ServerPlayer player, Mob observer) {
        if (player.isSpectator() || player.isCreative() || !player.isAlive() || !StealthMode.isTryingToSneak(player)) {
            return false;
        }
        if (!observer.isAlive() || observer.isSpectator() || observer.getId() == player.getId()) {
            return false;
        }
        if (observer.getTarget() == player || observer.getLastHurtByMob() == player) {
            return false;
        }

        PlayerAwarenessData data = PLAYER_AWARENESS.get(player.getUUID());
        if (data != null) {
            if (data.isObserverDetected(observer.getId()) || !data.isCrosshairHidden()) {
                return false;
            }
        }

        return isUndetectable(player) || !canDetectSneakingTarget(observer, player);
    }

    private static void alertNearbyObservers(ServerLevel level, ServerPlayer player, PlayerAwarenessData data,
                                             Mob detectedObserver, int tick) {
        AABB alertArea = detectedObserver.getBoundingBox().inflate(SHARED_DETECTION_RANGE);
        for (Mob observer : level.getEntitiesOfClass(Mob.class, alertArea,
                observer -> observer.getId() != detectedObserver.getId()
                        && observer.distanceToSqr(detectedObserver) <= SHARED_DETECTION_RANGE_SQR
                        && canObserve(observer, player))) {
            markObserverMovementThrottle(observer);

            ObserverAwareness observerAwareness = data.observer(observer.getId());
            observerAwareness.detectImmediately();
            observerAwareness.lastCheckedTick = tick;
            observerAwareness.lastSensedTick = tick;
            observerAwareness.lastDistanceSqr = observer.distanceToSqr(player);
            targetDetectedObserver(observer, player);
        }
    }

    private static void targetDetectedObserver(Mob observer, ServerPlayer player) {
        if (observer.getTarget() == player) {
            OBSERVER_MOVEMENT_THROTTLE_UNTIL.remove(observer.getUUID());
            return;
        }
        if (!observer.canAttack(player) || !observer.canAttackType(player.getType()) || observer.isAlliedTo(player)) {
            return;
        }

        observer.setTarget(player);
        if (observer.getTarget() == player) {
            OBSERVER_MOVEMENT_THROTTLE_UNTIL.remove(observer.getUUID());
        }
    }

    private static boolean hasDetectedAwareness(ServerPlayer player, Mob observer) {
        PlayerAwarenessData data = PLAYER_AWARENESS.get(player.getUUID());
        return data != null && data.isObserverDetected(observer.getId());
    }

    private static SneakAttackType getSneakAttackType(DamageSource source) {
        Entity directEntity = source.getDirectEntity();
        Entity causingEntity = source.getEntity();
        if (!(causingEntity instanceof ServerPlayer)) {
            return SneakAttackType.NONE;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) && directEntity != causingEntity) {
            return SneakAttackType.RANGED;
        }
        if (directEntity == causingEntity) {
            return SneakAttackType.MELEE;
        }
        return SneakAttackType.NONE;
    }

    private static double getSneakAttackMultiplier(ServerPlayer player, SneakAttackType sneakAttackType) {
        double multiplier = switch (sneakAttackType) {
            case RANGED -> ShadowWalkerConfig.RANGED_SNEAK_ATTACK_MULTIPLIER.get();
            case MELEE -> getMeleeSneakAttackMultiplier(player.getMainHandItem());
            case NONE -> MIN_SNEAK_ATTACK_MULTIPLIER;
        };
        return Math.max(MIN_SNEAK_ATTACK_MULTIPLIER, multiplier);
    }

    private static double getMeleeSneakAttackMultiplier(ItemStack stack) {
        if (isDagger(stack)) {
            return ShadowWalkerConfig.DAGGER_SNEAK_ATTACK_MULTIPLIER.get();
        }
        if (isOneHandedSneakWeapon(stack)) {
            return ShadowWalkerConfig.ONE_HANDED_SNEAK_ATTACK_MULTIPLIER.get();
        }
        return ShadowWalkerConfig.MELEE_SNEAK_ATTACK_MULTIPLIER.get();
    }

    private static void spawnSneakAttackCritParticles(Mob observer, SneakAttackType sneakAttackType) {
        if (!(observer.level() instanceof ServerLevel level)) {
            return;
        }

        double x = observer.getX();
        double y = observer.getY() + observer.getBbHeight() * 0.55D;
        double z = observer.getZ();
        double horizontalSpread = Math.max(0.25D, observer.getBbWidth() * 0.45D);
        double verticalSpread = Math.max(0.35D, observer.getBbHeight() * 0.25D);

        level.sendParticles(ParticleTypes.CRIT, x, y, z, SNEAK_ATTACK_CRIT_PARTICLES,
                horizontalSpread, verticalSpread, horizontalSpread, 0.25D);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, SNEAK_ATTACK_ENCHANTED_HIT_PARTICLES,
                horizontalSpread, verticalSpread, horizontalSpread, 0.18D);
        if (sneakAttackType == SneakAttackType.MELEE) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1,
                    horizontalSpread * 0.25D, verticalSpread * 0.1D, horizontalSpread * 0.25D, 0.0D);
        }
    }

    private static void sendSneakAttackCritMessage(ServerPlayer player, double multiplier) {
        if (!ShadowWalkerConfig.SHOW_SNEAK_ATTACK_MESSAGE.get()) {
            return;
        }
        player.sendSystemMessage(Component.literal("Sneak Attack ")
                .append(Component.literal(formatMultiplier(multiplier) + "x").withStyle(Style.EMPTY.withBold(true)))
                .append(Component.literal(" damage")));
    }

    private static String formatMultiplier(double multiplier) {
        if (multiplier == Math.rint(multiplier)) {
            return Integer.toString((int) multiplier);
        }
        return String.format(Locale.ROOT, "%.2f", multiplier).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static double getDetectionSignal(ServerPlayer player, Mob observer, double distanceSqr, double playerNoise,
                                             double normalizedSneak, int playerLight) {
        if (isUndetectable(player)) {
            return -0.2D;
        }

        double scanRange = getObserverScanRange(observer);
        if (distanceSqr > scanRange * scanRange) {
            return -0.2D;
        }

        double distance = Math.sqrt(distanceSqr);
        double distanceScore = 1.0D - Mth.clamp(distance / scanRange, 0.0D, 1.0D);
        boolean hasLineOfSight = observer.hasLineOfSight(player);
        double visualSignal = hasLineOfSight ? getVisualSignal(player, observer, distanceScore, distanceSqr, playerLight) : 0.0D;
        double soundSignal = getSoundSignal(distance, scanRange, hasLineOfSight, playerNoise);
        double signal = visualSignal + soundSignal;

        signal *= 0.78D;
        signal *= getSneakDetectionMultiplier(normalizedSneak);

        if (player.hasEffect(MobEffects.GLOWING) || player.getRemainingFireTicks() > 0) {
            signal = Math.max(signal, 0.86D);
        }
        if (observer.hasEffect(MobEffects.BLINDNESS)) {
            signal *= 0.12D;
        }
        if (!hasLineOfSight && soundSignal <= 0.04D) {
            signal = -0.08D;
        }

        return Mth.clamp(signal, -0.2D, 1.0D);
    }

    private static double getVisualSignal(ServerPlayer player, Mob observer, double distanceScore, double distanceSqr,
                                          int playerLight) {
        double facingScore = getFacingScore(observer, player);
        if (facingScore <= 0.0D && distanceSqr > 9.0D) {
            return 0.0D;
        }

        int observerLight = getActualLightLevel(observer.level(), observer.blockPosition());
        double flatLight = playerLight / 15.0D;
        double lightAdvantage = Mth.clamp((playerLight - observerLight + 8.0D) / 16.0D, 0.0D, 1.0D);
        double visibilityPercent = player.getVisibilityPercent(observer);

        return distanceScore * (0.35D + facingScore * 1.85D)
                * (0.18D + flatLight * 0.62D + lightAdvantage * 0.2D)
                * visibilityPercent;
    }

    private static boolean isUndetectable(ServerPlayer player) {
        return player.hasEffect(MobEffects.INVISIBILITY) || player.isInvisible();
    }

    private static void clearNearbyObserverTargets(ServerPlayer player) {
        AABB scanArea = player.getBoundingBox().inflate(maxScanRange());
        for (Mob observer : player.serverLevel().getEntitiesOfClass(Mob.class, scanArea,
                observer -> observer.isAlive()
                        && (observer.getTarget() == player || observer.getLastHurtByMob() == player))) {
            if (observer.getTarget() == player) {
                observer.setTarget(null);
            }
            if (observer.getLastHurtByMob() == player) {
                observer.setLastHurtByMob(null);
            }
        }
    }

    private static double getSoundSignal(double distance, double scanRange, boolean hasLineOfSight, double noise) {
        if (noise <= 0.0D) {
            return 0.0D;
        }

        double soundRange = Math.max(8.0D, scanRange * 0.65D);
        double falloff = 1.0D - Mth.clamp(distance / soundRange, 0.0D, 1.0D);
        double lineOfSightMultiplier = hasLineOfSight ? 1.0D : 0.35D;
        return noise * falloff * lineOfSightMultiplier;
    }

    private static double getSneakDetectionMultiplier(double normalizedSneak) {
        double multiplier = 1.0D - normalizedSneak * MAX_SNEAK_DETECTION_REDUCTION;
        return Mth.clamp(multiplier, MIN_SNEAK_DETECTION_MULTIPLIER, MAX_SNEAK_DETECTION_MULTIPLIER);
    }

    private static boolean canDetectSneakingTarget(Mob observer, ServerPlayer player) {
        double distanceSqr = observer.distanceToSqr(player);
        if (isImmediatelyDetectable(player, distanceSqr)) {
            return true;
        }

        float recentNoise = getRecentNoise(player);
        return canDetectSneakingTarget(observer, player, distanceSqr, getSneakingPlayerNoise(player, recentNoise),
                getNormalizedSneak(player), getActualLightLevel(player.level(), player.blockPosition()));
    }

    private static boolean canDetectSneakingTarget(Mob observer, ServerPlayer player, double distanceSqr,
                                                   double playerNoise, double normalizedSneak, int playerLight) {
        if (isImmediatelyDetectable(player, distanceSqr)) {
            return true;
        }

        boolean hasLineOfSight = observer.hasLineOfSight(player);
        double distance = Math.sqrt(distanceSqr);
        double scanRange = getObserverScanRange(observer);
        if (isSoundDisrupted(playerNoise, distanceSqr, scanRange)) {
            return true;
        }

        if (!hasLineOfSight) {
            double soundRange = Math.max(GUARANTEED_SNEAK_DETECTION_RANGE, scanRange * 0.35D * playerNoise);
            return playerNoise > 0.15D && distance <= soundRange;
        }

        double facingScore = getFacingScore(observer, player);
        if (facingScore <= 0.0D && distance > MIN_SNEAK_TARGET_DETECTION_RANGE) {
            return false;
        }

        double targetRange = getSneakTargetDetectionRange(scanRange, normalizedSneak);
        double lightMultiplier = 0.52D + playerLight / 15.0D * 0.83D;
        double facingMultiplier = 0.35D + facingScore * 2.75D;
        double noiseMultiplier = 1.0D + playerNoise * 0.65D;
        targetRange *= lightMultiplier * facingMultiplier * noiseMultiplier;
        if (observer.hasEffect(MobEffects.BLINDNESS)) {
            targetRange *= 0.12D;
        }

        return distance <= Math.max(GUARANTEED_SNEAK_DETECTION_RANGE, targetRange);
    }

    private static boolean isSoundDisrupted(double playerNoise, double distanceSqr, double scanRange) {
        return playerNoise >= SOUND_DISRUPTION_NOISE_THRESHOLD && distanceSqr <= scanRange * scanRange;
    }

    private static boolean isImmediatelyDetectable(ServerPlayer player, double distanceSqr) {
        return distanceSqr <= GUARANTEED_SNEAK_DETECTION_RANGE * GUARANTEED_SNEAK_DETECTION_RANGE
                || player.hasEffect(MobEffects.GLOWING)
                || player.getRemainingFireTicks() > 0;
    }

    private static double getSneakTargetDetectionRange(double scanRange, double normalizedSneak) {
        double rangeMultiplier = 0.9D - normalizedSneak * 0.78D;
        return Math.max(MIN_SNEAK_TARGET_DETECTION_RANGE,
                scanRange * Mth.clamp(rangeMultiplier, 0.12D, 1.2D));
    }

    private static double getObserverScanRange(Mob observer) {
        return Math.min(maxScanRange(), Math.max(16.0D, observer.getAttributeValue(Attributes.FOLLOW_RANGE)));
    }

    private static double getNormalizedSneak(ServerPlayer player) {
        return Mth.clamp(getEffectiveSneakLevel(player) / 100.0D, 0.0D, 1.0D);
    }

    private static double getEffectiveSneakLevel(ServerPlayer player) {
        AttributeInstance sneakAttribute = player.getAttribute(ModAttributes.SNEAK.get());
        double attributeSneak = sneakAttribute == null ? 0.0D : sneakAttribute.getValue();
        return Mth.clamp(ShadowWalkerConfig.SNEAK_LEVEL.get() + attributeSneak, 0.0D, 100.0D);
    }

    private static float getRecentNoise(ServerPlayer player) {
        PlayerAwarenessData data = PLAYER_AWARENESS.get(player.getUUID());
        return data == null ? 0.0F : data.recentNoise;
    }

    private static double getSneakingPlayerNoise(ServerPlayer player, float recentNoise) {
        double movementSpeed = Math.sqrt(player.getDeltaMovement().horizontalDistanceSqr());
        double movementNoise = movementSpeed * 5.0D;
        if (player.isSprinting()) {
            movementNoise += 0.55D;
        }
        movementNoise *= 0.45D;

        double armorNoise = player.getArmorValue() / 40.0D * ShadowWalkerConfig.ARMOR_NOISE_MULTIPLIER.get();
        return Mth.clamp(movementNoise + armorNoise + recentNoise * 0.55D, 0.0D, 1.2D);
    }

    private static boolean isDagger(ItemStack stack) {
        return stack.is(DAGGER_WEAPONS) || itemPath(stack).contains("dagger");
    }

    private static float getAttackNoise(ServerPlayer player, SneakAttackType sneakAttackType) {
        return switch (sneakAttackType) {
            case RANGED -> BOW_ATTACK_NOISE;
            case MELEE -> {
                ItemStack weapon = player.getMainHandItem();
                if (isDagger(weapon)) {
                    yield DAGGER_ATTACK_NOISE;
                }
                if (isHeavyMeleeWeapon(weapon)) {
                    yield HEAVY_ATTACK_NOISE;
                }
                if (isOneHandedSneakWeapon(weapon)) {
                    yield ONE_HANDED_ATTACK_NOISE;
                }
                yield UNCLASSIFIED_ATTACK_NOISE;
            }
            case NONE -> UNCLASSIFIED_ATTACK_NOISE;
        };
    }

    private static boolean isOneHandedSneakWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && !isHeavyMeleeWeapon(stack)
                && (stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof TieredItem);
    }

    private static boolean isHeavyMeleeWeapon(ItemStack stack) {
        String path = itemPath(stack);
        return path.contains("greatsword")
                || path.contains("battleaxe")
                || path.contains("warhammer")
                || path.contains("claymore")
                || path.contains("halberd")
                || path.contains("glaive")
                || path.contains("scythe");
    }

    private static String itemPath(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null ? "" : itemId.getPath();
    }

    private static double getFacingScore(LivingEntity watcher, Player player) {
        Vec3 toPlayer = player.getEyePosition().subtract(watcher.getEyePosition());
        if (toPlayer.lengthSqr() <= 0.0001D) {
            return 1.0D;
        }

        double dot = watcher.getLookAngle().normalize().dot(toPlayer.normalize());
        return Mth.clamp((dot - FACING_DOT_THRESHOLD) / (1.0D - FACING_DOT_THRESHOLD), 0.0D, 1.0D);
    }

    private static int getActualLightLevel(net.minecraft.world.level.Level level, BlockPos pos) {
        int skyLight = 0;
        if (level.dimensionType().hasSkyLight()) {
            skyLight = level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken();
        }

        return Mth.clamp(Math.max(level.getBrightness(LightLayer.BLOCK, pos), skyLight), 0, 15);
    }

    private static double maxScanRange() {
        return ShadowWalkerConfig.MAX_SCAN_RANGE.get();
    }

    private static double crosshairDetectionRangeSqr() {
        double range = ShadowWalkerConfig.CROSSHAIR_DETECTION_RANGE.get();
        return range * range;
    }

    private static double observerMobMovementMultiplier() {
        return ShadowWalkerConfig.OBSERVER_MOB_MOVEMENT_MULTIPLIER.get();
    }

    private static final class PlayerAwarenessData {
        private final Map<Integer, ObserverAwareness> observers = new HashMap<>();
        private SneakAwareness lastSentAwareness = SneakAwareness.HIDDEN;
        private float lastSentProgress;
        private int lastSentObserverId = -1;
        private List<Integer> lastSentDetectedObserverIds = List.of();
        private int lastSyncTick;
        private float recentNoise;

        private ObserverAwareness observer(int id) {
            ObserverAwareness awareness = observers.get(id);
            if (awareness == null) {
                awareness = new ObserverAwareness();
                observers.put(id, awareness);
            }
            return awareness;
        }

        private boolean isCrosshairHidden() {
            return lastSentAwareness == SneakAwareness.HIDDEN && lastSentProgress < SneakAwareness.SUSPICIOUS_PROGRESS;
        }

        private boolean isObserverDetected(int observerId) {
            ObserverAwareness awareness = observers.get(observerId);
            return awareness != null && awareness.isDetected();
        }

        private void recordNoise(float noise) {
            recentNoise = Math.max(recentNoise, noise);
        }

        private void decayRecentNoise() {
            recentNoise = Math.max(0.0F, recentNoise - 0.22F);
        }

        private void pruneObservers(int tick) {
            for (Iterator<Map.Entry<Integer, ObserverAwareness>> iterator = observers.entrySet().iterator(); iterator.hasNext(); ) {
                ObserverAwareness awareness = iterator.next().getValue();
                if (awareness.lastCheckedTick + STALE_OBSERVER_TICKS < tick) {
                    awareness.progress -= 0.08F;
                } else if (awareness.lastSensedTick + UPDATE_INTERVAL_TICKS < tick) {
                    awareness.progress -= 0.04F;
                }

                if (awareness.progress <= 0.0F) {
                    iterator.remove();
                } else {
                    awareness.progress = Mth.clamp(awareness.progress, 0.0F, 1.0F);
                }
            }
        }

        private void sync(ServerPlayer player, int tick, double maxDisplayDistanceSqr) {
            float highestProgress = 0.0F;
            int observerId = -1;
            double closestDetectedDistanceSqr = Double.MAX_VALUE;
            List<Integer> detectedObserverIds = new ArrayList<>();
            for (Map.Entry<Integer, ObserverAwareness> entry : observers.entrySet()) {
                ObserverAwareness awareness = entry.getValue();
                if (awareness.lastCheckedTick == tick && awareness.isDetected()) {
                    detectedObserverIds.add(entry.getKey());
                    closestDetectedDistanceSqr = Math.min(closestDetectedDistanceSqr, awareness.lastDistanceSqr);
                }
                if (awareness.lastCheckedTick == tick
                        && awareness.lastDistanceSqr <= maxDisplayDistanceSqr
                        && awareness.progress > highestProgress) {
                    highestProgress = awareness.progress;
                    observerId = entry.getKey();
                }
            }
            detectedObserverIds.sort(Integer::compareTo);

            SneakAwareness awareness = detectedObserverIds.isEmpty()
                    ? SneakAwareness.fromProgress(highestProgress)
                    : awarenessForDetectedDistance(closestDetectedDistanceSqr);
            highestProgress = Math.max(highestProgress, minimumProgressForAwareness(awareness));
            boolean shouldSync = awareness != lastSentAwareness
                    || observerId != lastSentObserverId
                    || !detectedObserverIds.equals(lastSentDetectedObserverIds)
                    || Math.abs(highestProgress - lastSentProgress) >= 0.04F
                    || tick - lastSyncTick >= FORCE_SYNC_TICKS;
            if (!shouldSync) {
                return;
            }

            lastSentAwareness = awareness;
            lastSentProgress = highestProgress;
            lastSentObserverId = observerId;
            lastSentDetectedObserverIds = List.copyOf(detectedObserverIds);
            lastSyncTick = tick;
            ModMessages.sendToPlayer(new ClientboundSneakAwarenessPacket(awareness, highestProgress, observerId,
                    detectedObserverIds), player);
        }

        private SneakAwareness awarenessForDetectedDistance(double distanceSqr) {
            if (distanceSqr <= DETECTED_DISPLAY_RANGE_SQR) {
                return SneakAwareness.DETECTED;
            }
            if (distanceSqr <= SEARCHING_DISPLAY_RANGE_SQR) {
                return SneakAwareness.SEARCHING;
            }
            return SneakAwareness.SUSPICIOUS;
        }

        private SneakAwareness minimumProgressForAwareness(SneakAwareness awareness) {
            return switch (awareness) {
                case DETECTED -> SneakAwareness.DETECTED_PROGRESS;
                case SEARCHING -> SneakAwareness.SEARCHING_PROGRESS;
                case SUSPICIOUS -> SneakAwareness.SUSPICIOUS_PROGRESS;
                case DISABLED, HIDDEN -> 0.0F;
            };
        }

        private void clear(ServerPlayer player) {
            observers.clear();
            recentNoise = 0.0F;
            if (lastSentAwareness != SneakAwareness.HIDDEN || lastSentProgress != 0.0F || lastSentObserverId != -1) {
                lastSentAwareness = SneakAwareness.HIDDEN;
                lastSentProgress = 0.0F;
                lastSentObserverId = -1;
                lastSentDetectedObserverIds = List.of();
                lastSyncTick = player.tickCount;
                ModMessages.sendToPlayer(new ClientboundSneakAwarenessPacket(SneakAwareness.HIDDEN, 0.0F, -1), player);
            }
        }
    }

    private static final class ObserverAwareness {
        private float progress;
        private int lastCheckedTick;
        private int lastSensedTick;
        private double lastDistanceSqr = Double.MAX_VALUE;

        private boolean hasBeenChecked() {
            return lastCheckedTick > 0;
        }

        private void advance(float amount) {
            progress = Mth.clamp(progress + amount, 0.0F, 1.0F);
        }

        private void detectImmediately() {
            progress = 1.0F;
        }

        private boolean isDetected() {
            return progress >= SneakAwareness.DETECTED_PROGRESS;
        }
    }

    private enum SneakAttackType {
        NONE,
        MELEE,
        RANGED
    }
}

package net.apotheoticstudios.client;

import net.apotheoticstudios.stealth.SneakAwareness;
import net.minecraft.Util;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientSneakAwarenessState {
    private static final long STALE_AFTER_MILLIS = 4000L;

    private static SneakAwareness awareness = SneakAwareness.HIDDEN;
    private static float progress;
    private static int observerId = -1;
    private static Set<Integer> detectedObserverIds = Set.of();
    private static long lastUpdateMillis;

    private ClientSneakAwarenessState() {
    }

    public static void update(SneakAwareness newAwareness, float newProgress, int newObserverId) {
        update(newAwareness, newProgress, newObserverId, List.of());
    }

    public static void update(SneakAwareness newAwareness, float newProgress, int newObserverId,
                              List<Integer> newDetectedObserverIds) {
        awareness = newAwareness == null ? SneakAwareness.HIDDEN : newAwareness;
        if (awareness == SneakAwareness.HIDDEN || awareness == SneakAwareness.DISABLED) {
            progress = 0.0F;
            observerId = -1;
            detectedObserverIds = Set.of();
        } else {
            progress = Mth.clamp(newProgress, 0.0F, 1.0F);
            observerId = newObserverId;
            detectedObserverIds = new HashSet<>(newDetectedObserverIds);
        }
        lastUpdateMillis = Util.getMillis();
    }

    public static SneakAwareness awareness() {
        if (isStale()) {
            clearStaleState();
            return SneakAwareness.HIDDEN;
        }
        return awareness;
    }

    public static float progress() {
        SneakAwareness currentAwareness = awareness();
        return currentAwareness == SneakAwareness.HIDDEN || currentAwareness == SneakAwareness.DISABLED ? 0.0F : progress;
    }

    public static int observerId() {
        SneakAwareness currentAwareness = awareness();
        return currentAwareness == SneakAwareness.HIDDEN || currentAwareness == SneakAwareness.DISABLED ? -1 : observerId;
    }

    public static boolean isDetectedObserver(int entityId) {
        SneakAwareness currentAwareness = awareness();
        return currentAwareness != SneakAwareness.HIDDEN
                && currentAwareness != SneakAwareness.DISABLED
                && detectedObserverIds.contains(entityId);
    }

    private static boolean isStale() {
        return Util.getMillis() - lastUpdateMillis > STALE_AFTER_MILLIS;
    }

    private static void clearStaleState() {
        awareness = SneakAwareness.HIDDEN;
        progress = 0.0F;
        observerId = -1;
        detectedObserverIds = Set.of();
    }
}

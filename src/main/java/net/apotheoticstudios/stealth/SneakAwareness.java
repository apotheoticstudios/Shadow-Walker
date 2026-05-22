package net.apotheoticstudios.stealth;

public enum SneakAwareness {
    DISABLED,
    HIDDEN,
    SUSPICIOUS,
    SEARCHING,
    DETECTED;

    public static final float SUSPICIOUS_PROGRESS = 0.18F;
    public static final float SEARCHING_PROGRESS = 0.55F;
    public static final float DETECTED_PROGRESS = 0.85F;

    public static SneakAwareness fromProgress(float progress) {
        if (progress >= DETECTED_PROGRESS) {
            return DETECTED;
        }
        if (progress >= SEARCHING_PROGRESS) {
            return SEARCHING;
        }
        if (progress >= SUSPICIOUS_PROGRESS) {
            return SUSPICIOUS;
        }
        return HIDDEN;
    }
}

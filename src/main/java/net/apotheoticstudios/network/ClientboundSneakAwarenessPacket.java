package net.apotheoticstudios.network;

import net.apotheoticstudios.client.ClientSneakAwarenessState;
import net.apotheoticstudios.stealth.SneakAwareness;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ClientboundSneakAwarenessPacket {
    private final SneakAwareness awareness;
    private final float progress;
    private final int observerId;
    private final List<Integer> detectedObserverIds;

    public ClientboundSneakAwarenessPacket(SneakAwareness awareness, float progress, int observerId) {
        this(awareness, progress, observerId, List.of());
    }

    public ClientboundSneakAwarenessPacket(SneakAwareness awareness, float progress, int observerId,
                                           List<Integer> detectedObserverIds) {
        this.awareness = awareness == null ? SneakAwareness.HIDDEN : awareness;
        if (this.awareness == SneakAwareness.HIDDEN || this.awareness == SneakAwareness.DISABLED) {
            this.progress = 0.0F;
            this.observerId = -1;
            this.detectedObserverIds = List.of();
        } else {
            this.progress = Mth.clamp(progress, 0.0F, 1.0F);
            this.observerId = observerId;
            this.detectedObserverIds = List.copyOf(detectedObserverIds);
        }
    }

    public ClientboundSneakAwarenessPacket(FriendlyByteBuf buffer) {
        this(buffer.readEnum(SneakAwareness.class), buffer.readFloat(), buffer.readVarInt(), readObserverIds(buffer));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(awareness);
        buffer.writeFloat(progress);
        buffer.writeVarInt(observerId);
        buffer.writeVarInt(detectedObserverIds.size());
        for (int detectedObserverId : detectedObserverIds) {
            buffer.writeVarInt(detectedObserverId);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientSneakAwarenessState.update(awareness, progress, observerId, detectedObserverIds));
        context.setPacketHandled(true);
    }

    private static List<Integer> readObserverIds(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Integer> observerIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            observerIds.add(buffer.readVarInt());
        }
        return observerIds;
    }
}

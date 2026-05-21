package net.apotheoticstudios.shadowwalker.network;

import net.apotheoticstudios.shadowwalker.client.ClientSneakAwarenessState;
import net.apotheoticstudios.shadowwalker.stealth.SneakAwareness;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ClientboundSneakAwarenessPacket {
    private final SneakAwareness awareness;
    private final float progress;
    private final int observerId;

    public ClientboundSneakAwarenessPacket(SneakAwareness awareness, float progress, int observerId) {
        this.awareness = awareness == null ? SneakAwareness.HIDDEN : awareness;
        if (this.awareness == SneakAwareness.HIDDEN || this.awareness == SneakAwareness.DISABLED) {
            this.progress = 0.0F;
            this.observerId = -1;
        } else {
            this.progress = Mth.clamp(progress, 0.0F, 1.0F);
            this.observerId = observerId;
        }
    }

    public ClientboundSneakAwarenessPacket(FriendlyByteBuf buffer) {
        this(buffer.readEnum(SneakAwareness.class), buffer.readFloat(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(awareness);
        buffer.writeFloat(progress);
        buffer.writeVarInt(observerId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientSneakAwarenessState.update(awareness, progress, observerId));
        context.setPacketHandled(true);
    }
}

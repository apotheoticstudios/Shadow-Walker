package net.apotheoticstudios.shadowwalker.network;

import net.apotheoticstudios.shadowwalker.ShadowWalker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModMessages {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId;
    private static SimpleChannel channel;

    private ModMessages() {
    }

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(ShadowWalker.MOD_ID, "messages"),
                () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

        channel.messageBuilder(ClientboundSneakAwarenessPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundSneakAwarenessPacket::encode)
                .decoder(ClientboundSneakAwarenessPacket::new)
                .consumerMainThread(ClientboundSneakAwarenessPacket::handle)
                .add();
    }

    public static void sendToPlayer(ClientboundSneakAwarenessPacket message, ServerPlayer player) {
        if (channel != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    private static int nextPacketId() {
        return packetId++;
    }
}

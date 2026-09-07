package sk.totalnavojna.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.client.ClientConfigOpener;

import java.util.function.Supplier;

public class PacketOpenConfig {

    public PacketOpenConfig() {
    }

    public static void encode(PacketOpenConfig msg, FriendlyByteBuf buf) {
    }

    public static PacketOpenConfig decode(FriendlyByteBuf buf) {
        return new PacketOpenConfig();
    }

    public static void handle(PacketOpenConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientConfigOpener::open));
        ctx.get().setPacketHandled(true);
    }
}

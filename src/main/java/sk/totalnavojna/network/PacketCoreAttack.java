package sk.totalnavojna.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.war.WarEvents;

import java.util.function.Supplier;

// C2S heartbeat: "I am holding LMB on this enemy NEXUS block" - sent every tick while mining it.
public class PacketCoreAttack {
    private final BlockPos pos;

    public PacketCoreAttack(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketCoreAttack msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketCoreAttack decode(FriendlyByteBuf buf) {
        return new PacketCoreAttack(buf.readBlockPos());
    }

    public static void handle(PacketCoreAttack msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            WarEvents.handleCoreAttack(sender, msg.pos);
        });
        ctx.get().setPacketHandled(true);
    }
}

package sk.totalnavojna.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.client.ClientDownedState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

// S2C (broadcast): the full set of currently downed players. Clients lock input for themselves and force the lying pose for everyone in the set.
public class PacketSyncDowned {
    private final List<UUID> downed;

    public PacketSyncDowned(Collection<UUID> downed) {
        this.downed = new ArrayList<>(downed);
    }

    public static void encode(PacketSyncDowned msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.downed.size());
        for (UUID id : msg.downed) buf.writeUUID(id);
    }

    public static PacketSyncDowned decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<UUID> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUUID());
        return new PacketSyncDowned(list);
    }

    public static void handle(PacketSyncDowned msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientDownedState.set(msg.downed));
        ctx.get().setPacketHandled(true);
    }
}

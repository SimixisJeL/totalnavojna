package sk.totalnavojna.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.entities.SwatEntity;

import java.util.function.Supplier;

public class PacketAssignGroup {

    private final int entityId;
    private final int group;

    public PacketAssignGroup(int entityId, int group) {
        this.entityId = entityId;
        this.group = group;
    }

    public static void encode(PacketAssignGroup msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.group);
    }

    public static PacketAssignGroup decode(FriendlyByteBuf buf) {
        return new PacketAssignGroup(buf.readInt(), buf.readInt());
    }

    public static void handle(PacketAssignGroup msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity target = sender.level().getEntity(msg.entityId);
            if (!(target instanceof SwatEntity soldier)) return;
            if (!soldier.isCommandedBy(sender)) return;

            soldier.setGroup(Math.max(0, Math.min(sk.totalnavojna.client.ClientWarState.MAX_GROUP, msg.group)));
        });
        ctx.get().setPacketHandled(true);
    }
}

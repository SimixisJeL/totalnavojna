package sk.totalnavojna.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.Team;
import sk.totalnavojna.client.ClientTeamCache;

import java.util.function.Supplier;

public class PacketSyncTeam {

    private final String teamName;

    public PacketSyncTeam(String teamName) {
        this.teamName = teamName;
    }

    public static PacketSyncTeam of(Team team) {
        return new PacketSyncTeam(team == null ? "" : team.getName());
    }

    public static void encode(PacketSyncTeam msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.teamName);
    }

    public static PacketSyncTeam decode(FriendlyByteBuf buf) {
        return new PacketSyncTeam(buf.readUtf());
    }

    public static void handle(PacketSyncTeam msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTeamCache.set(Team.byName(msg.teamName)));
        ctx.get().setPacketHandled(true);
    }
}

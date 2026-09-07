package sk.totalnavojna.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.WarGameData;

import java.util.List;
import java.util.function.Supplier;

public class PacketRequestWarState {

    public PacketRequestWarState() {
    }

    public static void encode(PacketRequestWarState msg, FriendlyByteBuf buf) {
    }

    public static PacketRequestWarState decode(FriendlyByteBuf buf) {
        return new PacketRequestWarState();
    }

    public static void handle(PacketRequestWarState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            Team team = TeamsSavedData.get(level).getTeam(sender.getUUID());

            WarGameData data = WarGameData.get(level);
            String dim = level.dimension().location().toString();

            List<? extends SwatEntity> ownSoldiers = team == null ? List.<SwatEntity>of()
                    : level.getEntities(EntityTypeTest.forClass(SwatEntity.class),
                    (e) -> e.getArmyTeam() == team && e.getState() == SwatEntity.STATE_ALIVE);

            int prepLeft = data.getPhase() == sk.totalnavojna.war.MatchPhase.PREP
                    ? (int) Math.max(0, (data.phaseEndsAt - level.getGameTime()) / 20) : 0;
            ModNetworking.sendToPlayer(PacketSyncWarState.build(data, dim, team, ownSoldiers).withPhaseCountdown(prepLeft), sender);
        });
        ctx.get().setPacketHandled(true);
    }
}

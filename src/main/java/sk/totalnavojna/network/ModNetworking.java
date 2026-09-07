package sk.totalnavojna.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import sk.totalnavojna.TotalnaVojna;

public class ModNetworking {
    public static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TotalnaVojna.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        CHANNEL.registerMessage(id++, PacketIssueOrder.class, PacketIssueOrder::encode, PacketIssueOrder::decode, PacketIssueOrder::handle);
        CHANNEL.registerMessage(id++, PacketSyncTeam.class, PacketSyncTeam::encode, PacketSyncTeam::decode, PacketSyncTeam::handle);
        CHANNEL.registerMessage(id++, PacketAssignGroup.class, PacketAssignGroup::encode, PacketAssignGroup::decode, PacketAssignGroup::handle);
        CHANNEL.registerMessage(id++, PacketRequestWarState.class, PacketRequestWarState::encode, PacketRequestWarState::decode, PacketRequestWarState::handle);
        CHANNEL.registerMessage(id++, PacketSyncWarState.class, PacketSyncWarState::encode, PacketSyncWarState::decode, PacketSyncWarState::handle);
        CHANNEL.registerMessage(id++, PacketMapOrder.class, PacketMapOrder::encode, PacketMapOrder::decode, PacketMapOrder::handle);
        CHANNEL.registerMessage(id++, PacketOpenConfig.class, PacketOpenConfig::encode, PacketOpenConfig::decode, PacketOpenConfig::handle);
        CHANNEL.registerMessage(id++, PacketReviveHold.class, PacketReviveHold::encode, PacketReviveHold::decode, PacketReviveHold::handle);
        CHANNEL.registerMessage(id++, PacketCoreAttack.class, PacketCoreAttack::encode, PacketCoreAttack::decode, PacketCoreAttack::handle);
        CHANNEL.registerMessage(id++, PacketSyncDowned.class, PacketSyncDowned::encode, PacketSyncDowned::decode, PacketSyncDowned::handle);
        CHANNEL.registerMessage(id++, PacketOpenRecruit.class, PacketOpenRecruit::encode, PacketOpenRecruit::decode, PacketOpenRecruit::handle);
        CHANNEL.registerMessage(id++, PacketRecruit.class, PacketRecruit::encode, PacketRecruit::decode, PacketRecruit::handle);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), packet);
    }
}

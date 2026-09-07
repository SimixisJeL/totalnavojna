package sk.totalnavojna.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.war.ReviveHelper;
import sk.totalnavojna.war.WarEvents;

import java.util.function.Supplier;

// C2S heartbeat: "I am holding LMB with a medkit on this downed unit" - sent every tick while the player keeps holding.
public class PacketReviveHold {
    private final int entityId;

    public PacketReviveHold(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(PacketReviveHold msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static PacketReviveHold decode(FriendlyByteBuf buf) {
        return new PacketReviveHold(buf.readVarInt());
    }

    public static void handle(PacketReviveHold msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || WarEvents.isDowned(sender)) return;
            Entity e = sender.level().getEntity(msg.entityId);
            if (!(e instanceof LivingEntity patient)) return;
            if (sender.distanceToSqr(patient) > 4.5 * 4.5) return;
            ReviveHelper.Channel ch = ReviveHelper.channelOf(patient);
            if (ch == null) return;
            long now = sender.serverLevel().getGameTime();
            ReviveHelper.Result result = ReviveHelper.channel(sender, patient, now);
            switch (result) {
                case BUSY -> sender.displayClientMessage(Component.literal("§7" + ch.getPatientName() + " už niekto oživuje."), true);
                case PROGRESS -> {
                    if (ch.getReviveTicks() % 5 == 0) {
                        sender.displayClientMessage(Component.literal("§a✚ Oživujem " + ch.getPatientName() + "… " + ReviveHelper.progressPercent(sender, patient) + "% §7(drž ľavé tlačidlo)"), true);
                    }
                }
                case DONE -> sender.displayClientMessage(Component.literal("§a✚ " + ch.getPatientName() + " oživený!"), true);
                case INVALID -> {
                    if (!ReviveHelper.hasMedkit(sender)) {
                        sender.displayClientMessage(Component.literal("§cNa oživenie potrebuješ medkit v ruke."), true);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

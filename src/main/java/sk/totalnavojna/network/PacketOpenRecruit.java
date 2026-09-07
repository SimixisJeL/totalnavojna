package sk.totalnavojna.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;

import java.util.function.Supplier;

// Server tells the client to open the recruitment menu for a particular SPAWNER block.
public class PacketOpenRecruit {

    public final BlockPos pos;
    public final Team team;
    // per role: how many are paid for and waiting, or -1 when nothing limits recruiting
    public final int[] available;

    public PacketOpenRecruit(BlockPos pos, Team team, int[] available) {
        this.pos = pos;
        this.team = team;
        this.available = available;
    }

    public static void encode(PacketOpenRecruit msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeByte(msg.team.ordinal());
        buf.writeByte(msg.available.length);
        for (int n : msg.available) buf.writeVarInt(n + 1);   // shift so -1 survives a VarInt
    }

    public static PacketOpenRecruit decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Team team = Team.VALUES[buf.readByte()];
        int n = buf.readByte();
        int[] available = new int[Role.VALUES.length];
        java.util.Arrays.fill(available, -1);
        for (int i = 0; i < n; i++) {
            int value = buf.readVarInt() - 1;
            if (i < available.length) available[i] = value;
        }
        return new PacketOpenRecruit(pos, team, available);
    }

    public static void handle(PacketOpenRecruit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> sk.totalnavojna.client.gui.RecruitScreen.open(msg.pos, msg.team, msg.available)));
        ctx.get().setPacketHandled(true);
    }
}

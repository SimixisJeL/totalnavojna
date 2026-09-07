package sk.totalnavojna.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.WarEvents;
import sk.totalnavojna.war.WarGameData;

import java.util.function.Supplier;

// "Recruit N soldiers of role R into group G at this SPAWNER."
// The soldiers come out of the block, get their kit from the usual spawn logic, and walk to their
// group's rally point if one is set.
public class PacketRecruit {

    private final BlockPos pos;
    private final int role;
    private final int group;
    private final int count;

    public PacketRecruit(BlockPos pos, int role, int group, int count) {
        this.pos = pos;
        this.role = role;
        this.group = group;
        this.count = count;
    }

    public static void encode(PacketRecruit msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeByte(msg.role);
        buf.writeByte(msg.group);
        buf.writeByte(msg.count);
    }

    public static PacketRecruit decode(FriendlyByteBuf buf) {
        return new PacketRecruit(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readByte());
    }

    public static void handle(PacketRecruit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            Team team = TeamsSavedData.get(level).getTeam(sender.getUUID());
            if (team == null) {
                sender.displayClientMessage(Component.literal("§cNie si v tíme."), true);
                return;
            }
            if (msg.pos.distSqr(sender.blockPosition()) > 64.0 * 64.0) return;

            WarGameData data = WarGameData.get(level);
            WarGameData.SpawnerEntry spawner = data.spawnerAt(level, msg.pos);
            if (spawner == null || spawner.team != team) {
                sender.displayClientMessage(Component.literal("§cToto nie je vaša základňa."), true);
                return;
            }
            if (data.isDefeated(team)) {
                sender.displayClientMessage(Component.literal("§cVáš tím už nemôže verbovať."), true);
                return;
            }

            Role role = msg.role >= 0 && msg.role < Role.VALUES.length ? Role.VALUES[msg.role] : Role.ATTACKER;
            int group = Math.max(0, Math.min(sk.totalnavojna.client.ClientWarState.MAX_GROUP, msg.group));
            int wanted = Math.max(1, Math.min(20, msg.count));

            int cap = CommonConfig.MAX_SOLDIERS_PER_TEAM.get();
            if (cap > 0) {
                int alive = level.getEntities(EntityTypeTest.forClass(SwatEntity.class),
                        (e) -> e.getArmyTeam() == team && e.getState() == SwatEntity.STATE_ALIVE).size();
                wanted = Math.min(wanted, Math.max(0, cap - alive));
                if (wanted <= 0) {
                    sender.displayClientMessage(Component.literal("§cDosiahnutý limit vojakov na tím (" + cap + ")."), true);
                    return;
                }
            }

            // With WarEconomy running, the Spawner only hands out men the commander already bought.
            int allowed = sk.totalnavojna.api.RecruitmentGate.claim(level, team, role, wanted);
            if (allowed <= 0) {
                sender.displayClientMessage(Component.literal("§cTáto rola nie je objednaná. Kúp ju na Komunikačnej stanici."), true);
                return;
            }
            wanted = allowed;

            int made = 0;
            for (int i = 0; i < wanted; i++) {
                SwatEntity soldier = SwatEntity.withTeam(level, team);
                soldier.setRole(role);
                soldier.setGroup(group);
                BlockPos at = WarEvents.findScatterPos(level, msg.pos);
                soldier.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(soldier, level,
                        level.getCurrentDifficultyAt(msg.pos), MobSpawnType.MOB_SUMMONED, null, null);
                if (!level.addFreshEntity(soldier)) continue;
                WarEvents.applyRally(level, data, soldier);
                made++;
            }
            if (made < wanted) {
                sk.totalnavojna.api.RecruitmentGate.refund(level, team, role, wanted - made);
            }
            final int n = made;
            sender.displayClientMessage(Component.literal("§aNaverbované: §f" + n + "× "
                    + role.getName().toUpperCase() + "§a, skupina §f" + (group == 0 ? "—" : group)), true);
            if (n > 0) {
                sk.totalnavojna.war.BattleLog.add(level.getServer(), team,
                        "§a+ " + n + "× " + role.getName().toUpperCase() + " naverbovaných");
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

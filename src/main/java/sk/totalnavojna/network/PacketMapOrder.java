package sk.totalnavojna.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;
import sk.totalnavojna.war.WarGameData;

import java.util.*;
import java.util.function.Supplier;


// Map-issued command: applies to all own soldiers of a group (-1 = all groups).
public class PacketMapOrder {

    public static final int MODE_MOVE = 0;
    public static final int MODE_PATH = 1;
    public static final int MODE_AREA_SET = 2;
    public static final int MODE_AREA_CLEAR = 3;
    public static final int MODE_DRONE_STRIKE = 4;
    public static final int MODE_DRONE_SCOUT = 5;
    public static final int MODE_UNLOAD = 6;
    public static final int MODE_RALLY_SET = 7;
    public static final int MODE_RALLY_CLEAR = 8;

    private final int mode;
    private final int group;
    private final List<BlockPos> points = new ArrayList<>();
    private final Set<Long> chunks = new HashSet<>();
    // when non-empty the order goes to exactly these entity ids (a box selection on the map),
    // and the group filter is ignored
    private final List<Integer> ids = new ArrayList<>();

    public PacketMapOrder(int mode, int group, List<BlockPos> points, Set<Long> chunks) {
        this(mode, group, points, chunks, null);
    }

    public PacketMapOrder(int mode, int group, List<BlockPos> points, Set<Long> chunks, Collection<Integer> ids) {
        this.mode = mode;
        this.group = group;
        if (points != null) this.points.addAll(points);
        if (chunks != null) this.chunks.addAll(chunks);
        if (ids != null) this.ids.addAll(ids);
    }

    public static void encode(PacketMapOrder msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.mode);
        buf.writeByte(msg.group);
        buf.writeVarInt(msg.points.size());
        for (BlockPos p : msg.points) buf.writeBlockPos(p);
        buf.writeVarInt(msg.chunks.size());
        for (long c : msg.chunks) buf.writeLong(c);
        buf.writeVarInt(msg.ids.size());
        for (int id : msg.ids) buf.writeVarInt(id);
    }

    public static PacketMapOrder decode(FriendlyByteBuf buf) {
        int mode = buf.readByte();
        int group = buf.readByte();
        int nPoints = buf.readVarInt();
        List<BlockPos> points = new ArrayList<>();
        for (int i = 0; i < nPoints; i++) points.add(buf.readBlockPos());
        int nChunks = buf.readVarInt();
        Set<Long> chunks = new HashSet<>();
        for (int i = 0; i < nChunks; i++) chunks.add(buf.readLong());
        int nIds = buf.readVarInt();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < nIds; i++) ids.add(buf.readVarInt());
        return new PacketMapOrder(mode, group, points, chunks, ids);
    }

    public static void handle(PacketMapOrder msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            Team team = TeamsSavedData.get(level).getTeam(sender.getUUID());
            if (team == null) return;

            WarGameData data = WarGameData.get(level);

            if (msg.mode == MODE_AREA_SET) {
                data.setGroupArea(team, Math.max(0, Math.min(sk.totalnavojna.client.ClientWarState.MAX_GROUP, msg.group)), msg.chunks);
                sender.displayClientMessage(Component.literal("§aMantinel uložený (" + msg.chunks.size() + " chunkov)."), true);
                return;
            }
            if (msg.mode == MODE_AREA_CLEAR) {
                data.setGroupArea(team, Math.max(0, Math.min(sk.totalnavojna.client.ClientWarState.MAX_GROUP, msg.group)), null);
                sender.displayClientMessage(Component.literal("§eMantinel zrušený."), true);
                return;
            }

            if (msg.mode == MODE_RALLY_SET || msg.mode == MODE_RALLY_CLEAR) {
                int grp = Math.max(0, Math.min(sk.totalnavojna.client.ClientWarState.MAX_GROUP, msg.group));
                if (msg.mode == MODE_RALLY_CLEAR || msg.points.isEmpty()) {
                    data.setRally(team, grp, null);
                    sender.displayClientMessage(Component.literal("§eZhromaždisko skupiny " + grp + " zrušené."), true);
                } else {
                    BlockPos rally = surface(level, msg.points.get(0));
                    data.setRally(team, grp, rally);
                    sender.displayClientMessage(Component.literal("§aZhromaždisko skupiny " + grp
                            + " nastavené na " + rally.getX() + ", " + rally.getZ() + ". Noví a oživení vojaci pôjdu sem."), true);
                }
                return;
            }

            java.util.Set<Integer> wanted = new java.util.HashSet<>(msg.ids);
            List<? extends SwatEntity> soldiers = level.getEntities(EntityTypeTest.forClass(SwatEntity.class),
                    (e) -> e.getArmyTeam() == team
                            && e.getState() == SwatEntity.STATE_ALIVE
                            && (wanted.isEmpty()
                                ? (msg.group < 0 || e.getGroup() == msg.group)
                                : wanted.contains(e.getId())));
            if (soldiers.isEmpty()) {
                sender.displayClientMessage(Component.literal("§cŽiadni vojaci v tejto skupine."), true);
                return;
            }

            if ((msg.mode == MODE_DRONE_STRIKE || msg.mode == MODE_DRONE_SCOUT) && !msg.points.isEmpty()) {
                BlockPos clicked = surface(level, msg.points.get(0));
                int n = 0;
                for (SwatEntity soldier : soldiers) {
                    if (soldier.getRole() != sk.totalnavojna.Role.DRONE_OPERATOR) continue;
                    soldier.setCommanderUUID(sender.getUUID());
                    soldier.setMoveToTarget(net.minecraft.world.phys.Vec3.atBottomCenterOf(clicked));
                    soldier.setOrder(msg.mode == MODE_DRONE_STRIKE ? OrderType.DRONE_STRIKE_POSITION : OrderType.DRONE_SCOUT);
                    n++;
                }
                sender.displayClientMessage(Component.literal(n == 0 ? "§cV tejto skupine nie je žiadny drone-operátor."
                        : (msg.mode == MODE_DRONE_STRIKE ? "§aDron: nálet na pozíciu (" : "§aDron: prieskum pozície (") + n + " operátorov)."), true);
                return;
            }
            if (msg.mode == MODE_UNLOAD && !msg.points.isEmpty()) {
                BlockPos clicked = surface(level, msg.points.get(0));
                int drivers = 0;
                int i = 0;
                for (SwatEntity soldier : soldiers) {
                    soldier.setCommanderUUID(sender.getUUID());
                    boolean driver = soldier.getVehicle() instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v && v.getFirstPassenger() == soldier;
                    if (driver) {
                        soldier.setMoveToTarget(net.minecraft.world.phys.Vec3.atBottomCenterOf(clicked));
                        soldier.setOrder(OrderType.MOVE_AND_UNLOAD);
                        drivers++;
                    } else if (!soldier.isPassenger()) {
                        int dx = (i % 5) - 2;
                        int dz = (i / 5) % 5 - 2;
                        soldier.setMoveToTarget(net.minecraft.world.phys.Vec3.atBottomCenterOf(surface(level, clicked.offset(dx * 2, 0, dz * 2))));
                        soldier.setOrder(OrderType.MOVE_TO_POSITION);
                        soldier.markOrderFocus();
                        i++;
                    }
                }
                sender.displayClientMessage(Component.literal("§aVozidlá: presun + vysadenie (" + drivers + " šoférov)."), true);
                return;
            }
            if (msg.mode == MODE_MOVE && !msg.points.isEmpty()) {
                BlockPos clicked = msg.points.get(0);
                int i = 0;
                for (SwatEntity soldier : soldiers) {
                    int dx = (i % 5) - 2;
                    int dz = (i / 5) % 5 - 2;
                    BlockPos target = surface(level, clicked.offset(dx * 2, 0, dz * 2));
                    soldier.setCommanderUUID(sender.getUUID());
                    soldier.setFormationIndex(i++);
                    soldier.setMoveToTarget(net.minecraft.world.phys.Vec3.atBottomCenterOf(target));
                    soldier.setOrder(OrderType.MOVE_TO_POSITION);
                    soldier.setTarget(null);
                    soldier.setLastHurtByMob(null);
                    soldier.markOrderFocus();
                    soldier.resetCommanderGoalCooldown();
                }
                sender.displayClientMessage(Component.literal("§aPresun z mapy: " + soldiers.size() + " vojakov."), true);
            } else if (msg.mode == MODE_PATH && !msg.points.isEmpty()) {
                List<BlockPos> path = new ArrayList<>();
                for (BlockPos p : msg.points) path.add(surface(level, p));
                for (SwatEntity soldier : soldiers) {
                    soldier.setCommanderUUID(sender.getUUID());
                    soldier.setPathWaypoints(path);
                    soldier.setTarget(null);
                    soldier.setLastHurtByMob(null);
                    soldier.markOrderFocus();
                }
                sender.displayClientMessage(Component.literal("§aTrasa (" + path.size() + " bodov) vyslaná " + soldiers.size() + " vojakom."), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static BlockPos surface(ServerLevel level, BlockPos pos) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }
}

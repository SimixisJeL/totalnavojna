package sk.totalnavojna.network;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;
import sk.totalnavojna.client.ClientWarState;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.WarGameData;

import java.util.*;
import java.util.function.Supplier;

public class PacketSyncWarState {

    private final List<ClientWarState.MapCore> cores = new ArrayList<>();
    private final List<ClientWarState.MapSpawner> spawners = new ArrayList<>();
    private final List<ClientWarState.MapSoldier> soldiers = new ArrayList<>();
    private final Map<Integer, Set<Long>> areas = new HashMap<>();
    private final Map<Team, Integer> deaths = new EnumMap<>(Team.class);
    private final List<double[]> spotted = new ArrayList<>();
    private final List<ClientWarState.MapCapture> captures = new ArrayList<>();
    private final Map<Team, Integer> tickets = new EnumMap<>(Team.class);
    private final List<String> fallen = new ArrayList<>();
    private int phase;
    private int phaseSecondsLeft;
    private final Map<Team, String> teamNames = new EnumMap<>(Team.class);

    public static PacketSyncWarState build(WarGameData data, String dim, @Nullable Team team, List<? extends SwatEntity> ownSoldiers) {
        PacketSyncWarState p = new PacketSyncWarState();
        for (WarGameData.CoreEntry c : data.cores) {
            if (!c.dim.equals(dim)) continue;
            ClientWarState.MapCore mc = new ClientWarState.MapCore();
            mc.pos = c.pos;
            mc.team = c.team;
            mc.lives = c.lives;
            p.cores.add(mc);
        }
        for (WarGameData.SpawnerEntry s : data.spawners) {
            if (!s.dim.equals(dim)) continue;
            ClientWarState.MapSpawner ms = new ClientWarState.MapSpawner();
            ms.pos = s.pos;
            ms.team = s.team;
            ms.lives = s.lives;
            p.spawners.add(ms);
        }
        for (WarGameData.CaptureEntry c : data.captures) {
            if (!c.dim.equals(dim)) continue;
            ClientWarState.MapCapture mc = new ClientWarState.MapCapture();
            mc.pos = c.pos;
            mc.owner = c.owner;
            int captureTime = Math.max(1, sk.totalnavojna.config.CommonConfig.CAPTURE_TIME.get());
            mc.progressPct = Math.max(0, Math.min(100, c.progress * 100 / captureTime));
            mc.contested = c.contested;
            p.captures.add(mc);
        }
        for (SwatEntity soldier : ownSoldiers) {
            ClientWarState.MapSoldier m = new ClientWarState.MapSoldier();
            m.id = soldier.getId();
            m.x = soldier.getX();
            m.y = soldier.getY();
            m.z = soldier.getZ();
            m.group = soldier.getGroup();
            m.role = soldier.getRole().ordinal();
            m.hp = soldier.getHealth();
            m.maxHp = soldier.getMaxHealth();
            m.order = soldier.getOrder().ordinal();
            m.state = soldier.getState();
            m.inVehicle = soldier.getVehicle() instanceof VehicleEntity;
            m.flyingDrone = soldier.hasDroneAloft();
            m.leader = sk.totalnavojna.war.SquadTactics.isLeader(soldier);
            m.name = soldier.hasCustomName() ? soldier.getCustomName().getString() : "Vojak";
            p.soldiers.add(m);
        }
        if (team != null) {
            for (int g = 0; g <= ClientWarState.MAX_GROUP; g++) {
                Set<Long> area = data.getGroupArea(team, g);
                if (!area.isEmpty()) p.areas.put(g, area);
            }
        }
        for (Team t : Team.VALUES) {
            p.deaths.put(t, data.getTeamDeaths(t));
        }
        if (team != null) {
            p.spotted.addAll(data.getSpotted(team));
            p.fallen.addAll(data.fallenOf(team));
        }
        for (Team t : Team.VALUES) {
            p.tickets.put(t, data.getTickets(t));
        }
        for (Team t : Team.VALUES) {
            p.teamNames.put(t, t.getDisplayName());
        }
        p.phase = data.getPhase().ordinal();
        p.phaseSecondsLeft = data.getPhase() == sk.totalnavojna.war.MatchPhase.PREP ? -1 : 0;
        return p;
    }

    // The PREP countdown needs the level clock, which build() does not have - the caller fills it in.
    public PacketSyncWarState withPhaseCountdown(int secondsLeft) {
        this.phaseSecondsLeft = secondsLeft;
        return this;
    }

    public static void encode(PacketSyncWarState msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.cores.size());
        for (ClientWarState.MapCore c : msg.cores) {
            buf.writeBlockPos(c.pos);
            buf.writeByte(c.team.ordinal());
            buf.writeVarInt(c.lives);
        }
        buf.writeVarInt(msg.spawners.size());
        for (ClientWarState.MapSpawner s : msg.spawners) {
            buf.writeBlockPos(s.pos);
            buf.writeByte(s.team.ordinal());
            buf.writeVarInt(s.lives);
        }
        buf.writeVarInt(msg.soldiers.size());
        for (ClientWarState.MapSoldier m : msg.soldiers) {
            buf.writeVarInt(m.id);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeByte(m.group);
            buf.writeByte(m.role);
            buf.writeFloat(m.hp);
            buf.writeFloat(m.maxHp);
            buf.writeByte(m.order);
            buf.writeByte(m.state);
            buf.writeByte((m.inVehicle ? 1 : 0) | (m.flyingDrone ? 2 : 0) | (m.leader ? 4 : 0));
            buf.writeUtf(m.name, 48);
        }
        buf.writeVarInt(msg.areas.size());
        for (Map.Entry<Integer, Set<Long>> e : msg.areas.entrySet()) {
            buf.writeByte(e.getKey());
            buf.writeVarInt(e.getValue().size());
            for (long chunk : e.getValue()) buf.writeLong(chunk);
        }
        for (Team t : Team.VALUES) {
            buf.writeVarInt(msg.deaths.getOrDefault(t, 0));
        }
        buf.writeVarInt(msg.spotted.size());
        for (double[] s : msg.spotted) {
            buf.writeDouble(s[0]);
            buf.writeDouble(s[1]);
        }
        buf.writeVarInt(msg.captures.size());
        for (ClientWarState.MapCapture c : msg.captures) {
            buf.writeBlockPos(c.pos);
            buf.writeByte(c.owner == null ? 0 : c.owner.ordinal() + 1);
            buf.writeByte(c.progressPct);
            buf.writeBoolean(c.contested);
        }
        for (Team t : Team.VALUES) {
            buf.writeVarInt(msg.tickets.getOrDefault(t, 0));
        }
        buf.writeVarInt(msg.fallen.size());
        for (String name : msg.fallen) buf.writeUtf(name, 48);
        buf.writeByte(msg.phase);
        buf.writeVarInt(msg.phaseSecondsLeft);
        for (Team t : Team.VALUES) {
            buf.writeUtf(msg.teamNames.getOrDefault(t, sk.totalnavojna.TeamNames.fallback(t)), 32);
        }
    }

    public static PacketSyncWarState decode(FriendlyByteBuf buf) {
        PacketSyncWarState msg = new PacketSyncWarState();
        int nCores = buf.readVarInt();
        for (int i = 0; i < nCores; i++) {
            ClientWarState.MapCore c = new ClientWarState.MapCore();
            c.pos = buf.readBlockPos();
            c.team = Team.VALUES[buf.readByte()];
            c.lives = buf.readVarInt();
            msg.cores.add(c);
        }
        int nSpawners = buf.readVarInt();
        for (int i = 0; i < nSpawners; i++) {
            ClientWarState.MapSpawner s = new ClientWarState.MapSpawner();
            s.pos = buf.readBlockPos();
            s.team = Team.VALUES[buf.readByte()];
            s.lives = buf.readVarInt();
            msg.spawners.add(s);
        }
        int nSoldiers = buf.readVarInt();
        for (int i = 0; i < nSoldiers; i++) {
            ClientWarState.MapSoldier m = new ClientWarState.MapSoldier();
            m.id = buf.readVarInt();
            m.x = buf.readDouble();
            m.y = buf.readDouble();
            m.z = buf.readDouble();
            m.group = buf.readByte();
            m.role = buf.readByte();
            m.hp = buf.readFloat();
            m.maxHp = buf.readFloat();
            m.order = buf.readByte();
            m.state = buf.readByte();
            int flags = buf.readByte();
            m.inVehicle = (flags & 1) != 0;
            m.flyingDrone = (flags & 2) != 0;
            m.leader = (flags & 4) != 0;
            m.name = buf.readUtf(48);
            msg.soldiers.add(m);
        }
        int nAreas = buf.readVarInt();
        for (int i = 0; i < nAreas; i++) {
            int group = buf.readByte();
            int count = buf.readVarInt();
            Set<Long> set = new HashSet<>();
            for (int j = 0; j < count; j++) set.add(buf.readLong());
            msg.areas.put(group, set);
        }
        for (Team t : Team.VALUES) {
            msg.deaths.put(t, buf.readVarInt());
        }
        int nSpotted = buf.readVarInt();
        for (int i = 0; i < nSpotted; i++) {
            msg.spotted.add(new double[]{buf.readDouble(), buf.readDouble()});
        }
        int nCaptures = buf.readVarInt();
        for (int i = 0; i < nCaptures; i++) {
            ClientWarState.MapCapture c = new ClientWarState.MapCapture();
            c.pos = buf.readBlockPos();
            int owner = buf.readByte();
            c.owner = owner == 0 ? null : Team.VALUES[owner - 1];
            c.progressPct = buf.readByte();
            c.contested = buf.readBoolean();
            msg.captures.add(c);
        }
        for (Team t : Team.VALUES) {
            msg.tickets.put(t, buf.readVarInt());
        }
        int nFallen = buf.readVarInt();
        for (int i = 0; i < nFallen; i++) msg.fallen.add(buf.readUtf(48));
        msg.phase = buf.readByte();
        msg.phaseSecondsLeft = buf.readVarInt();
        for (Team t : Team.VALUES) {
            msg.teamNames.put(t, buf.readUtf(32));
        }
        return msg;
    }

    public static void handle(PacketSyncWarState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientWarState.clear();
            ClientWarState.CORES.addAll(msg.cores);
            ClientWarState.SPAWNERS.addAll(msg.spawners);
            ClientWarState.SOLDIERS.addAll(msg.soldiers);
            ClientWarState.MY_AREAS.putAll(msg.areas);
            ClientWarState.DEATHS.putAll(msg.deaths);
            ClientWarState.SPOTTED.addAll(msg.spotted);
            ClientWarState.CAPTURES.addAll(msg.captures);
            ClientWarState.TICKETS.putAll(msg.tickets);
            ClientWarState.FALLEN.addAll(msg.fallen);
            msg.teamNames.forEach(sk.totalnavojna.TeamNames::setOverride);
            ClientWarState.phase = msg.phase;
            ClientWarState.phaseSecondsLeft = msg.phaseSecondsLeft;
            sk.totalnavojna.client.ClientSelection.retainAlive();
            ClientWarState.lastSync = System.currentTimeMillis();
        });
        ctx.get().setPacketHandled(true);
    }
}

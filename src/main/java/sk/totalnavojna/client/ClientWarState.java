package sk.totalnavojna.client;

import net.minecraft.core.BlockPos;
import sk.totalnavojna.Team;

import java.util.*;

// Client-side snapshot of the war state, filled by PacketSyncWarState.
// This is the whole army, not just what the client can see - the commander screen and the tactical map both read it,
// so you can review and command a unit that is a thousand blocks away.
public class ClientWarState {

    public static final int MAX_GROUP = 9;

    public static class MapCore {
        public BlockPos pos;
        public Team team;
        public int lives;
    }

    public static class MapSpawner {
        public BlockPos pos;
        public Team team;
        public int lives;
    }

    public static class MapCapture {
        public BlockPos pos;
        public Team owner;          // null = neutral
        public int progressPct;     // 0-100 towards the next owner
        public boolean contested;
    }

    public static class MapSoldier {
        public int id;
        public double x;
        public double y;
        public double z;
        public int group;
        public int role;
        public float hp;
        public float maxHp;
        public int order;
        public byte state;          // SwatEntity.STATE_*
        public boolean inVehicle;
        public boolean flyingDrone;
        public boolean leader;
        public String name = "";

        public boolean isDowned() {
            return this.state == 1;
        }

        public float healthFraction() {
            return this.maxHp <= 0 ? 0f : Math.max(0f, Math.min(1f, this.hp / this.maxHp));
        }
    }

    public static final List<MapCore> CORES = new ArrayList<>();
    public static final List<MapSpawner> SPAWNERS = new ArrayList<>();
    public static final List<MapSoldier> SOLDIERS = new ArrayList<>();
    public static final Map<Integer, Set<Long>> MY_AREAS = new HashMap<>();
    public static final Map<Team, Integer> DEATHS = new EnumMap<>(Team.class);
    // enemies spotted by our drones: {x, z}
    public static final List<double[]> SPOTTED = new ArrayList<>();
    public static final List<MapCapture> CAPTURES = new ArrayList<>();
    public static final Map<Team, Integer> TICKETS = new EnumMap<>(Team.class);
    // soldiers of my team who used up their lives - listed greyed out in the roster
    public static final List<String> FALLEN = new ArrayList<>();
    public static int phase = 0;
    public static int phaseSecondsLeft = 0;
    public static long lastSync = 0;

    public static void clear() {
        CORES.clear();
        SPAWNERS.clear();
        SOLDIERS.clear();
        MY_AREAS.clear();
        DEATHS.clear();
        SPOTTED.clear();
        CAPTURES.clear();
        TICKETS.clear();
        FALLEN.clear();
    }

    public static int points(Team team) {
        int n = 0;
        for (MapCapture c : CAPTURES) {
            if (c.owner == team) n++;
        }
        return n;
    }

    public static List<MapSoldier> group(int group) {
        List<MapSoldier> out = new ArrayList<>();
        for (MapSoldier s : SOLDIERS) {
            if (s.group == group) out.add(s);
        }
        return out;
    }
}

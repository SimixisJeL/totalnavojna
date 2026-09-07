package sk.totalnavojna.war;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;

import java.util.*;

// Shared battlefield picture per team ("what does the army know right now").
//
// Every soldier reports what he sees, a drone reports what it flies over, and a hit from an unseen shooter is
// reported as well. Everyone else then acts on that shared knowledge instead of only on what they personally see:
// a unit can hold fire on a threat it cannot see yet, a driver can avoid a known ambush, a drone operator can pick
// the target that matters instead of the closest one.
//
// Transient (never saved) - it is a picture of the last few seconds, not war state.
public final class BattleIntel {

    public static final int CONTACT_TTL = 300;      // 15 s
    private static final int PRUNE_INTERVAL = 40;

    public static class Contact {
        public final UUID id;
        public Vec3 pos;
        public long seenAt;
        public boolean vehicle;
        public int cluster = 1;      // how many enemies stood together when last seen
        public String name = "";

        Contact(UUID id, Vec3 pos, long seenAt, boolean vehicle) {
            this.id = id;
            this.pos = pos;
            this.seenAt = seenAt;
            this.vehicle = vehicle;
        }

        public double[] toMapPoint() {
            return new double[]{this.pos.x, this.pos.z};
        }
    }

    private static final Map<Team, Map<UUID, Contact>> CONTACTS = new EnumMap<>(Team.class);
    private static long lastPrune = 0;

    private BattleIntel() {
    }

    public static void clear() {
        CONTACTS.clear();
    }

    private static Map<UUID, Contact> map(Team team) {
        return CONTACTS.computeIfAbsent(team, t -> new HashMap<>());
    }

    // Someone on 'team' can see this enemy right now.
    public static void report(Team team, Entity enemy, long gameTime) {
        if (team == null || enemy == null) return;
        Map<UUID, Contact> m = map(team);
        Contact c = m.get(enemy.getUUID());
        boolean isVehicle = !(enemy instanceof LivingEntity);
        if (c == null) {
            c = new Contact(enemy.getUUID(), enemy.position(), gameTime, isVehicle);
            c.name = enemy.getName().getString();
            m.put(enemy.getUUID(), c);
        } else {
            c.pos = enemy.position();
            c.seenAt = gameTime;
            c.vehicle = isVehicle;
        }
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastPrune < PRUNE_INTERVAL) return;
        lastPrune = now;
        for (Map<UUID, Contact> m : CONTACTS.values()) {
            m.values().removeIf(c -> now - c.seenAt > CONTACT_TTL);
        }
        // recompute clusters so "a group of six" outranks "one guy" when picking targets
        for (Map<UUID, Contact> m : CONTACTS.values()) {
            for (Contact a : m.values()) {
                int n = 0;
                for (Contact b : m.values()) {
                    if (a != b && b.pos.distanceToSqr(a.pos) < 8.0 * 8.0) n++;
                }
                a.cluster = n + 1;
            }
        }
    }

    public static Collection<Contact> contacts(Team team) {
        Map<UUID, Contact> m = CONTACTS.get(team);
        return m == null ? List.of() : m.values();
    }

    public static List<double[]> mapPoints(Team team) {
        List<double[]> out = new ArrayList<>();
        for (Contact c : contacts(team)) out.add(c.toMapPoint());
        return out;
    }

    public static boolean isKnown(Team team, Entity enemy) {
        Map<UUID, Contact> m = CONTACTS.get(team);
        return m != null && m.containsKey(enemy.getUUID());
    }

    @Nullable
    public static Contact nearest(Team team, Vec3 from, double maxDist) {
        Contact best = null;
        double bestDist = maxDist * maxDist;
        for (Contact c : contacts(team)) {
            double d = c.pos.distanceToSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    // How hot is this spot? Sum of known enemies weighted by closeness - used to avoid parking a vehicle
    // or walking a squad into the middle of a known enemy position.
    public static double threatAt(Team team, Vec3 pos, double radius) {
        double threat = 0;
        double r2 = radius * radius;
        for (Contact c : contacts(team)) {
            double d = c.pos.distanceToSqr(pos);
            if (d > r2) continue;
            double w = 1.0 - Math.sqrt(d) / radius;
            threat += (c.vehicle ? 3.0 : 1.0) * w;
        }
        return threat;
    }

    // The most valuable thing to hit: vehicles first, then clusters, then singles - all discounted by distance.
    @Nullable
    public static Contact bestTarget(Team team, Vec3 from, double maxDist) {
        Contact best = null;
        double bestScore = Double.MAX_VALUE;
        for (Contact c : contacts(team)) {
            double d = Math.sqrt(c.pos.distanceToSqr(from));
            if (d > maxDist) continue;
            double weight = c.vehicle ? 0.35 : (c.cluster >= 3 ? 0.5 : 1.0);
            double score = Math.max(4.0, d) * weight;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }
}

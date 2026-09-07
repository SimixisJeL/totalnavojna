package sk.totalnavojna.war;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// Deconfliction between drone operators of the same team.
//
// Two operators used to pick the same target within a tick of each other and launch together; the first warhead then
// blew the second drone out of the air before it arrived, so one of the two was always wasted. Three rules fix that:
//   1. an operator CLAIMS the target it is flying at, and everybody else picks something else
//   2. a launch at a spot somebody is already attacking is delayed, so the runs arrive one after the other
//   3. a drone whose aim point was just hit by another blast breaks off, climbs and comes round again
public final class DroneOps {

    private static final long CLAIM_TTL = 400;      // 20 s without a refresh and the claim is free again
    private static final long BLAST_TTL = 60;       // 3 s of "do not fly into that fireball"
    private static final double SAME_AREA = 14.0;   // two targets this close count as the same objective
    private static final long STAGGER = 70;         // 3.5 s between runs on the same objective

    private static class Claim {
        UUID operator;
        Vec3 pos;
        long refreshed;
    }

    private static class Blast {
        Vec3 pos;
        long at;
        double radius;
    }

    private static final Map<String, Claim> CLAIMS = new HashMap<>();
    private static final java.util.List<Blast> BLASTS = new java.util.ArrayList<>();
    private static long lastLaunch = Long.MIN_VALUE;
    private static Vec3 lastLaunchPos = null;

    private DroneOps() {
    }

    public static void clear() {
        CLAIMS.clear();
        BLASTS.clear();
        lastLaunchPos = null;
    }

    private static void prune(long now) {
        CLAIMS.values().removeIf(c -> now - c.refreshed > CLAIM_TTL);
        BLASTS.removeIf(b -> now - b.at > BLAST_TTL);
    }

    // key for a target: an entity uuid, or a rounded block position for a ground strike
    public static String keyOf(@Nullable UUID entity, @Nullable Vec3 pos) {
        if (entity != null) return entity.toString();
        if (pos != null) return "pos:" + (int) pos.x + "," + (int) pos.y + "," + (int) pos.z;
        return "?";
    }

    public static boolean claimedByOther(String key, UUID operator, long now) {
        prune(now);
        Claim c = CLAIMS.get(key);
        return c != null && !c.operator.equals(operator);
    }

    // Is anybody else already working this general area? Used to skip a target rather than pile onto it.
    public static boolean areaTakenByOther(Vec3 pos, UUID operator, long now) {
        prune(now);
        for (Claim c : CLAIMS.values()) {
            if (c.operator.equals(operator)) continue;
            if (c.pos != null && c.pos.distanceToSqr(pos) < SAME_AREA * SAME_AREA) return true;
        }
        return false;
    }

    public static void claim(String key, UUID operator, Vec3 pos, long now) {
        Claim c = CLAIMS.computeIfAbsent(key, k -> new Claim());
        c.operator = operator;
        c.pos = pos;
        c.refreshed = now;
    }

    public static void releaseAllOf(UUID operator) {
        Iterator<Map.Entry<String, Claim>> it = CLAIMS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().operator.equals(operator)) it.remove();
        }
    }

    // Runs on the same objective are spaced out so the second drone is not caught in the first blast.
    public static boolean launchBlocked(Vec3 targetPos, long now) {
        if (lastLaunchPos == null) return false;
        if (now - lastLaunch >= STAGGER) return false;
        return lastLaunchPos.distanceToSqr(targetPos) < SAME_AREA * SAME_AREA;
    }

    public static void noteLaunch(Vec3 targetPos, long now) {
        lastLaunch = now;
        lastLaunchPos = targetPos;
    }

    public static void noteBlast(Vec3 pos, double radius, long now) {
        prune(now);
        Blast b = new Blast();
        b.pos = pos;
        b.at = now;
        b.radius = radius;
        BLASTS.add(b);
    }

    // Did something just explode where this drone is heading? Then break off for a moment.
    public static boolean blastAt(Vec3 pos, long now) {
        prune(now);
        for (Blast b : BLASTS) {
            double r = b.radius + 4.0;
            if (b.pos.distanceToSqr(pos) < r * r) return true;
        }
        return false;
    }
}

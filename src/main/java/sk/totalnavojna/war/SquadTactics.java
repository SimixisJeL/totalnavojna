package sk.totalnavojna.war;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Everything that treats a control group as one body rather than a bag of soldiers:
// who leads it, how badly it has been mauled, and whether it should stop walking into the same
// gun and try to come at the enemy from the side instead.
//
// All of this is derived state - it is rebuilt from the living soldiers every couple of seconds
// and never saved, so a restart just re-derives it.
public final class SquadTactics {

    private static final int FLANK_DURATION = 600;   // 30 s of going around, then re-evaluate
    private static final int FLANK_COOLDOWN = 400;

    private static class Squad {
        int peak;
        int leaderId = -1;
        long flankUntil;
        long flankReadyAt;
        int flankSide = 1;
        int alive;
    }

    private static final Map<String, Squad> SQUADS = new HashMap<>();

    private SquadTactics() {
    }

    private static String key(Team team, int group) {
        return team.getName() + "/" + group;
    }

    private static Squad squad(Team team, int group) {
        return SQUADS.computeIfAbsent(key(team, group), (k) -> new Squad());
    }

    public static void clear() {
        SQUADS.clear();
    }

    // Called a few times a second from MatchManager. Cheap: one entity sweep per level.
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, SwatEntity> bestLeader = new HashMap<>();
        Map<String, Boolean> inContact = new HashMap<>();

        List<? extends SwatEntity> all = level.getEntities(EntityTypeTest.forClass(SwatEntity.class),
                (e) -> e.getState() == SwatEntity.STATE_ALIVE);
        for (SwatEntity s : all) {
            String k = key(s.getArmyTeam(), s.getGroup());
            counts.merge(k, 1, Integer::sum);
            if (s.getTarget() != null) inContact.put(k, true);
            SwatEntity cur = bestLeader.get(k);
            // a leader should be someone who can actually fight and who is not a support specialist
            if (cur == null || leaderScore(s) > leaderScore(cur)) bestLeader.put(k, s);
        }

        for (Map.Entry<String, Squad> e : SQUADS.entrySet()) {
            e.getValue().alive = counts.getOrDefault(e.getKey(), 0);
        }

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Squad sq = SQUADS.computeIfAbsent(e.getKey(), (k) -> new Squad());
            int alive = e.getValue();
            sq.alive = alive;
            if (alive > sq.peak) sq.peak = alive;

            SwatEntity leader = bestLeader.get(e.getKey());
            if (CommonConfig.SQUAD_LEADERS.get() && leader != null) {
                sq.leaderId = leader.getId();
            } else {
                sq.leaderId = -1;
            }

            if (!CommonConfig.FLANK_ENABLED.get()) continue;
            if (now < sq.flankUntil || now < sq.flankReadyAt) continue;
            if (sq.peak < 3 || !inContact.getOrDefault(e.getKey(), false)) continue;

            double lostFraction = 1.0 - (double) alive / sq.peak;
            if (lostFraction >= CommonConfig.FLANK_THRESHOLD.get()) {
                sq.flankUntil = now + FLANK_DURATION;
                sq.flankReadyAt = now + FLANK_DURATION + FLANK_COOLDOWN;
                sq.flankSide = level.random.nextBoolean() ? 1 : -1;
                Team team = Team.byName(e.getKey().substring(0, e.getKey().indexOf('/')));
                if (team != null) {
                    VoiceLines.radio(level, team, VoiceLines.Line.FLANKING);
                    WarGameData.broadcast(level.getServer(), "§e[VOJNA] Skupina §6"
                            + e.getKey().substring(e.getKey().indexOf('/') + 1)
                            + "§e tímu §6" + team.getDisplayName().toUpperCase()
                            + "§e stratila polovicu mužov a skúša obchádzku.");
                }
            }
        }
    }

    private static int leaderScore(SwatEntity s) {
        int score = (int) s.getHealth();
        if (!s.getRole().isSupport()) score += 100;
        if (s.getRole() == sk.totalnavojna.Role.DEFENDER || s.getRole() == sk.totalnavojna.Role.ATTACKER) score += 20;
        return score;
    }

    public static boolean isFlanking(Team team, int group, long now) {
        Squad sq = SQUADS.get(key(team, group));
        return sq != null && now < sq.flankUntil;
    }

    public static int flankSide(Team team, int group) {
        Squad sq = SQUADS.get(key(team, group));
        return sq == null ? 1 : sq.flankSide;
    }

    public static void cancelFlank(Team team, int group) {
        Squad sq = SQUADS.get(key(team, group));
        if (sq != null) sq.flankUntil = 0;
    }

    public static int leaderId(Team team, int group) {
        Squad sq = SQUADS.get(key(team, group));
        return sq == null ? -1 : sq.leaderId;
    }

    public static boolean isLeader(SwatEntity soldier) {
        return leaderId(soldier.getArmyTeam(), soldier.getGroup()) == soldier.getId();
    }

    @Nullable
    public static SwatEntity leaderOf(ServerLevel level, Team team, int group) {
        int id = leaderId(team, group);
        if (id < 0) return null;
        return level.getEntity(id) instanceof SwatEntity s && s.getState() == SwatEntity.STATE_ALIVE ? s : null;
    }

    // Reset the "how strong were we" baseline, e.g. when a new match starts.
    public static void resetPeaks() {
        for (Squad sq : SQUADS.values()) {
            sq.peak = sq.alive;
            sq.flankUntil = 0;
            sq.flankReadyAt = 0;
        }
    }
}

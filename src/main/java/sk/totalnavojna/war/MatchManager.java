package sk.totalnavojna.war;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.blocks.CaptureBlock;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// The referee. Runs the phases of a match, ticks the capture points, bleeds tickets and decides when it is over.
//
// A match is optional: with no /totalnavojna start the mod stays in IDLE and behaves exactly like it did before -
// a sandbox where you build, spawn and fight with nothing being scored.
public final class MatchManager {

    private static int captureTimer = 0;
    private static int bleedTimer = 0;

    private MatchManager() {
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        WarGameData data = WarGameData.get(overworld);

        // PREP counts down into COMBAT
        if (data.getPhase() == MatchPhase.PREP) {
            long left = data.phaseEndsAt - overworld.getGameTime();
            if (left <= 0) {
                data.setPhase(MatchPhase.COMBAT, 0);
                WarGameData.broadcast(server, "§c[VOJNA] ⚔ BOJ SA ZAČAL!");
                BattleLog.add(server, null, "§cZačiatok boja");
                for (Team t : Team.VALUES) VoiceLines.radio(overworld, t, VoiceLines.Line.CONTACT);
            } else if (left % 200 == 0) {
                WarGameData.broadcast(server, "§e[VOJNA] Príprava: §f" + (left / 20) + " s do začiatku boja.");
            }
        }

        if (++captureTimer >= 20) {
            captureTimer = 0;
            for (ServerLevel level : server.getAllLevels()) {
                SquadTactics.tick(level);
            }
            tickCaptures(server, data);
        }

        if (data.getPhase() == MatchPhase.COMBAT && CommonConfig.START_TICKETS.get() > 0) {
            if (++bleedTimer >= CommonConfig.BLEED_INTERVAL.get()) {
                bleedTimer = 0;
                bleedTickets(server, data);
            }
        }
    }

    // ===== capture points =====

    private static void tickCaptures(MinecraftServer server, WarGameData data) {
        if (data.captures.isEmpty()) return;
        int radius = CommonConfig.CAPTURE_RADIUS.get();
        int captureTime = CommonConfig.CAPTURE_TIME.get();
        boolean fighting = data.getPhase() == MatchPhase.COMBAT || data.getPhase() == MatchPhase.IDLE;

        for (WarGameData.CaptureEntry cap : data.captures) {
            ServerLevel level = levelOf(server, cap.dim);
            if (level == null || !level.isLoaded(cap.pos)) continue;

            Map<Team, Integer> present = new EnumMap<>(Team.class);
            AABB box = new AABB(cap.pos).inflate(radius);
            for (SwatEntity s : level.getEntitiesOfClass(SwatEntity.class, box,
                    (e) -> e.getState() == SwatEntity.STATE_ALIVE)) {
                present.merge(s.getArmyTeam(), 1, Integer::sum);
            }
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
                Team t = TeamsSavedData.get(level).getTeam(p.getUUID());
                if (t != null && !p.isSpectator()) present.merge(t, 1, Integer::sum);
            }

            Team olivaPresent = present.getOrDefault(Team.OLIVA, 0) > 0 ? Team.OLIVA : null;
            Team piesokPresent = present.getOrDefault(Team.PIESOK, 0) > 0 ? Team.PIESOK : null;
            boolean contested = olivaPresent != null && piesokPresent != null;
            Team pushing = contested ? null : (olivaPresent != null ? olivaPresent : piesokPresent);

            cap.contested = contested;

            if (!fighting || pushing == null || pushing == cap.owner) {
                // nobody pushing, or the owner is just standing on his own point: progress decays
                if (cap.progress > 0) {
                    cap.progress = Math.max(0, cap.progress - 20);
                    if (cap.progress == 0) cap.progressTeam = null;
                    data.setDirty();
                }
                continue;
            }

            if (cap.progressTeam != pushing) {
                cap.progressTeam = pushing;
                cap.progress = 0;
            }
            int men = Math.min(3, present.getOrDefault(pushing, 1));
            cap.progress += 20 * men;
            data.setDirty();

            if (cap.progress >= captureTime) {
                Team lost = cap.owner;
                cap.owner = pushing;
                cap.progress = 0;
                cap.progressTeam = null;
                applyOwnerToBlock(level, cap);
                String where = cap.pos.getX() + ", " + cap.pos.getZ();
                WarGameData.broadcast(server, "§a[VOJNA] ⚑ Záchytný bod §f" + where
                        + "§a obsadil tím §e" + pushing.getDisplayName().toUpperCase() + "§a.");
                BattleLog.capture(server, pushing, where);
                VoiceLines.radio(level, pushing, VoiceLines.Line.POINT_TAKEN);
                if (lost != null) VoiceLines.radio(level, lost, VoiceLines.Line.POINT_LOST);
            }
        }
    }

    private static void applyOwnerToBlock(ServerLevel level, WarGameData.CaptureEntry cap) {
        BlockState state = level.getBlockState(cap.pos);
        if (state.getBlock() instanceof CaptureBlock) {
            level.setBlock(cap.pos, state.setValue(CaptureBlock.OWNER, CaptureBlock.ownerValue(cap.owner)), 3);
        }
    }

    public static int countPoints(WarGameData data, Team team) {
        int n = 0;
        for (WarGameData.CaptureEntry c : data.captures) {
            if (c.owner == team) n++;
        }
        return n;
    }

    // ===== tickets =====

    private static void bleedTickets(MinecraftServer server, WarGameData data) {
        if (data.captures.isEmpty()) return;
        int oliva = countPoints(data, Team.OLIVA);
        int piesok = countPoints(data, Team.PIESOK);
        if (oliva == piesok) return;
        Team losing = oliva > piesok ? Team.PIESOK : Team.OLIVA;
        int diff = Math.abs(oliva - piesok);
        spendTickets(server, data, losing, diff, "prevaha na bodoch");
    }

    public static void spendTickets(MinecraftServer server, WarGameData data, Team team, int amount, String reason) {
        if (CommonConfig.START_TICKETS.get() <= 0 || amount <= 0) return;
        if (data.getPhase() != MatchPhase.COMBAT) return;
        int left = data.takeTickets(team, amount);
        if (left <= 0) {
            end(server, team.enemy(), "Tímu " + team.getDisplayName().toUpperCase() + " došli tikety (" + reason + ").");
        } else if (left <= 20 || left % 50 == 0) {
            WarGameData.broadcast(server, "§e[VOJNA] Tím §6" + team.getDisplayName().toUpperCase()
                    + "§e má už len §c" + left + "§e tiketov.");
        }
    }

    // ===== phases =====

    public static void start(MinecraftServer server, int prepSeconds) {
        WarGameData data = WarGameData.get(server.overworld());
        data.resetMatch(server);
        BattleLog.reset();
        SquadTactics.resetPeaks();
        VoiceLines.clear();
        captureTimer = 0;
        bleedTimer = 0;

        if (prepSeconds > 0) {
            data.setPhase(MatchPhase.PREP, server.overworld().getGameTime() + prepSeconds * 20L);
            WarGameData.broadcast(server, "§6[VOJNA] ⏱ PRÍPRAVA — §f" + prepSeconds + " s. Rozostavte jednotky, zbrane sú studené.");
        } else {
            data.setPhase(MatchPhase.COMBAT, 0);
            WarGameData.broadcast(server, "§c[VOJNA] ⚔ BOJ SA ZAČAL!");
        }
        int tickets = CommonConfig.START_TICKETS.get();
        if (tickets > 0) {
            WarGameData.broadcast(server, "§6[VOJNA] Tikety: §f" + tickets + " §7na tím · záchytných bodov: §f" + data.captures.size());
        }
        BattleLog.add(server, null, "§6Zápas začal");
        WarEvents.updateScoreboard(server);
    }

    public static void stop(MinecraftServer server) {
        WarGameData data = WarGameData.get(server.overworld());
        data.setPhase(MatchPhase.IDLE, 0);
        WarGameData.broadcast(server, "§7[VOJNA] Zápas zrušený, späť do voľného režimu.");
        WarEvents.updateScoreboard(server);
    }

    // The end of a match: report, and (by default) both teams go passive so the world stops shooting itself.
    public static void end(MinecraftServer server, @Nullable Team winner, String reason) {
        WarGameData data = WarGameData.get(server.overworld());
        if (data.getPhase() == MatchPhase.ENDED) return;
        data.setPhase(MatchPhase.ENDED, 0);

        ServerLevel overworld = server.overworld();
        if (winner != null) {
            VoiceLines.radio(overworld, winner, VoiceLines.Line.VICTORY);
            VoiceLines.radio(overworld, winner.enemy(), VoiceLines.Line.DEFEAT);
        }
        BattleLog.afterAction(server, winner, reason);

        if (CommonConfig.NEUTRAL_ON_END.get()) {
            for (Team t : Team.VALUES) {
                data.stances.put(t, Stance.PASSIVE);
            }
            data.setDirty();
            WarGameData.broadcast(server, "§7[VOJNA] Oba tímy prešli do §7PASIVITY§7. Novú vojnu spustíš cez §f/totalnavojna start§7.");
            for (ServerLevel level : server.getAllLevels()) {
                for (SwatEntity s : level.getEntities(EntityTypeTest.forClass(SwatEntity.class),
                        (e) -> e.getState() == SwatEntity.STATE_ALIVE)) {
                    s.setTarget(null);
                    s.setLastHurtByMob(null);
                }
            }
        }
        WarEvents.updateScoreboard(server);
    }

    // Called from WarGameData whenever a NEXUS goes down.
    public static void onCoreLost(MinecraftServer server, Team team) {
        WarGameData data = WarGameData.get(server.overworld());
        if (data.countCores(team) > 0) return;
        end(server, team.enemy(), "Tím " + team.getDisplayName().toUpperCase() + " stratil posledný NEXUS.");
    }

    @Nullable
    public static ServerLevel levelOf(MinecraftServer server, String dim) {
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(dim)));
    }

    // Header line used by the scoreboard / HUD.
    public static Component statusLine(WarGameData data) {
        MatchPhase phase = data.getPhase();
        StringBuilder sb = new StringBuilder(phase.getLabel());
        if (CommonConfig.START_TICKETS.get() > 0) {
            sb.append(" §7| §aO ").append(data.getTickets(Team.OLIVA))
                    .append(" §7/ §6P ").append(data.getTickets(Team.PIESOK));
        }
        if (!data.captures.isEmpty()) {
            sb.append(" §7| ⚑ §a").append(countPoints(data, Team.OLIVA))
                    .append("§7-§6").append(countPoints(data, Team.PIESOK));
        }
        return Component.literal(sb.toString());
    }

    // A soldier list for the recruitment menu / rally point handling.
    public static List<? extends SwatEntity> aliveOf(ServerLevel level, Team team) {
        return level.getEntities(EntityTypeTest.forClass(SwatEntity.class),
                (e) -> e.getArmyTeam() == team && e.getState() == SwatEntity.STATE_ALIVE);
    }

    public static BlockPos surface(ServerLevel level, BlockPos pos) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }
}

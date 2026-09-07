package sk.totalnavojna.war;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.config.CommonConfig;

import java.util.*;

public class WarGameData extends SavedData {
    private static final String DATA_NAME = TotalnaVojna.MOD_ID + "_wargame";

    public static class CoreEntry {
        public String dim;
        public BlockPos pos;
        public Team team;
        public int lives;
    }

    public static class SpawnerEntry {
        public String dim;
        public BlockPos pos;
        public Team team;
        public int lives;
    }

    public static class SupplyEntry {
        public String dim;
        public BlockPos pos;
        public Team team;
    }

    // A capture point. owner == null means nobody holds it yet.
    public static class CaptureEntry {
        public String dim;
        public BlockPos pos;
        public Team owner;
        public Team progressTeam;
        public int progress;
        public boolean contested;
    }

    public static class SoldierStats {
        public int kills;
        public int deaths;
    }

    public static class RespawnEntry {
        public String dim;
        public Team team;
        public Role role;
        public int group;
        public String name;
        public int ticksLeft;
        // where the man fell, so he comes back at the nearest forward base rather than a random one
        public int x;
        public int z;
    }

    public final List<CoreEntry> cores = new ArrayList<>();
    public final List<SpawnerEntry> spawners = new ArrayList<>();
    public final List<SupplyEntry> supplies = new ArrayList<>();
    public final Map<Team, Integer> teamDeaths = new EnumMap<>(Team.class);
    public final Map<String, SoldierStats> stats = new HashMap<>();
    public final List<RespawnEntry> respawnQueue = new ArrayList<>();
    public final Set<Team> defeatedTeams = EnumSet.noneOf(Team.class);
    // chunk boundaries ("mantinely") per team+group: key = team.name + "/" + group, value = packed ChunkPos longs
    public final Map<String, Set<Long>> groupAreas = new HashMap<>();
    // chunks the army occupies, per dimension - kept loaded by ArmyChunkKeeper, saved so they come back after a restart
    public final Map<String, Set<Long>> armyChunks = new HashMap<>();
    // diplomacy: stance of a team towards the other team; mob stance of a team
    public final Map<Team, Stance> stances = new EnumMap<>(Team.class);
    public final Map<Team, MobStance> mobStances = new EnumMap<>(Team.class);
    // v0.15.0: shape of the war
    public final List<CaptureEntry> captures = new ArrayList<>();
    public final Map<Team, Integer> tickets = new EnumMap<>(Team.class);
    // soldiers who used up their personal lives - shown greyed out in the commander roster, never respawned
    public final Set<String> permaDead = new HashSet<>();
    // per team+group gathering point new and respawned soldiers walk to
    public final Map<String, BlockPos> rallyPoints = new HashMap<>();
    private MatchPhase phase = MatchPhase.IDLE;
    public long phaseEndsAt;
    // transient: when a team under TRUCE was last attacked by the other team (60 s retaliation window)
    private final Map<Team, Long> truceBrokenAt = new EnumMap<>(Team.class);
    private static final long RETALIATION_TICKS = 1200;
    // Spotting is the army's shared picture and lives in BattleIntel; these are just the map's view of it.
    public void spot(Team team, Entity enemy) {
        if (enemy.level() instanceof ServerLevel sl) BattleIntel.report(team, enemy, sl.getGameTime());
    }

    public List<double[]> getSpotted(Team team) {
        return BattleIntel.mapPoints(team);
    }

    public Stance getStance(Team team) {
        return this.stances.getOrDefault(team, Stance.WAR);
    }

    public void setStance(MinecraftServer server, Team team, Stance stance) {
        this.stances.put(team, stance);
        this.truceBrokenAt.remove(team);
        this.setDirty();
        broadcast(server, "§6[VOJNA] §fTím §e" + team.getDisplayName().toUpperCase() + "§f vyhlásil voči tímu §e" + team.enemy().getDisplayName().toUpperCase() + "§f: " + stance.getLabel());
    }

    public MobStance getMobStance(Team team) {
        return this.mobStances.getOrDefault(team, MobStance.NEUTRAL);
    }

    public void setMobStance(MinecraftServer server, Team team, MobStance stance) {
        this.mobStances.put(team, stance);
        this.setDirty();
        broadcast(server, "§6[VOJNA] §fTím §e" + team.getDisplayName().toUpperCase() + "§f voči mobom: " + stance.getLabel());
    }

    // both sides must declare ALLIANCE for it to be mutual (friendly fire off, revive/heal across teams)
    public boolean areAllied(Team a, Team b) {
        if (a == b) return true;
        return getStance(a) == Stance.ALLIANCE && getStance(b) == Stance.ALLIANCE;
    }

    public boolean isFriendly(Team a, Team b) {
        return a == b || areAllied(a, b);
    }

    // Team 'victim' (under TRUCE) got attacked by the other team -> retaliate for a while.
    public void markTruceBroken(MinecraftServer server, Team victim, long now) {
        Long last = this.truceBrokenAt.get(victim);
        this.truceBrokenAt.put(victim, now);
        if (last == null || now - last > RETALIATION_TICKS) {
            broadcast(server, "§c[VOJNA] Tím §e" + victim.enemy().getDisplayName().toUpperCase() + "§c porušil prímerie! Tím §e" + victim.getDisplayName().toUpperCase() + "§c opätuje paľbu (60 s).");
        }
    }

    public boolean isRetaliating(Team team, long now) {
        Long t = this.truceBrokenAt.get(team);
        return t != null && now - t <= RETALIATION_TICKS;
    }

    // Does team 'me' shoot members of team 'other' on sight right now?
    public boolean isHostile(Team me, Team other, long now) {
        if (me == other) return false;
        // during PREP nobody shoots, after the match ends nobody shoots either
        if (!this.phase.isFighting()) return false;
        return switch (getStance(me)) {
            case WAR -> true;
            case TRUCE -> isRetaliating(me, now);
            case PASSIVE, ALLIANCE -> false;
        };
    }

    public Set<Long> getArmyChunks(String dim) {
        return this.armyChunks.getOrDefault(dim, Collections.emptySet());
    }

    public void setArmyChunks(String dim, Set<Long> chunks) {
        Set<Long> old = this.armyChunks.get(dim);
        if (old != null && old.equals(chunks)) return;
        this.armyChunks.put(dim, new HashSet<>(chunks));
        this.setDirty();
    }

    // ===== match phase =====

    public MatchPhase getPhase() {
        return this.phase;
    }

    public void setPhase(MatchPhase phase, long endsAt) {
        this.phase = phase;
        this.phaseEndsAt = endsAt;
        this.setDirty();
    }

    // ===== tickets =====

    public int getTickets(Team team) {
        Integer t = this.tickets.get(team);
        if (t == null) {
            int start = CommonConfig.START_TICKETS.get();
            this.tickets.put(team, start);
            return start;
        }
        return t;
    }

    public int takeTickets(Team team, int amount) {
        int left = Math.max(0, getTickets(team) - amount);
        this.tickets.put(team, left);
        this.setDirty();
        return left;
    }

    // ===== capture points =====

    public void addCapture(ServerLevel level, BlockPos pos, @Nullable Team owner) {
        String dim = dimKey(level);
        for (CaptureEntry c : this.captures) {
            if (c.dim.equals(dim) && c.pos.equals(pos)) return;
        }
        CaptureEntry c = new CaptureEntry();
        c.dim = dim;
        c.pos = pos.immutable();
        c.owner = owner;
        this.captures.add(c);
        this.setDirty();
        broadcast(level.getServer(), "§6[VOJNA] §fPostavený ZÁCHYTNÝ BOD (" + pos.getX() + ", " + pos.getZ() + "). Spolu: " + this.captures.size());
    }

    public void removeCapture(ServerLevel level, BlockPos pos) {
        String dim = dimKey(level);
        if (this.captures.removeIf(c -> c.dim.equals(dim) && c.pos.equals(pos))) {
            this.setDirty();
        }
    }

    @Nullable
    public CaptureEntry captureAt(ServerLevel level, BlockPos pos) {
        String dim = dimKey(level);
        for (CaptureEntry c : this.captures) {
            if (c.dim.equals(dim) && c.pos.equals(pos)) return c;
        }
        return null;
    }

    // ===== permanently dead soldiers (SOLDIER death-limit mode) =====

    private static String soldierKey(Team team, String name) {
        return team.getName() + "/" + name;
    }

    public boolean isPermaDead(Team team, String name) {
        return this.permaDead.contains(soldierKey(team, name));
    }

    public List<String> fallenOf(Team team) {
        List<String> out = new ArrayList<>();
        String prefix = team.getName() + "/";
        for (String key : this.permaDead) {
            if (key.startsWith(prefix)) out.add(key.substring(prefix.length()));
        }
        Collections.sort(out);
        return out;
    }

    // ===== rally points =====

    private static String rallyKey(Team team, int group) {
        return team.getName() + "/" + group;
    }

    public void setRally(Team team, int group, @Nullable BlockPos pos) {
        if (pos == null) this.rallyPoints.remove(rallyKey(team, group));
        else this.rallyPoints.put(rallyKey(team, group), pos.immutable());
        this.setDirty();
    }

    @Nullable
    public BlockPos getRally(Team team, int group) {
        return this.rallyPoints.get(rallyKey(team, group));
    }

    public static WarGameData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(WarGameData::load, WarGameData::new, DATA_NAME);
    }

    private static String dimKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    // ===== cores (NEXUS) =====

    public void addCore(ServerLevel level, Team team, BlockPos pos) {
        String dim = dimKey(level);
        for (CoreEntry c : this.cores) {
            if (c.dim.equals(dim) && c.pos.equals(pos)) return;
        }
        CoreEntry c = new CoreEntry();
        c.dim = dim;
        c.pos = pos.immutable();
        c.team = team;
        c.lives = CommonConfig.CORE_LIVES.get();
        this.cores.add(c);
        this.setDirty();
        broadcast(level.getServer(), "§6[VOJNA] §fTím §e" + team.getDisplayName().toUpperCase() + "§f postavil NEXUS (" + c.lives + " životov).");
    }

    public void removeCore(ServerLevel level, Team team, BlockPos pos) {
        String dim = dimKey(level);
        boolean removed = this.cores.removeIf(c -> c.dim.equals(dim) && c.pos.equals(pos));
        if (removed) {
            this.setDirty();
            if (countCores(team) == 0) {
                broadcast(level.getServer(), "§c[VOJNA] Tím §e" + team.getDisplayName().toUpperCase() + "§c stratil POSLEDNÝ NEXUS! Víťazí tím §a" + team.enemy().getDisplayName().toUpperCase() + "§c!");
                MatchManager.onCoreLost(level.getServer(), team);
            } else {
                broadcast(level.getServer(), "§c[VOJNA] NEXUS tímu §e" + team.getDisplayName().toUpperCase() + "§c bol zničený! Zostáva: " + countCores(team));
            }
        }
    }

    public int countCores(Team team) {
        int n = 0;
        for (CoreEntry c : this.cores) {
            if (c.team == team) n++;
        }
        return n;
    }

    public int totalCoreLives(Team team) {
        int n = 0;
        for (CoreEntry c : this.cores) {
            if (c.team == team) n += Math.max(0, c.lives);
        }
        return n;
    }

    @Nullable
    public CoreEntry coreAt(ServerLevel level, BlockPos pos) {
        String dim = dimKey(level);
        for (CoreEntry c : this.cores) {
            if (c.dim.equals(dim) && c.pos.equals(pos)) return c;
        }
        return null;
    }

    @Nullable
    public CoreEntry nearestCore(ServerLevel level, Team team, BlockPos from) {
        String dim = dimKey(level);
        CoreEntry best = null;
        double bestDist = Double.MAX_VALUE;
        for (CoreEntry c : this.cores) {
            if (c.team != team || !c.dim.equals(dim)) continue;
            double d = c.pos.distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    public boolean isNearOwnCore(ServerLevel level, Team team, BlockPos pos, int radius) {
        CoreEntry c = nearestCore(level, team, pos);
        return c != null && c.pos.distSqr(pos) <= (double) radius * radius;
    }

    // One attacker finished a hit cycle: the NEXUS loses one life.
    public void takeCoreLife(ServerLevel level, CoreEntry core, @Nullable Entity attacker) {
        if (!this.cores.contains(core)) return;
        core.lives--;
        this.setDirty();
        int max = CommonConfig.CORE_LIVES.get();
        if (core.lives > 0) {
            // milestone broadcasts so both commanders know the state
            if (core.lives == max * 3 / 4 || core.lives == max / 2 || core.lives == max / 4 || core.lives == 10 || core.lives == 5) {
                broadcast(level.getServer(), "§e[VOJNA] NEXUS tímu §6" + core.team.getDisplayName().toUpperCase() + "§e je pod útokom! Zostáva §c" + core.lives + "§e životov.");
                VoiceLines.radio(level, core.team, VoiceLines.Line.NEXUS_ATTACK);
            }
            BattleLog.nexusHit(level.getServer(), core.team.enemy());
            return;
        }
        // remove from registry first so onRemove does not double-broadcast, then blow the block
        Team team = core.team;
        BlockPos pos = core.pos;
        this.cores.remove(core);
        level.destroyBlock(pos, false);
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0f, Level.ExplosionInteraction.NONE);
        if (countCores(team) == 0) {
            broadcast(level.getServer(), "§c[VOJNA] Tím §e" + team.getDisplayName().toUpperCase() + "§c stratil POSLEDNÝ NEXUS! Víťazí tím §a" + team.enemy().getDisplayName().toUpperCase() + "§c!");
            MatchManager.onCoreLost(level.getServer(), team);
        } else {
            broadcast(level.getServer(), "§c[VOJNA] NEXUS tímu §e" + team.getDisplayName().toUpperCase() + "§c bol zničený útokom! Zostáva: " + countCores(team));
        }
    }

    // ===== spawners =====

    public void addSpawner(ServerLevel level, Team team, BlockPos pos) {
        String dim = dimKey(level);
        for (SpawnerEntry s : this.spawners) {
            if (s.dim.equals(dim) && s.pos.equals(pos)) return;
        }
        SpawnerEntry s = new SpawnerEntry();
        s.dim = dim;
        s.pos = pos.immutable();
        s.team = team;
        s.lives = CommonConfig.SPAWNER_LIVES.get();
        this.spawners.add(s);
        this.setDirty();
    }

    @Nullable
    public SpawnerEntry spawnerAt(ServerLevel level, BlockPos pos) {
        String dim = dimKey(level);
        for (SpawnerEntry s : this.spawners) {
            if (s.dim.equals(dim) && s.pos.equals(pos)) return s;
        }
        return null;
    }

    // Forward FOB: an enemy can knock a spawner out the same way he chews through a NEXUS.
    public void takeSpawnerLife(ServerLevel level, SpawnerEntry spawner) {
        if (!this.spawners.contains(spawner) || CommonConfig.SPAWNER_LIVES.get() <= 0) return;
        spawner.lives--;
        this.setDirty();
        if (spawner.lives > 0) return;
        Team team = spawner.team;
        BlockPos pos = spawner.pos;
        this.spawners.remove(spawner);
        level.destroyBlock(pos, false);
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2.0f, Level.ExplosionInteraction.NONE);
        broadcast(level.getServer(), "§c[VOJNA] Predsunutá základňa tímu §e" + team.getDisplayName().toUpperCase()
                + "§c bola zničená! Zostáva základní: " + countSpawners(team));
        BattleLog.add(level.getServer(), team, "§cStratená predsunutá základňa");
    }

    public int countSpawners(Team team) {
        int n = 0;
        for (SpawnerEntry s : this.spawners) {
            if (s.team == team) n++;
        }
        return n;
    }

    // Respawn at the base nearest to where the man fell - that is what makes a forward FOB worth building.
    @Nullable
    public SpawnerEntry nearestSpawner(String dim, Team team, @Nullable BlockPos from) {
        SpawnerEntry best = null;
        double bestDist = Double.MAX_VALUE;
        for (SpawnerEntry s : this.spawners) {
            if (s.team != team || !s.dim.equals(dim)) continue;
            double dx = from == null ? 0 : s.pos.getX() - from.getX();
            double dz = from == null ? 0 : s.pos.getZ() - from.getZ();
            double d = dx * dx + dz * dz;
            if (best == null || d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    public void removeSpawner(ServerLevel level, Team team, BlockPos pos) {
        String dim = dimKey(level);
        if (this.spawners.removeIf(s -> s.dim.equals(dim) && s.pos.equals(pos))) {
            this.setDirty();
        }
    }

    @Nullable
    public SpawnerEntry anySpawner(String dim, Team team) {
        List<SpawnerEntry> options = new ArrayList<>();
        for (SpawnerEntry s : this.spawners) {
            if (s.team == team && s.dim.equals(dim)) options.add(s);
        }
        if (options.isEmpty()) return null;
        return options.get(new Random().nextInt(options.size()));
    }

    // ===== supply stations =====

    public void addSupply(ServerLevel level, Team team, BlockPos pos) {
        String dim = dimKey(level);
        for (SupplyEntry s : this.supplies) {
            if (s.dim.equals(dim) && s.pos.equals(pos)) return;
        }
        SupplyEntry s = new SupplyEntry();
        s.dim = dim;
        s.pos = pos.immutable();
        s.team = team;
        this.supplies.add(s);
        this.setDirty();
    }

    public void removeSupply(ServerLevel level, Team team, BlockPos pos) {
        String dim = dimKey(level);
        if (this.supplies.removeIf(s -> s.dim.equals(dim) && s.pos.equals(pos))) {
            this.setDirty();
        }
    }

    @Nullable
    public SupplyEntry nearestSupply(ServerLevel level, Team team, BlockPos from, double maxDist) {
        String dim = dimKey(level);
        SupplyEntry best = null;
        double bestDist = maxDist * maxDist;
        for (SupplyEntry s : this.supplies) {
            if (s.team != team || !s.dim.equals(dim)) continue;
            double d = s.pos.distSqr(from);
            if (d <= bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    // ===== stats & respawn =====

    public SoldierStats statsFor(Team team, String name) {
        return this.stats.computeIfAbsent(team.getName() + "/" + name, (k) -> new SoldierStats());
    }

    public SoldierStats statsForPlayer(String playerName) {
        return this.stats.computeIfAbsent("hraci/" + playerName, (k) -> new SoldierStats());
    }

    public int getTeamDeaths(Team team) {
        return this.teamDeaths.getOrDefault(team, 0);
    }

    public boolean isDefeated(Team team) {
        return this.defeatedTeams.contains(team);
    }

    public void recordSoldierDeath(ServerLevel level, Team team, Role role, int group, String name) {
        recordSoldierDeath(level, team, role, group, name, null);
    }

    public void recordSoldierDeath(ServerLevel level, Team team, Role role, int group, String name, @Nullable BlockPos where) {
        int deaths = getTeamDeaths(team) + 1;
        this.teamDeaths.put(team, deaths);
        SoldierStats st = statsFor(team, name);
        st.deaths++;
        this.setDirty();

        BattleLog.loss(level.getServer(), team, name);
        VoiceLines.radio(level, team, VoiceLines.Line.MAN_DOWN);
        MatchManager.spendTickets(level.getServer(), this, team, CommonConfig.TICKETS_PER_DEATH.get(), "padlí");

        DeathLimitMode mode = CommonConfig.DEATH_LIMIT_MODE.get();
        boolean permanentlyGone = false;

        if (mode == DeathLimitMode.SOLDIER) {
            int lives = CommonConfig.MAX_SOLDIER_DEATHS.get();
            if (st.deaths >= lives) {
                permanentlyGone = true;
                if (this.permaDead.add(soldierKey(team, name))) {
                    broadcast(level.getServer(), "§8[VOJNA] §7" + name + " §8(" + team.getDisplayName().toUpperCase()
                            + ") padol " + st.deaths + ". raz a už sa nevráti.");
                    BattleLog.add(level.getServer(), team, "§8✝ " + name + " definitívne padol");
                }
            }
        } else if (mode == DeathLimitMode.TEAM) {
            int maxDeaths = CommonConfig.MAX_DEATHS.get();
            if (maxDeaths > 0 && deaths >= maxDeaths && !this.defeatedTeams.contains(team)) {
                this.defeatedTeams.add(team);
                broadcast(level.getServer(), "§c[VOJNA] Tím §e" + team.getDisplayName().toUpperCase() + "§c dosiahol limit strát (" + maxDeaths + ")!");
                MatchManager.end(level.getServer(), team.enemy(), "Tím " + team.getDisplayName().toUpperCase() + " vyčerpal limit strát.");
            }
        }

        if (permanentlyGone || !CommonConfig.ALLOW_RESPAWN.get() || this.defeatedTeams.contains(team)) return;
        SpawnerEntry spawner = nearestSpawner(dimKey(level), team, where);
        if (spawner == null) return;

        RespawnEntry r = new RespawnEntry();
        r.dim = dimKey(level);
        r.team = team;
        r.role = role;
        r.group = group;
        r.name = name;
        r.ticksLeft = CommonConfig.RESPAWN_TIME.get();
        r.x = where == null ? spawner.pos.getX() : where.getX();
        r.z = where == null ? spawner.pos.getZ() : where.getZ();
        this.respawnQueue.add(r);
        this.setDirty();
    }

    public void recordKill(Team killerTeam, String killerName) {
        statsFor(killerTeam, killerName).kills++;
        this.setDirty();
    }

    public void recordPlayerKill(String playerName) {
        statsForPlayer(playerName).kills++;
        this.setDirty();
    }

    public void resetGame(MinecraftServer server) {
        resetMatch(server);
        this.phase = MatchPhase.IDLE;
        this.phaseEndsAt = 0;
        broadcast(server, "§6[VOJNA] §fŠtatistiky boli resetované. Nová hra!");
    }

    // Everything a fresh match needs zeroed - used by /totalnavojna start and /totalnavojna reset.
    public void resetMatch(MinecraftServer server) {
        this.teamDeaths.clear();
        this.stats.clear();
        this.respawnQueue.clear();
        this.defeatedTeams.clear();
        this.permaDead.clear();
        this.tickets.clear();
        for (Team t : Team.VALUES) {
            this.tickets.put(t, CommonConfig.START_TICKETS.get());
        }
        for (CoreEntry c : this.cores) {
            c.lives = CommonConfig.CORE_LIVES.get();
        }
        for (SpawnerEntry sp : this.spawners) {
            sp.lives = CommonConfig.SPAWNER_LIVES.get();
        }
        for (CaptureEntry c : this.captures) {
            c.progress = 0;
            c.progressTeam = null;
            c.contested = false;
        }
        this.setDirty();
    }

    // ===== group areas (mantinely) =====

    private static String areaKey(Team team, int group) {
        return team.getName() + "/" + group;
    }

    public void setGroupArea(Team team, int group, Set<Long> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            this.groupAreas.remove(areaKey(team, group));
        } else {
            this.groupAreas.put(areaKey(team, group), new HashSet<>(chunks));
        }
        this.setDirty();
    }

    public Set<Long> getGroupArea(Team team, int group) {
        return this.groupAreas.getOrDefault(areaKey(team, group), Collections.emptySet());
    }

    public boolean isInsideArea(Team team, int group, BlockPos pos) {
        Set<Long> area = getGroupArea(team, group);
        if (area.isEmpty()) return true;
        long key = (((long) (pos.getZ() >> 4)) << 32) | (((long) (pos.getX() >> 4)) & 0xFFFFFFFFL);
        return area.contains(key);
    }

    @Nullable
    public BlockPos nearestAreaChunkCenter(Team team, int group, BlockPos from) {
        Set<Long> area = getGroupArea(team, group);
        if (area.isEmpty()) return null;
        long best = 0;
        double bestDist = Double.MAX_VALUE;
        int fx = from.getX() >> 4;
        int fz = from.getZ() >> 4;
        for (long key : area) {
            int cx = (int) (key & 0xFFFFFFFFL);
            int cz = (int) (key >>> 32);
            double d = (double) (cx - fx) * (cx - fx) + (double) (cz - fz) * (cz - fz);
            if (d < bestDist) {
                bestDist = d;
                best = key;
            }
        }
        int cx = (int) (best & 0xFFFFFFFFL);
        int cz = (int) (best >>> 32);
        return new BlockPos((cx << 4) + 8, from.getY(), (cz << 4) + 8);
    }

    public static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    // ===== NBT =====

    private static WarGameData load(CompoundTag tag) {
        WarGameData data = new WarGameData();

        ListTag coresTag = tag.getList("Cores", Tag.TAG_COMPOUND);
        for (int i = 0; i < coresTag.size(); i++) {
            CompoundTag t = coresTag.getCompound(i);
            Team team = Team.byName(t.getString("Team"));
            if (team == null) continue;
            CoreEntry c = new CoreEntry();
            c.dim = t.getString("Dim");
            c.pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
            c.team = team;
            // migration from the old HP pool: keep the same number of "points" as lives
            c.lives = t.contains("Lives") ? t.getInt("Lives") : (t.contains("Hp") ? Math.max(1, Math.round(t.getFloat("Hp"))) : CommonConfig.CORE_LIVES.get());
            data.cores.add(c);
        }

        ListTag spawnersTag = tag.getList("Spawners", Tag.TAG_COMPOUND);
        for (int i = 0; i < spawnersTag.size(); i++) {
            CompoundTag t = spawnersTag.getCompound(i);
            Team team = Team.byName(t.getString("Team"));
            if (team == null) continue;
            SpawnerEntry s = new SpawnerEntry();
            s.dim = t.getString("Dim");
            s.pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
            s.team = team;
            s.lives = t.contains("Lives") ? t.getInt("Lives") : CommonConfig.SPAWNER_LIVES.get();
            data.spawners.add(s);
        }

        ListTag capturesTag = tag.getList("Captures", Tag.TAG_COMPOUND);
        for (int i = 0; i < capturesTag.size(); i++) {
            CompoundTag t = capturesTag.getCompound(i);
            CaptureEntry c = new CaptureEntry();
            c.dim = t.getString("Dim");
            c.pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
            c.owner = t.contains("Owner") ? Team.byName(t.getString("Owner")) : null;
            c.progressTeam = t.contains("PTeam") ? Team.byName(t.getString("PTeam")) : null;
            c.progress = t.getInt("Progress");
            data.captures.add(c);
        }

        CompoundTag ticketsTag = tag.getCompound("Tickets");
        for (Team team : Team.VALUES) {
            if (ticketsTag.contains(team.getName())) data.tickets.put(team, ticketsTag.getInt(team.getName()));
        }

        for (String key : tag.getList("PermaDead", Tag.TAG_STRING).stream().map(Tag::getAsString).toList()) {
            data.permaDead.add(key);
        }

        CompoundTag rallyTag = tag.getCompound("Rally");
        for (String key : rallyTag.getAllKeys()) {
            int[] xyz = rallyTag.getIntArray(key);
            if (xyz.length == 3) data.rallyPoints.put(key, new BlockPos(xyz[0], xyz[1], xyz[2]));
        }

        MatchPhase ph = MatchPhase.byName(tag.getString("Phase"));
        if (ph != null) data.phase = ph;
        data.phaseEndsAt = tag.getLong("PhaseEndsAt");

        ListTag suppliesTag = tag.getList("Supplies", Tag.TAG_COMPOUND);
        for (int i = 0; i < suppliesTag.size(); i++) {
            CompoundTag t = suppliesTag.getCompound(i);
            Team team = Team.byName(t.getString("Team"));
            if (team == null) continue;
            SupplyEntry s = new SupplyEntry();
            s.dim = t.getString("Dim");
            s.pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
            s.team = team;
            data.supplies.add(s);
        }

        CompoundTag deathsTag = tag.getCompound("TeamDeaths");
        for (Team team : Team.VALUES) {
            if (deathsTag.contains(team.getName())) {
                data.teamDeaths.put(team, deathsTag.getInt(team.getName()));
            }
        }

        CompoundTag statsTag = tag.getCompound("Stats");
        for (String key : statsTag.getAllKeys()) {
            CompoundTag t = statsTag.getCompound(key);
            SoldierStats s = new SoldierStats();
            s.kills = t.getInt("K");
            s.deaths = t.getInt("D");
            data.stats.put(key, s);
        }

        ListTag queueTag = tag.getList("RespawnQueue", Tag.TAG_COMPOUND);
        for (int i = 0; i < queueTag.size(); i++) {
            CompoundTag t = queueTag.getCompound(i);
            Team team = Team.byName(t.getString("Team"));
            Role role = Role.byName(t.getString("Role"));
            if (team == null || role == null) continue;
            RespawnEntry r = new RespawnEntry();
            r.dim = t.getString("Dim");
            r.team = team;
            r.role = role;
            r.group = t.getInt("Group");
            r.name = t.getString("Name");
            r.ticksLeft = t.getInt("Ticks");
            r.x = t.getInt("Fx");
            r.z = t.getInt("Fz");
            data.respawnQueue.add(r);
        }

        for (Team team : Team.VALUES) {
            if (tag.getBoolean("Defeated_" + team.getName())) {
                data.defeatedTeams.add(team);
            }
        }

        for (Team team : Team.VALUES) {
            Stance st = Stance.byName(tag.getString("Stance_" + team.getName()));
            if (st != null) data.stances.put(team, st);
            MobStance ms = MobStance.byName(tag.getString("MobStance_" + team.getName()));
            if (ms != null) data.mobStances.put(team, ms);
        }

        CompoundTag armyTag = tag.getCompound("ArmyChunks");
        for (String key : armyTag.getAllKeys()) {
            Set<Long> set = new HashSet<>();
            for (long c : armyTag.getLongArray(key)) set.add(c);
            if (!set.isEmpty()) data.armyChunks.put(key, set);
        }

        CompoundTag areasTag = tag.getCompound("GroupAreas");
        for (String key : areasTag.getAllKeys()) {
            long[] chunks = areasTag.getLongArray(key);
            Set<Long> set = new HashSet<>();
            for (long c : chunks) set.add(c);
            if (!set.isEmpty()) data.groupAreas.put(key, set);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag coresTag = new ListTag();
        for (CoreEntry c : this.cores) {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", c.dim);
            t.putInt("X", c.pos.getX());
            t.putInt("Y", c.pos.getY());
            t.putInt("Z", c.pos.getZ());
            t.putString("Team", c.team.getName());
            t.putInt("Lives", c.lives);
            coresTag.add(t);
        }
        tag.put("Cores", coresTag);

        ListTag spawnersTag = new ListTag();
        for (SpawnerEntry s : this.spawners) {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", s.dim);
            t.putInt("X", s.pos.getX());
            t.putInt("Y", s.pos.getY());
            t.putInt("Z", s.pos.getZ());
            t.putString("Team", s.team.getName());
            t.putInt("Lives", s.lives);
            spawnersTag.add(t);
        }
        tag.put("Spawners", spawnersTag);

        ListTag capturesTag = new ListTag();
        for (CaptureEntry c : this.captures) {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", c.dim);
            t.putInt("X", c.pos.getX());
            t.putInt("Y", c.pos.getY());
            t.putInt("Z", c.pos.getZ());
            if (c.owner != null) t.putString("Owner", c.owner.getName());
            if (c.progressTeam != null) t.putString("PTeam", c.progressTeam.getName());
            t.putInt("Progress", c.progress);
            capturesTag.add(t);
        }
        tag.put("Captures", capturesTag);

        CompoundTag ticketsTag = new CompoundTag();
        this.tickets.forEach((team, n) -> ticketsTag.putInt(team.getName(), n));
        tag.put("Tickets", ticketsTag);

        ListTag permaTag = new ListTag();
        for (String key : this.permaDead) permaTag.add(net.minecraft.nbt.StringTag.valueOf(key));
        tag.put("PermaDead", permaTag);

        CompoundTag rallyTag = new CompoundTag();
        this.rallyPoints.forEach((key, pos) -> rallyTag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()}));
        tag.put("Rally", rallyTag);

        tag.putString("Phase", this.phase.getName());
        tag.putLong("PhaseEndsAt", this.phaseEndsAt);

        ListTag suppliesTag = new ListTag();
        for (SupplyEntry s : this.supplies) {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", s.dim);
            t.putInt("X", s.pos.getX());
            t.putInt("Y", s.pos.getY());
            t.putInt("Z", s.pos.getZ());
            t.putString("Team", s.team.getName());
            suppliesTag.add(t);
        }
        tag.put("Supplies", suppliesTag);

        CompoundTag deathsTag = new CompoundTag();
        this.teamDeaths.forEach((team, deaths) -> deathsTag.putInt(team.getName(), deaths));
        tag.put("TeamDeaths", deathsTag);

        CompoundTag statsTag = new CompoundTag();
        this.stats.forEach((key, s) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("K", s.kills);
            t.putInt("D", s.deaths);
            statsTag.put(key, t);
        });
        tag.put("Stats", statsTag);

        ListTag queueTag = new ListTag();
        for (RespawnEntry r : this.respawnQueue) {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", r.dim);
            t.putString("Team", r.team.getName());
            t.putString("Role", r.role.getName());
            t.putInt("Group", r.group);
            t.putString("Name", r.name);
            t.putInt("Ticks", r.ticksLeft);
            t.putInt("Fx", r.x);
            t.putInt("Fz", r.z);
            queueTag.add(t);
        }
        tag.put("RespawnQueue", queueTag);

        for (Team team : this.defeatedTeams) {
            tag.putBoolean("Defeated_" + team.getName(), true);
        }

        this.stances.forEach((team, st) -> tag.putString("Stance_" + team.getName(), st.getName()));
        this.mobStances.forEach((team, ms) -> tag.putString("MobStance_" + team.getName(), ms.getName()));

        CompoundTag armyTag = new CompoundTag();
        this.armyChunks.forEach((key, set) -> {
            long[] arr = new long[set.size()];
            int i = 0;
            for (long c : set) arr[i++] = c;
            armyTag.putLongArray(key, arr);
        });
        tag.put("ArmyChunks", armyTag);

        CompoundTag areasTag = new CompoundTag();
        this.groupAreas.forEach((key, set) -> {
            long[] arr = new long[set.size()];
            int i = 0;
            for (long c : set) arr[i++] = c;
            areasTag.putLongArray(key, arr);
        });
        tag.put("GroupAreas", areasTag);

        return tag;
    }
}

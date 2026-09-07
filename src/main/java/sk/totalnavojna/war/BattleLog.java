package sk.totalnavojna.war;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.config.CommonConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Rolling record of what happened, so a commander who was busy elsewhere can read back the battle,
// and so the end of a match can print an after-action report instead of just going quiet.
public final class BattleLog {

    public static class Entry {
        public final long gameTime;
        public final Team team;      // null = neutral / both
        public final String text;

        Entry(long gameTime, Team team, String text) {
            this.gameTime = gameTime;
            this.team = team;
            this.text = text;
        }
    }

    private static final int MAX = 300;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    // running totals for the after-action report
    private static final Map<Team, int[]> TALLY = new EnumMap<>(Team.class);
    private static final int T_KILLS = 0;
    private static final int T_LOSSES = 1;
    private static final int T_CAPTURES = 2;
    private static final int T_NEXUS_HITS = 3;
    private static final int T_DRONE_HITS = 4;

    private BattleLog() {
    }

    private static int[] tally(Team team) {
        return TALLY.computeIfAbsent(team, (k) -> new int[5]);
    }

    public static void reset() {
        ENTRIES.clear();
        TALLY.clear();
    }

    public static void add(MinecraftServer server, Team team, String text) {
        if (!CommonConfig.BATTLE_LOG.get()) return;
        long now = server.overworld().getGameTime();
        ENTRIES.addLast(new Entry(now, team, text));
        while (ENTRIES.size() > MAX) ENTRIES.removeFirst();
    }

    public static void kill(MinecraftServer server, Team killerTeam, String killer, String victim) {
        if (killerTeam != null) tally(killerTeam)[T_KILLS]++;
        add(server, killerTeam, "§7" + killer + " §fzabil §7" + victim);
    }

    public static void loss(MinecraftServer server, Team team, String name) {
        if (team != null) tally(team)[T_LOSSES]++;
        add(server, team, "§c☠ §7" + name + " §cpadol");
    }

    public static void capture(MinecraftServer server, Team team, String where) {
        if (team != null) tally(team)[T_CAPTURES]++;
        add(server, team, "§a⚑ Bod " + where + " obsadený");
    }

    public static void nexusHit(MinecraftServer server, Team attacker) {
        if (attacker != null) tally(attacker)[T_NEXUS_HITS]++;
    }

    public static void droneHit(MinecraftServer server, Team team) {
        if (team != null) tally(team)[T_DRONE_HITS]++;
        add(server, team, "§d✈ Dron zasiahol cieľ");
    }

    public static List<Entry> recent(int count) {
        List<Entry> all = new ArrayList<>(ENTRIES);
        int from = Math.max(0, all.size() - count);
        return all.subList(from, all.size());
    }

    // "/totalnavojna log" - the caller sees his own team's traffic plus anything neutral.
    public static void print(ServerPlayer player, int count) {
        Team myTeam = TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
        List<Entry> shown = new ArrayList<>();
        for (Entry e : recent(MAX)) {
            if (e.team == null || e.team == myTeam) shown.add(e);
        }
        int from = Math.max(0, shown.size() - count);
        player.sendSystemMessage(Component.literal("§6=== DENNÍK BOJA ==="));
        if (shown.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7Zatiaľ nič."));
            return;
        }
        long now = player.serverLevel().getGameTime();
        for (Entry e : shown.subList(from, shown.size())) {
            long agoSec = Math.max(0, (now - e.gameTime) / 20);
            player.sendSystemMessage(Component.literal("§8[-" + agoSec + "s] §r" + e.text));
        }
    }

    // After-action report: printed to everyone when a match ends.
    public static void afterAction(MinecraftServer server, Team winner, String reason) {
        WarGameData data = WarGameData.get(server.overworld());
        WarGameData.broadcast(server, "§6==================================");
        WarGameData.broadcast(server, "§6  ZÁVEREČNÁ SPRÁVA O BOJI");
        WarGameData.broadcast(server, "§7  " + reason);
        WarGameData.broadcast(server, winner == null
                ? "§eVýsledok: §fremíza"
                : "§eVíťaz: §a" + winner.getDisplayName().toUpperCase());
        for (Team team : Team.VALUES) {
            int[] t = tally(team);
            WarGameData.broadcast(server, "§e" + team.getDisplayName().toUpperCase()
                    + " §f— zabití §a" + t[T_KILLS]
                    + "§f, straty §c" + t[T_LOSSES]
                    + "§f, body §b" + t[T_CAPTURES]
                    + "§f, zásahy NEXUSu §6" + t[T_NEXUS_HITS]
                    + "§f, nálety §d" + t[T_DRONE_HITS]
                    + "§f, tikety §b" + data.getTickets(team));
        }
        // top three soldiers by kills
        List<Map.Entry<String, WarGameData.SoldierStats>> sorted = new ArrayList<>(data.stats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills));
        int shown = 0;
        for (Map.Entry<String, WarGameData.SoldierStats> e : sorted) {
            if (e.getValue().kills <= 0 || shown++ >= 3) break;
            WarGameData.broadcast(server, "§7  ★ " + e.getKey().replace("/", " §f")
                    + " §a⚔" + e.getValue().kills + " §c☠" + e.getValue().deaths);
        }
        WarGameData.broadcast(server, "§6==================================");
    }
}

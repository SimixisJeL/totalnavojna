package sk.totalnavojna;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketOpenConfig;
import sk.totalnavojna.network.PacketSyncTeam;
import sk.totalnavojna.war.BattleLog;
import sk.totalnavojna.war.MatchManager;
import sk.totalnavojna.war.MatchPhase;
import sk.totalnavojna.war.MobStance;
import sk.totalnavojna.war.Stance;
import sk.totalnavojna.war.WarEvents;
import sk.totalnavojna.war.WarGameData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TotalnaVojnaCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("totalnavojna")
                .then(Commands.literal("join")
                        .then(Commands.literal("oliva").executes(ctx -> join(ctx.getSource(), Team.OLIVA)))
                        .then(Commands.literal("piesok").executes(ctx -> join(ctx.getSource(), Team.PIESOK))))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(Commands.literal("bleedout").executes(ctx -> bleedout(ctx.getSource())))
                .then(Commands.literal("resetorders").executes(ctx -> resetOrders(ctx.getSource())))
                .then(Commands.literal("stance")
                        .then(Commands.literal("war").executes(ctx -> stance(ctx.getSource(), Stance.WAR)))
                        .then(Commands.literal("truce").executes(ctx -> stance(ctx.getSource(), Stance.TRUCE)))
                        .then(Commands.literal("passive").executes(ctx -> stance(ctx.getSource(), Stance.PASSIVE)))
                        .then(Commands.literal("alliance").executes(ctx -> stance(ctx.getSource(), Stance.ALLIANCE))))
                .then(Commands.literal("mobs")
                        .then(Commands.literal("neutral").executes(ctx -> mobs(ctx.getSource(), MobStance.NEUTRAL)))
                        .then(Commands.literal("hostile").executes(ctx -> mobs(ctx.getSource(), MobStance.HOSTILE)))
                        .then(Commands.literal("all").executes(ctx -> mobs(ctx.getSource(), MobStance.ALL))))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), -1))
                        .then(Commands.argument("priprava", IntegerArgumentType.integer(0, 3600))
                                .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "priprava")))))
                .then(Commands.literal("stop").executes(ctx -> stopMatch(ctx.getSource())))
                .then(Commands.literal("audio")
                        .executes(ctx -> audio(ctx.getSource(), null))
                        .then(Commands.argument("zapnute", BoolArgumentType.bool())
                                .executes(ctx -> audio(ctx.getSource(), BoolArgumentType.getBool(ctx, "zapnute")))))
                .then(Commands.literal("log")
                        .executes(ctx -> log(ctx.getSource(), 15))
                        .then(Commands.argument("pocet", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> log(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "pocet")))))
                .then(Commands.literal("config").executes(ctx -> config(ctx.getSource()))));
        // short alias for downed players
        dispatcher.register(Commands.literal("bleedout").executes(ctx -> bleedout(ctx.getSource())));
    }

    // Starts a match: fresh stats, tickets refilled, PREP countdown, then COMBAT.
    private static int start(CommandSourceStack source, int prepSeconds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        int prep = prepSeconds >= 0 ? prepSeconds : sk.totalnavojna.config.CommonConfig.PREP_SECONDS.get();
        MatchManager.start(player.getServer(), prep);
        return 1;
    }

    private static int stopMatch(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        MatchManager.stop(player.getServer());
        return 1;
    }

    // Per-player mute for the dubbed radio traffic and soldier one-liners.
    private static int audio(CommandSourceStack source, Boolean on) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        boolean nowOn = on != null ? on : !sk.totalnavojna.war.VoiceLines.audioOn(player.getUUID());
        sk.totalnavojna.war.VoiceLines.setAudio(player.getUUID(), nowOn);
        source.sendSuccess(() -> Component.literal(nowOn
                ? "§aHlasy a rádio ZAPNUTÉ."
                : "§7Hlasy a rádio VYPNUTÉ. Späť: §f/totalnavojna audio true"), false);
        return 1;
    }

    private static int log(CommandSourceStack source, int count) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        BattleLog.print(player, count);
        return 1;
    }

    private static int stance(CommandSourceStack source, Stance stance) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Team team = TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.translatable("chat.totalnavojna.status.none"), false);
            return 0;
        }
        WarGameData.get(player.serverLevel()).setStance(player.getServer(), team, stance);
        return 1;
    }

    private static int mobs(CommandSourceStack source, MobStance stance) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Team team = TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.translatable("chat.totalnavojna.status.none"), false);
            return 0;
        }
        WarGameData.get(player.serverLevel()).setMobStance(player.getServer(), team, stance);
        return 1;
    }

    // Clears orders on every unit of the caller's team, wherever they are - the get-out-of-jail button for a stuck unit.
    private static int resetOrders(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Team team = TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.translatable("chat.totalnavojna.status.none"), false);
            return 0;
        }
        int n = 0;
        for (sk.totalnavojna.entities.SwatEntity soldier : player.serverLevel().getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(sk.totalnavojna.entities.SwatEntity.class),
                (e) -> e.getArmyTeam() == team && e.getState() == sk.totalnavojna.entities.SwatEntity.STATE_ALIVE)) {
            soldier.resetOrders();
            n++;
        }
        final int count = n;
        source.sendSuccess(() -> Component.literal("§aRozkazy zresetované: " + count + " jednotiek stojí a čaká."), false);
        return 1;
    }

    private static int bleedout(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (WarEvents.bleedOut(player)) {
            source.sendSuccess(() -> Component.literal("§c☠ Vykrvácal si."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("§7Nie si downnutý."), false);
        return 0;
    }

    private static int config(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ModNetworking.sendToPlayer(new PacketOpenConfig(), player);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        WarGameData data = WarGameData.get(player.serverLevel());

        source.sendSuccess(() -> Component.literal("§6=== ŠTATISTIKY VOJNY ==="), false);
        for (Team team : Team.VALUES) {
            String defeated = data.isDefeated(team) ? " §c[PORAZENÝ]" : "";
            source.sendSuccess(() -> Component.literal("§e" + team.getDisplayName().toUpperCase()
                    + "§f — smrti: §c" + data.getTeamDeaths(team)
                    + "§f, NEXUS: §b" + data.countCores(team) + "§f (životy §b" + data.totalCoreLives(team) + "§f)" + defeated), false);
        }

        List<Map.Entry<String, WarGameData.SoldierStats>> sorted = new ArrayList<>(data.stats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills));
        int shown = 0;
        for (Map.Entry<String, WarGameData.SoldierStats> e : sorted) {
            if (shown++ >= 15) break;
            final String line = "§7" + e.getKey().replace("/", " §f") + " §a⚔" + e.getValue().kills + " §c☠" + e.getValue().deaths;
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (sorted.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Zatiaľ žiadne záznamy."), false);
        }
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        WarGameData.get(player.serverLevel()).resetGame(player.getServer());
        WarEvents.resetScoreboard(player.getServer());
        WarEvents.updateScoreboard(player.getServer());
        return 1;
    }

    private static int join(CommandSourceStack source, Team team) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TeamsSavedData.get(player.serverLevel()).setTeam(player.getUUID(), team);
        ModNetworking.sendToPlayer(PacketSyncTeam.of(team), player);
        source.sendSuccess(() -> Component.translatable("chat.totalnavojna.join", team.getDisplayName().toUpperCase()), true);
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TeamsSavedData.get(player.serverLevel()).setTeam(player.getUUID(), null);
        ModNetworking.sendToPlayer(PacketSyncTeam.of(null), player);
        source.sendSuccess(() -> Component.translatable("chat.totalnavojna.leave"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Team team = TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.translatable("chat.totalnavojna.status.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("chat.totalnavojna.status", team.getDisplayName().toUpperCase()), false);
            WarGameData data = WarGameData.get(player.serverLevel());
            for (Team t : Team.VALUES) {
                source.sendSuccess(() -> Component.literal("§e" + t.getDisplayName().toUpperCase() + "§f → " + t.enemy().getDisplayName().toUpperCase() + ": " + data.getStance(t).getLabel()
                        + "§f, moby: " + data.getMobStance(t).getLabel()), false);
            }
            MatchPhase phase = data.getPhase();
            source.sendSuccess(() -> Component.literal("§fZápas: " + phase.getLabel()
                    + (phase == MatchPhase.PREP ? " §7(" + Math.max(0, (data.phaseEndsAt - player.serverLevel().getGameTime()) / 20) + " s)" : "")), false);
            if (!data.captures.isEmpty() || sk.totalnavojna.config.CommonConfig.START_TICKETS.get() > 0) {
                for (Team t : Team.VALUES) {
                    source.sendSuccess(() -> Component.literal("§e" + t.getDisplayName().toUpperCase()
                            + "§f — body §b" + MatchManager.countPoints(data, t)
                            + "§f, tikety §b" + data.getTickets(t)
                            + "§f, základne §b" + data.countSpawners(t)), false);
                }
            }
            List<String> fallen = data.fallenOf(team);
            if (!fallen.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§8Definitívne padlí (" + fallen.size() + "): " + String.join(", ", fallen)), false);
            }
            source.sendSuccess(() -> Component.literal("§7/totalnavojna start [s] · stop · audio true|false · log [n]"), false);
            source.sendSuccess(() -> Component.literal("§7/totalnavojna stance war|truce|passive|alliance · mobs neutral|hostile|all"), false);
        }
        return 1;
    }
}

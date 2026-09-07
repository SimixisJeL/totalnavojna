package sk.totalnavojna.war;

import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.blocks.CoreBlock;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketSyncDowned;

import java.util.*;

@Mod.EventBusSubscriber(modid = TotalnaVojna.MOD_ID)
public class WarEvents {

    // Downed player state = the patient side of the revive channel.
    public static class DownedPlayer implements ReviveHelper.Channel {
        public final ServerPlayer player;
        public int ticksLeft;
        public Vec3 downPos;
        private UUID reviver;
        private int reviveTicks;
        private long heartbeat;

        DownedPlayer(ServerPlayer player) {
            this.player = player;
            this.downPos = player.position();
        }

        @Override
        @Nullable
        public UUID getReviverUUID() {
            return this.reviver;
        }

        @Override
        public void setReviverUUID(@Nullable UUID uuid) {
            this.reviver = uuid;
        }

        @Override
        public int getReviveTicks() {
            return this.reviveTicks;
        }

        @Override
        public void setReviveTicks(int ticks) {
            this.reviveTicks = ticks;
        }

        @Override
        public long getReviveHeartbeat() {
            return this.heartbeat;
        }

        @Override
        public void setReviveHeartbeat(long gameTime) {
            this.heartbeat = gameTime;
        }

        @Override
        public void completeRevive() {
            revivePlayer(this.player);
        }

        @Override
        public String getPatientName() {
            return this.player.getGameProfile().getName();
        }
    }

    private static class CoreAttack {
        BlockPos pos;
        int progress;
        long lastTick;
        int lastStage = -1;
    }

    private static final Map<UUID, DownedPlayer> DOWNED_PLAYERS = new HashMap<>();
    private static final Set<UUID> DEATH_BYPASS = new HashSet<>();
    private static final Map<UUID, CoreAttack> CORE_ATTACKS = new HashMap<>();
    private static final String SCOREBOARD_NAME = "totalnavojna";

    public static boolean isDowned(ServerPlayer player) {
        return DOWNED_PLAYERS.containsKey(player.getUUID());
    }

    @Nullable
    public static DownedPlayer getDowned(ServerPlayer player) {
        return DOWNED_PLAYERS.get(player.getUUID());
    }

    // ===== player down =====

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isDowned(player)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (DEATH_BYPASS.remove(player.getUUID())) {
                clearDowned(player, false);
                return;
            }

            if (DOWNED_PLAYERS.containsKey(player.getUUID())) {
                if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                    clearDowned(player, false);
                    return;
                }
                // downed players can not be finished off - only the timer / bleedout kills them
                event.setCanceled(true);
                player.setHealth(1.0f);
                return;
            }

            if (!CommonConfig.ALLOW_DOWN.get()) return;
            if (player.isCreative() || player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
            if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            Team team = TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
            if (team == null) return;

            event.setCanceled(true);
            player.setHealth(1.0f);
            DownedPlayer d = new DownedPlayer(player);
            d.ticksLeft = CommonConfig.DOWN_TIME.get();
            DOWNED_PLAYERS.put(player.getUUID(), d);
            applyDownedState(player);
            broadcastDowned();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§c☠ SI DOWNNUTÝ!")));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("§eLež a čakaj na medkit… alebo napíš /bleedout")));
        }
    }

    private static void applyDownedState(ServerPlayer player) {
        player.setForcedPose(Pose.SWIMMING);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 9, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 5, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 5, false, false));
        player.setSprinting(false);
        player.stopUsingItem();
    }

    private static void clearDowned(ServerPlayer player, boolean notify) {
        DOWNED_PLAYERS.remove(player.getUUID());
        player.setForcedPose(null);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);
        if (notify) broadcastDowned();
    }

    private static void broadcastDowned() {
        ModNetworking.sendToAll(new PacketSyncDowned(DOWNED_PLAYERS.keySet()));
    }

    public static void revivePlayer(ServerPlayer player) {
        clearDowned(player, true);
        player.setHealth(10.0f);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§a✚ OŽIVENÝ")));
    }

    // /bleedout
    public static boolean bleedOut(ServerPlayer player) {
        if (!isDowned(player)) return false;
        clearDowned(player, true);
        DEATH_BYPASS.add(player.getUUID());
        player.kill();
        return true;
    }

    @SubscribeEvent
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isDowned(player)) {
            clearDowned(player, true);
            player.setHealth(Math.max(player.getHealth(), 10.0f));
            player.displayClientMessage(Component.literal("§7Down stav zrušený (zmena herného módu)."), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (DOWNED_PLAYERS.remove(player.getUUID()) != null) broadcastDowned();
            CORE_ATTACKS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!isDowned(player)) player.setForcedPose(null);
            ModNetworking.sendToPlayer(new PacketSyncDowned(DOWNED_PLAYERS.keySet()), player);
        }
    }

    // --- a downed player can do nothing: no interaction, no attacks, no item use, no dropping ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent event) {
        if (!event.isCancelable()) return;
        if (event.getEntity() instanceof ServerPlayer player && isDowned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isDowned(player)) {
            event.setCanceled(true);
            return;
        }
        // punching a downed unit with a medkit in hand is the revive gesture, not an attack
        if (event.getTarget() instanceof LivingEntity target && ReviveHelper.channelOf(target) != null
                && ReviveHelper.hasMedkit(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isDowned(player)) {
            event.setCanceled(true);
            if (!player.getInventory().add(event.getEntity().getItem())) {
                player.drop(event.getEntity().getItem(), false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && isDowned(player)) {
            event.setCanceled(true);
        }
    }

    // downed players cannot shoot
    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getShooter() instanceof ServerPlayer player && isDowned(player)) {
            event.setCanceled(true);
        }
    }

    // ===== mobs vs soldiers =====

    // Monsters hunt soldiers whose team is HOSTILE / ALL towards mobs (goal added on every join, predicate checks the live stance).
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster monster)) return;
        monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, SwatEntity.class, 10, true, false,
                (le) -> le instanceof SwatEntity s && s.getState() == SwatEntity.STATE_ALIVE
                        && s.level() instanceof ServerLevel sl && WarGameData.get(sl).getMobStance(s.getArmyTeam()) != sk.totalnavojna.war.MobStance.NEUTRAL));
    }

    // ===== player attacking a NEXUS (heartbeat from the client while holding LMB on the block) =====

    public static void handleCoreAttack(ServerPlayer player, BlockPos pos) {
        if (isDowned(player)) return;
        ServerLevel level = player.serverLevel();
        if (pos.distSqr(player.blockPosition()) > 6.0 * 6.0) return;
        BlockState state = level.getBlockState(pos);
        boolean isCore = state.getBlock() instanceof CoreBlock;
        boolean isFob = state.getBlock() instanceof sk.totalnavojna.blocks.SpawnerBlock
                && CommonConfig.SPAWNER_LIVES.get() > 0;
        if (!isCore && !isFob) return;
        Team blockTeam = isCore ? ((CoreBlock) state.getBlock()).getTeam()
                : ((sk.totalnavojna.blocks.SpawnerBlock) state.getBlock()).getTeam();
        Team team = TeamsSavedData.get(level).getTeam(player.getUUID());
        if (team == null) {
            player.displayClientMessage(Component.literal("§cNie si v tíme (/totalnavojna join …)."), true);
            return;
        }
        if (blockTeam == team) return;
        WarGameData data = WarGameData.get(level);
        WarGameData.CoreEntry core = isCore ? data.coreAt(level, pos) : null;
        WarGameData.SpawnerEntry fob = isFob ? data.spawnerAt(level, pos) : null;
        if (core == null && fob == null) return;
        int livesLeft = core != null ? core.lives : fob.lives;

        long now = level.getGameTime();
        CoreAttack attack = CORE_ATTACKS.computeIfAbsent(player.getUUID(), k -> new CoreAttack());
        if (!pos.equals(attack.pos)) {
            attack.pos = pos.immutable();
            attack.progress = 0;
            attack.lastStage = -1;
        }
        if (attack.lastTick == now) return;
        attack.lastTick = now;
        attack.progress++;
        int required = core != null ? CommonConfig.CORE_HIT_TICKS.get() : Math.max(1, CommonConfig.CORE_HIT_TICKS.get() / 2);
        int stage = Math.min(9, attack.progress * 10 / required);
        if (stage != attack.lastStage) {
            attack.lastStage = stage;
            level.destroyBlockProgress(player.getId(), pos, stage);
        }
        if (attack.progress >= required) {
            attack.progress = 0;
            attack.lastStage = -1;
            level.destroyBlockProgress(player.getId(), pos, -1);
            if (core != null) data.takeCoreLife(level, core, player);
            else data.takeSpawnerLife(level, fob);
            player.swing(player.getUsedItemHand(), true);
        }
        String what = core != null ? "NEXUS" : "ZÁKLADŇU";
        player.displayClientMessage(Component.literal("§6⚔ Útok na " + what + " " + blockTeam.getDisplayName().toUpperCase()
                + " §7" + (attack.progress * 100 / required) + "% §f| životy: §c" + Math.max(0, livesLeft)), true);
    }

    // A fresh soldier walks to his group's gathering point instead of milling around the base.
    public static void applyRally(ServerLevel level, WarGameData data, SwatEntity soldier) {
        BlockPos rally = data.getRally(soldier.getArmyTeam(), soldier.getGroup());
        if (rally == null) return;
        BlockPos target = MatchManager.surface(level, rally);
        soldier.setMoveToTarget(Vec3.atBottomCenterOf(target));
        soldier.setOrder(sk.totalnavojna.orders.OrderType.MOVE_TO_POSITION);
        soldier.markOrderFocus();
    }

    private static void creditKill(Entity killer, SwatEntity victim) {
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;
        WarGameData data = WarGameData.get(serverLevel);
        String victimName = victim.hasCustomName() ? victim.getCustomName().getString() : "Vojak";
        if (killer instanceof SwatEntity soldier && soldier.hasCustomName()) {
            String killerName = soldier.getCustomName().getString();
            data.recordKill(soldier.getArmyTeam(), killerName);
            BattleLog.kill(serverLevel.getServer(), soldier.getArmyTeam(), killerName, victimName);
            sk.totalnavojna.war.VoiceLines.speak(serverLevel, soldier.getArmyTeam(), soldier, sk.totalnavojna.war.VoiceLines.Line.KILL);
        } else if (killer instanceof ServerPlayer player) {
            String killerName = player.getGameProfile().getName();
            data.recordPlayerKill(killerName);
            Team killerTeam = TeamsSavedData.get(serverLevel).getTeam(player.getUUID());
            BattleLog.kill(serverLevel.getServer(), killerTeam, killerName, victimName);
        }
    }

    public static void onSoldierFinalDeath(SwatEntity soldier, Entity killer) {
        if (!(soldier.level() instanceof ServerLevel serverLevel)) return;
        WarGameData data = WarGameData.get(serverLevel);
        String name = soldier.hasCustomName() ? soldier.getCustomName().getString() : "Vojak";
        creditKill(killer, soldier);
        data.recordSoldierDeath(serverLevel, soldier.getArmyTeam(), soldier.getRole(), soldier.getGroup(), name, soldier.blockPosition());
        updateScoreboard(serverLevel.getServer());
    }

    // ===== tick: respawn queue + downed players + core attacks =====

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        if (event.getServer() != null) {
            for (ServerLevel level : event.getServer().getAllLevels()) sk.totalnavojna.war.DroneLink.releaseAll(level);
            sk.totalnavojna.war.ArmyChunkKeeper.releaseAll(event.getServer());
        }
        BattleIntel.clear();
        sk.totalnavojna.war.DroneOps.clear();
        sk.totalnavojna.war.SquadTactics.clear();
        sk.totalnavojna.war.VoiceLines.clear();
        BattleLog.reset();
        DOWNED_PLAYERS.clear();
        CORE_ATTACKS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        BattleIntel.tick(server.overworld());
        sk.totalnavojna.war.ArmyChunkKeeper.tick(server);
        MatchManager.tick(server);

        // respawn queue
        WarGameData data = WarGameData.get(server.overworld());
        if (!data.respawnQueue.isEmpty()) {
            Iterator<WarGameData.RespawnEntry> it = data.respawnQueue.iterator();
            while (it.hasNext()) {
                WarGameData.RespawnEntry r = it.next();
                r.ticksLeft--;
                if (r.ticksLeft > 0) continue;
                it.remove();
                data.setDirty();

                ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(r.dim)));
                if (level == null) continue;
                WarGameData.SpawnerEntry spawner = data.nearestSpawner(r.dim, r.team, new BlockPos(r.x, 64, r.z));
                if (spawner == null) continue;

                SwatEntity soldier = SwatEntity.withTeam(level, r.team);
                soldier.setRole(r.role);
                soldier.setGroup(r.group);
                soldier.setCustomName(Component.literal(r.name));
                soldier.setCustomNameVisible(true);
                BlockPos scatterPos = findScatterPos(level, spawner.pos);
                soldier.setPos(scatterPos.getX() + 0.5, scatterPos.getY(), scatterPos.getZ() + 0.5);
                net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(soldier, level, level.getCurrentDifficultyAt(spawner.pos), MobSpawnType.MOB_SUMMONED, null, null);
                level.addFreshEntity(soldier);
                applyRally(level, data, soldier);
                sk.totalnavojna.war.VoiceLines.speak(level, r.team, soldier, sk.totalnavojna.war.VoiceLines.Line.SPAWN);
            }
        }

        // downed players
        if (!DOWNED_PLAYERS.isEmpty()) {
            List<UUID> toKill = new ArrayList<>();
            for (Map.Entry<UUID, DownedPlayer> e : new ArrayList<>(DOWNED_PLAYERS.entrySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                if (player == null) {
                    DOWNED_PLAYERS.remove(e.getKey());
                    continue;
                }
                DownedPlayer d = e.getValue();
                d.ticksLeft--;
                long now = player.serverLevel().getGameTime();
                ReviveHelper.tickTimeout(d, now);

                if (player.tickCount % 40 == 0) {
                    applyDownedState(player);
                }
                // hard lock: lying on the ground, no containers
                if (player.position().distanceToSqr(d.downPos) > 1.0) {
                    player.connection.teleport(d.downPos.x, d.downPos.y, d.downPos.z, player.getYRot(), player.getXRot());
                }
                if (player.containerMenu != player.inventoryMenu) {
                    player.closeContainer();
                }
                if (d.ticksLeft % 20 == 0) {
                    String reviving = d.getReviverUUID() != null && now - d.getReviveHeartbeat() <= ReviveHelper.HEARTBEAT_TIMEOUT
                            ? " §a| oživujú ťa…" : "";
                    player.displayClientMessage(Component.literal("§c☠ Downnutý — smrť o " + (d.ticksLeft / 20) + "s" + reviving + " §7(/bleedout = vzdať sa)"), true);
                }

                if (d.ticksLeft <= 0) {
                    toKill.add(e.getKey());
                }
            }
            for (UUID uuid : toKill) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    clearDowned(player, true);
                    DEATH_BYPASS.add(uuid);
                    player.kill();
                }
            }
        }

        // stale core attacks (player let go of the button)
        if (!CORE_ATTACKS.isEmpty()) {
            long now = server.overworld().getGameTime();
            Iterator<Map.Entry<UUID, CoreAttack>> it = CORE_ATTACKS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, CoreAttack> e = it.next();
                if (now - e.getValue().lastTick > 5) {
                    ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                    if (player != null && e.getValue().pos != null) {
                        player.serverLevel().destroyBlockProgress(player.getId(), e.getValue().pos, -1);
                    }
                    it.remove();
                }
            }
        }
    }

    // Random safe position within 5 blocks of the spawner: 2 air blocks, solid non-fluid ground (no wall, no void, no lava).
    public static BlockPos findScatterPos(ServerLevel level, BlockPos base) {
        net.minecraft.util.RandomSource rng = level.random;
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = rng.nextInt(11) - 5;
            int dz = rng.nextInt(11) - 5;
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos feet = new BlockPos(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                BlockPos below = feet.below();
                if (!level.getBlockState(below).isSolidRender(level, below)) continue;
                if (!level.getFluidState(below).isEmpty()) continue;
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
                if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
                if (!level.getFluidState(feet).isEmpty()) continue;
                return feet;
            }
        }
        return base.above();
    }

    // ===== scoreboard =====

    public static void updateScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(SCOREBOARD_NAME);

        if (!CommonConfig.SCOREBOARD_ENABLED.get()) {
            if (objective != null) scoreboard.removeObjective(objective);
            return;
        }

        if (objective == null) {
            objective = scoreboard.addObjective(SCOREBOARD_NAME, ObjectiveCriteria.DUMMY,
                    Component.literal("§6⚔ TOTÁLNA VOJNA"), ObjectiveCriteria.RenderType.INTEGER);
        }
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);

        WarGameData data = WarGameData.get(server.overworld());
        objective.setDisplayName(Component.literal("§6⚔ TOTÁLNA VOJNA §r").append(MatchManager.statusLine(data)));
        for (Team team : Team.VALUES) {
            scoreboard.getOrCreatePlayerScore("☠ " + team.getDisplayName().toUpperCase(), objective).setScore(data.getTeamDeaths(team));
            scoreboard.getOrCreatePlayerScore("⬥ NEXUS " + team.getDisplayName().toUpperCase(), objective).setScore(data.totalCoreLives(team));
            if (!data.captures.isEmpty()) {
                scoreboard.getOrCreatePlayerScore("⚑ BODY " + team.getDisplayName().toUpperCase(), objective).setScore(MatchManager.countPoints(data, team));
            }
            if (CommonConfig.START_TICKETS.get() > 0) {
                scoreboard.getOrCreatePlayerScore("▣ TIKETY " + team.getDisplayName().toUpperCase(), objective).setScore(data.getTickets(team));
            }
        }
    }

    public static void resetScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(SCOREBOARD_NAME);
        if (objective != null) scoreboard.removeObjective(objective);
    }
}

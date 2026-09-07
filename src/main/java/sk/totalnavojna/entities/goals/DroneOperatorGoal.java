package sk.totalnavojna.entities.goals;

import com.atsuishio.superbwarfare.data.CustomData;
import com.atsuishio.superbwarfare.data.drone_attachment.DroneAttachmentData;
import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;
import sk.totalnavojna.war.BattleIntel;
import sk.totalnavojna.war.DroneLink;
import sk.totalnavojna.war.DroneOps;
import sk.totalnavojna.war.TerrainScan;
import sk.totalnavojna.war.WarGameData;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

// DRONE OPERATOR: launches a Superb Warfare drone armed with a C4 kamikaze charge and flies it.
// Modes (from the commander's orders):
//   NONE / FREE_FIRE      = automatic: strike the best enemy in range (vehicles > groups > players > single soldiers)
//   CEASE_FIRE            = no launches; a flying drone comes home and lands
//   DRONE_STRIKE_TARGET   = strike the designated entity      (attackTargetId)
//   DRONE_STRIKE_POSITION = strike the designated position    (moveToTarget)
//   DRONE_SCOUT           = hover over a position, report + mark enemies on the map
//   DRONE_RECALL          = come back to the operator and land (drone is kept for the next launch)
//
// Flight: the goal drives the drone's velocity directly every tick (SBW's own input model is pulse-based and made the
// drone orbit its target); SBW inputs are only used for the engine sound / body tilt. Terminal phase = straight line
// into the target at full speed, and a guaranteed detonation (destroy() -> kamikaze explosion) when it gets within reach.
public class DroneOperatorGoal extends Goal {
    public static final ResourceLocation DRONE_ITEM_ID = new ResourceLocation("superbwarfare", "drone");
    public static final ResourceLocation MONITOR_ITEM_ID = new ResourceLocation("superbwarfare", "monitor");
    private static final String WARHEAD_ID = "superbwarfare:c4_bomb";
    private static final double CRUISE_SPEED = 0.55;
    private static final double DIVE_SPEED = 0.95;
    private static final double TERMINAL_RADIUS = 8.0;
    private static final double DETONATE_RADIUS = 1.7;
    private static final double SCOUT_RADIUS = 32.0;
    private static final double FRIENDLY_SAFE_RADIUS = 6.0;
    private static final int IDLE_BEFORE_LANDING = 200;

    private enum Phase { TAKEOFF, CRUISE, TERMINAL, HOVER, LANDING }

    private final SwatEntity soldier;
    @Nullable
    private DroneEntity drone = null;
    @Nullable
    private UUID droneUUID = null;
    @Nullable
    private Entity target = null;
    private Phase phase = Phase.TAKEOFF;
    private int cooldown = 0;
    private int scanTimer = 0;
    private int idleTicks = 0;
    private int flightTicks = 0;
    private int terminalTicks = 0;
    private int spotTimer = 0;
    private int reportTimer = 0;
    private int friendlyWarnTimer = 0;
    private int linkWarnTimer = 0;
    private boolean finished = false;
    private int terminalRetries = 0;
    private Vec3 lastDivePos = Vec3.ZERO;

    public DroneOperatorGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    public static Item droneItem() {
        return ForgeRegistries.ITEMS.getValue(DRONE_ITEM_ID);
    }

    @Nullable
    public static Item monitorItem() {
        return ForgeRegistries.ITEMS.getValue(MONITOR_ITEM_ID);
    }

    private boolean hasDroneItem() {
        Item item = droneItem();
        return item != null && this.soldier.countItem(item) > 0;
    }

    // The operator flies unless the order actually requires him to walk somewhere. Listing what BLOCKS flying is far
    // safer than listing what allows it: an order missing from an allow-list silently grounds the whole role.
    private boolean droneDutyOrder() {
        return switch (this.soldier.getOrder()) {
            case MOVE_TO_POSITION, MOVE_AND_UNLOAD, MOVE_ALONG_PATH, PATROL_PATH,
                 FOLLOW_COMMANDER, FORM_WEDGE, FORM_COLUMN,
                 BOARD_VEHICLE, DISMOUNT_VEHICLE, RESUPPLY, RETREAT_TO_NEXUS, FULL_HEAL -> false;
            default -> true;
        };
    }

    private boolean manualOrder() {
        OrderType o = this.soldier.getOrder();
        return o == OrderType.DRONE_STRIKE_TARGET || o == OrderType.DRONE_STRIKE_POSITION || o == OrderType.DRONE_SCOUT || o == OrderType.DRONE_RECALL;
    }

    private boolean threatened() {
        LivingEntity hurtBy = this.soldier.getLastHurtByMob();
        if (hurtBy != null && this.soldier.tickCount - this.soldier.getLastHurtByMobTimestamp() < 60) return true;
        List<LivingEntity> close = this.soldier.level().getEntitiesOfClass(LivingEntity.class, this.soldier.getBoundingBox().inflate(8.0),
                (e) -> (e instanceof SwatEntity || e instanceof Player) && this.soldier.isValidTarget(e));
        return !close.isEmpty();
    }

    private boolean liveDrone(ServerLevel level) {
        if (this.droneUUID == null) return false;
        Entity e = level.getEntity(this.droneUUID);
        if (e instanceof DroneEntity d && d.isAlive()) {
            this.drone = d;
            return true;
        }
        this.droneUUID = null;
        this.drone = null;
        return false;
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getRole() != Role.DRONE_OPERATOR) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        if (this.soldier.isPassenger()) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.soldier.tickCount % 10 != 0) return false;
        OrderType order = this.soldier.getOrder();
        boolean live = liveDrone(level);
        if (!droneDutyOrder()) return false;
        if (order == OrderType.DRONE_RECALL) return live;
        if (order == OrderType.CEASE_FIRE) return live && !this.drone.onGround();
        if (manualOrder()) return live || hasDroneItem();
        if (threatened()) return false;
        this.target = findTarget(level);
        if (this.target == null) return false;
        // space the runs out, otherwise the first warhead takes the second drone down with it
        if (!live && DroneOps.launchBlocked(this.target.position(), level.getGameTime())) return false;
        return live || hasDroneItem();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.finished) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.drone == null || !this.drone.isAlive()) return false;
        if (this.soldier.isPassenger()) return false;
        if (this.flightTicks > 6000) return false;
        if (!droneDutyOrder()) return false;
        if (manualOrder() || this.soldier.getOrder() == OrderType.CEASE_FIRE) return true;
        return !threatened();
    }

    @Override
    public void start() {
        this.finished = false;
        this.flightTicks = 0;
        this.idleTicks = 0;
        this.scanTimer = 0;
        this.terminalTicks = 0;
        this.terminalRetries = 0;
        this.phase = Phase.TAKEOFF;
        this.soldier.getNavigation().stop();
        Item monitor = monitorItem();
        if (monitor != null) this.soldier.holdItem(monitor);
        if (this.drone == null || !this.drone.isAlive()) {
            this.drone = launchDrone();
        }
    }

    @Override
    public void stop() {
        if (this.drone != null && this.drone.isAlive()) {
            zeroInputs(this.drone);
            // hold position in the air instead of drifting away
            this.drone.setDeltaMovement(Vec3.ZERO);
        }
        this.soldier.restoreGun();
        boolean lost = this.drone == null || !this.drone.isAlive();
        if (this.soldier.level() instanceof ServerLevel sl && this.droneUUID != null && (lost || this.finished)) {
            DroneLink.release(sl, this.droneUUID);
        }
        if (lost || this.finished) {
            this.soldier.setDroneAloft(false);
            DroneOps.releaseAllOf(this.soldier.getUUID());
        }
        if (lost) {
            this.droneUUID = null;
            this.drone = null;
            this.cooldown = CommonConfig.DRONE_COOLDOWN.get();
            OrderType o = this.soldier.getOrder();
            if (o == OrderType.DRONE_STRIKE_TARGET || o == OrderType.DRONE_STRIKE_POSITION || o == OrderType.DRONE_RECALL) {
                this.soldier.setOrder(OrderType.NONE);
            }
        }
        this.target = null;
    }

    @Nullable
    private DroneEntity launchDrone() {
        ServerLevel level = (ServerLevel) this.soldier.level();
        Item item = droneItem();
        if (item == null) return null;
        int slot = -1;
        for (int i = 0; i < SwatEntity.SWAT_INVENTORY_SIZE; i++) {
            if (this.soldier.getItem(i).is(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return null;
        DroneEntity d = ModEntities.DRONE.get().create(level);
        if (d == null) return null;
        Vec3 look = this.soldier.getLookAngle();
        d.setPos(this.soldier.getX() + look.x * 1.5, this.soldier.getY() + 3.0, this.soldier.getZ() + look.z * 1.5);
        d.setYRot(this.soldier.getYRot());
        if (!armWarhead(d)) {
            TotalnaVojna.LOGGER.warn("[TV] Drone attachment {} not found - launching unarmed drone", WARHEAD_ID);
        }
        DroneLink.tagOwner(d, this.soldier.getArmyTeam(), this.soldier.getUUID());
        level.addFreshEntity(d);
        this.soldier.setDroneAloft(true);
        sk.totalnavojna.war.VoiceLines.radio(level, this.soldier.getArmyTeam(), sk.totalnavojna.war.VoiceLines.Line.DRONE_UP);
        Vec3 aim = this.target != null ? this.target.position() : this.soldier.position();
        DroneOps.noteLaunch(aim, level.getGameTime());
        if (this.target != null) {
            DroneOps.claim(DroneOps.keyOf(this.target.getUUID(), null), this.soldier.getUUID(), aim, level.getGameTime());
        }
        ItemStack stack = this.soldier.getItem(slot);
        stack.shrink(1);
        if (stack.isEmpty()) this.soldier.setItem(slot, ItemStack.EMPTY);
        this.droneUUID = d.getUUID();
        this.soldier.playSound(SoundEvents.BEACON_ACTIVATE, 0.6f, 1.8f);
        TotalnaVojna.LOGGER.info("[TV] {} launched a kamikaze drone ({})", this.soldier.getPatientName(), this.soldier.getOrder());
        return d;
    }

    // Mirrors what SBW does when a player mounts a C4 on the drone (see Combined Arms DroneSupport.armMortarShell).
    private boolean armWarhead(DroneEntity d) {
        DroneAttachmentData data = CustomData.DRONE_ATTACHMENT.get(WARHEAD_ID);
        if (data == null) return false;
        Item c4 = ForgeRegistries.ITEMS.getValue(new ResourceLocation(WARHEAD_ID));
        if (c4 != null) d.setCurrentItem(new ItemStack(c4, 1));
        d.getEntityData().set(DroneEntity.DISPLAY_ENTITY, data.displayEntity());
        d.setAmmo(1);
        d.getEntityData().set(DroneEntity.IS_KAMIKAZE, data.isKamikaze);
        d.getEntityData().set(DroneEntity.MAX_AMMO, data.count());
        float[] scale = data.scale();
        float[] offset = data.offset();
        float[] rotation = data.rotation();
        d.getEntityData().set(DroneEntity.DISPLAY_DATA, List.of(
                scale[0], scale[1], scale[2],
                offset[0], offset[1], offset[2],
                rotation[0], rotation[1], rotation[2],
                data.xLength, data.zLength,
                (float) data.tickCount
        ));
        return true;
    }

    // ===== targeting =====

    private boolean hasHostileCrew(VehicleEntity v) {
        for (Entity p : v.getPassengers()) {
            if (p instanceof LivingEntity le && this.soldier.isValidTarget(le)) return true;
        }
        return false;
    }

    private boolean isStrikeable(Entity e) {
        if (!e.isAlive()) return false;
        if (e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())) return false;
        if (e instanceof LivingEntity le && (e instanceof SwatEntity || e instanceof Player)) return this.soldier.isValidTarget(le);
        if (e instanceof VehicleEntity v) return !v.isWreck() && hasHostileCrew(v);
        return false;
    }

    private boolean isFriendly(Entity e) {
        if (e instanceof SwatEntity s) {
            return s.getState() != SwatEntity.STATE_DEAD && this.soldier.level() instanceof ServerLevel sl
                    && WarGameData.get(sl).isFriendly(this.soldier.getArmyTeam(), s.getArmyTeam());
        }
        if (e instanceof ServerPlayer p) {
            Team t = TeamsSavedData.get(p.serverLevel()).getTeam(p.getUUID());
            return t != null && this.soldier.level() instanceof ServerLevel sl && WarGameData.get(sl).isFriendly(this.soldier.getArmyTeam(), t);
        }
        return false;
    }

    private boolean friendliesNear(ServerLevel level, Vec3 point) {
        List<Entity> near = level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(point, point).inflate(FRIENDLY_SAFE_RADIUS), this::isFriendly);
        for (Entity e : near) {
            if (e.position().distanceToSqr(point) <= FRIENDLY_SAFE_RADIUS * FRIENDLY_SAFE_RADIUS) return true;
        }
        return false;
    }

    // Best target: vehicles first, then groups of enemies, then players, then lone soldiers - all scaled by distance.
    @Nullable
    private Entity findTarget(ServerLevel level) {
        double range = CommonConfig.DRONE_RANGE.get();
        Vec3 origin = this.drone != null && this.drone.isAlive() ? this.drone.position() : this.soldier.position();
        List<Entity> candidates = level.getEntities(this.soldier, this.soldier.getBoundingBox().inflate(range), this::isStrikeable);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        long now = level.getGameTime();
        for (Entity c : candidates) {
            double d = Math.sqrt(c.position().distanceToSqr(origin));
            if (d > range) continue;
            // somebody else is already flying at this - or at something right next to it
            if (DroneOps.claimedByOther(DroneOps.keyOf(c.getUUID(), null), this.soldier.getUUID(), now)) continue;
            if (DroneOps.areaTakenByOther(c.position(), this.soldier.getUUID(), now)) continue;
            double weight = 1.0;
            if (c instanceof VehicleEntity) weight = 0.35;
            else if (c instanceof Player) weight = 0.7;
            else {
                int cluster = 0;
                for (Entity o : candidates) {
                    if (o != c && o.position().distanceToSqr(c.position()) < 6.0 * 6.0) cluster++;
                }
                if (cluster >= 2) weight = 0.5;
            }
            // never pick something with our own people standing next to it
            if (friendliesNear(level, c.position())) weight *= 3.0;
            double score = d * weight;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best == null) {
            // nothing in sight: fly at what the team knows about
            BattleIntel.Contact c = BattleIntel.bestTarget(this.soldier.getArmyTeam(), origin, range);
            if (c != null) {
                Entity known = level.getEntity(c.id);
                if (known != null && isStrikeable(known)) best = known;
            }
        }
        return best;
    }

    // ===== flight primitives =====

    private static void zeroInputs(DroneEntity d) {
        d.setForwardInputDown(false);
        d.setBackInputDown(false);
        d.setLeftInputDown(false);
        d.setRightInputDown(false);
        d.setUpInputDown(false);
        d.setDownInputDown(false);
    }

    private static double surfaceY(ServerLevel level, double x, double z) {
        return TerrainScan.surfaceY(level, x, z);
    }

    // cruise altitude: above the terrain under the drone AND under the next few blocks of its route
    private double cruiseAltitude(ServerLevel level, DroneEntity d, double tx, double tz) {
        double alt = CommonConfig.DRONE_ALTITUDE.get();
        // clear the highest ground between here and the target, not just the ground underneath
        double best = TerrainScan.maxSurfaceAlong(level, d.getX(), d.getZ(), tx, tz, 8);
        return best + alt;
    }

    // Drive the drone straight towards a point. SBW's travel() damps velocity (x/z *0.965, y *0.7) and adds a little
    // rotor lift, so the vertical component is pre-compensated.
    private void flyToward(DroneEntity d, double tx, double ty, double tz, double speed) {
        double dx = tx - d.getX();
        double dy = ty - d.getY();
        double dz = tz - d.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double vx = 0, vz = 0;
        if (horiz > 0.3) {
            double h = Math.min(speed, horiz * 0.5);
            vx = dx / horiz * h;
            vz = dz / horiz * h;
            float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            d.setYRot(Mth.rotLerp(0.35f, d.getYRot(), yaw));
        }
        double vy = Mth.clamp(dy * 0.3, -0.5, 0.5);
        d.setDeltaMovement(vx, vy / 0.7, vz);
        // inputs only for looks and engine sound
        zeroInputs(d);
        d.setForwardInputDown(horiz > 1.0);
        if (dy > 1.0) d.setUpInputDown((d.tickCount % 4) < 2);
        else if (dy < -1.0) d.setDownInputDown((d.tickCount % 4) < 2);
    }

    // Terminal run: full speed straight into the target point, no altitude hold.
    private void diveInto(DroneEntity d, Vec3 point) {
        Vec3 dir = point.subtract(d.position());
        double len = dir.length();
        if (len < 1.0E-3) return;
        dir = dir.scale(1.0 / len);
        d.setDeltaMovement(dir.x * DIVE_SPEED, dir.y * DIVE_SPEED / 0.7, dir.z * DIVE_SPEED);
        float yaw = (float) (Math.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f;
        d.setYRot(yaw);
        d.setXRot((float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1, 1))));
        zeroInputs(d);
        d.setForwardInputDown(true);
        d.setDownInputDown(dir.y < -0.2);
    }

    // We run the warhead ourselves instead of leaning on SBW's kamikazeExplosion(): that one builds a CustomExplosion
    // around the drone's *player* controller, and an AI drone has none, so it went off with no damage and no terrain
    // damage at all. Here the blast is a real explosion (breaks blocks) plus an explicit damage pass, so infantry,
    // vehicles and buildings all feel it, and the kill is credited to the operator.
    private void detonate(DroneEntity d, String why) {
        TotalnaVojna.LOGGER.info("[TV] drone of {} detonating: {}", this.soldier.getPatientName(), why);
        Vec3 at = d.position();
        if (d.level() instanceof ServerLevel sl) {
            DroneLink.release(sl, d.getUUID());
            DroneOps.noteBlast(at, CommonConfig.DRONE_EXPLOSION_RADIUS.get(), sl.getGameTime());
            DroneOps.releaseAllOf(this.soldier.getUUID());
            d.discard();
            blast(sl, at);
        } else if (d.isAlive()) {
            d.discard();
        }
        this.drone = null;
        this.droneUUID = null;
        this.finished = true;
        this.cooldown = CommonConfig.DRONE_COOLDOWN.get();
    }

    private void blast(ServerLevel level, Vec3 at) {
        sk.totalnavojna.war.VoiceLines.radio(level, this.soldier.getArmyTeam(), sk.totalnavojna.war.VoiceLines.Line.DRONE_HIT);
        sk.totalnavojna.war.BattleLog.droneHit(level.getServer(), this.soldier.getArmyTeam());
        float radius = (float) (double) CommonConfig.DRONE_EXPLOSION_RADIUS.get();
        double damage = CommonConfig.DRONE_EXPLOSION_DAMAGE.get();
        boolean breakBlocks = CommonConfig.DRONE_BREAKS_BLOCKS.get();

        level.explode(this.soldier, null, null, at.x, at.y, at.z, radius, false,
                breakBlocks ? net.minecraft.world.level.Level.ExplosionInteraction.TNT : net.minecraft.world.level.Level.ExplosionInteraction.NONE);

        // explicit damage pass: vanilla explosion damage alone is unreliable against modded vehicles and armour
        net.minecraft.world.damagesource.DamageSource src = this.soldier.damageSources().explosion(this.soldier, this.soldier);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(at, at).inflate(radius);
        for (Entity e : level.getEntities((Entity) null, box, (x) -> x instanceof LivingEntity || x instanceof VehicleEntity)) {
            double dist = Math.sqrt(e.position().distanceToSqr(at));
            if (dist > radius) continue;
            float dmg = (float) (damage * (1.0 - dist / radius));
            if (dmg <= 0) continue;
            e.hurt(src, dmg);
        }
    }

    private void hover(DroneEntity d, double alt) {
        double dy = alt - d.getY();
        d.setDeltaMovement(d.getDeltaMovement().x * 0.5, Mth.clamp(dy * 0.3, -0.4, 0.4) / 0.7, d.getDeltaMovement().z * 0.5);
        zeroInputs(d);
        if (dy > 1.0) d.setUpInputDown((d.tickCount % 4) < 2);
    }

    private void warnCommander(ServerLevel level, String msg) {
        if (this.soldier.getOwnerUUID() == null) return;
        Player commander = level.getPlayerByUUID(this.soldier.getOwnerUUID());
        if (commander != null) commander.displayClientMessage(Component.literal(msg), true);
    }

    // Everything the drone sees gets marked on the commander's map and, now and then, reported in chat.
    private void spot(ServerLevel level, DroneEntity d) {
        if (--this.spotTimer > 0) return;
        this.spotTimer = 40;
        WarGameData data = WarGameData.get(level);
        List<Entity> seen = level.getEntities(d, d.getBoundingBox().inflate(SCOUT_RADIUS), this::isStrikeable);
        for (Entity e : seen) data.spot(this.soldier.getArmyTeam(), e);
        if (!seen.isEmpty() && --this.reportTimer <= 0 && this.soldier.getOwnerUUID() != null) {
            this.reportTimer = 5; // ~every 200 ticks
            Player commander = level.getPlayerByUUID(this.soldier.getOwnerUUID());
            if (commander != null) {
                commander.displayClientMessage(Component.literal("§b✈ Dron (" + this.soldier.getPatientName() + "): vidím " + seen.size()
                        + " nepriateľov pri X " + d.getBlockX() + " Z " + d.getBlockZ() + " §7(mapa M)"), false);
            }
        }
    }

    @Nullable
    private Vec3 strikePoint(ServerLevel level, OrderType order) {
        if (order == OrderType.DRONE_STRIKE_POSITION) {
            Vec3 p = this.soldier.getMoveToTarget();
            if (p == null || p == Vec3.ZERO) return null;
            double ty = surfaceY(level, p.x, p.z);
            return new Vec3(p.x, Math.max(p.y, ty) + 0.5, p.z);
        }
        Entity e = order == OrderType.DRONE_STRIKE_TARGET ? level.getEntity(this.soldier.getAttackTargetId()) : this.target;
        if (e == null || !e.isAlive()) return null;
        return new Vec3(e.getX(), e.getY() + Math.min(1.0, e.getBbHeight() * 0.5), e.getZ());
    }

    // fly home and land beside the operator; returns true when landed
    private boolean returnAndLand(ServerLevel level, DroneEntity d) {
        double dx = this.soldier.getX() - d.getX();
        double dz = this.soldier.getZ() - d.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > 3.0) {
            double alt = cruiseAltitude(level, d, this.soldier.getX(), this.soldier.getZ());
            flyToward(d, this.soldier.getX() + dx / horiz * -2.0, alt, this.soldier.getZ() + dz / horiz * -2.0, CRUISE_SPEED);
            return false;
        }
        if (d.onGround()) {
            d.setDeltaMovement(Vec3.ZERO);
            zeroInputs(d);
            return true;
        }
        d.setDeltaMovement(dx * 0.1, -0.35 / 0.7, dz * 0.1);
        zeroInputs(d);
        d.setDownInputDown(true);
        return false;
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) this.soldier.level();
        this.flightTicks++;
        DroneEntity d = this.drone;
        if (d == null || !d.isAlive()) return;

        // operator stands, holds the monitor, looks at the drone
        this.soldier.getNavigation().stop();
        this.soldier.getLookControl().setLookAt(d, 30.0f, 30.0f);

        // keep the world under the drone ticking, otherwise it freezes past the simulation distance
        if (CommonConfig.DRONE_FORCE_CHUNKS.get()) DroneLink.hold(level, d);
        this.soldier.setDroneAloft(true);
        if (this.target != null && this.soldier.tickCount % 20 == 0) {
            DroneOps.claim(DroneOps.keyOf(this.target.getUUID(), null), this.soldier.getUUID(), this.target.position(), level.getGameTime());
        }

        OrderType order = this.soldier.getOrder();

        // out of control range: turn around and fly home
        double linkDist = Math.sqrt(d.distanceToSqr(this.soldier));
        double maxLink = CommonConfig.DRONE_MAX_DISTANCE.get();
        if (linkDist > maxLink && order != OrderType.DRONE_RECALL) {
            if (--this.linkWarnTimer <= 0) {
                this.linkWarnTimer = 100;
                warnCommander(level, "§c✈ Dron mimo dosahu (" + (int) linkDist + " > " + (int) maxLink + " blokov), vracia sa.");
            }
            if (returnAndLand(level, d)) this.finished = true;
            return;
        }

        // 1) recall / hold fire: come home
        if (order == OrderType.DRONE_RECALL || order == OrderType.CEASE_FIRE) {
            if (returnAndLand(level, d)) {
                if (order == OrderType.DRONE_RECALL) this.soldier.setOrder(OrderType.NONE);
                this.finished = true;
            }
            return;
        }

        // 2) takeoff: climb straight up until clear of the ground
        double agl = d.getY() - surfaceY(level, d.getX(), d.getZ());
        if (this.phase == Phase.TAKEOFF) {
            if (agl >= 3.0 && !d.onGround()) {
                this.phase = Phase.CRUISE;
            } else {
                d.setDeltaMovement(0, 0.45 / 0.7, 0);
                zeroInputs(d);
                d.setUpInputDown((d.tickCount % 4) < 3);
                return;
            }
        }

        spot(level, d);

        // 3) scouting: hover over the point
        if (order == OrderType.DRONE_SCOUT) {
            Vec3 p = this.soldier.getMoveToTarget();
            if (p == null || p == Vec3.ZERO) {
                this.soldier.setOrder(OrderType.NONE);
                return;
            }
            double alt = cruiseAltitude(level, d, p.x, p.z);
            double dx = p.x - d.getX();
            double dz = p.z - d.getZ();
            if (dx * dx + dz * dz > 3.0 * 3.0) flyToward(d, p.x, alt, p.z, CRUISE_SPEED);
            else hover(d, alt);
            return;
        }

        // 4) automatic target selection
        if (order != OrderType.DRONE_STRIKE_TARGET && order != OrderType.DRONE_STRIKE_POSITION) {
            if (--this.scanTimer <= 0) {
                this.scanTimer = 20;
                if (this.target == null || !isStrikeable(this.target)) this.target = findTarget(level);
            }
        }

        Vec3 point = strikePoint(level, order);
        if (point == null) {
            // nothing to hit: loiter a while above the operator, then land to save the drone
            if (order == OrderType.DRONE_STRIKE_TARGET || order == OrderType.DRONE_STRIKE_POSITION) this.soldier.setOrder(OrderType.NONE);
            this.target = null;
            this.idleTicks++;
            if (this.idleTicks > IDLE_BEFORE_LANDING) {
                if (returnAndLand(level, d)) this.finished = true;
            } else {
                double alt = cruiseAltitude(level, d, this.soldier.getX(), this.soldier.getZ());
                double dx = this.soldier.getX() - d.getX();
                double dz = this.soldier.getZ() - d.getZ();
                if (dx * dx + dz * dz > 5.0 * 5.0) flyToward(d, this.soldier.getX(), alt, this.soldier.getZ(), CRUISE_SPEED);
                else hover(d, alt);
            }
            return;
        }
        this.idleTicks = 0;

        double dx = point.x - d.getX();
        double dz = point.z - d.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.sqrt(d.position().distanceToSqr(point));

        // our own people next to the target: wait above at a safe distance instead of blowing them up too
        if (order != OrderType.DRONE_STRIKE_POSITION && friendliesNear(level, point)) {
            double alt = cruiseAltitude(level, d, point.x, point.z) + 4.0;
            flyToward(d, point.x - dx / Math.max(1, horiz) * 10.0, alt, point.z - dz / Math.max(1, horiz) * 10.0, CRUISE_SPEED);
            if (--this.friendlyWarnTimer <= 0 && this.soldier.getOwnerUUID() != null) {
                this.friendlyWarnTimer = 200;
                Player commander = level.getPlayerByUUID(this.soldier.getOwnerUUID());
                if (commander != null) commander.displayClientMessage(Component.literal("§e✈ Dron čaká — pri cieli sú naši (do 6 blokov)."), true);
            }
            this.phase = Phase.CRUISE;
            return;
        }

        if (this.phase != Phase.TERMINAL) {
            if (horiz <= TERMINAL_RADIUS) {
                this.phase = Phase.TERMINAL;
                this.terminalTicks = 0;
                this.lastDivePos = d.position();
            } else {
                // approach at cruise altitude, but stay a few blocks above the target itself
                double alt = Math.max(cruiseAltitude(level, d, point.x, point.z), point.y + 5.0);
                flyToward(d, point.x, alt, point.z, CRUISE_SPEED);
                return;
            }
        }

        // 5) terminal run
        // somebody else's warhead just went off here - climb out and come round again instead of being shot down by it
        if (DroneOps.blastAt(point, level.getGameTime())) {
            this.phase = Phase.CRUISE;
            double alt = cruiseAltitude(level, d, point.x, point.z) + 6.0;
            flyToward(d, d.getX(), alt, d.getZ(), CRUISE_SPEED * 0.5);
            return;
        }
        this.terminalTicks++;
        if (dist <= DETONATE_RADIUS) {
            detonate(d, "reached target");
            return;
        }
        // blocked by a wall / the hull it is diving at: close enough, blow it there
        double moved = d.position().distanceToSqr(this.lastDivePos);
        this.lastDivePos = d.position();
        if (this.terminalTicks > 6 && moved < 0.02 && dist <= 6.0) {
            detonate(d, "blocked at " + String.format("%.1f", dist) + " blocks");
            return;
        }
        // shot up on the way in: spend it rather than lose it for nothing
        if (d.getHealth() <= d.getMaxHealth() * 0.3f && dist <= 12.0) {
            detonate(d, "damaged during the run");
            return;
        }
        // flew past / under the target: climb back and try again, but not forever
        if (this.terminalTicks > 60 || horiz > TERMINAL_RADIUS + 4.0) {
            this.terminalRetries++;
            if (this.terminalRetries >= 3) {
                detonate(d, "could not connect after 3 runs");
                return;
            }
            this.phase = Phase.CRUISE;
            return;
        }
        // moving entity: aim a bit ahead of it
        Vec3 aim = point;
        Entity e = order == OrderType.DRONE_STRIKE_TARGET ? level.getEntity(this.soldier.getAttackTargetId()) : this.target;
        if (order != OrderType.DRONE_STRIKE_POSITION && e != null) {
            aim = point.add(e.getDeltaMovement().scale(4.0));
        }
        diveInto(d, aim);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

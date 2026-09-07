package sk.totalnavojna.entities.goals;

import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import sk.totalnavojna.Role;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;
import sk.totalnavojna.war.Stance;
import sk.totalnavojna.war.BattleIntel;
import sk.totalnavojna.war.DroneLink;
import sk.totalnavojna.war.TerrainScan;
import sk.totalnavojna.war.WarGameData;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// The driver (first passenger) of a ground/sea Superb Warfare hull: turns the soldier's orders into steering inputs.
// Steering model adapted from Combined Arms (GPL-3.0, NeoAlive): tracked hulls pivot in place, wheeled hulls roll through turns
// and back-and-fill when the bearing is behind them; a hull that stops moving reverses for a moment.
// Added here: real braking before the destination (speed-dependent stopping distance), a terrain sensor that refuses to
// drive into water, off cliffs or into walls and picks a clear bearing instead, a standoff ring while engaged, and a
// retreat towards the own NEXUS when the hull is badly damaged.
public class VehicleDriveGoal extends Goal {
    private static final double MIN_ANGLE_RAD = Math.toRadians(3.0);
    private static final double MAX_ANGLE_RAD = Math.toRadians(22.5);
    private static final double WHEEL_REVERSE_ANGLE_RAD = Math.toRadians(110.0);
    private static final int STUCK_TICKS = 40;
    private static final int UNSTICK_TICKS = 30;
    private static final double ARRIVE_DIST = 4.0;
    private static final double STANDOFF_MIN = 12.0;
    private static final double STANDOFF_MAX = 34.0;
    private static final float RETREAT_HEALTH_FRACTION = 0.35f;
    private static final double[] PROBE_ANGLES = {0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5};

    private final SwatEntity soldier;
    private VehicleEntity vehicle = null;
    private boolean wheeled = false;
    private boolean ship = false;
    private Vec3 lastPos = Vec3.ZERO;
    private float lastYaw = 0;
    private int stuckTicks = 0;
    private int unstickTicks = 0;
    private boolean unstickLeft = false;
    private int wheelReverseTicks = 0;
    private int throttlePhase = 0;
    private int boxedInTicks = 0;
    private int logTimer = 0;
    private int destCheckTimer = 0;

    public VehicleDriveGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.getVehicle() instanceof VehicleEntity v)) return false;
        if (v.getFirstPassenger() != this.soldier) return false;
        if (!(this.soldier.level() instanceof ServerLevel)) return false;
        EngineType type = v.computed().getEngineType();
        if (type != EngineType.WHEEL && type != EngineType.TRACK && type != EngineType.SHIP) return false;
        this.vehicle = v;
        this.wheeled = type != EngineType.TRACK;
        this.ship = type == EngineType.SHIP;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.vehicle != null && this.soldier.getVehicle() == this.vehicle && this.vehicle.getFirstPassenger() == this.soldier
                && this.soldier.getState() == SwatEntity.STATE_ALIVE;
    }

    @Override
    public void start() {
        this.lastPos = this.vehicle.position();
        this.lastYaw = this.vehicle.getYRot();
        this.stuckTicks = 0;
        this.unstickTicks = 0;
        this.wheelReverseTicks = 0;
        this.boxedInTicks = 0;
    }

    @Override
    public void stop() {
        if (this.vehicle != null) {
            zeroInputs();
            if (this.soldier.level() instanceof ServerLevel sl) DroneLink.release(sl, this.vehicle.getUUID());
        }
        this.vehicle = null;
    }

    private void zeroInputs() {
        this.vehicle.setForwardInputDown(false);
        this.vehicle.setBackInputDown(false);
        this.vehicle.setLeftInputDown(false);
        this.vehicle.setRightInputDown(false);
        this.vehicle.setSprintInputDown(false);
    }

    private double speed() {
        return this.vehicle.getAbsoluteSpeed();
    }

    // brake: SBW treats the back input as brake while rolling forward
    private void brake() {
        this.vehicle.setForwardInputDown(false);
        this.vehicle.setSprintInputDown(false);
        this.vehicle.setLeftInputDown(false);
        this.vehicle.setRightInputDown(false);
        this.vehicle.setBackInputDown(speed() > 0.04);
    }

    // ===== destination from orders =====

    @Nullable
    private Vec3 destination(ServerLevel level) {
        OrderType order = this.soldier.getOrder();
        WarGameData data = WarGameData.get(level);

        // badly damaged: get out of the fight towards the own NEXUS (crew bails at 15 %)
        if (this.vehicle.getHealth() <= this.vehicle.getMaxHealth() * RETREAT_HEALTH_FRACTION && order != OrderType.HOLD_POSITION) {
            WarGameData.CoreEntry core = data.nearestCore(level, this.soldier.getArmyTeam(), this.vehicle.blockPosition());
            if (core != null) {
                Vec3 t = Vec3.atBottomCenterOf(core.pos);
                if (horizDistSq(t) > 20.0 * 20.0) return t;
            }
        }

        switch (order) {
            case MOVE_TO_POSITION, MOVE_AND_UNLOAD -> {
                Vec3 t = this.soldier.getMoveToTarget();
                if (t == null || t == Vec3.ZERO) return null;
                if (horizDistSq(t) < ARRIVE_DIST * ARRIVE_DIST && speed() < 0.05) {
                    if (order == OrderType.MOVE_AND_UNLOAD) unloadPassengers();
                    this.soldier.setOrder(OrderType.HOLD_POSITION);
                    return null;
                }
                return t;
            }
            case ATTACK_THAT_TARGET -> {
                Entity e = level.getEntity(this.soldier.getAttackTargetId());
                if (e == null || !e.isAlive()) {
                    this.soldier.setOrder(OrderType.HOLD_POSITION);
                    return null;
                }
                double keep = CommonConfig.VEHICLE_ENGAGE_RANGE.get() * 0.6;
                return horizDistSq(e.position()) < keep * keep ? null : e.position();
            }
            case MOVE_ALONG_PATH, PATROL_PATH -> {
                List<BlockPos> path = this.soldier.getPathWaypoints();
                int idx = this.soldier.getPathIndex();
                if (path == null || path.isEmpty() || idx >= path.size()) {
                    this.soldier.setOrder(OrderType.HOLD_POSITION);
                    return null;
                }
                Vec3 t = Vec3.atBottomCenterOf(path.get(idx));
                boolean last = idx + 1 >= path.size();
                double reach = last ? ARRIVE_DIST : 7.0;
                if (horizDistSq(t) < reach * reach) {
                    if (!last) {
                        this.soldier.setPathIndex(idx + 1);
                        return Vec3.atBottomCenterOf(path.get(idx + 1));
                    }
                    if (order == OrderType.PATROL_PATH) {
                        this.soldier.reversePathWaypoints();
                        return Vec3.atBottomCenterOf(this.soldier.getPathWaypoints().get(this.soldier.getPathIndex()));
                    }
                    if (speed() < 0.05) this.soldier.setOrder(OrderType.HOLD_POSITION);
                    return null;
                }
                return t;
            }
            case FOLLOW_COMMANDER, FORM_WEDGE, FORM_COLUMN -> {
                if (this.soldier.getOwnerUUID() == null) return null;
                Player owner = level.getPlayerByUUID(this.soldier.getOwnerUUID());
                if (owner == null) return null;
                if (horizDistSq(owner.position()) < 10.0 * 10.0) return null;
                return owner.position();
            }
            case ATTACK_CORE, NONE, FREE_FIRE -> {
                boolean attack = order == OrderType.ATTACK_CORE
                        || (this.soldier.getRole() == Role.ATTACKER && data.getStance(this.soldier.getArmyTeam()) == Stance.WAR);
                if (!attack) return null;
                WarGameData.CoreEntry core = data.nearestCore(level, this.soldier.getArmyTeam().enemy(), this.vehicle.blockPosition());
                if (core == null) return null;
                Vec3 t = Vec3.atBottomCenterOf(core.pos);
                return horizDistSq(t) < 18.0 * 18.0 ? null : t;
            }
            case DEFEND_CORE -> {
                WarGameData.CoreEntry core = data.nearestCore(level, this.soldier.getArmyTeam(), this.vehicle.blockPosition());
                if (core == null) return null;
                Vec3 t = Vec3.atBottomCenterOf(core.pos);
                return horizDistSq(t) < 18.0 * 18.0 ? null : t;
            }
            default -> {
                return null;
            }
        }
    }

    // everybody except the driver gets out and holds position here
    private void unloadPassengers() {
        for (Entity p : new ArrayList<>(this.vehicle.getPassengers())) {
            if (p == this.soldier) continue;
            if (p instanceof SwatEntity s) {
                s.stopRiding();
                s.setOrder(OrderType.HOLD_POSITION);
            }
        }
    }

    private double horizDistSq(Vec3 t) {
        double dx = t.x - this.vehicle.getX();
        double dz = t.z - this.vehicle.getZ();
        return dx * dx + dz * dz;
    }

    private static double signedAngleTo(Vector3f forward, Vec3 target) {
        double cross = forward.x * target.z - forward.z * target.x;
        double dot = forward.x * target.x + forward.z * target.z;
        return -Math.atan2(cross, dot);
    }

    private boolean updateStuck() {
        Vec3 pos = this.vehicle.position();
        float yaw = this.vehicle.getYRot();
        boolean moved = pos.distanceToSqr(this.lastPos) > 0.01 || Math.abs(Mth.degreesDifference(yaw, this.lastYaw)) > 1.5f;
        if (moved) {
            this.lastPos = pos;
            this.lastYaw = yaw;
            this.stuckTicks = 0;
            return false;
        }
        return ++this.stuckTicks > STUCK_TICKS;
    }

    // ===== terrain sensor =====

    // Is the ground along this bearing drivable? Shared with every other unit through TerrainScan.
    private boolean bearingClear(ServerLevel level, Vec3 dir, double lookahead) {
        return TerrainScan.bearingDrivable(level, this.vehicle.position(), dir, lookahead, this.ship, 1, 3);
    }

    // Pick the bearing that is both drivable and least exposed to what the team knows about.
    @Nullable
    private Vec3 chooseBearing(ServerLevel level, Vec3 desired, double lookahead) {
        Vec3 best = null;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < PROBE_ANGLES.length; i++) {
            double a = PROBE_ANGLES[i];
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            Vec3 dir = new Vec3(desired.x * cos - desired.z * sin, 0, desired.x * sin + desired.z * cos);
            if (!bearingClear(level, dir, lookahead)) continue;
            // straight ahead is cheapest; a detour costs, and driving into a known enemy position costs more
            Vec3 probe = this.vehicle.position().add(dir.scale(lookahead));
            double cost = Math.abs(a) * 2.0
                    + BattleIntel.threatAt(this.soldier.getArmyTeam(), probe, 24.0) * 1.5;
            if (cost < bestCost) {
                bestCost = cost;
                best = dir;
            }
        }
        return best;
    }

    // ===== main =====

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) this.soldier.level();
        double speed = speed();

        // a vehicle driven far from any player stops ticking (simulation distance) - hold its chunks
        if (CommonConfig.VEHICLE_FORCE_CHUNKS.get()) DroneLink.hold(level, this.vehicle);

        // engaged: hold the standoff ring so the turret can work
        LivingEntity target = this.soldier.getTarget();
        boolean engaged = target != null && this.soldier.isValidTarget(target)
                && this.vehicle.distanceToSqr(target) < CommonConfig.VEHICLE_ENGAGE_RANGE.get() * CommonConfig.VEHICLE_ENGAGE_RANGE.get()
                && this.vehicle.getHealth() > this.vehicle.getMaxHealth() * RETREAT_HEALTH_FRACTION;
        if (engaged && this.soldier.getOrder() != OrderType.MOVE_TO_POSITION && this.soldier.getOrder() != OrderType.MOVE_AND_UNLOAD
                && this.soldier.getOrder() != OrderType.MOVE_ALONG_PATH && this.soldier.getOrder() != OrderType.PATROL_PATH) {
            double distSq = this.vehicle.distanceToSqr(target);
            if (distSq < STANDOFF_MIN * STANDOFF_MIN) {
                // too close: back away from the target
                Vec3 away = this.vehicle.position().subtract(target.position());
                away = new Vec3(away.x, 0, away.z).normalize();
                Vector3f forward = this.vehicle.getForwardDirection().normalize();
                double angle = signedAngleTo(forward, away);
                this.vehicle.setForwardInputDown(false);
                this.vehicle.setBackInputDown(true);
                this.vehicle.setLeftInputDown(angle < 0);
                this.vehicle.setRightInputDown(angle > 0);
                return;
            }
            brake();
            this.stuckTicks = 0;
            return;
        }

        Vec3 dest = destination(level);
        if (dest == null) {
            brake();
            this.stuckTicks = 0;
            return;
        }
        // a destination a hull can never occupy (open water for a tank, dry land for a boat) is corrected once,
        // instead of driving at it until something falls in
        if (this.destCheckTimer-- <= 0) {
            this.destCheckTimer = 40;
            int dy = TerrainScan.surfaceY(level, dest.x, dest.z);
            if (!TerrainScan.drivable(level, (int) Math.floor(dest.x), (int) Math.floor(dest.z), dy, this.ship, 1, 3)) {
                Vec3 fixed = TerrainScan.nearestDrivable(level, dest, this.ship, 16);
                if (fixed != null) {
                    this.soldier.setMoveToTarget(fixed);
                    dest = fixed;
                } else {
                    this.soldier.setOrder(OrderType.HOLD_POSITION);
                    brake();
                    return;
                }
            }
        }

        // recovery: straight reverse with the rudder over
        if (this.unstickTicks > 0) {
            this.unstickTicks--;
            this.vehicle.setForwardInputDown(false);
            this.vehicle.setSprintInputDown(false);
            this.vehicle.setBackInputDown(true);
            this.vehicle.setLeftInputDown(this.unstickLeft);
            this.vehicle.setRightInputDown(!this.unstickLeft);
            return;
        }
        if (updateStuck()) {
            this.stuckTicks = 0;
            this.unstickTicks = UNSTICK_TICKS;
            this.unstickLeft = this.soldier.getRandom().nextBoolean();
            return;
        }

        Vec3 desired = new Vec3(dest.x - this.vehicle.getX(), 0, dest.z - this.vehicle.getZ());
        double dist = desired.length();
        if (dist < 1.0E-2) {
            brake();
            return;
        }
        desired = desired.scale(1.0 / dist);

        // braking distance: stop before the destination instead of overshooting into the sea
        double brakeDist = Math.max(ARRIVE_DIST, speed * 30.0 + 2.0);
        if (dist < ARRIVE_DIST || (dist < brakeDist && speed > 0.08)) {
            brake();
            return;
        }

        // terrain: pick a clear bearing (or refuse to move)
        double lookahead = Math.max(6.0, speed * 40.0);
        Vec3 steer = chooseBearing(level, desired, lookahead);
        if (steer == null) {
            this.boxedInTicks++;
            brake();
            if (this.boxedInTicks > 20) {
                // boxed in: back out and try again
                this.unstickTicks = UNSTICK_TICKS;
                this.unstickLeft = this.soldier.getRandom().nextBoolean();
                this.boxedInTicks = 0;
                if (--this.logTimer <= 0) {
                    this.logTimer = 20;
                    TotalnaVojna.LOGGER.info("[TV] vehicle driver {} boxed in at {}", this.soldier.getPatientName(), this.vehicle.blockPosition());
                }
            }
            return;
        }
        this.boxedInTicks = 0;

        Vector3f forward = this.vehicle.getForwardDirection().normalize();
        forward.y = 0;
        double angle = signedAngleTo(forward, steer);

        // wheeled hull with the target behind it: back and fill
        if (this.wheeled && Math.abs(angle) > WHEEL_REVERSE_ANGLE_RAD && this.wheelReverseTicks <= 0) {
            this.wheelReverseTicks = 30;
        }
        if (this.wheelReverseTicks > 0) {
            this.wheelReverseTicks--;
            this.vehicle.setForwardInputDown(false);
            this.vehicle.setSprintInputDown(false);
            this.vehicle.setBackInputDown(true);
            // steering sense inverts while reversing
            this.vehicle.setLeftInputDown(angle < 0);
            this.vehicle.setRightInputDown(angle > 0);
            return;
        }

        double threshold = Mth.clampedLerp(MIN_ANGLE_RAD, MAX_ANGLE_RAD, Mth.inverseLerp(dist, 6.0, 30.0));
        this.vehicle.setBackInputDown(false);
        if (Math.abs(angle) < threshold) {
            this.vehicle.setForwardInputDown(true);
            this.vehicle.setLeftInputDown(false);
            this.vehicle.setRightInputDown(false);
            // only floor it on a long, clear straight
            this.vehicle.setSprintInputDown(dist > 40.0 && bearingClear(level, steer, Math.max(lookahead, 16.0)));
        } else {
            this.vehicle.setLeftInputDown(angle > 0);
            this.vehicle.setRightInputDown(angle < 0);
            this.vehicle.setSprintInputDown(false);
            if (this.wheeled) {
                // roll through the turn, duty-cycled on hard bends; slow down first if fast
                if (speed > 0.35) {
                    this.vehicle.setForwardInputDown(false);
                    this.vehicle.setBackInputDown(true);
                } else {
                    int duty = Math.abs(angle) > Math.toRadians(45.0) ? 3 : 2;
                    this.vehicle.setForwardInputDown(this.throttlePhase++ % duty == 0);
                }
            } else {
                this.vehicle.setForwardInputDown(false);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

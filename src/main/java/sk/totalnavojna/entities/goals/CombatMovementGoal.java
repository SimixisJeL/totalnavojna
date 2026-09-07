package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;
import sk.totalnavojna.war.BattleIntel;
import sk.totalnavojna.war.RoleDoctrine;
import sk.totalnavojna.war.TerrainScan;

import java.util.EnumSet;

// Tactical movement while engaged with a visible target in range:
//  - side-step when a teammate stands in the line of fire (bullets would just hit him)
//  - occasional short strafes so the unit is not a standing target
//  - snipers keep their distance, everybody else holds
//  - below 35% HP a non-medic ducks behind cover (no line of sight from the target) so SelfHeal can kick in
public class CombatMovementGoal extends Goal {
    private final SwatEntity soldier;
    private Vec3 moveTarget = null;
    private int cooldown = 0;
    private int moveTicks = 0;

    public CombatMovementGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean ordersAllowMovement() {
        OrderType order = this.soldier.getOrder();
        return order == OrderType.NONE || order == OrderType.FREE_FIRE || order == OrderType.ATTACK_CORE
                || order == OrderType.DEFEND_CORE || order == OrderType.ATTACK_THAT_TARGET;
    }

    @Override
    public boolean canUse() {
        if (!CommonConfig.SMART_COMBAT_MOVEMENT.get()) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel)) return false;
        if (!ordersAllowMovement()) return false;
        if (--this.cooldown > 0) return false;
        LivingEntity target = this.soldier.getTarget();
        if (target == null || !target.isAlive()) return false;
        double dist = this.soldier.distanceToSqr(target);
        if (dist > this.soldier.getGunAttackRadiusSqr()) return false;

        this.moveTarget = null;
        Role role = this.soldier.getRole();
        boolean lowHp = this.soldier.getHealth() < this.soldier.getMaxHealth() * RoleDoctrine.retreatHealth(role);
        double standoff = RoleDoctrine.standoff(role);
        // the whole team's picture, not just what this soldier can see
        double threatHere = BattleIntel.threatAt(this.soldier.getArmyTeam(), this.soldier.position(), 20.0);
        boolean overwhelmed = threatHere > RoleDoctrine.threatTolerance(role);

        if (lowHp || overwhelmed) {
            this.moveTarget = findCover(target);
            this.cooldown = lowHp ? 80 : 50;
        } else if (this.soldier.friendlyInLineOfFire()) {
            this.moveTarget = sideStep(target, 2.0, true);
            this.cooldown = 15;
        } else if (dist < standoff * standoff * 0.45) {
            // too close for this role's doctrine: open the range back up
            this.moveTarget = backOff(target, standoff * 0.6);
            this.cooldown = 60;
        } else if (this.soldier.hasLineOfSight(target) && this.soldier.getRandom().nextFloat() < 0.35f) {
            this.moveTarget = sideStep(target, 1.5 + this.soldier.getRandom().nextFloat() * 1.5, false);
            this.cooldown = 40 + this.soldier.getRandom().nextInt(50);
        } else {
            this.cooldown = 20;
        }
        return this.moveTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.moveTarget != null && this.moveTicks < 40 && !this.soldier.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.moveTicks = 0;
        this.soldier.getNavigation().moveTo(this.moveTarget.x, this.moveTarget.y, this.moveTarget.z, 1.1);
    }

    @Override
    public void stop() {
        this.moveTarget = null;
    }

    @Override
    public void tick() {
        this.moveTicks++;
    }

    private boolean isStandable(BlockPos feet) {
        return TerrainScan.standable((ServerLevel) this.soldier.level(), feet);
    }

    private boolean hasLineFrom(Vec3 from, LivingEntity target) {
        return this.soldier.level().clip(new ClipContext(from, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.soldier)).getType() == HitResult.Type.MISS;
    }

    @Nullable
    private Vec3 sideStep(LivingEntity target, double distance, boolean requireLine) {
        Vec3 toTarget = target.position().subtract(this.soldier.position());
        Vec3 side = new Vec3(-toTarget.z, 0, toTarget.x).normalize();
        boolean first = this.soldier.getRandom().nextBoolean();
        for (int attempt = 0; attempt < 2; attempt++) {
            Vec3 dir = (attempt == 0) == first ? side : side.scale(-1);
            Vec3 candidate = this.soldier.position().add(dir.scale(distance));
            BlockPos feet = BlockPos.containing(candidate);
            if (!isStandable(feet)) continue;
            Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.6, feet.getZ() + 0.5);
            if (requireLine && !hasLineFrom(eye, target)) continue;
            return Vec3.atBottomCenterOf(feet);
        }
        return null;
    }

    @Nullable
    private Vec3 backOff(LivingEntity target, double distance) {
        Vec3 away = this.soldier.position().subtract(target.position());
        away = new Vec3(away.x, 0, away.z).normalize();
        for (double d = distance; d >= 3.0; d -= 2.0) {
            BlockPos feet = BlockPos.containing(this.soldier.position().add(away.scale(d)));
            if (isStandable(feet)) return Vec3.atBottomCenterOf(feet);
        }
        return null;
    }

    // A standable spot within 7 blocks that the target cannot see.
    @Nullable
    private Vec3 findCover(LivingEntity target) {
        BlockPos me = this.soldier.blockPosition();
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.withinManhattan(me, 7, 2, 7)) {
            if (p.equals(me)) continue;
            if (!isStandable(p)) continue;
            Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + 1.6, p.getZ() + 0.5);
            if (hasLineFrom(eye, target)) continue;
            double d = p.distSqr(me);
            if (d < bestDist) {
                bestDist = d;
                best = Vec3.atBottomCenterOf(p);
            }
        }
        return best;
    }
}

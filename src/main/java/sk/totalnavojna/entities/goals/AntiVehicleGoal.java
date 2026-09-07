package sk.totalnavojna.entities.goals;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import sk.totalnavojna.Team;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;
import sk.totalnavojna.war.BattleIntel;
import sk.totalnavojna.war.DroneLink;
import sk.totalnavojna.war.WarGameData;

import java.util.EnumSet;
import java.util.List;

// Shooting at things that are not alive: hostile drones overhead (automatically) and any hostile Superb Warfare
// vehicle the commander designates with "Zaútoč na cieľ" (vanilla targeting only accepts LivingEntity, so a vehicle
// picked in the menu or on the map would otherwise be silently dropped).
//
// A drone has 5 HP, so rifles are a real air defence. A tank obviously is not going to care much about rifle fire -
// that is what the attacker's grenade launcher and your own vehicles are for - but the squad will at least engage it.
public class AntiVehicleGoal extends Goal {
    private final SwatEntity shooter;
    private Entity target;
    private int lastAttackTick = 0;
    private float bulletPitch;
    private float bulletYaw;

    public AntiVehicleGoal(SwatEntity shooter) {
        this.shooter = shooter;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    private boolean hostile(Entity e) {
        if (!e.isAlive()) return false;
        if (!(this.shooter.level() instanceof ServerLevel level)) return false;
        if (e instanceof DroneEntity d) {
            Team owner = DroneLink.teamOf(d);
            if (owner != null) return !WarGameData.get(level).isFriendly(this.shooter.getArmyTeam(), owner);
            Player controller = d.getController();
            return controller != null && this.shooter.isEnemy(controller);
        }
        if (e instanceof VehicleEntity v) {
            if (v.isWreck()) return false;
            for (Entity p : v.getPassengers()) {
                if (p instanceof LivingEntity le && this.shooter.isValidTarget(le)) return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean canUse() {
        if (this.shooter.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.shooter.getOrder() == OrderType.CEASE_FIRE) return false;
        if (this.shooter.isInVehicleWeaponSeat()) return false;
        if (!(this.shooter.level() instanceof ServerLevel level)) return false;
        if (this.shooter.tickCount % 5 != 0) return false;

        // 1) an explicitly designated vehicle outranks everything, at gun range
        int designated = this.shooter.getAttackTargetId();
        if (designated >= 0) {
            Entity e = level.getEntity(designated);
            if (e instanceof VehicleEntity && hostile(e)
                    && this.shooter.distanceToSqr(e) <= this.shooter.getGunAttackRadiusSqr()
                    && this.shooter.hasLineOfSight(e)) {
                this.target = e;
                return true;
            }
        }

        // 2) hostile drones nearby
        double range = CommonConfig.DRONE_ENGAGE_RANGE.get();
        if (range <= 0) return false;
        List<DroneEntity> drones = level.getEntitiesOfClass(DroneEntity.class,
                this.shooter.getBoundingBox().inflate(range), this::hostile);
        DroneEntity best = null;
        double bestDist = range * range;
        for (DroneEntity d : drones) {
            double dist = this.shooter.distanceToSqr(d);
            if (dist < bestDist && this.shooter.hasLineOfSight(d)) {
                bestDist = dist;
                best = d;
            }
        }
        if (best == null) return false;

        // an infantry fight in progress outranks a drone that is still far away
        LivingEntity living = this.shooter.getTarget();
        if (living != null && living.isAlive() && this.shooter.isValidTarget(living)) {
            if (bestDist > 20.0 * 20.0 && bestDist > this.shooter.distanceToSqr(living)) return false;
        }
        this.target = best;
        BattleIntel.report(this.shooter.getArmyTeam(), best, level.getGameTime());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.shooter.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.shooter.getOrder() == OrderType.CEASE_FIRE) return false;
        if (!this.shooter.hasLineOfSight(this.target)) return false;
        double limit = this.target instanceof DroneEntity
                ? Math.pow(CommonConfig.DRONE_ENGAGE_RANGE.get() + 8, 2)
                : this.shooter.getGunAttackRadiusSqr();
        return this.shooter.distanceToSqr(this.target) < limit;
    }

    @Override
    public void start() {
        this.shooter.restoreGun();
        this.shooter.aim(true);
    }

    @Override
    public void stop() {
        this.shooter.aim(false);
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.target == null) return;
        // lead the target: a drone on a dive run moves about a block per tick
        double dist = Math.sqrt(this.shooter.distanceToSqr(this.target));
        double leadTicks = Mth.clamp(dist / 3.0, 0.0, 12.0);
        Vec3 aim = this.target.position()
                .add(this.target.getDeltaMovement().scale(leadTicks))
                .add(0, Math.min(1.0, this.target.getBbHeight() * 0.5), 0);

        this.shooter.getLookControl().setLookAt(aim.x, aim.y, aim.z);
        if (this.shooter.tickCount - this.lastAttackTick < CommonConfig.GUN_ATTACK_COOLDOWN.get()) return;
        if (this.shooter.friendlyInLineOfFire()) return;
        this.lastAttackTick = this.shooter.tickCount;

        computeBulletPitchYaw(aim.x, aim.y, aim.z);
        ShootResult result = this.shooter.shoot(() -> this.bulletPitch, () -> this.bulletYaw);
        if (result == ShootResult.NO_AMMO) {
            if (this.shooter.hasAmmoForGun(this.shooter.getSelectedItem())) this.shooter.reload();
            else this.shooter.takeNextGun();
        }
    }

    private void computeBulletPitchYaw(double targetX, double targetY, double targetZ) {
        double lookOffset = CommonConfig.SOLDIER_AIM_ERROR.get() * this.shooter.aimErrorMult();
        targetX += Mth.nextDouble(this.shooter.getRandom(), -lookOffset, lookOffset);
        targetY += Mth.nextDouble(this.shooter.getRandom(), -lookOffset, lookOffset);
        targetZ += Mth.nextDouble(this.shooter.getRandom(), -lookOffset, lookOffset);
        double lookX = targetX - this.shooter.getX();
        double lookZ = targetZ - this.shooter.getZ();
        double lookY = targetY - this.shooter.getEyeY();
        double lookD = Math.sqrt(lookX * lookX + lookZ * lookZ);
        float yaw = (float) (Mth.atan2(lookZ, lookX) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) (-(Mth.atan2(lookY, lookD) * Mth.RAD_TO_DEG));
        this.bulletYaw = rotateTowards(this.shooter.getYHeadRot(), yaw, this.shooter.getHeadRotSpeed());
        this.bulletPitch = rotateTowards(this.shooter.getXRot(), pitch, this.shooter.getMaxHeadXRot());
    }

    private static float rotateTowards(float from, float to, float max) {
        float f = Mth.degreesDifference(from, to);
        f = Mth.clamp(f, -max, max);
        return from + f;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

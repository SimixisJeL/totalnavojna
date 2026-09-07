package sk.totalnavojna.entities.goals;

import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;

import java.util.EnumSet;

public class GunAttackGoal extends Goal {
    private final SwatEntity shooter;
    private int lastAttackTick = 0;
    private boolean isAimingAtHead = false;

    private float bulletPitch;
    private float bulletYaw;

    public GunAttackGoal(SwatEntity shooter) {
        this.shooter = shooter;

        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.shooter.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.shooter.getOrder() == OrderType.CEASE_FIRE) return false;
        if (this.shooter.isInVehicleWeaponSeat()) return false;
        LivingEntity target = this.shooter.getTarget();
        if (target == null || target.isDeadOrDying()) return false;
        if (!this.shooter.isValidTarget(target)) {
            // downed / no longer hostile -> forget it and go back to objectives
            this.shooter.setTarget(null);
            return false;
        }
        return !(this.shooter.distanceToSqr(target) > this.shooter.getGunAttackRadiusSqr())
                && this.shooter.hasLineOfSight(target);
    }

    @Override
    public void start() {
        this.shooter.restoreGun();
        this.shooter.aim(true);
        this.shooter.setAggressive(true);
    }

    @Override
    public void stop() {
        LivingEntity target = this.shooter.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            this.shooter.setTarget(null);
        }
        this.shooter.aim(false);
        this.shooter.setAggressive(false);
    }

    @Override
    public void tick() {
        LivingEntity target = this.shooter.getTarget();
        if (target == null || target.isDeadOrDying()) return;

        double targetX = target.getX();
        double targetY = this.isAimingAtHead ? target.getEyeY() : getBodyY(target);
        double targetZ = target.getZ();

        this.shooter.getLookControl().setLookAt(targetX, targetY, targetZ);

        if (this.shooter.tickCount - lastAttackTick < CommonConfig.GUN_ATTACK_COOLDOWN.get()) return;
        // do not shoot a teammate in the back - CombatMovementGoal side-steps meanwhile
        if (CommonConfig.SMART_COMBAT_MOVEMENT.get() && this.shooter.friendlyInLineOfFire()) return;
        lastAttackTick = this.shooter.tickCount;

        this.computeBulletPitchYaw(targetX, targetY, targetZ);
        ShootResult result = this.shooter.shoot(() -> this.bulletPitch, () -> this.bulletYaw);
        if (result == ShootResult.NO_AMMO) {
            if (this.shooter.hasAmmoForGun(this.shooter.getSelectedItem())) {
                this.shooter.reload();
            } else {
                this.shooter.takeNextGun();
            }
        }

        this.isAimingAtHead = this.shooter.getRandom().nextFloat() < this.shooter.getArmyTeam().getHeadAimChance();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void computeBulletPitchYaw(double targetX, double targetY, double targetZ) {
        RandomSource rng = this.shooter.getRandom();
        double lookOffset = CommonConfig.SOLDIER_AIM_ERROR.get() * this.shooter.aimErrorMult();
        targetX += Mth.nextDouble(rng, -lookOffset, lookOffset);
        targetY += Mth.nextDouble(rng, -lookOffset, lookOffset);
        targetZ += Mth.nextDouble(rng, -lookOffset, lookOffset);
        double lookX = targetX - this.shooter.getX();
        double lookZ = targetZ - this.shooter.getZ();
        double lookY = targetY - this.shooter.getEyeY();
        double lookD = Math.sqrt(lookX * lookX + lookZ * lookZ);
        float bulletYaw = (float) (Mth.atan2(lookZ, lookX) * Mth.RAD_TO_DEG) - 90.0f;
        float bulletPitch = (float) (-(Mth.atan2(lookY, lookD) * Mth.RAD_TO_DEG));
        this.bulletYaw = rotateTowards(this.shooter.getYHeadRot(), bulletYaw, this.shooter.getHeadRotSpeed());
        this.bulletPitch = rotateTowards(this.shooter.getXRot(), bulletPitch, this.shooter.getMaxHeadXRot());
    }

    // Adapted from LookControl
    private static float rotateTowards(float from, float to, float max) {
        float f = Mth.degreesDifference(from, to);
        f = Mth.clamp(f, -max, max);
        return from + f;
    }

    private static double getBodyY(Entity entity) {
        return (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0;
    }
}

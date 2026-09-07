package sk.totalnavojna.entities.goals;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;

import java.util.EnumSet;
import java.util.List;

// Any soldier sitting in a Superb Warfare vehicle: picks targets for the hull (SBW aims turrets / passenger weapons
// at AI_TURRET_TARGET / AI_PASSENGER_WEAPON_TARGET and fires natively for Mob crews whose getTarget() is set),
// bails out of a wreck and handles the DISMOUNT order.
public class VehicleCrewGoal extends Goal {
    private final SwatEntity soldier;
    private VehicleEntity vehicle = null;

    public VehicleCrewGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.getVehicle() instanceof VehicleEntity v)) return false;
        if (!(this.soldier.level() instanceof ServerLevel)) return false;
        this.vehicle = v;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.vehicle != null && this.soldier.getVehicle() == this.vehicle && this.soldier.getState() == SwatEntity.STATE_ALIVE;
    }

    @Override
    public void stop() {
        this.vehicle = null;
    }

    private boolean hasLineFromVehicle(LivingEntity target) {
        Vec3 from = this.vehicle.position().add(0, this.vehicle.getBbHeight() + 0.5, 0);
        Vec3 to = target.getEyePosition();
        return this.soldier.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.vehicle)).getType() == HitResult.Type.MISS;
    }

    private LivingEntity findTarget(ServerLevel level) {
        double range = CommonConfig.VEHICLE_ENGAGE_RANGE.get();
        LivingEntity current = this.soldier.getTarget();
        if (current != null && this.soldier.isValidTarget(current) && this.soldier.distanceToSqr(current) <= range * range * 1.5) {
            return current;
        }
        LivingEntity best = null;
        double bestDist = range * range;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, this.vehicle.getBoundingBox().inflate(range),
                (e) -> (e instanceof SwatEntity || e instanceof Player) && e != this.soldier && this.soldier.isValidTarget(e));
        for (LivingEntity c : candidates) {
            if (c instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())) continue;
            double d = this.vehicle.distanceToSqr(c);
            if (d < bestDist && hasLineFromVehicle(c)) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) this.soldier.level();

        // bail out of a dying hull
        if (this.vehicle.isWreck() || this.vehicle.getHealth() <= this.vehicle.getMaxHealth() * 0.15f) {
            this.soldier.stopRiding();
            this.soldier.setOrder(OrderType.NONE);
            return;
        }
        if (this.soldier.getOrder() == OrderType.DISMOUNT_VEHICLE) {
            this.soldier.stopRiding();
            this.soldier.setOrder(OrderType.NONE);
            return;
        }
        if (this.soldier.getOrder() == OrderType.CEASE_FIRE) {
            this.soldier.setTarget(null);
            return;
        }

        if (this.soldier.tickCount % 10 == 0) {
            LivingEntity target = findTarget(level);
            this.soldier.setTarget(target);
            if (target != null) {
                String uuid = target.getUUID().toString();
                this.vehicle.setAiTurretTargetUUID(uuid);
                this.vehicle.setAiPassengerWeaponTargetUUID(uuid);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

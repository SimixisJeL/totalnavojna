package sk.totalnavojna.entities.goals;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;

import java.util.EnumSet;

// Order BOARD_VEHICLE: walk to the Superb Warfare vehicle and climb in. The first one in becomes the driver (SBW: first passenger drives).
public class BoardVehicleGoal extends Goal {
    private static final double MOUNT_DISTANCE = 5.0;
    private static final int MAX_TICKS = 400;

    private final SwatEntity soldier;
    private VehicleEntity vehicle = null;
    private int ticks = 0;

    public BoardVehicleGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.getOrder() != OrderType.BOARD_VEHICLE) return false;
        if (this.soldier.isPassenger()) {
            this.soldier.setOrder(OrderType.NONE);
            return false;
        }
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        Entity e = level.getEntity(this.soldier.getMountTargetId());
        if (!(e instanceof VehicleEntity v) || !v.isAlive() || v.isWreck()) {
            this.soldier.setOrder(OrderType.NONE);
            this.soldier.setMountTargetId(-1);
            return false;
        }
        this.vehicle = v;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.vehicle != null && this.vehicle.isAlive() && !this.vehicle.isWreck()
                && this.soldier.getOrder() == OrderType.BOARD_VEHICLE
                && !this.soldier.isPassenger()
                && this.ticks < MAX_TICKS;
    }

    @Override
    public void start() {
        this.ticks = 0;
    }

    @Override
    public void stop() {
        if (this.ticks >= MAX_TICKS && this.soldier.getOrder() == OrderType.BOARD_VEHICLE) {
            this.soldier.setOrder(OrderType.NONE);
            this.soldier.setMountTargetId(-1);
        }
        this.vehicle = null;
        this.soldier.getNavigation().stop();
    }

    private boolean isFull() {
        return this.vehicle.getPassengers().size() >= this.vehicle.getMaxPassengers();
    }

    @Override
    public void tick() {
        this.ticks++;
        if (isFull()) {
            this.soldier.setOrder(OrderType.NONE);
            this.soldier.setMountTargetId(-1);
            return;
        }
        this.soldier.getLookControl().setLookAt(this.vehicle, 30.0f, 30.0f);
        double distSq = this.soldier.distanceToSqr(this.vehicle);
        boolean navStuck = this.soldier.getNavigation().isDone() && distSq <= 7.0 * 7.0;
        if (distSq <= MOUNT_DISTANCE * MOUNT_DISTANCE || navStuck) {
            if (this.soldier.startRiding(this.vehicle)) {
                this.soldier.setOrder(OrderType.NONE);
                this.soldier.setMountTargetId(-1);
                this.soldier.setTarget(null);
                this.soldier.getNavigation().stop();
            }
        } else if (this.ticks % 10 == 1) {
            this.soldier.navigateTo(this.vehicle.getX(), this.vehicle.getY(), this.vehicle.getZ(), 1.2);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

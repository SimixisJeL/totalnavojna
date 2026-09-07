package sk.totalnavojna.entities.goals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import sk.totalnavojna.Role;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;

import java.util.EnumSet;
import java.util.List;

// PATHFINDER default behaviour (no explicit order): tag along with the nearest combat teammate of its group so it is
// close by to dig, heal and revive - but hangs back a few blocks instead of leading the charge.
public class PathfinderSupportGoal extends Goal {
    private static final double FOLLOW_RADIUS = 24.0;

    private final SwatEntity soldier;
    private SwatEntity buddy = null;
    private int recalc = 0;

    public PathfinderSupportGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.soldier.getRole().isSupport()) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.getOrder() != OrderType.NONE) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        if (this.soldier.tickCount % 20 != 0) return false;

        List<SwatEntity> mates = level.getEntitiesOfClass(SwatEntity.class, this.soldier.getBoundingBox().inflate(FOLLOW_RADIUS),
                (e) -> e != this.soldier && e.getArmyTeam() == this.soldier.getArmyTeam() && e.getState() == SwatEntity.STATE_ALIVE
                        && !e.getRole().isSupport() && (this.soldier.getGroup() == 0 || e.getGroup() == this.soldier.getGroup()));
        this.buddy = null;
        double best = Double.MAX_VALUE;
        for (SwatEntity m : mates) {
            double d = this.soldier.distanceToSqr(m);
            if (d < best) {
                best = d;
                this.buddy = m;
            }
        }
        double keep = this.soldier.getRole() == Role.DRONE_OPERATOR ? 14.0 : 7.0;
        return this.buddy != null && best > keep * keep;
    }

    @Override
    public boolean canContinueToUse() {
        return this.buddy != null && this.buddy.isAlive() && this.soldier.getOrder() == OrderType.NONE
                && this.soldier.distanceToSqr(this.buddy) > (this.soldier.getRole() == Role.DRONE_OPERATOR ? 10.0 * 10.0 : 5.0 * 5.0);
    }

    @Override
    public void stop() {
        this.buddy = null;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.buddy == null) return;
        if (--this.recalc <= 0) {
            this.recalc = 10;
            this.soldier.navigateTo(this.buddy.getX(), this.buddy.getY(), this.buddy.getZ(), 1.1);
        }
    }
}

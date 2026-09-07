package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;

import java.util.EnumSet;
import java.util.List;

// Walks the soldier through map-drawn waypoints, then holds at the end.
//
// The route the commander draws on the map is approximate on purpose. A waypoint counts as reached from
// several blocks away, and one the soldier simply cannot get to - drawn into a treetop, inside a wall,
// on the far side of a ravine - is dropped after a few failed attempts instead of being died for.
public class PathOrderGoal extends Goal {
    private static final int GIVE_UP_TICKS = 200;

    private final SwatEntity soldier;
    private int recalcTimer = 0;
    private int stuckOnPoint = 0;
    private int lastIndex = -1;

    public PathOrderGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.getOrder() != OrderType.MOVE_ALONG_PATH && this.soldier.getOrder() != OrderType.PATROL_PATH) return false;
        List<BlockPos> path = this.soldier.getPathWaypoints();
        return path != null && this.soldier.getPathIndex() < path.size();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.stuckOnPoint = 0;
        this.lastIndex = this.soldier.getPathIndex();
    }

    @Override
    public void tick() {
        List<BlockPos> path = this.soldier.getPathWaypoints();
        int index = this.soldier.getPathIndex();
        if (path == null || index >= path.size()) return;

        if (index != this.lastIndex) {
            this.lastIndex = index;
            this.stuckOnPoint = 0;
        }

        BlockPos target = path.get(index);
        double tolerance = CommonConfig.PATH_TOLERANCE.get();
        // horizontal tolerance only: a waypoint on a hillside should not need the soldier to match its height
        double dx = target.getX() + 0.5 - this.soldier.getX();
        double dz = target.getZ() + 0.5 - this.soldier.getZ();
        double flatDistSqr = dx * dx + dz * dz;

        if (flatDistSqr < tolerance * tolerance) {
            advance(path, index);
            return;
        }

        // The point cannot be walked to at all (up a tree, behind a wall): give it a while, then skip it.
        if (++this.stuckOnPoint > GIVE_UP_TICKS) {
            this.soldier.getNavigation().stop();
            advance(path, index);
            return;
        }

        if (--this.recalcTimer <= 0) {
            this.recalcTimer = 10;
            if (!this.soldier.navigateTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.15)) {
                // no path at all to this waypoint - burn the give-up budget faster
                this.stuckOnPoint += 40;
            }
        }
    }

    private void advance(List<BlockPos> path, int index) {
        index++;
        this.soldier.setPathIndex(index);
        this.stuckOnPoint = 0;
        if (index < path.size()) return;
        if (this.soldier.getOrder() == OrderType.PATROL_PATH) {
            this.soldier.reversePathWaypoints();
        } else {
            this.soldier.setOrder(OrderType.HOLD_POSITION);
            this.soldier.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

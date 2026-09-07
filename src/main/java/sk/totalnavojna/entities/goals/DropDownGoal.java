package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import sk.totalnavojna.entities.SwatEntity;

import java.util.EnumSet;

// The destination is below us and the path planner refuses the drop: walk off the edge on purpose when the landing
// is at most 7 blocks down (3-4 HP for a 60 HP soldier) and it brings us closer to the destination.
public class DropDownGoal extends Goal {
    private static final int MAX_DROP = 7;

    private final SwatEntity soldier;
    private Vec3 destination = null;
    private BlockPos edgeTarget = null;
    private int ticks = 0;
    private int cooldown = 0;

    public DropDownGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.soldier.tickCount % 10 != 0 || !this.soldier.onGround()) return false;
        Vec3 dest = this.soldier.getWantedDestination();
        if (dest == null || this.soldier.getWantedDestinationAge() > 200) return false;
        double dy = dest.y - this.soldier.getY();
        if (dy > -2.0) return false;
        Path path = this.soldier.getNavigation().getPath();
        boolean unreachable = path == null || !path.canReach() || this.soldier.getNavigation().isDone() || this.soldier.isStuck();
        if (!unreachable) return false;

        // find an edge in the destination direction within 4 blocks with a landing spot not deeper than MAX_DROP
        Vec3 dir = new Vec3(dest.x - this.soldier.getX(), 0, dest.z - this.soldier.getZ());
        if (dir.lengthSqr() < 0.25) return false;
        dir = dir.normalize();
        BlockPos me = this.soldier.blockPosition();
        for (int step = 1; step <= 4; step++) {
            BlockPos p = BlockPos.containing(this.soldier.getX() + dir.x * step, this.soldier.getY(), this.soldier.getZ() + dir.z * step);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false; // wall in the way
            if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return false;
            if (!level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) continue; // still floor
            // hole: measure the drop
            for (int down = 2; down <= MAX_DROP + 1; down++) {
                BlockPos g = p.below(down);
                if (!level.getFluidState(g).isEmpty()) return false;
                if (!level.getBlockState(g).getCollisionShape(level, g).isEmpty()) {
                    // landing at g.above(); accept only if it is not far above the destination
                    if (g.getY() + 1 > dest.y + MAX_DROP) return false;
                    this.destination = dest;
                    this.edgeTarget = p.immutable();
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.edgeTarget != null && this.ticks < 40 && this.soldier.getState() == SwatEntity.STATE_ALIVE;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.soldier.getMoveControl().strafe(0.0f, 0.0f);
        this.cooldown = 40;
        if (this.destination != null) {
            this.soldier.navigateTo(this.destination.x, this.destination.y, this.destination.z, 1.1);
        }
        this.edgeTarget = null;
        this.destination = null;
    }

    @Override
    public void tick() {
        this.ticks++;
        double dx = this.edgeTarget.getX() + 0.5 - this.soldier.getX();
        double dz = this.edgeTarget.getZ() + 0.5 - this.soldier.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        this.soldier.setYRot(yaw);
        this.soldier.yBodyRot = yaw;
        this.soldier.getMoveControl().strafe(0.9f, 0.0f);
        if (!this.soldier.onGround() && this.ticks > 5) {
            // airborne: keep a little forward momentum and finish
            Vec3 d = this.soldier.getDeltaMovement();
            this.soldier.setDeltaMovement(d.x * 0.9, d.y, d.z * 0.9);
            if (this.ticks > 12) this.ticks = 40;
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

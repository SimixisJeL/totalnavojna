package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

// Vanilla ground pathfinding never plans vertical moves. When the wanted destination is on another level and the path
// cannot reach it, find a nearby ladder/vine/scaffolding column, walk to its open side, step into it and climb the way
// vanilla mobs do (push into the wall + jump flag = +0.2/tick), then step off the top towards the destination.
public class ClimbLadderGoal extends Goal {
    private static final int SCAN_RADIUS = 10;
    private enum Phase { APPROACH, ENTER, CLIMB, EXIT }

    private final SwatEntity soldier;
    private Vec3 destination = null;
    private BlockPos columnBottom = null;
    private BlockPos columnTop = null;
    private Direction openSide = null; // direction from the ladder block towards the free space (ladder FACING)
    private boolean ascending = true;
    private Phase phase = Phase.APPROACH;
    private int ticks = 0;
    private int phaseTicks = 0;
    private int noProgressTicks = 0;
    private double lastY = 0;
    private int cooldown = 0;

    public ClimbLadderGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!CommonConfig.SOLDIERS_CLIMB.get()) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel)) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.soldier.tickCount % 10 != 0) return false;

        Vec3 dest = this.soldier.getWantedDestination();
        if (dest == null || this.soldier.getWantedDestinationAge() > 200) return false;
        double dy = dest.y - this.soldier.getY();
        if (Math.abs(dy) < 2.0) return false;
        double horiz = new Vec3(dest.x - this.soldier.getX(), 0, dest.z - this.soldier.getZ()).lengthSqr();
        if (horiz > 40.0 * 40.0) return false;

        // already hanging on something -> just use it
        boolean onLadder = this.soldier.onClimbable();
        Path path = this.soldier.getNavigation().getPath();
        boolean unreachable = path == null || !path.canReach() || this.soldier.getNavigation().isDone() || this.soldier.isStuck();
        if (!unreachable && !onLadder) return false;

        this.destination = dest;
        this.ascending = dy > 0;
        return findColumn();
    }

    private boolean findColumn() {
        ServerLevel level = (ServerLevel) this.soldier.level();
        BlockPos me = this.soldier.blockPosition();
        int yMin = (int) Math.floor(Math.min(me.getY(), this.destination.y)) - 2;
        int yMax = (int) Math.floor(Math.max(me.getY(), this.destination.y)) + 2;
        Map<Long, int[]> columns = new HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(me.getX() - SCAN_RADIUS, yMin, me.getZ() - SCAN_RADIUS, me.getX() + SCAN_RADIUS, yMax, me.getZ() + SCAN_RADIUS)) {
            if (!level.getBlockState(p).is(BlockTags.CLIMBABLE)) continue;
            long key = (((long) p.getX()) << 32) ^ (p.getZ() & 0xFFFFFFFFL);
            int[] span = columns.computeIfAbsent(key, k -> new int[]{p.getY(), p.getY(), p.getX(), p.getZ()});
            span[0] = Math.min(span[0], p.getY());
            span[1] = Math.max(span[1], p.getY());
        }
        if (columns.isEmpty()) return false;

        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] span : columns.values()) {
            int bottom = span[0];
            int top = span[1];
            if (top - bottom < 1) continue;
            if (this.ascending) {
                if (top < this.destination.y - 2.0 || bottom > this.soldier.getY() + 1.5) continue;
            } else {
                if (bottom > this.destination.y + 2.0 || top < this.soldier.getY() - 2.0) continue;
            }
            double dx = span[2] + 0.5 - this.soldier.getX();
            double dz = span[3] + 0.5 - this.soldier.getZ();
            double score = dx * dx + dz * dz;
            if (score < bestScore) {
                bestScore = score;
                best = span;
            }
        }
        if (best == null) return false;
        this.columnBottom = new BlockPos(best[2], best[0], best[3]);
        this.columnTop = new BlockPos(best[2], best[1], best[3]);
        BlockState bottomState = level.getBlockState(this.columnBottom);
        this.openSide = bottomState.getBlock() instanceof LadderBlock ? bottomState.getValue(LadderBlock.FACING) : null;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.destination == null || this.columnBottom == null) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.ticks > 500 || this.noProgressTicks > 100) return false;
        if (this.phase == Phase.EXIT) return this.phaseTicks < 20;
        double dy = this.destination.y - this.soldier.getY();
        if (this.ascending && dy < 0.5) return false;
        if (!this.ascending && dy > -0.5) return false;
        return true;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.phaseTicks = 0;
        this.noProgressTicks = 0;
        this.lastY = this.soldier.getY();
        this.phase = this.soldier.onClimbable() ? Phase.CLIMB : Phase.APPROACH;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.soldier.setJumping(false);
        this.soldier.getMoveControl().strafe(0.0f, 0.0f);
        boolean failed = this.noProgressTicks > 100 || this.ticks > 500;
        if (failed) this.cooldown = 100;
        if (this.destination != null) {
            this.soldier.navigateTo(this.destination.x, this.destination.y, this.destination.z, 1.1);
        }
        this.destination = null;
        this.columnBottom = null;
        this.columnTop = null;
        this.openSide = null;
    }

    // Block the soldier should stand on before stepping into the column.
    private BlockPos approachPos() {
        BlockPos base = this.ascending ? this.columnBottom : this.columnTop.above();
        if (this.openSide != null && this.ascending) return base.relative(this.openSide);
        return base;
    }

    private void faceColumn() {
        double cx = this.columnBottom.getX() + 0.5;
        double cz = this.columnBottom.getZ() + 0.5;
        Vec3 dir;
        if (this.openSide != null) {
            dir = new Vec3(-this.openSide.getStepX(), 0, -this.openSide.getStepZ());
        } else {
            dir = new Vec3(cx - this.soldier.getX(), 0, cz - this.soldier.getZ());
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        }
        float yaw = (float) (Math.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f;
        this.soldier.setYRot(yaw);
        this.soldier.yBodyRot = yaw;
        this.soldier.yHeadRot = yaw;
        this.soldier.getLookControl().setLookAt(cx, this.soldier.getEyeY(), cz);
    }

    @Override
    public void tick() {
        this.ticks++;
        this.phaseTicks++;
        double cx = this.columnBottom.getX() + 0.5;
        double cz = this.columnBottom.getZ() + 0.5;
        double hx = cx - this.soldier.getX();
        double hz = cz - this.soldier.getZ();
        double horizSqr = hx * hx + hz * hz;
        boolean onLadder = this.soldier.onClimbable();

        switch (this.phase) {
            case APPROACH -> {
                BlockPos ap = approachPos();
                double d = ap.distSqr(this.soldier.blockPosition());
                if (onLadder) {
                    this.phase = Phase.CLIMB;
                    this.phaseTicks = 0;
                } else if (d <= 1.0 || horizSqr < 1.3 * 1.3) {
                    this.phase = Phase.ENTER;
                    this.phaseTicks = 0;
                    this.soldier.getNavigation().stop();
                } else if (this.phaseTicks % 10 == 1) {
                    this.soldier.getNavigation().moveTo(ap.getX() + 0.5, ap.getY(), ap.getZ() + 0.5, 1.15);
                }
            }
            case ENTER -> {
                if (onLadder) {
                    this.phase = Phase.CLIMB;
                    this.phaseTicks = 0;
                    return;
                }
                faceColumn();
                // walk straight into the column
                this.soldier.getMoveControl().strafe(0.6f, 0.0f);
                this.soldier.setDeltaMovement(this.soldier.getDeltaMovement().add(hx * 0.08, 0, hz * 0.08));
                if (this.ascending) this.soldier.getJumpControl().jump();
                if (this.phaseTicks > 40) {
                    this.phase = Phase.APPROACH;
                    this.phaseTicks = 0;
                }
            }
            case CLIMB -> {
                faceColumn();
                Vec3 delta = this.soldier.getDeltaMovement();
                if (this.ascending) {
                    // push into the wall + jump flag: vanilla gives +0.2/tick on climbables
                    this.soldier.getMoveControl().strafe(0.5f, 0.0f);
                    this.soldier.getJumpControl().jump();
                    this.soldier.setDeltaMovement(hx * 0.2, Math.max(delta.y, 0.2), hz * 0.2);
                    if (this.soldier.getY() >= this.columnTop.getY() + 0.95 || (!onLadder && this.soldier.getY() > this.columnTop.getY())) {
                        this.phase = Phase.EXIT;
                        this.phaseTicks = 0;
                    }
                } else {
                    this.soldier.getMoveControl().strafe(0.0f, 0.0f);
                    this.soldier.setDeltaMovement(hx * 0.2, -0.15, hz * 0.2);
                    if (this.soldier.onGround() && this.soldier.getY() <= this.destination.y + 1.5) {
                        this.phase = Phase.EXIT;
                        this.phaseTicks = 0;
                    }
                }
                if (!onLadder && this.phaseTicks > 10 && this.soldier.onGround()) {
                    // fell off / never got on -> approach again
                    this.phase = Phase.APPROACH;
                    this.phaseTicks = 0;
                }
            }
            case EXIT -> {
                // step off towards the destination
                double dx = this.destination.x - this.soldier.getX();
                double dz = this.destination.z - this.soldier.getZ();
                double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
                this.soldier.setYRot(yaw);
                this.soldier.yBodyRot = yaw;
                this.soldier.getMoveControl().strafe(0.8f, 0.0f);
                if (this.ascending) {
                    this.soldier.getJumpControl().jump();
                    Vec3 delta = this.soldier.getDeltaMovement();
                    this.soldier.setDeltaMovement(dx / len * 0.2, Math.max(delta.y, onLadder ? 0.25 : delta.y), dz / len * 0.2);
                }
            }
        }

        if (Math.abs(this.soldier.getY() - this.lastY) < 0.02 && horizSqr < 1.5 * 1.5 && this.phase != Phase.EXIT) {
            this.noProgressTicks++;
        } else if (this.phase == Phase.APPROACH && this.soldier.isStuck()) {
            this.noProgressTicks += 2;
        } else {
            this.noProgressTicks = 0;
        }
        this.lastY = this.soldier.getY();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.blocks.CoreBlock;
import sk.totalnavojna.blocks.SpawnerBlock;
import sk.totalnavojna.blocks.SupplyBlock;
import sk.totalnavojna.entities.SwatEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// PATHFINDER: carries pickaxe/axe/shovel. When it or a nearby teammate is stuck, it clears a purposeful route between the
// stuck unit and where that unit wants to go:
//   - same level  -> a 1x2 corridor straight ahead (max 6 blocks)
//   - destination higher (fell into a pit / cliff) -> a STAIRCASE: keeps every step block solid, clears the 2 blocks above it
//   - destination lower -> a descending staircase
// Blocks are dug nearest-first, so the pathfinder can work from the top of a pit as well as from inside it.
public class PathfinderDigGoal extends Goal {
    private static final double HELP_RADIUS = 20.0;
    private static final double REACH = 4.5;
    private static final int MAX_BLOCKS_PER_SESSION = 16;

    private final SwatEntity soldier;
    private SwatEntity subject = null;
    private final List<BlockPos> plan = new ArrayList<>();
    private BlockPos digPos = null;
    private int digProgress = 0;
    private int digRequired = 0;
    private int lastStage = -1;
    private int blocksDug = 0;
    private int sessionTicks = 0;
    private int travelTicks = 0;

    public PathfinderDigGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static boolean hasIntent(SwatEntity e) {
        return e.getWantedDestination() != null && e.getWantedDestinationAge() < 300
                && e.getWantedDestination().distanceToSqr(e.position()) > 2.0 * 2.0;
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getRole() != Role.PATHFINDER) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        if (this.soldier.getTarget() != null && this.soldier.getTarget().isAlive()) return false;
        if (this.soldier.tickCount % 10 != 0) return false;

        this.subject = null;
        if (this.soldier.getStuckTicks() > 40 && hasIntent(this.soldier)) {
            this.subject = this.soldier;
        } else {
            double best = HELP_RADIUS * HELP_RADIUS;
            List<SwatEntity> mates = level.getEntitiesOfClass(SwatEntity.class, this.soldier.getBoundingBox().inflate(HELP_RADIUS),
                    (e) -> e != this.soldier && e.getArmyTeam() == this.soldier.getArmyTeam() && e.getState() == SwatEntity.STATE_ALIVE
                            && e.getStuckTicks() > 50 && hasIntent(e));
            for (SwatEntity mate : mates) {
                double d = this.soldier.distanceToSqr(mate);
                if (d < best) {
                    best = d;
                    this.subject = mate;
                }
            }
        }
        if (this.subject == null) return false;
        buildPlan();
        return !this.plan.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.subject == null || !this.subject.isAlive() || this.subject.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.getTarget() != null && this.soldier.getTarget().isAlive()) return false;
        if (this.blocksDug >= MAX_BLOCKS_PER_SESSION || this.sessionTicks > 900) return false;
        // the unit got moving again -> job done
        if (this.subject != this.soldier && this.subject.getStuckTicks() == 0 && this.sessionTicks > 60 && this.digPos == null) return false;
        return this.digPos != null || !this.plan.isEmpty();
    }

    @Override
    public void start() {
        this.blocksDug = 0;
        this.sessionTicks = 0;
        this.travelTicks = 0;
        this.digProgress = 0;
        this.lastStage = -1;
        this.digPos = null;
        TotalnaVojna.LOGGER.info("[TV] Pathfinder {} digging for {} ({} blocks planned)", this.soldier.getPatientName(), this.subject.getPatientName(), this.plan.size());
    }

    @Override
    public void stop() {
        if (this.digPos != null && this.soldier.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(this.soldier.getId(), this.digPos, -1);
        }
        this.soldier.restoreGun();
        this.digPos = null;
        this.plan.clear();
        this.subject = null;
    }

    private boolean isDiggable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (state.getBlock() instanceof CoreBlock || state.getBlock() instanceof SpawnerBlock || state.getBlock() instanceof SupplyBlock) return false;
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.CHEST) || state.is(Blocks.SPAWNER)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isSolid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    // Plans the route from the subject towards its destination. Only blocks that are actually in the way are listed.
    private void buildPlan() {
        this.plan.clear();
        ServerLevel level = (ServerLevel) this.soldier.level();
        Vec3 dest = this.subject.getWantedDestination();
        if (dest == null) return;
        Vec3 dir = new Vec3(dest.x - this.subject.getX(), 0, dest.z - this.subject.getZ());
        if (dir.lengthSqr() < 0.01) dir = Vec3.directionFromRotation(0, this.subject.getYRot());
        dir = dir.normalize();
        // snap to the dominant axis so stairs/corridors are straight
        Vec3 step = Math.abs(dir.x) >= Math.abs(dir.z) ? new Vec3(Math.signum(dir.x), 0, 0) : new Vec3(0, 0, Math.signum(dir.z));
        BlockPos feet = this.subject.blockPosition();
        int dy = (int) Math.round(dest.y - feet.getY());

        if (dy >= 2) {
            // ascending staircase: step i stands on (ahead_i, y+i-1), needs (ahead_i, y+i) and (ahead_i, y+i+1) free
            int steps = Math.min(dy, 10);
            for (int i = 1; i <= steps; i++) {
                BlockPos ahead = feet.offset((int) step.x * i, i, (int) step.z * i);
                BlockPos stand = ahead.below();
                if (!isSolid(level, stand)) {
                    // nothing to stand on -> cannot build stairs into the air, stop here
                    break;
                }
                if (isDiggable(level, ahead)) this.plan.add(ahead.immutable());
                if (isDiggable(level, ahead.above())) this.plan.add(ahead.above().immutable());
            }
            // headroom directly above the subject so it can make the first jump
            if (isDiggable(level, feet.above(2))) this.plan.add(0, feet.above(2).immutable());
        } else if (dy <= -2) {
            // descending staircase: clear (ahead_i, y-i .. y-i+1)
            int steps = Math.min(-dy, 10);
            for (int i = 1; i <= steps; i++) {
                BlockPos ahead = feet.offset((int) step.x * i, -i, (int) step.z * i);
                if (isDiggable(level, ahead)) this.plan.add(ahead.immutable());
                if (isDiggable(level, ahead.above())) this.plan.add(ahead.above().immutable());
                if (isDiggable(level, ahead.above(2))) this.plan.add(ahead.above(2).immutable());
            }
        } else {
            // corridor on the same level, max 6 blocks or until the destination
            int len = (int) Math.min(6, Math.ceil(Math.sqrt(dest.distanceToSqr(this.subject.position()))));
            for (int i = 1; i <= Math.max(1, len); i++) {
                BlockPos ahead = feet.offset((int) step.x * i, 0, (int) step.z * i);
                if (isDiggable(level, ahead)) this.plan.add(ahead.immutable());
                if (isDiggable(level, ahead.above())) this.plan.add(ahead.above().immutable());
                // 1-block step up in the corridor is fine (auto jump), 2 is a wall -> handled by the two lines above
            }
        }
    }

    @Nullable
    private BlockPos nearestPlanned(ServerLevel level) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        this.plan.removeIf(p -> !isDiggable(level, p));
        for (BlockPos p : this.plan) {
            double d = p.distSqr(this.soldier.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void tick() {
        this.sessionTicks++;
        ServerLevel level = (ServerLevel) this.soldier.level();

        if (this.digPos == null) {
            this.digPos = nearestPlanned(level);
            this.digProgress = 0;
            this.lastStage = -1;
            if (this.digPos == null) return;
        }

        double distSqr = this.digPos.distSqr(this.soldier.blockPosition());
        if (distSqr > REACH * REACH) {
            this.travelTicks++;
            if (this.travelTicks % 10 == 1) {
                this.soldier.navigateTo(this.digPos.getX() + 0.5, this.digPos.getY(), this.digPos.getZ() + 0.5, 1.15);
            }
            if (this.travelTicks > 200) {
                // cannot get to this one - try another block of the plan
                this.plan.remove(this.digPos);
                this.digPos = null;
                this.travelTicks = 0;
            }
            return;
        }
        this.travelTicks = 0;

        BlockState state = level.getBlockState(this.digPos);
        if (!isDiggable(level, this.digPos)) {
            this.plan.remove(this.digPos);
            this.digPos = null;
            return;
        }

        this.soldier.getNavigation().stop();
        this.soldier.getLookControl().setLookAt(this.digPos.getX() + 0.5, this.digPos.getY() + 0.5, this.digPos.getZ() + 0.5);
        boolean hasTool = this.soldier.holdTool(state);
        if (this.digProgress == 0) {
            float hardness = state.getDestroySpeed(level, this.digPos);
            this.digRequired = (int) Math.max(8, Math.min(200, hardness * 30.0f / (hasTool ? 4.0f : 1.0f)));
        }
        this.digProgress++;
        if (this.digProgress % 6 == 0) {
            this.soldier.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, this.digPos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.5f, 0.8f);
        }
        int stage = Math.min(9, this.digProgress * 10 / Math.max(1, this.digRequired));
        if (stage != this.lastStage) {
            this.lastStage = stage;
            level.destroyBlockProgress(this.soldier.getId(), this.digPos, stage);
        }
        if (this.digProgress >= this.digRequired) {
            level.destroyBlockProgress(this.soldier.getId(), this.digPos, -1);
            level.destroyBlock(this.digPos, false);
            this.blocksDug++;
            this.plan.remove(this.digPos);
            this.digPos = null;
            this.digProgress = 0;
            this.lastStage = -1;
            if (this.plan.isEmpty()) buildPlan();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

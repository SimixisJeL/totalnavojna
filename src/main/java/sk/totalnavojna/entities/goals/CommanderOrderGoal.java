package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.entities.goals.utils.FormationUtils;
import sk.totalnavojna.orders.OrderType;

import java.util.EnumSet;
import java.util.List;

// Ported from Simple-Enemy-Mod-Public (GPL-3.0) by NekoYuni, adapted for TotalnaVojna
public class CommanderOrderGoal extends Goal {

    private final SwatEntity mob;
    private final double speedModifier;
    private final float stopDistance;
    private final float startDistance;
    private int timeToRecalcPath;

    private Vec3 frozenFormationDirection = null;
    private Vec3 lastOwnerPosition = Vec3.ZERO;

    private static final int FORMATION_COOLDOWN = 100;
    private int ticksSinceLastCombat = FORMATION_COOLDOWN;

    public CommanderOrderGoal(SwatEntity mob, double speedModifier, float startDist, float stopDist) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.startDistance = startDist;
        this.stopDistance = stopDist;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public void resetCombatCooldown() {
        this.ticksSinceLastCombat = FORMATION_COOLDOWN;
    }

    @Override
    public boolean canUse() {
        OrderType currentOrder = this.mob.getOrder();
        boolean handled = currentOrder == OrderType.HOLD_POSITION
                || currentOrder == OrderType.MOVE_TO_POSITION
                || currentOrder == OrderType.FOLLOW_COMMANDER
                || currentOrder == OrderType.FORM_WEDGE
                || currentOrder == OrderType.FORM_COLUMN;
        if (!handled) return false;
        // HOLD and MOVE push through combat; follow/formations pause while fighting
        boolean pausesInCombat = currentOrder == OrderType.FOLLOW_COMMANDER
                || currentOrder == OrderType.FORM_WEDGE
                || currentOrder == OrderType.FORM_COLUMN;
        if (pausesInCombat && this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null) {
            ticksSinceLastCombat = 0;
        }
        return this.canUse();
    }

    @Override
    public void start() {
        super.start();
        this.frozenFormationDirection = null;
        this.lastOwnerPosition = Vec3.ZERO;
    }

    @Override
    public void tick() {
        OrderType tickOrder = this.mob.getOrder();
        boolean inCombat = this.mob.getTarget() != null && this.mob.getTarget().isAlive();
        if (!inCombat) {
            ticksSinceLastCombat++;
        } else {
            ticksSinceLastCombat = 0;
            if (tickOrder != OrderType.HOLD_POSITION && tickOrder != OrderType.MOVE_TO_POSITION) {
                return;
            }
        }

        OrderType order = this.mob.getOrder();

        boolean hasActiveOrder = (order == OrderType.HOLD_POSITION ||
                order == OrderType.MOVE_TO_POSITION ||
                order == OrderType.FOLLOW_COMMANDER ||
                order == OrderType.FORM_WEDGE ||
                order == OrderType.FORM_COLUMN);

        if (!hasActiveOrder && ticksSinceLastCombat < FORMATION_COOLDOWN) {
            return;
        }

        switch (order) {
            case HOLD_POSITION:
                performHoldPosition();
                break;

            case FOLLOW_COMMANDER:
            case FORM_WEDGE:
            case FORM_COLUMN:
                performFollowOwner(order);
                break;

            case MOVE_TO_POSITION:
                performMoveToPosition();
                break;
            default:
                break;
        }
    }

    private void performHoldPosition() {
        if (!this.mob.getNavigation().isDone()) {
            this.mob.getNavigation().stop();
        }
    }

    private void performFollowOwner(OrderType currentOrder) {
        if (this.mob.getOwnerUUID() == null) return;
        LivingEntity owner = this.mob.level().getPlayerByUUID(this.mob.getOwnerUUID());
        if (owner == null) return;

        Vec3 idealTarget;
        int myIndex = this.mob.getFormationIndex();

        if (currentOrder == OrderType.FORM_COLUMN && myIndex > 0) {
            SwatEntity predecessor = findPredecessor(myIndex - 1);

            if (predecessor != null) {
                float yRot = predecessor.yBodyRot;
                double rad = Math.toRadians(yRot);
                Vec3 offset = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize().scale(-1.5);
                idealTarget = predecessor.position().add(offset);
            } else {
                idealTarget = calculateClassicFormationTarget(owner, currentOrder, myIndex);
            }
        } else {
            idealTarget = calculateClassicFormationTarget(owner, currentOrder, myIndex);
        }

        double distSqr = this.mob.distanceToSqr(idealTarget);
        double stopThreshold;
        double startThreshold;

        if (currentOrder == OrderType.FORM_COLUMN) {
            stopThreshold = 1.2D * 1.2D;
            startThreshold = 1.8D * 1.8D;
        } else if (currentOrder == OrderType.FORM_WEDGE) {
            stopThreshold = 2.0D * 2.0D;
            startThreshold = 4.5D * 4.5D;
        } else {
            stopThreshold = (this.stopDistance * this.stopDistance);
            startThreshold = (this.startDistance * this.startDistance);
        }

        if (distSqr < stopThreshold) {
            if (!this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().stop();
            }
        } else if (distSqr > startThreshold) {
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = 10;

                Vec3 finalMoveTarget = idealTarget;

                if (myIndex == 0 && !isPositionSafe(idealTarget)) {
                    finalMoveTarget = owner.position();
                }

                this.mob.navigateTo(finalMoveTarget.x, finalMoveTarget.y, finalMoveTarget.z, this.speedModifier);
            }
        }
    }

    private Vec3 calculateClassicFormationTarget(LivingEntity owner, OrderType currentOrder, int myIndex) {
        Vec3 currentOwnerPos = owner.position();

        double positionChangeSqr = lastOwnerPosition.distanceToSqr(currentOwnerPos);
        boolean ownerMoved = positionChangeSqr > 0.25;

        float yRot = owner.yBodyRot;
        double f = yRot * (Math.PI / 180F);

        Vec3 currentBodyDirection = new Vec3(-Math.sin(f), 0, Math.cos(f)).normalize();
        Vec3 formationDirection;

        if (currentOrder == OrderType.FOLLOW_COMMANDER) {
            formationDirection = currentBodyDirection;
            frozenFormationDirection = null;
        } else {
            if (ownerMoved) {
                frozenFormationDirection = currentBodyDirection;
                formationDirection = currentBodyDirection;
                lastOwnerPosition = currentOwnerPos;
            } else {
                if (frozenFormationDirection == null) {
                    frozenFormationDirection = currentBodyDirection;
                    lastOwnerPosition = currentOwnerPos;
                }
                formationDirection = frozenFormationDirection;
            }
        }

        return (currentOrder == OrderType.FOLLOW_COMMANDER) ?
                owner.position() :
                FormationUtils.getTargetPosition(owner.position(), formationDirection, currentOrder, myIndex);
    }

    private SwatEntity findPredecessor(int targetIndex) {
        List<SwatEntity> allies = this.mob.level().getEntitiesOfClass(
                SwatEntity.class,
                this.mob.getBoundingBox().inflate(20.0),
                e -> e != this.mob && e.getArmyTeam() == this.mob.getArmyTeam() && e.getFormationIndex() == targetIndex
        );

        if (!allies.isEmpty()) {
            return allies.get(0);
        }
        return null;
    }

    private void performMoveToPosition() {
        Vec3 finalTarget = snapMoveOrderToBlockCenter(this.mob.getMoveToTarget());
        if (finalTarget == Vec3.ZERO) return;

        double distSqr = this.mob.distanceToSqr(finalTarget);
        if (distSqr < 2.0D) {
            this.mob.getNavigation().stop();
            // Arrived. Without this the order stayed MOVE_TO_POSITION for the rest of the unit's life, which quietly
            // disabled everything gated on "is this unit free" - drone operators never flew again, for one.
            this.mob.setOrder(OrderType.HOLD_POSITION);
        } else {
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = 8;
                this.mob.navigateTo(finalTarget.x, finalTarget.y, finalTarget.z, this.speedModifier);
            }
        }
    }

    private Vec3 snapMoveOrderToBlockCenter(Vec3 target) {
        if (target == null || target == Vec3.ZERO) {
            return Vec3.ZERO;
        }
        BlockPos block = BlockPos.containing(target);
        return Vec3.atBottomCenterOf(block);
    }


    private boolean isPositionSafe(Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        boolean feetBlocked = isBlocked(blockPos);
        boolean headBlocked = isBlocked(blockPos.above());

        return !feetBlocked && !headBlocked;
    }

    private boolean isBlocked(BlockPos pos) {
        BlockState state = this.mob.level().getBlockState(pos);
        return state.isRedstoneConductor(this.mob.level(), pos) || state.isSolid();
    }
}

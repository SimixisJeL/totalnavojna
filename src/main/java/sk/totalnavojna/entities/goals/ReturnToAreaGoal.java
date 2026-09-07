package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.WarGameData;

import java.util.EnumSet;

// Enforces the group's chunk boundary ("mantinel"): soldiers outside walk back in.
public class ReturnToAreaGoal extends Goal {

    private final SwatEntity soldier;
    private int checkTimer = 0;
    private BlockPos returnTarget = null;

    public ReturnToAreaGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (--this.checkTimer > 0) return false;
        this.checkTimer = 20;

        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;

        WarGameData data = WarGameData.get(level);
        if (data.isInsideArea(this.soldier.getArmyTeam(), this.soldier.getGroup(), this.soldier.blockPosition())) {
            return false;
        }
        this.returnTarget = data.nearestAreaChunkCenter(this.soldier.getArmyTeam(), this.soldier.getGroup(), this.soldier.blockPosition());
        return this.returnTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.returnTarget == null) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        return !WarGameData.get(level).isInsideArea(this.soldier.getArmyTeam(), this.soldier.getGroup(), this.soldier.blockPosition())
                && !this.soldier.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (this.returnTarget != null) {
            this.soldier.navigateTo(this.returnTarget.getX() + 0.5, this.returnTarget.getY(), this.returnTarget.getZ() + 0.5, 1.2);
        }
    }

    @Override
    public void stop() {
        this.returnTarget = null;
    }
}

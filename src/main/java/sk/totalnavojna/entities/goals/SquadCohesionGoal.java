package sk.totalnavojna.entities.goals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;
import sk.totalnavojna.war.SquadTactics;

import java.util.EnumSet;

// Squad cohesion: a soldier with nothing to do keeps near his squad leader instead of wandering off alone.
//
// This runs at the very bottom of the priority list and only for a man under no orders at all, so it can never
// pull a unit off a command - it just stops a group from slowly turning into scattered singles.
public class SquadCohesionGoal extends Goal {

    private final SwatEntity soldier;
    private SwatEntity leader;
    private int recalcTimer;

    public SquadCohesionGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!CommonConfig.SQUAD_LEADERS.get()) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.isPassenger() || this.soldier.hasDroneAloft()) return false;
        if (this.soldier.getGroup() <= 0) return false;
        if (this.soldier.getOrder() != OrderType.NONE) return false;
        if (this.soldier.getTarget() != null) return false;
        if (SquadTactics.isLeader(this.soldier)) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;

        this.leader = SquadTactics.leaderOf(level, this.soldier.getArmyTeam(), this.soldier.getGroup());
        if (this.leader == null || this.leader == this.soldier) return false;
        double max = CommonConfig.SQUAD_COHESION.get();
        return this.soldier.distanceToSqr(this.leader) > max * max;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.leader == null || !this.leader.isAlive()) return false;
        if (this.soldier.getOrder() != OrderType.NONE || this.soldier.getTarget() != null) return false;
        double close = CommonConfig.SQUAD_COHESION.get() * 0.5;
        return this.soldier.distanceToSqr(this.leader) > close * close;
    }

    @Override
    public void stop() {
        this.leader = null;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.leader == null) return;
        if (--this.recalcTimer > 0) return;
        this.recalcTimer = 30;
        // spread out around the leader rather than piling onto him
        int index = Math.floorMod(this.soldier.getId(), 8);
        double angle = index * (Math.PI / 4.0);
        double ring = 4.0;
        this.soldier.navigateTo(
                this.leader.getX() + Math.cos(angle) * ring,
                this.leader.getY(),
                this.leader.getZ() + Math.sin(angle) * ring,
                1.0);
    }
}

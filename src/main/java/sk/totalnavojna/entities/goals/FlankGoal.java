package sk.totalnavojna.entities.goals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.SquadTactics;
import sk.totalnavojna.war.TerrainScan;

import java.util.EnumSet;

// When a squad has been chewed up walking into the same guns, SquadTactics flags it as flanking and this goal
// takes over the movement: swing out to one side, then come back in on the enemy from there.
//
// It only moves the soldier. Shooting keeps running from GunAttackGoal, so a flanking man who gets a clear
// shot on the way still takes it.
public class FlankGoal extends Goal {

    private final SwatEntity soldier;
    private Vec3 flankPos;
    private int recalcTimer;
    private int maxTicks;

    public FlankGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!CommonConfig.FLANK_ENABLED.get()) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.isPassenger()) return false;
        if (this.soldier.getRole().isSupport()) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        LivingEntity target = this.soldier.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (!SquadTactics.isFlanking(this.soldier.getArmyTeam(), this.soldier.getGroup(), level.getGameTime())) return false;
        return pickFlankPos(level, target) != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.flankPos == null || --this.maxTicks <= 0) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE || this.soldier.isPassenger()) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        if (!SquadTactics.isFlanking(this.soldier.getArmyTeam(), this.soldier.getGroup(), level.getGameTime())) return false;
        LivingEntity target = this.soldier.getTarget();
        if (target == null || !target.isAlive()) return false;
        // arrived on the flank: hand movement back to the normal combat goals, they will close from here
        return this.soldier.position().distanceToSqr(this.flankPos) > 36.0;
    }

    @Override
    public void start() {
        this.maxTicks = 400;
        this.recalcTimer = 0;
    }

    @Override
    public void stop() {
        this.flankPos = null;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.flankPos == null) return;
        if (--this.recalcTimer > 0) return;
        this.recalcTimer = 20;
        this.soldier.getLookControl().setLookAt(this.flankPos.x, this.flankPos.y + 1.0, this.flankPos.z);
        if (!this.soldier.navigateTo(this.flankPos.x, this.flankPos.y, this.flankPos.z, 1.25)) {
            // the swing is blocked - drop the attempt so the squad does not stand around
            this.maxTicks = 0;
        }
    }

    // A point off to the side of the line between us and the enemy, at roughly the enemy's distance.
    private Vec3 pickFlankPos(ServerLevel level, LivingEntity target) {
        Vec3 me = this.soldier.position();
        Vec3 toEnemy = target.position().subtract(me);
        double len = toEnemy.horizontalDistance();
        if (len < 8.0) return null;   // already on top of him, nothing to go around

        Vec3 forward = new Vec3(toEnemy.x / len, 0, toEnemy.z / len);
        int side = SquadTactics.flankSide(this.soldier.getArmyTeam(), this.soldier.getGroup());
        Vec3 perpendicular = new Vec3(-forward.z * side, 0, forward.x * side);
        double offset = CommonConfig.FLANK_OFFSET.get();

        for (double scale = 1.0; scale >= 0.4; scale -= 0.2) {
            Vec3 wanted = me
                    .add(forward.scale(len * 0.5))
                    .add(perpendicular.scale(offset * scale));
            int y = TerrainScan.surfaceY(level, wanted.x, wanted.z);
            net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(wanted.x, y, wanted.z);
            if (!TerrainScan.standable(level, feet)) continue;
            this.flankPos = new Vec3(wanted.x, y, wanted.z);
            return this.flankPos;
        }
        return null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }
}

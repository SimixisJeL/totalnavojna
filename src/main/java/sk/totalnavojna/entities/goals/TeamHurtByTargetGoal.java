package sk.totalnavojna.entities.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import sk.totalnavojna.entities.SwatEntity;

public class TeamHurtByTargetGoal extends HurtByTargetGoal {

    public TeamHurtByTargetGoal(SwatEntity mob) {
        super(mob);
        this.setAlertOthers();
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) return false;
        LivingEntity hurtBy = this.mob.getLastHurtByMob();
        return hurtBy != null && ((SwatEntity) this.mob).isValidTarget(hurtBy);
    }

    @Override
    protected void alertOther(Mob other, LivingEntity attacker) {
        // support roles only fight back when shot at themselves
        SwatEntity me = (SwatEntity) this.mob;
        if (other instanceof SwatEntity swat && !swat.getRole().isSupport() && swat.isValidTarget(attacker)
                && me.level() instanceof net.minecraft.server.level.ServerLevel sl && sk.totalnavojna.war.WarGameData.get(sl).isFriendly(me.getArmyTeam(), swat.getArmyTeam())) {
            super.alertOther(other, attacker);
        }
    }
}

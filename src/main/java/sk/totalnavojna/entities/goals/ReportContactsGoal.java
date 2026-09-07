package sk.totalnavojna.entities.goals;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import sk.totalnavojna.entities.SwatEntity;
import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import sk.totalnavojna.war.BattleIntel;
import sk.totalnavojna.war.DroneLink;
import sk.totalnavojna.war.WarGameData;
import sk.totalnavojna.Team;

import java.util.EnumSet;
import java.util.List;

// Every soldier is also a pair of eyes: what he sees goes into the team's shared picture (BattleIntel), so the rest
// of the army can act on it. Claims no flags - it runs alongside whatever else the soldier is doing.
public class ReportContactsGoal extends Goal {
    private static final double SIGHT = 48.0;

    private final SwatEntity soldier;

    public ReportContactsGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return this.soldier.getState() == SwatEntity.STATE_ALIVE && this.soldier.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (this.soldier.tickCount % 20 != 0) return;
        if (!(this.soldier.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        // whoever shot at us is a contact even if we never saw him
        LivingEntity hurtBy = this.soldier.getLastHurtByMob();
        if (hurtBy != null && this.soldier.tickCount - this.soldier.getLastHurtByMobTimestamp() < 100) {
            BattleIntel.report(this.soldier.getArmyTeam(), hurtBy, now);
        }

        List<Entity> seen = level.getEntities(this.soldier, this.soldier.getBoundingBox().inflate(SIGHT), (e) -> {
            if (e instanceof DroneEntity d) {
                Team owner = DroneLink.teamOf(d);
                if (owner != null) return !WarGameData.get(level).isFriendly(this.soldier.getArmyTeam(), owner);
                return d.getController() != null && this.soldier.isEnemy(d.getController());
            }
            if (e instanceof VehicleEntity v) {
                if (v.isWreck()) return false;
                for (Entity p : v.getPassengers()) {
                    if (p instanceof LivingEntity le && this.soldier.isValidTarget(le)) return true;
                }
                return false;
            }
            if (!(e instanceof LivingEntity le)) return false;
            if (!(e instanceof SwatEntity || e instanceof Player)) return false;
            return this.soldier.isValidTarget(le);
        });
        for (Entity e : seen) {
            if (!this.soldier.hasLineOfSight(e)) continue;
            boolean fresh = !BattleIntel.isKnown(this.soldier.getArmyTeam(), e);
            BattleIntel.report(this.soldier.getArmyTeam(), e, now);
            if (!fresh) continue;
            if (e instanceof VehicleEntity && !(e instanceof DroneEntity)) {
                sk.totalnavojna.war.VoiceLines.radio(level, this.soldier.getArmyTeam(), sk.totalnavojna.war.VoiceLines.Line.ENEMY_VEHICLE);
            } else {
                sk.totalnavojna.war.VoiceLines.radio(level, this.soldier.getArmyTeam(), sk.totalnavojna.war.VoiceLines.Line.CONTACT);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }
}

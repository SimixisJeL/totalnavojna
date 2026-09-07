package sk.totalnavojna.entities.goals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import sk.totalnavojna.Role;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.ReviveHelper;

import java.util.EnumSet;
import java.util.List;

// Every soldier with a medkit can revive downed teammates.
// Medics: bigger radius, keep reviving even under fire, downed PLAYER commanders first, 4 s instead of 6 s.
// A patient already being revived by somebody else is skipped (one reviver per patient).
public class MedicReviveGoal extends Goal {
    private static final double REVIVE_RANGE_SQR = 2.5 * 2.5;

    private final SwatEntity soldier;
    private LivingEntity patient;
    private int stuckTicks = 0;

    public MedicReviveGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean isMedic() {
        return this.soldier.getRole() == Role.MEDIC;
    }

    private boolean isPatientCandidate(LivingEntity candidate, long now) {
        ReviveHelper.Channel ch = ReviveHelper.channelOf(candidate);
        if (ch == null || !candidate.isAlive()) return false;
        if (!ReviveHelper.sameTeam(this.soldier, candidate)) return false;
        return !ReviveHelper.isClaimedByOther(ch, now, this.soldier.getUUID());
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.findMedkitSlot() < 0) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        if (this.soldier.tickCount % 10 != 0) return false;

        boolean medic = isMedic();
        // regular soldiers only help when not actively fighting; medics always do
        if (!medic && this.soldier.getTarget() != null && this.soldier.getTarget().isAlive()) return false;

        double radius = medic ? 20.0 : 8.0;
        double radiusSqr = radius * radius;
        long now = level.getGameTime();

        this.patient = null;
        double bestDist = Double.MAX_VALUE;

        // medics prioritize downed player commanders
        if (medic) {
            for (Player p : level.players()) {
                if (!(p instanceof ServerPlayer sp)) continue;
                if (!isPatientCandidate(sp, now)) continue;
                double d = this.soldier.distanceToSqr(sp);
                if (d <= radiusSqr && d < bestDist) {
                    bestDist = d;
                    this.patient = sp;
                }
            }
        }

        if (this.patient == null) {
            bestDist = Double.MAX_VALUE;
            List<SwatEntity> downed = level.getEntitiesOfClass(
                    SwatEntity.class,
                    this.soldier.getBoundingBox().inflate(radius),
                    (e) -> e != this.soldier && e.getState() == SwatEntity.STATE_DOWN && isPatientCandidate(e, now)
            );
            for (SwatEntity candidate : downed) {
                double d = this.soldier.distanceToSqr(candidate);
                if (d < bestDist) {
                    bestDist = d;
                    this.patient = candidate;
                }
            }
            // non-medics also help downed players nearby
            if (this.patient == null && !medic) {
                for (Player p : level.players()) {
                    if (!(p instanceof ServerPlayer sp)) continue;
                    if (!isPatientCandidate(sp, now)) continue;
                    double d = this.soldier.distanceToSqr(sp);
                    if (d <= radiusSqr && d < bestDist) {
                        bestDist = d;
                        this.patient = sp;
                    }
                }
            }
        }

        return this.patient != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.patient == null || !this.patient.isAlive()) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.findMedkitSlot() < 0) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        ReviveHelper.Channel ch = ReviveHelper.channelOf(this.patient);
        if (ch == null) return false;
        // somebody else took over (e.g. a medic arrived) -> let them
        if (ReviveHelper.isClaimedByOther(ch, level.getGameTime(), this.soldier.getUUID())) return false;
        return this.stuckTicks < 200;
    }

    @Override
    public void start() {
        this.stuckTicks = 0;
    }

    @Override
    public void stop() {
        if (this.patient != null && this.soldier.level() instanceof ServerLevel) {
            ReviveHelper.Channel ch = ReviveHelper.channelOf(this.patient);
            if (ch != null && this.soldier.getUUID().equals(ch.getReviverUUID())) {
                ch.setReviverUUID(null);
                ch.setReviveTicks(0);
            }
        }
        this.patient = null;
        this.stuckTicks = 0;
    }

    @Override
    public void tick() {
        if (this.patient == null || !(this.soldier.level() instanceof ServerLevel level)) return;

        double distSqr = this.soldier.distanceToSqr(this.patient);
        if (distSqr > REVIVE_RANGE_SQR) {
            this.stuckTicks++;
            if (this.soldier.getNavigation().isDone() || this.soldier.tickCount % 20 == 0) {
                this.soldier.navigateTo(this.patient.getX(), this.patient.getY(), this.patient.getZ(), 1.25);
            }
            return;
        }

        this.stuckTicks = 0;
        this.soldier.getNavigation().stop();
        this.soldier.getLookControl().setLookAt(this.patient);
        ReviveHelper.channel(this.soldier, this.patient, level.getGameTime());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

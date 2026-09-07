package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;
import sk.totalnavojna.war.WarGameData;

import java.util.EnumSet;

// What an attacker walks towards when nobody is shooting at him, and what a defender stands on.
//
// The attack side picks the nearest objective of three kinds, in whatever order the ground puts them:
//   * an unheld CAPTURE POINT   - taken by standing on it, MatchManager does the counting
//   * an enemy forward SPAWNER  - knocked out life by life, same as a NEXUS but with far fewer lives
//   * the enemy NEXUS           - the thing that actually ends the war
// Defenders stay near their own NEXUS. Explicit ATTACK_CORE / DEFEND_CORE orders override role defaults.
public class WarObjectiveGoal extends Goal {
    private static final double CORE_ATTACK_RANGE_SQR = 3.5 * 3.5;
    private static final double DEFEND_RADIUS_SQR = 18.0 * 18.0;
    private static final double CAPTURE_DETOUR = 96.0;

    private enum Kind { CORE, SPAWNER, CAPTURE }

    private final SwatEntity soldier;
    private int recalcTimer = 0;
    private int hitTimer = 0;
    private int lastStage = -1;
    private BlockPos lastPos = null;
    private Kind kind = Kind.CORE;

    public WarObjectiveGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean attackMode() {
        OrderType order = this.soldier.getOrder();
        if (order == OrderType.ATTACK_CORE) return true;
        if (order == OrderType.DEFEND_CORE) return false;
        return this.soldier.getRole() == Role.ATTACKER;
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.getTarget() != null && this.soldier.getTarget().isAlive()) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;

        OrderType order = this.soldier.getOrder();
        boolean explicitOrder = order == OrderType.ATTACK_CORE || order == OrderType.DEFEND_CORE;
        boolean roleDefault = order == OrderType.NONE
                && (this.soldier.getRole() == Role.ATTACKER || this.soldier.getRole() == Role.DEFENDER);
        if (!explicitOrder && !roleDefault) return false;

        WarGameData data = WarGameData.get(level);
        if (attackMode()) {
            // only a team at WAR pushes onto the enemy
            if (data.getStance(this.soldier.getArmyTeam()) != sk.totalnavojna.war.Stance.WAR) return false;
            return pickObjective(level, data) != null;
        }
        return data.nearestCore(level, this.soldier.getArmyTeam(), this.soldier.blockPosition()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        clearCrack();
        this.hitTimer = 0;
    }

    private void clearCrack() {
        if (this.lastPos != null && this.soldier.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(this.soldier.getId(), this.lastPos, -1);
        }
        this.lastPos = null;
        this.lastStage = -1;
    }

    private int hitTicks() {
        int base = this.soldier.getRole() == Role.ATTACKER
                ? CommonConfig.CORE_HIT_TICKS_ATTACKER.get() : CommonConfig.CORE_HIT_TICKS.get();
        // a forward base is a soft target compared to a NEXUS
        return this.kind == Kind.SPAWNER ? Math.max(1, base / 2) : base;
    }

    // Nearest thing worth walking to. Sets this.kind as a side effect.
    @Nullable
    private BlockPos pickObjective(ServerLevel level, WarGameData data) {
        BlockPos me = this.soldier.blockPosition();
        WarGameData.CoreEntry core = data.nearestCore(level, this.soldier.getArmyTeam().enemy(), me);
        BlockPos best = core == null ? null : core.pos;
        Kind bestKind = Kind.CORE;
        double bestDist = core == null ? Double.MAX_VALUE : core.pos.distSqr(me);

        if (CommonConfig.SPAWNER_LIVES.get() > 0) {
            WarGameData.SpawnerEntry fob = data.nearestSpawner(level.dimension().location().toString(),
                    this.soldier.getArmyTeam().enemy(), me);
            if (fob != null) {
                double d = fob.pos.distSqr(me);
                if (d < bestDist) {
                    bestDist = d;
                    best = fob.pos;
                    bestKind = Kind.SPAWNER;
                }
            }
        }

        // a capture point we do not hold, but only if it is roughly on the way
        String dim = level.dimension().location().toString();
        for (WarGameData.CaptureEntry cap : data.captures) {
            if (!cap.dim.equals(dim) || cap.owner == this.soldier.getArmyTeam()) continue;
            double d = cap.pos.distSqr(me);
            if (d > CAPTURE_DETOUR * CAPTURE_DETOUR) continue;
            if (d < bestDist) {
                bestDist = d;
                best = cap.pos;
                bestKind = Kind.CAPTURE;
            }
        }

        this.kind = bestKind;
        return best;
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) this.soldier.level();
        WarGameData data = WarGameData.get(level);

        if (!attackMode()) {
            WarGameData.CoreEntry core = data.nearestCore(level, this.soldier.getArmyTeam(), this.soldier.blockPosition());
            if (core == null) return;
            double distSqr = core.pos.distSqr(this.soldier.blockPosition());
            if (distSqr > DEFEND_RADIUS_SQR) {
                if (--this.recalcTimer <= 0) {
                    this.recalcTimer = 20;
                    this.soldier.navigateTo(core.pos.getX() + 0.5, core.pos.getY(), core.pos.getZ() + 0.5, 1.1);
                }
            } else if (!this.soldier.getNavigation().isDone() && distSqr < DEFEND_RADIUS_SQR * 0.5) {
                this.soldier.getNavigation().stop();
            }
            return;
        }

        BlockPos objective = pickObjective(level, data);
        if (objective == null) return;
        if (!objective.equals(this.lastPos)) {
            clearCrack();
            this.lastPos = objective;
            this.hitTimer = 0;
        }

        // A capture point is taken by being there, not by hitting it.
        if (this.kind == Kind.CAPTURE) {
            double radius = CommonConfig.CAPTURE_RADIUS.get() * 0.6;
            if (objective.distSqr(this.soldier.blockPosition()) > radius * radius) {
                if (--this.recalcTimer <= 0) {
                    this.recalcTimer = 20;
                    this.soldier.navigateTo(objective.getX() + 0.5, objective.getY(), objective.getZ() + 0.5, 1.1);
                }
            } else {
                this.soldier.getNavigation().stop();
            }
            return;
        }

        double distSqr = objective.distSqr(this.soldier.blockPosition());
        if (distSqr > CORE_ATTACK_RANGE_SQR) {
            this.hitTimer = 0;
            if (--this.recalcTimer <= 0) {
                this.recalcTimer = 20;
                this.soldier.navigateTo(objective.getX() + 0.5, objective.getY(), objective.getZ() + 0.5, 1.1);
            }
            return;
        }

        this.soldier.getNavigation().stop();
        this.soldier.getLookControl().setLookAt(objective.getX() + 0.5, objective.getY() + 0.5, objective.getZ() + 0.5);
        int required = hitTicks();
        this.hitTimer++;
        if (this.hitTimer % 10 == 0) {
            this.soldier.swing(InteractionHand.MAIN_HAND);
        }
        int stage = Math.min(9, this.hitTimer * 10 / required);
        if (stage != this.lastStage) {
            this.lastStage = stage;
            level.destroyBlockProgress(this.soldier.getId(), objective, stage);
        }
        if (this.hitTimer < required) return;

        this.hitTimer = 0;
        this.lastStage = -1;
        this.soldier.playSound(SoundEvents.ANVIL_LAND, 0.4f, 1.6f);
        if (this.kind == Kind.SPAWNER) {
            WarGameData.SpawnerEntry fob = data.spawnerAt(level, objective);
            if (fob != null) data.takeSpawnerLife(level, fob);
        } else {
            WarGameData.CoreEntry core = data.coreAt(level, objective);
            if (core != null) data.takeCoreLife(level, core, this.soldier);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

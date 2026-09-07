package sk.totalnavojna.entities.goals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.WarGameData;

import java.util.EnumSet;

// Out of combat and running low? Walk to the nearest friendly SUPPLY STATION and restock ammo, medkits and potions.
public class ResupplyGoal extends Goal {
    private static final int RESUPPLY_TICKS = 60;
    private static final int OUT_OF_COMBAT_TICKS = 100;

    private final SwatEntity soldier;
    private WarGameData.SupplyEntry station = null;
    private int channel = 0;
    private int travelTicks = 0;

    public ResupplyGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel level)) return false;
        boolean forced = this.soldier.getOrder() == sk.totalnavojna.orders.OrderType.RESUPPLY;
        if (this.soldier.tickCount % (forced ? 10 : 40) != 0) return false;
        if (!forced) {
            if (this.soldier.getTarget() != null && this.soldier.getTarget().isAlive()) return false;
            if (this.soldier.tickCount - this.soldier.getLastHurtByMobTimestamp() < OUT_OF_COMBAT_TICKS) return false;
            if (!this.soldier.needsResupply()) return false;
        }
        this.station = WarGameData.get(level).nearestSupply(level, this.soldier.getArmyTeam(), this.soldier.blockPosition(), forced ? 4096 : CommonConfig.SUPPLY_RANGE.get());
        if (this.station == null && forced) this.soldier.setOrder(sk.totalnavojna.orders.OrderType.NONE);
        return this.station != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.station == null) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        boolean forced = this.soldier.getOrder() == sk.totalnavojna.orders.OrderType.RESUPPLY;
        if (!forced && this.soldier.getTarget() != null && this.soldier.getTarget().isAlive()) return false;
        if (this.travelTicks > (forced ? 1800 : 600)) return false;
        return this.channel < RESUPPLY_TICKS;
    }

    @Override
    public void start() {
        if (this.soldier.level() instanceof net.minecraft.server.level.ServerLevel voiceLevel) {
            sk.totalnavojna.war.VoiceLines.radio(voiceLevel, this.soldier.getArmyTeam(), sk.totalnavojna.war.VoiceLines.Line.NO_AMMO);
        }
        this.channel = 0;
        this.travelTicks = 0;
    }

    @Override
    public void stop() {
        this.station = null;
        this.channel = 0;
    }

    @Override
    public void tick() {
        if (this.station == null) return;
        ServerLevel level = (ServerLevel) this.soldier.level();
        double distSqr = this.station.pos.distSqr(this.soldier.blockPosition());
        if (distSqr > 2.5 * 2.5) {
            this.travelTicks++;
            if (this.travelTicks % 10 == 1) {
                this.soldier.navigateTo(this.station.pos.getX() + 0.5, this.station.pos.getY(), this.station.pos.getZ() + 0.5, 1.15);
            }
            return;
        }
        this.soldier.getNavigation().stop();
        this.soldier.getLookControl().setLookAt(this.station.pos.getX() + 0.5, this.station.pos.getY() + 0.5, this.station.pos.getZ() + 0.5);
        this.channel++;
        if (this.channel % 15 == 0) this.soldier.swing(InteractionHand.MAIN_HAND);
        if (this.channel >= RESUPPLY_TICKS) {
            this.soldier.resupply();
            if (this.soldier.getOrder() == sk.totalnavojna.orders.OrderType.RESUPPLY) this.soldier.setOrder(sk.totalnavojna.orders.OrderType.HOLD_POSITION);
            level.playSound(null, this.station.pos, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.NEUTRAL, 0.8f, 1.0f);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

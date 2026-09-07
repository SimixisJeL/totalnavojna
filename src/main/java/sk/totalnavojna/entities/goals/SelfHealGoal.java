package sk.totalnavojna.entities.goals;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;

import java.util.EnumSet;

// Soldier patches himself up out of combat: a bandage first, a leftover healing potion only if he has
// no bandage left. Never while somebody is shooting at him.
public class SelfHealGoal extends Goal {
    private static final float POTION_HEAL_AMOUNT = 8.0f;
    private static final int OUT_OF_COMBAT_TICKS = 60;

    private final SwatEntity soldier;
    private int drinkTimer = 0;
    private boolean usingBandage = false;

    public SelfHealGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.soldier.getTarget() != null && this.soldier.getTarget().isAlive()) return false;
        if (this.soldier.tickCount - this.soldier.getLastHurtByMobTimestamp() < OUT_OF_COMBAT_TICKS) return false;

        boolean wantsHeal;
        if (this.soldier.getOrder() == OrderType.FULL_HEAL) {
            wantsHeal = this.soldier.getHealth() < this.soldier.getMaxHealth();
        } else {
            wantsHeal = this.soldier.getHealth() < CommonConfig.SOLDIER_HEAL_THRESHOLD.get();
        }
        if (!wantsHeal) {
            if (this.soldier.getOrder() == OrderType.FULL_HEAL) {
                this.soldier.setOrder(OrderType.NONE);
            }
            return false;
        }

        return this.soldier.findBandageSlot() >= 0 || this.soldier.findPotionSlot() >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.drinkTimer > 0 && this.canUse();
    }

    @Override
    public void start() {
        this.usingBandage = this.soldier.findBandageSlot() >= 0;
        this.drinkTimer = this.usingBandage ? CommonConfig.BANDAGE_USE_TICKS.get() : 32;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.drinkTimer--;
        if (this.drinkTimer > 0) return;

        int slot = this.usingBandage ? this.soldier.findBandageSlot() : this.soldier.findPotionSlot();
        if (slot < 0) {
            // ran out mid-application, fall back to whatever else is in the pouch
            slot = this.usingBandage ? this.soldier.findPotionSlot() : this.soldier.findBandageSlot();
            this.usingBandage = !this.usingBandage;
        }
        if (slot < 0) return;

        ItemStack used = this.soldier.getItem(slot);
        used.shrink(1);
        if (used.isEmpty()) {
            this.soldier.setItem(slot, ItemStack.EMPTY);
        }
        if (this.usingBandage) {
            this.soldier.heal((float) (double) CommonConfig.BANDAGE_HEAL.get());
            this.soldier.playSound(SoundEvents.WOOL_PLACE, 0.8f, 1.2f);
        } else {
            this.soldier.heal(POTION_HEAL_AMOUNT);
            this.soldier.playSound(SoundEvents.GENERIC_DRINK, 1.0f, 1.0f);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

package sk.totalnavojna.items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.WarEvents;

// Right-click = heal a living teammate (+10 HP). Reviving downed units is done by HOLDING LEFT CLICK with the medkit (see ClientReviveHandler / PacketReviveHold).
public class MedkitItem extends Item {

    public MedkitItem(Properties props) {
        super(props);
    }

    private static final float HEAL_AMOUNT = 10.0f;

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof SwatEntity soldier) {
            if (soldier.getState() == SwatEntity.STATE_DOWN) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("§e✚ Podrž ĽAVÉ tlačidlo s medkitom na downnutom, kým sa neoživí."), true);
                }
                return InteractionResult.sidedSuccess(player.level().isClientSide);
            }
            // heal an injured living teammate
            if (soldier.getState() == SwatEntity.STATE_ALIVE && soldier.getHealth() < soldier.getMaxHealth()
                    && player instanceof ServerPlayer sp && soldier.isCommandedBy(sp)) {
                soldier.heal(HEAL_AMOUNT);
                stack.shrink(1);
                soldier.playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 0.5f, 1.6f);
                return InteractionResult.SUCCESS;
            }
        }
        if (target instanceof ServerPlayer targetPlayer && player instanceof ServerPlayer healer) {
            if (WarEvents.isDowned(targetPlayer)) {
                healer.displayClientMessage(Component.literal("§e✚ Podrž ĽAVÉ tlačidlo s medkitom na downnutom hráčovi, kým sa neoživí."), true);
                return InteractionResult.SUCCESS;
            }
            // heal an injured living team player
            Team healerTeam = TeamsSavedData.get(healer.serverLevel()).getTeam(healer.getUUID());
            Team targetTeam = TeamsSavedData.get(targetPlayer.serverLevel()).getTeam(targetPlayer.getUUID());
            if (healerTeam != null && healerTeam == targetTeam && targetPlayer.getHealth() < targetPlayer.getMaxHealth()) {
                targetPlayer.heal(HEAL_AMOUNT);
                stack.shrink(1);
                targetPlayer.playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 0.5f, 1.6f);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}

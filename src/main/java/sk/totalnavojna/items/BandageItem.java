package sk.totalnavojna.items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.war.WarEvents;

// The field dressing. Same job a healing potion did, but it looks like something a soldier would carry
// and it takes long enough that patching yourself up in the open is a decision, not a reflex.
//
// The division of labour matters and is deliberate:
//   BANDAGE heals a man who is still standing.
//   MEDKIT  brings back a man who is down.
public class BandageItem extends Item {

    public BandageItem(Properties props) {
        super(props);
    }

    private static float healAmount() {
        return (float) (double) CommonConfig.BANDAGE_HEAL.get();
    }

    private static int useTicks() {
        return CommonConfig.BANDAGE_USE_TICKS.get();
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return useTicks();
    }

    // ===== bandaging yourself =====

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() >= player.getMaxHealth()) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("§7Si zdravý, obväz šetri."), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (WarEvents.isDowned(player instanceof ServerPlayer sp ? sp : null)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity user) {
        if (!level.isClientSide) {
            user.heal(healAmount());
            user.playSound(SoundEvents.WOOL_PLACE, 0.8f, 1.2f);
            if (user instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("§a✚ Obviazané. §7HP: §f"
                        + (int) sp.getHealth() + "/" + (int) sp.getMaxHealth()), true);
            }
        }
        if (!(user instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    // ===== bandaging somebody else =====

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
                                                           @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (!(player instanceof ServerPlayer healer)) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }

        if (target instanceof SwatEntity soldier) {
            if (soldier.getState() == SwatEntity.STATE_DOWN) {
                healer.displayClientMessage(Component.literal("§eObväz raneného nepostaví. Na oživenie treba MEDKIT (podrž ĽAVÉ tlačidlo)."), true);
                return InteractionResult.CONSUME;
            }
            if (soldier.getState() == SwatEntity.STATE_ALIVE && soldier.getHealth() < soldier.getMaxHealth()
                    && soldier.isCommandedBy(healer)) {
                soldier.heal(healAmount());
                stack.shrink(1);
                soldier.playSound(SoundEvents.WOOL_PLACE, 0.8f, 1.2f);
                healer.displayClientMessage(Component.literal("§a✚ Obviazaný spolubojovník."), true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.CONSUME;
        }

        if (target instanceof ServerPlayer targetPlayer) {
            if (WarEvents.isDowned(targetPlayer)) {
                healer.displayClientMessage(Component.literal("§eObväz downnutého nezdvihne. Na oživenie treba MEDKIT."), true);
                return InteractionResult.CONSUME;
            }
            Team healerTeam = TeamsSavedData.get(healer.serverLevel()).getTeam(healer.getUUID());
            Team targetTeam = TeamsSavedData.get(targetPlayer.serverLevel()).getTeam(targetPlayer.getUUID());
            if (healerTeam != null && healerTeam == targetTeam && targetPlayer.getHealth() < targetPlayer.getMaxHealth()) {
                targetPlayer.heal(healAmount());
                stack.shrink(1);
                targetPlayer.playSound(SoundEvents.WOOL_PLACE, 0.8f, 1.2f);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}

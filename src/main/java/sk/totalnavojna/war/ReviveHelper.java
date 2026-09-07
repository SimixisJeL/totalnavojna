package sk.totalnavojna.war;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.items.ModItems;

import java.util.UUID;

// One shared revive-channel protocol for every reviver (player holding LMB, soldier goal) and every patient (downed soldier, downed player).
// The patient owns the channel state; exactly one reviver may hold the claim at a time (heartbeat based, so a dead/absent reviver frees it).
public final class ReviveHelper {
    public static final int HEARTBEAT_TIMEOUT = 10;

    public enum Result { INVALID, BUSY, PROGRESS, DONE }

    public interface Channel {
        @Nullable UUID getReviverUUID();
        void setReviverUUID(@Nullable UUID uuid);
        int getReviveTicks();
        void setReviveTicks(int ticks);
        long getReviveHeartbeat();
        void setReviveHeartbeat(long gameTime);
        void completeRevive();
        String getPatientName();
    }

    private ReviveHelper() {
    }

    public static boolean isClaimedByOther(Channel ch, long now, @Nullable UUID me) {
        UUID reviver = ch.getReviverUUID();
        if (reviver == null) return false;
        if (reviver.equals(me)) return false;
        return now - ch.getReviveHeartbeat() <= HEARTBEAT_TIMEOUT;
    }

    public static boolean isBeingRevived(Channel ch, long now) {
        return isClaimedByOther(ch, now, null);
    }

    // Drops a stale claim (reviver walked away / died / stopped holding the key).
    public static void tickTimeout(Channel ch, long now) {
        if (ch.getReviverUUID() != null && now - ch.getReviveHeartbeat() > HEARTBEAT_TIMEOUT) {
            ch.setReviverUUID(null);
            ch.setReviveTicks(0);
        }
    }

    public static int requiredTicks(LivingEntity reviver) {
        if (reviver instanceof SwatEntity soldier && soldier.getRole() == Role.MEDIC) {
            return CommonConfig.MEDIC_REVIVE_TIME.get();
        }
        return CommonConfig.REVIVE_TIME.get();
    }

    @Nullable
    public static Channel channelOf(LivingEntity patient) {
        if (patient instanceof SwatEntity swat) {
            return swat.getState() == SwatEntity.STATE_DOWN ? swat : null;
        }
        if (patient instanceof ServerPlayer player) {
            return WarEvents.getDowned(player);
        }
        return null;
    }

    @Nullable
    public static Team teamOf(LivingEntity entity) {
        if (entity instanceof SwatEntity swat) return swat.getArmyTeam();
        if (entity instanceof ServerPlayer player) return TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
        return null;
    }

    // same team, or both teams declared ALLIANCE
    public static boolean sameTeam(LivingEntity a, LivingEntity b) {
        Team ta = teamOf(a);
        Team tb = teamOf(b);
        if (ta == null || tb == null) return false;
        if (ta == tb) return true;
        return a.level() instanceof net.minecraft.server.level.ServerLevel sl && WarGameData.get(sl).areAllied(ta, tb);
    }

    public static boolean hasMedkit(LivingEntity reviver) {
        if (reviver instanceof SwatEntity soldier) return soldier.findMedkitSlot() >= 0;
        if (reviver instanceof Player player) {
            return player.getMainHandItem().is(ModItems.MEDKIT.get()) || player.getOffhandItem().is(ModItems.MEDKIT.get());
        }
        return false;
    }

    private static void consumeMedkit(LivingEntity reviver) {
        if (reviver instanceof SwatEntity soldier) {
            int slot = soldier.findMedkitSlot();
            if (slot >= 0) {
                ItemStack kit = soldier.getItem(slot);
                kit.shrink(1);
                if (kit.isEmpty()) soldier.setItem(slot, ItemStack.EMPTY);
            }
        } else if (reviver instanceof Player player) {
            ItemStack kit = player.getMainHandItem().is(ModItems.MEDKIT.get()) ? player.getMainHandItem() : player.getOffhandItem();
            if (!player.isCreative()) kit.shrink(1);
        }
    }

    // Call once per tick while the reviver is in range with a medkit. Progress is kept on the patient.
    public static Result channel(LivingEntity reviver, LivingEntity patient, long now) {
        Channel ch = channelOf(patient);
        if (ch == null || !patient.isAlive()) return Result.INVALID;
        if (!sameTeam(reviver, patient) || !hasMedkit(reviver)) return Result.INVALID;
        UUID me = reviver.getUUID();
        if (isClaimedByOther(ch, now, me)) return Result.BUSY;
        if (!me.equals(ch.getReviverUUID())) {
            ch.setReviverUUID(me);
            ch.setReviveTicks(0);
        }
        // at most one progress tick per game tick even if several heartbeats arrive
        if (ch.getReviveHeartbeat() == now && ch.getReviveTicks() > 0) return Result.PROGRESS;
        ch.setReviveHeartbeat(now);
        ch.setReviveTicks(ch.getReviveTicks() + 1);
        int required = requiredTicks(reviver);
        if (ch.getReviveTicks() >= required) {
            consumeMedkit(reviver);
            ch.setReviverUUID(null);
            ch.setReviveTicks(0);
            ch.completeRevive();
            reviver.playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 0.6f, 1.4f);
            if (reviver instanceof Player player) player.swing(InteractionHand.MAIN_HAND, true);
            return Result.DONE;
        }
        return Result.PROGRESS;
    }

    public static int progressPercent(LivingEntity reviver, LivingEntity patient) {
        Channel ch = channelOf(patient);
        if (ch == null) return 0;
        return ch.getReviveTicks() * 100 / Math.max(1, requiredTicks(reviver));
    }
}

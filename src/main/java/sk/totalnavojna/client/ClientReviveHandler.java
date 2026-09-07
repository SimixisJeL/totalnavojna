package sk.totalnavojna.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.items.ModItems;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketReviveHold;

// HOLD LEFT MOUSE with a medkit on a downed soldier/player = revive channel. The click itself is swallowed (no punch).
@Mod.EventBusSubscriber(modid = TotalnaVojna.MOD_ID, value = Dist.CLIENT)
public class ClientReviveHandler {
    private static int swingTimer = 0;

    private static boolean holdingMedkit(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.MEDKIT.get()) || player.getOffhandItem().is(ModItems.MEDKIT.get());
    }

    @Nullable
    private static Entity revivableUnderCrosshair(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof EntityHitResult ehr)) return null;
        Entity target = ehr.getEntity();
        if (target instanceof SwatEntity swat) {
            return swat.getState() == SwatEntity.STATE_DOWN ? swat : null;
        }
        if (target instanceof Player other && other != mc.player) {
            return ClientDownedState.contains(other.getUUID()) ? other : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void onKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !holdingMedkit(mc.player)) return;
        if (revivableUnderCrosshair(mc) != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!mc.options.keyAttack.isDown()) return;
        if (!holdingMedkit(mc.player)) return;
        Entity target = revivableUnderCrosshair(mc);
        if (target == null) return;
        if (mc.player.distanceToSqr(target) > 4.0 * 4.0) return;
        ModNetworking.sendToServer(new PacketReviveHold(target.getId()));
        if (++swingTimer >= 8) {
            swingTimer = 0;
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }
}

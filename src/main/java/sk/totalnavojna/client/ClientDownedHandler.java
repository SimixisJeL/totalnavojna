package sk.totalnavojna.client;

import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sk.totalnavojna.TotalnaVojna;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// While downed the local player lies on the ground and can do nothing: no movement, no clicks, no inventory - only chat (/bleedout) and pause.
// Every downed player (local or remote) gets the lying (swimming) pose forced client-side, because Player.updatePlayerPose recomputes poses locally.
@Mod.EventBusSubscriber(modid = TotalnaVojna.MOD_ID, value = Dist.CLIENT)
public class ClientDownedHandler {
    private static final Set<UUID> FORCED_BY_US = new HashSet<>();

    private static boolean screenAllowed(Screen screen) {
        return screen == null || screen instanceof ChatScreen || screen instanceof PauseScreen || screen instanceof DeathScreen;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientDownedState.isDowned()) return;
        var input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        event.getEntity().setSprinting(false);
    }

    @SubscribeEvent
    public static void onKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ClientDownedState.isDowned()) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!ClientDownedState.isDowned()) return;
        if (!screenAllowed(event.getNewScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            FORCED_BY_US.clear();
            return;
        }

        // lying pose for everybody who is downed
        for (Player p : mc.level.players()) {
            boolean downed = ClientDownedState.contains(p.getUUID());
            if (downed) {
                if (p.getForcedPose() != Pose.SWIMMING) p.setForcedPose(Pose.SWIMMING);
                FORCED_BY_US.add(p.getUUID());
            } else if (FORCED_BY_US.remove(p.getUUID())) {
                if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
            }
        }

        if (!ClientDownedState.isDowned()) return;
        if (!screenAllowed(mc.screen)) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (!event.getLogicalSide().isClient()) return;
        if (ClientDownedState.isDowned() && event.getShooter() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }
}

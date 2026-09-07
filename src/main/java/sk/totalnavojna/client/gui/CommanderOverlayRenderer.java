package sk.totalnavojna.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sk.totalnavojna.client.util.CommanderRayTrace;

import java.util.HashSet;
import java.util.Set;

// Ported from Simple-Enemy-Mod-Public (GPL-3.0) by NekoYuni
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class CommanderOverlayRenderer {

    public static boolean isSelectingPosition = false;
    public static Set<Integer> selectedUnitsSnapshot = new HashSet<>();
    public static boolean isSelectingTarget = false;
    // which order the next target click issues (ATTACK_THAT_TARGET or BOARD_VEHICLE)
    public static sk.totalnavojna.orders.OrderType pendingTargetOrder = sk.totalnavojna.orders.OrderType.ATTACK_THAT_TARGET;
    public static sk.totalnavojna.orders.OrderType pendingPositionOrder = sk.totalnavojna.orders.OrderType.MOVE_TO_POSITION;

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!isSelectingPosition && !isSelectingTarget) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        if (isSelectingPosition) {
            BlockHitResult result = CommanderRayTrace.rayTrace(player, 45.0);

            if (CommanderRayTrace.isValidMoveTarget(result)) {
                Vec3 hitPos = result.getLocation();

                if (player.tickCount % 2 == 0) {
                    player.level().addParticle(
                            ParticleTypes.HAPPY_VILLAGER,
                            hitPos.x, hitPos.y + 0.1, hitPos.z,
                            0, 0.1, 0
                    );
                }
            }
        }

        if (isSelectingTarget) {
            Entity target = CommanderRayTrace.rayTraceEntity(player, 64.0);

            if (target == null) return;
            if (player.tickCount % 2 == 0) return;

            player.level().addParticle(
                    ParticleTypes.FLAME,
                    target.getX(), target.getY() + (target.getBbHeight() / 2), target.getZ(),
                    0, 0.1, 0
            );
        }
    }
}

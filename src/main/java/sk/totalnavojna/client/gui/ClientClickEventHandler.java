package sk.totalnavojna.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import sk.totalnavojna.client.util.CommanderRayTrace;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketIssueOrder;
import sk.totalnavojna.orders.OrderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Ported from Simple-Enemy-Mod-Public (GPL-3.0) by NekoYuni
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientClickEventHandler {

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (CommanderOverlayRenderer.isSelectingPosition) {
            handleMoveSelection(event, mc);
        } else if (CommanderOverlayRenderer.isSelectingTarget) {
            handleAttackSelection(event, mc);
        }
    }

    private static void handleMoveSelection(InputEvent.MouseButton.Pre event, Minecraft mc) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_1 && event.getAction() == GLFW.GLFW_PRESS) {
            var result = CommanderRayTrace.rayTrace(mc.player, 45.0);

            if (CommanderRayTrace.isValidMoveTarget(result)) {
                sendMoveToOrder(result.getLocation());
                CommanderOverlayRenderer.isSelectingPosition = false;
                CommanderMenuScreen.markOrderTime();
                event.setCanceled(true);

                mc.player.displayClientMessage(Component.literal("§aRozkaz na presun odoslaný!"), true);
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_2 && event.getAction() == GLFW.GLFW_PRESS) {
            CommanderOverlayRenderer.isSelectingPosition = false;
            event.setCanceled(true);
        }
    }

    private static void handleAttackSelection(InputEvent.MouseButton.Pre event, Minecraft mc) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_1 && event.getAction() == GLFW.GLFW_PRESS) {
            Entity target = CommanderRayTrace.rayTraceEntity(mc.player, 50.0);

            if (target != null) {
                sendAttackOrder(target.getId());
                CommanderOverlayRenderer.isSelectingTarget = false;
                CommanderMenuScreen.markOrderTime();
                event.setCanceled(true);
                mc.player.displayClientMessage(Component.literal("§aCieľ určený: "
                        + target.getDisplayName().getString()), true);
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_2 && event.getAction() == GLFW.GLFW_PRESS) {
            CommanderOverlayRenderer.isSelectingTarget = false;
            event.setCanceled(true);
        }
    }

    private static void sendMoveToOrder(Vec3 pos) {
        Set<Integer> targets = CommanderOverlayRenderer.selectedUnitsSnapshot;
        if (targets == null || targets.isEmpty()) return;

        List<Integer> sortedIds = new ArrayList<>(targets);
        sortedIds.sort((id1, id2) -> Integer.compare(id2, id1));

        for (int i = 0; i < sortedIds.size(); i++) {
            ModNetworking.sendToServer(new PacketIssueOrder(
                    sortedIds.get(i), CommanderOverlayRenderer.pendingPositionOrder,
                    pos, i, -1
            ));
        }
        CommanderOverlayRenderer.selectedUnitsSnapshot.clear();
    }

    private static void sendAttackOrder(int targetId) {
        Set<Integer> targets = CommanderOverlayRenderer.selectedUnitsSnapshot;
        if (targets == null || targets.isEmpty()) return;

        List<Integer> sortedIds = new ArrayList<>(targets);
        sortedIds.sort((id1, id2) -> Integer.compare(id2, id1));

        for (int i = 0; i < sortedIds.size(); i++) {
            ModNetworking.sendToServer(new PacketIssueOrder(
                    sortedIds.get(i), CommanderOverlayRenderer.pendingTargetOrder,
                    Vec3.ZERO, i, targetId
            ));
        }
        CommanderOverlayRenderer.selectedUnitsSnapshot.clear();
    }
}

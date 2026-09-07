package sk.totalnavojna.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.orders.OrderType;

import java.util.function.Supplier;

// Ported from Simple-Enemy-Mod-Public (GPL-3.0) by NekoYuni, adapted for TotalnaVojna
public class PacketIssueOrder {

    private final int entityId;
    private final OrderType order;
    private final Vec3 targetPos;
    private final int formationIndex;
    private final int targetEntityId;

    public PacketIssueOrder(int entityId, OrderType order, Vec3 targetPos, int formationIndex, int targetEntityId) {
        this.entityId = entityId;
        this.order = order;
        this.targetPos = targetPos;
        this.formationIndex = formationIndex;
        this.targetEntityId = targetEntityId;
    }

    public static void encode(PacketIssueOrder msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeEnum(msg.order);
        buf.writeDouble(msg.targetPos.x);
        buf.writeDouble(msg.targetPos.y);
        buf.writeDouble(msg.targetPos.z);
        buf.writeInt(msg.formationIndex);
        buf.writeInt(msg.targetEntityId);
    }

    public static PacketIssueOrder decode(FriendlyByteBuf buf) {
        return new PacketIssueOrder(
                buf.readInt(),
                buf.readEnum(OrderType.class),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readInt(),
                buf.readInt()
        );
    }

    public static void handle(PacketIssueOrder msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity target = sender.level().getEntity(msg.entityId);
            if (!(target instanceof SwatEntity soldier)) return;

            if (!soldier.isCommandedBy(sender)) return;

            OrderType effective = msg.order;
            if (msg.order == OrderType.ATTACK_THAT_TARGET || msg.order == OrderType.DRONE_STRIKE_TARGET) {
                soldier.setAttackTargetId(msg.targetEntityId);
            } else if (msg.order == OrderType.MOVE_TO_POSITION || msg.order == OrderType.DRONE_STRIKE_POSITION || msg.order == OrderType.DRONE_SCOUT) {
                soldier.setMoveToTarget(msg.targetPos);
            } else if (msg.order == OrderType.MOVE_AND_UNLOAD) {
                soldier.setMoveToTarget(msg.targetPos);
                // only a vehicle driver can unload anybody - everyone else just moves there
                if (!(soldier.getVehicle() instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v) || v.getFirstPassenger() != soldier) {
                    effective = OrderType.MOVE_TO_POSITION;
                }
            } else if (msg.order == OrderType.RETREAT_TO_NEXUS) {
                sk.totalnavojna.war.WarGameData.CoreEntry core = sk.totalnavojna.war.WarGameData.get(sender.serverLevel())
                        .nearestCore(sender.serverLevel(), soldier.getArmyTeam(), soldier.blockPosition());
                if (core == null) {
                    sender.displayClientMessage(net.minecraft.network.chat.Component.literal("§cTvoj tím nemá NEXUS, niet kam ustúpiť."), true);
                    return;
                }
                double ang = (msg.formationIndex * 0.7);
                soldier.setMoveToTarget(new Vec3(core.pos.getX() + 0.5 + Math.cos(ang) * (3 + msg.formationIndex % 4), core.pos.getY(), core.pos.getZ() + 0.5 + Math.sin(ang) * (3 + msg.formationIndex % 4)));
                effective = OrderType.MOVE_TO_POSITION;
            } else if (msg.order == OrderType.PATROL_PATH) {
                if (soldier.getPathWaypoints().isEmpty()) {
                    if (msg.formationIndex == 0) sender.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNajprv im na mape (M) nakresli a vyšli trasu, potom Hliadka po trase."), true);
                    return;
                }
                soldier.setPathIndex(0);
            } else if (msg.order == OrderType.BOARD_VEHICLE) {
                soldier.setMountTargetId(msg.targetEntityId);
            } else if (msg.order == OrderType.DISMOUNT_VEHICLE) {
                soldier.stopRiding();
                soldier.setMountTargetId(-1);
            }

            if (msg.order == OrderType.FREE_FIRE) {
                soldier.setAttackTargetId(-1);
            }

            if (msg.order == OrderType.CEASE_FIRE) {
                soldier.setTarget(null);
                soldier.setAttackTargetId(-1);
                soldier.setLastHurtByMob(null);
            }

            soldier.setFormationIndex(msg.formationIndex);
            soldier.setCommanderUUID(sender.getUUID());
            soldier.setOrder(effective == OrderType.DISMOUNT_VEHICLE ? OrderType.NONE : effective);
            soldier.resetCommanderGoalCooldown();

            boolean movementOrder = msg.order == OrderType.MOVE_TO_POSITION
                    || msg.order == OrderType.HOLD_POSITION
                    || msg.order == OrderType.FOLLOW_COMMANDER
                    || msg.order == OrderType.FORM_WEDGE
                    || msg.order == OrderType.FORM_COLUMN
                    || msg.order == OrderType.ATTACK_CORE
                    || msg.order == OrderType.DEFEND_CORE
                    || msg.order == OrderType.BOARD_VEHICLE
                    || msg.order == OrderType.RETREAT_TO_NEXUS
                    || msg.order == OrderType.PATROL_PATH
                    || msg.order == OrderType.MOVE_AND_UNLOAD
                    || msg.order == OrderType.RESUPPLY;
            if (movementOrder) {
                soldier.setTarget(null);
                soldier.setLastHurtByMob(null);
                soldier.markOrderFocus();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

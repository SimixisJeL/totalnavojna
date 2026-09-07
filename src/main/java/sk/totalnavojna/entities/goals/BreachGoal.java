package sk.totalnavojna.entities.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;

import java.util.EnumSet;

// Iron doors / trapdoors in the way: first look for a lever or button on OUR side and press it; if the door still does not open,
// break it slowly (ironDoorBreakTicks, pathfinders 3x faster with a pickaxe). Wooden trapdoors are simply toggled.
public class BreachGoal extends Goal {
    private enum Phase { NONE, GO_TO_SWITCH, WAIT_SWITCH, PASS, BREAK }

    private final SwatEntity soldier;
    private BlockPos doorPos = null;
    private BlockPos switchPos = null;
    private Phase phase = Phase.NONE;
    private int progress = 0;
    private int waitTicks = 0;
    private int totalTicks = 0;
    private int lastStage = -1;
    private final java.util.List<BlockPos> triedSwitches = new java.util.ArrayList<>();

    public BreachGoal(SwatEntity soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static boolean isIronDoorClosed(BlockState state) {
        return state.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand() && !state.getValue(DoorBlock.OPEN);
    }

    private static boolean isTrapdoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock;
    }

    private boolean isObstacle(BlockState state) {
        return isIronDoorClosed(state) || (isTrapdoor(state) && !state.getValue(TrapDoorBlock.OPEN));
    }

    @Nullable
    private BlockPos findObstacle() {
        ServerLevel level = (ServerLevel) this.soldier.level();
        BlockPos me = this.soldier.blockPosition();
        // 1) doors on the upcoming path nodes
        Path path = this.soldier.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            int end = Math.min(path.getNextNodeIndex() + 3, path.getNodeCount());
            for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < end; i++) {
                Node n = path.getNode(i);
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos p = new BlockPos(n.x, n.y + dy, n.z);
                    if (p.distSqr(me) > 3.0 * 3.0) continue;
                    if (isObstacle(level.getBlockState(p))) return p;
                }
            }
        }
        // 2) blocked & standing next to one: scan the 3x3x3 around the head/feet
        if (this.soldier.horizontalCollision || this.soldier.isStuck()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos p = me.offset(dx, dy, dz);
                        if (isObstacle(level.getBlockState(p))) return p;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean canUse() {
        if (!CommonConfig.SOLDIERS_BREACH_DOORS.get()) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (!(this.soldier.level() instanceof ServerLevel)) return false;
        if (this.soldier.tickCount % 10 != 0) return false;
        if (this.soldier.getNavigation().isDone() && !this.soldier.horizontalCollision && !this.soldier.isStuck()) return false;
        this.doorPos = findObstacle();
        return this.doorPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.doorPos == null || this.phase == Phase.NONE) return false;
        if (this.soldier.getState() != SwatEntity.STATE_ALIVE) return false;
        if (this.totalTicks > 900) return false;
        if (this.phase == Phase.PASS) return true;
        BlockState state = this.soldier.level().getBlockState(this.doorPos);
        return isObstacle(state);
    }

    @Override
    public void start() {
        this.progress = 0;
        this.waitTicks = 0;
        this.totalTicks = 0;
        this.lastStage = -1;
        BlockState state = this.soldier.level().getBlockState(this.doorPos);
        if (isTrapdoor(state) && state.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS)) {
            // wooden trapdoor: just open it
            toggleTrapdoor(state);
            this.phase = Phase.NONE;
            return;
        }
        this.triedSwitches.clear();
        this.switchPos = findSwitch();
        this.phase = this.switchPos != null ? Phase.GO_TO_SWITCH : Phase.BREAK;
        sk.totalnavojna.TotalnaVojna.LOGGER.info("[TV] {} breaching {} at {} - switch: {}", this.soldier.getPatientName(), state.getBlock().getName().getString(), this.doorPos, this.switchPos);
    }

    @Override
    public void stop() {
        if (this.doorPos != null && this.soldier.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(this.soldier.getId(), this.doorPos, -1);
        }
        this.soldier.restoreGun();
        this.doorPos = null;
        this.switchPos = null;
        this.phase = Phase.NONE;
    }

    private void toggleTrapdoor(BlockState state) {
        ServerLevel level = (ServerLevel) this.soldier.level();
        level.setBlock(this.doorPos, state.cycle(TrapDoorBlock.OPEN), 10);
        level.playSound(null, this.doorPos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
        this.soldier.swing(InteractionHand.MAIN_HAND);
    }

    // Lever/button within 3 blocks of the door that is on the soldier's side of the doorway.
    @Nullable
    private BlockPos findSwitch() {
        ServerLevel level = (ServerLevel) this.soldier.level();
        BlockState doorState = level.getBlockState(this.doorPos);
        BlockPos base = this.doorPos;
        if (doorState.getBlock() instanceof DoorBlock && doorState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            base = this.doorPos.below();
        }
        double sx = this.soldier.getX() - (base.getX() + 0.5);
        double sz = this.soldier.getZ() - (base.getZ() + 0.5);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, -1, -4), base.offset(4, 3, 4))) {
            if (this.triedSwitches.contains(p)) continue;
            BlockState s = level.getBlockState(p);
            boolean isSwitch = s.getBlock() instanceof LeverBlock || s.getBlock() instanceof ButtonBlock;
            if (!isSwitch) continue;
            double px = p.getX() + 0.5 - (base.getX() + 0.5);
            double pz = p.getZ() + 0.5 - (base.getZ() + 0.5);
            // same side of the doorway as the soldier (dot product), or right at the door column
            boolean sameSide = (px * sx + pz * sz) > -0.6;
            if (!sameSide) continue;
            if (s.getBlock() instanceof LeverBlock && s.getValue(LeverBlock.POWERED)) continue;
            double d = p.distSqr(this.soldier.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = p.immutable();
            }
        }
        return best;
    }

    @Override
    public void tick() {
        this.totalTicks++;
        ServerLevel level = (ServerLevel) this.soldier.level();
        switch (this.phase) {
            case GO_TO_SWITCH -> {
                double d = this.soldier.distanceToSqr(this.switchPos.getX() + 0.5, this.switchPos.getY() + 0.5, this.switchPos.getZ() + 0.5);
                if (d > 3.0 * 3.0) {
                    if (this.soldier.tickCount % 10 == 0) {
                        this.soldier.getNavigation().moveTo(this.switchPos.getX() + 0.5, this.switchPos.getY(), this.switchPos.getZ() + 0.5, 1.0);
                    }
                    this.waitTicks++;
                    if (this.waitTicks > 200) {
                        // unreachable switch: try another one before resorting to force
                        this.triedSwitches.add(this.switchPos);
                        this.switchPos = findSwitch();
                        this.waitTicks = 0;
                        if (this.switchPos == null) this.phase = Phase.BREAK;
                    }
                    return;
                }
                this.soldier.getNavigation().stop();
                this.soldier.getLookControl().setLookAt(this.switchPos.getX() + 0.5, this.switchPos.getY() + 0.5, this.switchPos.getZ() + 0.5);
                BlockState s = level.getBlockState(this.switchPos);
                this.soldier.swing(InteractionHand.MAIN_HAND);
                if (s.getBlock() instanceof LeverBlock lever) {
                    lever.pull(s, level, this.switchPos);
                    level.playSound(null, this.switchPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 0.6f);
                } else if (s.getBlock() instanceof ButtonBlock button) {
                    if (!s.getValue(ButtonBlock.POWERED)) button.press(s, level, this.switchPos);
                }
                this.phase = Phase.WAIT_SWITCH;
                this.waitTicks = 0;
            }
            case WAIT_SWITCH -> {
                this.waitTicks++;
                BlockState door = level.getBlockState(this.doorPos);
                if (!isObstacle(door)) {
                    // opened - hurry through before a button releases
                    this.phase = Phase.PASS;
                    this.waitTicks = 0;
                    double dx = this.doorPos.getX() + 0.5 - this.soldier.getX();
                    double dz = this.doorPos.getZ() + 0.5 - this.soldier.getZ();
                    double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                    BlockPos beyond = BlockPos.containing(this.doorPos.getX() + 0.5 + dx / len * 1.5, this.doorPos.getY(), this.doorPos.getZ() + 0.5 + dz / len * 1.5);
                    this.soldier.getNavigation().moveTo(beyond.getX() + 0.5, beyond.getY(), beyond.getZ() + 0.5, 1.2);
                    return;
                }
                if (this.waitTicks > 25) {
                    // the switch did not open this door: try another switch, then force
                    this.triedSwitches.add(this.switchPos);
                    this.switchPos = findSwitch();
                    if (this.switchPos != null) {
                        this.phase = Phase.GO_TO_SWITCH;
                        this.waitTicks = 0;
                    } else {
                        this.phase = Phase.BREAK;
                        this.progress = 0;
                    }
                }
            }
            case PASS -> {
                this.waitTicks++;
                if (this.waitTicks > 60 || this.soldier.getNavigation().isDone()) this.phase = Phase.NONE;
            }
            case BREAK -> {
                double d = this.doorPos.distSqr(this.soldier.blockPosition());
                if (d > 2.5 * 2.5) {
                    if (this.soldier.tickCount % 10 == 0) {
                        this.soldier.getNavigation().moveTo(this.doorPos.getX() + 0.5, this.doorPos.getY(), this.doorPos.getZ() + 0.5, 1.0);
                    }
                    return;
                }
                this.soldier.getNavigation().stop();
                this.soldier.getLookControl().setLookAt(this.doorPos.getX() + 0.5, this.doorPos.getY() + 0.5, this.doorPos.getZ() + 0.5);
                int required = CommonConfig.IRON_DOOR_BREAK_TICKS.get();
                if (this.soldier.getRole() == Role.PATHFINDER && this.soldier.holdTool(level.getBlockState(this.doorPos))) {
                    required = Math.max(20, required * 2 / 3);
                }
                this.progress++;
                if (this.progress % 12 == 0) {
                    this.soldier.swing(InteractionHand.MAIN_HAND);
                    level.playSound(null, this.doorPos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 0.7f, 1.0f);
                }
                int stage = Math.min(9, this.progress * 10 / required);
                if (stage != this.lastStage) {
                    this.lastStage = stage;
                    level.destroyBlockProgress(this.soldier.getId(), this.doorPos, stage);
                }
                if (this.progress >= required) {
                    level.destroyBlockProgress(this.soldier.getId(), this.doorPos, -1);
                    level.destroyBlock(this.doorPos, false);
                    level.playSound(null, this.doorPos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0f, 0.7f);
                    this.phase = Phase.NONE;
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}

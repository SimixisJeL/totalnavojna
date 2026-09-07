package sk.totalnavojna.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import sk.totalnavojna.Team;
import sk.totalnavojna.war.WarGameData;

public class SpawnerBlock extends Block {
    private final Team team;

    public SpawnerBlock(Team team, Properties props) {
        super(props);
        this.team = team;
    }

    public Team getTeam() {
        return this.team;
    }

    // Right-click your own base to open the recruitment menu.
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                 @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        sk.totalnavojna.Team playerTeam = sk.totalnavojna.TeamsSavedData.get(serverPlayer.serverLevel()).getTeam(player.getUUID());
        if (playerTeam == null) {
            player.displayClientMessage(Component.literal("§cNajprv sa pridaj do tímu: /totalnavojna join oliva|piesok"), true);
            return InteractionResult.CONSUME;
        }
        if (playerTeam != this.team) {
            player.displayClientMessage(Component.literal("§cToto je základňa tímu " + this.team.getDisplayName().toUpperCase() + "."), true);
            return InteractionResult.CONSUME;
        }
        int[] available = new int[sk.totalnavojna.Role.VALUES.length];
        for (int i = 0; i < available.length; i++) {
            available[i] = sk.totalnavojna.api.RecruitmentGate.available(
                    serverPlayer.serverLevel(), this.team, sk.totalnavojna.Role.VALUES[i]);
        }
        sk.totalnavojna.network.ModNetworking.sendToPlayer(
                new sk.totalnavojna.network.PacketOpenRecruit(pos, this.team, available), serverPlayer);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarGameData.get(serverLevel).addSpawner(serverLevel, this.team, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarGameData.get(serverLevel).removeSpawner(serverLevel, this.team, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

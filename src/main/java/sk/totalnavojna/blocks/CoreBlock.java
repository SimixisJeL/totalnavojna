package sk.totalnavojna.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import sk.totalnavojna.Team;
import sk.totalnavojna.war.WarGameData;

public class CoreBlock extends Block {
    private final Team team;

    public CoreBlock(Team team, Properties props) {
        super(props);
        this.team = team;
    }

    public Team getTeam() {
        return this.team;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarGameData.get(serverLevel).addCore(serverLevel, this.team, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarGameData.get(serverLevel).removeCore(serverLevel, this.team, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

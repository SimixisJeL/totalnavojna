package sk.totalnavojna.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;
import sk.totalnavojna.war.WarGameData;

// A capture point ("záchytný bod"). Neutral when placed; whoever stands on it long enough owns it,
// and owning more of them than the enemy bleeds the enemy's tickets. The owner lives in the block state
// purely so the world shows it - the authoritative copy is in WarGameData.
public class CaptureBlock extends Block {

    public static final IntegerProperty OWNER = IntegerProperty.create("owner", 0, 2);

    public CaptureBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(OWNER, 0));
    }

    public static int ownerValue(@Nullable Team team) {
        if (team == null) return 0;
        return team == Team.OLIVA ? 1 : 2;
    }

    @Nullable
    public static Team ownerTeam(int value) {
        return switch (value) {
            case 1 -> Team.OLIVA;
            case 2 -> Team.PIESOK;
            default -> null;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OWNER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarGameData.get(serverLevel).addCapture(serverLevel, pos, ownerTeam(state.getValue(OWNER)));
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarGameData.get(serverLevel).removeCapture(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

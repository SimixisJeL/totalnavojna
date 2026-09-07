package sk.totalnavojna.blocks;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.items.ModItems;
import sk.totalnavojna.war.WarGameData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Supply station: soldiers restock here out of combat (ResupplyGoal); team players right-click it with a gun for ammo + medkits.
public class SupplyBlock extends Block {
    public static final int PLAYER_AMMO_PER_USE = 60;
    public static final int PLAYER_MEDKIT_TARGET = 3;
    private static final Map<UUID, Long> PLAYER_COOLDOWN = new HashMap<>();

    private final Team team;

    public SupplyBlock(Team team, Properties props) {
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
            WarGameData.get(serverLevel).addSupply(serverLevel, this.team, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarGameData.get(serverLevel).removeSupply(serverLevel, this.team, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        Team playerTeam = TeamsSavedData.get(sp.serverLevel()).getTeam(sp.getUUID());
        if (playerTeam != this.team) {
            sp.displayClientMessage(Component.literal("§cToto nie je zásobovacia stanica tvojho tímu."), true);
            return InteractionResult.CONSUME;
        }
        long now = level.getGameTime();
        Long last = PLAYER_COOLDOWN.get(sp.getUUID());
        if (last != null && now - last < 60) {
            sp.displayClientMessage(Component.literal("§7Stanica sa dopĺňa..."), true);
            return InteractionResult.CONSUME;
        }

        boolean gave = false;
        ItemStack held = sp.getItemInHand(hand);
        IGun iGun = IGun.getIGunOrNull(held);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(held);
            Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
            if (index.isPresent()) {
                ResourceLocation ammoId = index.get().getGunData().getAmmoId();
                int count = gunId.getPath().equals("m320") ? 3 : PLAYER_AMMO_PER_USE;
                ItemStack ammo = AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
                if (!sp.getInventory().add(ammo)) sp.drop(ammo, false);
                gave = true;
            }
        }
        int medkits = sp.getInventory().countItem(ModItems.MEDKIT.get());
        if (medkits < PLAYER_MEDKIT_TARGET) {
            ItemStack kit = new ItemStack(ModItems.MEDKIT.get(), PLAYER_MEDKIT_TARGET - medkits);
            if (!sp.getInventory().add(kit)) sp.drop(kit, false);
            gave = true;
        }
        if (gave) {
            PLAYER_COOLDOWN.put(sp.getUUID(), now);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.BLOCKS, 0.8f, 1.0f);
            sp.displayClientMessage(Component.literal("§a⬆ Zásoby doplnené" + (iGun == null ? " §7(drž zbraň v ruke pre náboje)" : "")), true);
        } else {
            sp.displayClientMessage(Component.literal("§7Drž TACZ zbraň v ruke — stanica ti dá náboje a doplní medkity."), true);
        }
        return InteractionResult.CONSUME;
    }
}

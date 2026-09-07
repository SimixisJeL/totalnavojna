package sk.totalnavojna.client;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.blocks.CoreBlock;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketCoreAttack;

// Holding LMB on an enemy NEXUS or an enemy forward base: the block is unbreakable, so the client just
// streams "still attacking" heartbeats and the server counts the lives down.
@Mod.EventBusSubscriber(modid = TotalnaVojna.MOD_ID, value = Dist.CLIENT)
public class ClientCoreAttackHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START && event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        Team blockTeam;
        if (state.getBlock() instanceof CoreBlock core) {
            blockTeam = core.getTeam();
        } else if (state.getBlock() instanceof sk.totalnavojna.blocks.SpawnerBlock fob) {
            blockTeam = fob.getTeam();
        } else {
            return;
        }
        Team mine = ClientTeamCache.get();
        if (mine == null || blockTeam == mine) return;
        ModNetworking.sendToServer(new PacketCoreAttack(event.getPos()));
    }
}

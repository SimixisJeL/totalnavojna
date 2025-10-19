package su.uTa4u.specialforces.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import su.uTa4u.specialforces.entities.SwatEntity;

@Mixin(SummonCommand.class)
public abstract class SummonCommandMixin {

    @Inject(
            method = "createEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z",
                    shift = At.Shift.BEFORE
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void taczsf_forceFinalizeSpawnForSWAT(CommandSourceStack sourceStack, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomizeProperties, CallbackInfoReturnable<Entity> cir, BlockPos blockPos, CompoundTag compoundTag, ServerLevel serverLevel, Entity entity) {
        if (entity instanceof SwatEntity swatEntity) {
            ForgeEventFactory.onFinalizeSpawn(swatEntity, serverLevel, serverLevel.getCurrentDifficultyAt(swatEntity.blockPosition()), MobSpawnType.COMMAND, null, null);
        }
    }
}

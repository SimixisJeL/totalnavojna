package sk.totalnavojna.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;

// Smart nametags: soldier and player names only show when you look straight at them (within 64 blocks, line of sight).
@Mod.EventBusSubscriber(modid = TotalnaVojna.MOD_ID, value = Dist.CLIENT)
public class SmartNametagHandler {
    private static final double MAX_DIST = 64.0;

    private static boolean lookingAt(Minecraft mc, Entity entity) {
        if (mc.cameraEntity == null) return false;
        Vec3 eye = mc.cameraEntity.getEyePosition();
        Vec3 look = mc.cameraEntity.getViewVector(1.0f);
        Vec3 center = entity.getBoundingBox().getCenter();
        Vec3 to = center.subtract(eye);
        double dist = to.length();
        if (dist > MAX_DIST || dist < 0.01) return false;
        // ray must pass within ~0.9 block of the entity centre (a bit more when close)
        double perp = to.cross(look).length(); // |to| * sin(angle)
        double tolerance = 0.9 + entity.getBbWidth() * 0.5;
        if (to.dot(look) < 0 || perp > tolerance) return false;
        return mc.cameraEntity instanceof net.minecraft.world.entity.LivingEntity living && living.hasLineOfSight(entity);
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!CommonConfig.SMART_NAMETAGS.get()) return;
        Entity entity = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player) return;
        if (!(entity instanceof SwatEntity) && !(entity instanceof Player)) return;
        event.setResult(lookingAt(mc, entity) ? Event.Result.ALLOW : Event.Result.DENY);
    }
}

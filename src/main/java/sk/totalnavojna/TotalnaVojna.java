package sk.totalnavojna;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import sk.totalnavojna.client.ModModelLayers;
import sk.totalnavojna.client.models.SwatModel;
import sk.totalnavojna.client.renderers.SwatRenderer;
import sk.totalnavojna.client.screens.SwatCorpseScreen;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.ModEntities;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.blocks.ModBlocks;
import sk.totalnavojna.client.input.KeyBindings;
import sk.totalnavojna.items.ModItems;
import sk.totalnavojna.menus.ModMenuTypes;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketSyncTeam;

@Mod(TotalnaVojna.MOD_ID)
public class TotalnaVojna {
    public static final String MOD_ID = "totalnavojna";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TotalnaVojna(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModMenuTypes.MENU_TYPES.register(modBus);
        sk.totalnavojna.init.ModSounds.SOUNDS.register(modBus);

        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);

        ModNetworking.register();
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEventBusClientEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            EntityRenderers.register(ModEntities.SWAT_ENTITY.get(), SwatRenderer::new);
            MenuScreens.register(ModMenuTypes.SWAT_CORPSE.get(), SwatCorpseScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(ModModelLayers.SWAT, SwatModel::createBodyLayer);
            event.registerLayerDefinition(ModModelLayers.SWAT_INNER_ARMOR, () -> LayerDefinition.create(HumanoidArmorModel.createBodyLayer(LayerDefinitions.INNER_ARMOR_DEFORMATION), 64, 32));
            event.registerLayerDefinition(ModModelLayers.SWAT_OUTER_ARMOR, () -> LayerDefinition.create(HumanoidArmorModel.createBodyLayer(LayerDefinitions.OUTER_ARMOR_DEFORMATION), 64, 32));
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.COMMANDER_MENU_KEY);
            event.register(KeyBindings.TACTICAL_MAP_KEY);
        }

        @SubscribeEvent
        public static void onCreativeModeTabContent(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                for (Team team : Team.VALUES) {
                    for (Role role : Role.VALUES) {
                        event.accept(ModItems.getEgg(team, role).getDefaultInstance());
                    }
                }
            }
            if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                event.accept(ModItems.MEDKIT.get());
                event.accept(ModItems.BANDAGE.get());
                for (Team team : Team.VALUES) {
                    event.accept(ModBlocks.CORES.get(team).get());
                    event.accept(ModBlocks.SPAWNERS.get(team).get());
                    event.accept(ModBlocks.SUPPLIES.get(team).get());
                }
                event.accept(ModBlocks.CAPTURE_POINT.get());
                for (Team team : Team.VALUES) {
                    for (Role role : Role.VALUES) {
                        event.accept(ModItems.getTunic(team, role));
                    }
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void onEntityAttributeCreationEvent(EntityAttributeCreationEvent event) {
            event.put(ModEntities.SWAT_ENTITY.get(), SwatEntity.createDefaultAttributes().build());
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            TotalnaVojnaCommands.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                Team team = TeamsSavedData.get(serverPlayer.serverLevel()).getTeam(serverPlayer.getUUID());
                ModNetworking.sendToPlayer(PacketSyncTeam.of(team), serverPlayer);
            }
        }
    }
}

package sk.totalnavojna.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import sk.totalnavojna.TotalnaVojna;

public class ClientConfigOpener {

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        var container = ModList.get().getModContainerById(TotalnaVojna.MOD_ID);
        if (container.isPresent()) {
            var factory = ConfigScreenHandler.getScreenFactoryFor(container.get().getModInfo());
            if (factory.isPresent()) {
                mc.setScreen(factory.get().apply(mc, mc.screen));
                return;
            }
        }
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§eConfig GUI: hlavné menu → Mods → Totálna Vojna → tlačidlo Config (od modu Configured)."), false);
        }
    }
}

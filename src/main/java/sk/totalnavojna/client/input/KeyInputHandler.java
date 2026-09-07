package sk.totalnavojna.client.input;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.client.gui.CommanderMenuScreen;
import sk.totalnavojna.client.gui.TacticalMapScreen;

@Mod.EventBusSubscriber(modid = TotalnaVojna.MOD_ID, value = Dist.CLIENT)
public class KeyInputHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeyBindings.COMMANDER_MENU_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new CommanderMenuScreen());
            }
        }
        if (KeyBindings.TACTICAL_MAP_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new TacticalMapScreen());
            }
        }
    }
}

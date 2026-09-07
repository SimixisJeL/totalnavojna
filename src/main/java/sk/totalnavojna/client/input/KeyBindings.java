package sk.totalnavojna.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static final String KEY_CATEGORY = "key.category.totalnavojna";
    public static final String KEY_COMMANDER_MENU = "key.totalnavojna.commander_menu";

    public static final KeyMapping COMMANDER_MENU_KEY = new KeyMapping(
            KEY_COMMANDER_MENU,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY
    );

    public static final KeyMapping TACTICAL_MAP_KEY = new KeyMapping(
            "key.totalnavojna.tactical_map",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KEY_CATEGORY
    );
}

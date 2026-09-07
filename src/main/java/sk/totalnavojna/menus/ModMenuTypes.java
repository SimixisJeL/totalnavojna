package sk.totalnavojna.menus;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import sk.totalnavojna.TotalnaVojna;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES;

    public static final RegistryObject<MenuType<SwatCorpseMenu>> SWAT_CORPSE;

    static {
        MENU_TYPES = DeferredRegister.create(Registries.MENU, TotalnaVojna.MOD_ID);

        SWAT_CORPSE = MENU_TYPES.register("swat_corpse", () -> new MenuType<>(SwatCorpseMenu::new, FeatureFlags.DEFAULT_FLAGS));

    }
}

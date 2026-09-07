package sk.totalnavojna.items;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS;

    public static final RegistryObject<Item> MEDKIT;
    public static final RegistryObject<Item> BANDAGE;
    public static final Map<Team, Map<Role, RegistryObject<Item>>> SPAWN_EGGS;
    public static final Map<Team, Map<Role, RegistryObject<Item>>> TUNICS;

    private static int roleHighlight(Role role) {
        return switch (role) {
            case ATTACKER -> 0xb22222;
            case DEFENDER -> 0x4169e1;
            case MEDIC -> 0xffffff;
            case SNIPER -> 0x1a1a1a;
            case PATHFINDER -> 0xff8c00;
            case DRONE_OPERATOR -> 0x1f9aa8;
        };
    }

    private static int teamBackground(Team team) {
        return team == Team.OLIVA ? 0x3a4423 : 0xc2b280;
    }

    static {
        ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TotalnaVojna.MOD_ID);

        MEDKIT = ITEMS.register("medkit", () -> new MedkitItem(new Item.Properties().stacksTo(16)));
        BANDAGE = ITEMS.register("bandage", () -> new BandageItem(new Item.Properties().stacksTo(16)));

        SPAWN_EGGS = new EnumMap<>(Team.class);
        TUNICS = new EnumMap<>(Team.class);
        for (Team team : Team.VALUES) {
            Map<Role, RegistryObject<Item>> byRole = new EnumMap<>(Role.class);
            Map<Role, RegistryObject<Item>> tunicsByRole = new EnumMap<>(Role.class);
            for (Role role : Role.VALUES) {
                String id = team.getName() + "_" + role.getName() + "_spawn_egg";
                byRole.put(role, ITEMS.register(id,
                        () -> new TeamSpawnEggItem(team.getName(), role.getName(), teamBackground(team), roleHighlight(role), new Item.Properties())));
                String tunicId = team.getName() + "_" + role.getName() + "_tunic";
                tunicsByRole.put(role, ITEMS.register(tunicId, () -> new TunicItem(team, role, new Item.Properties().stacksTo(1))));
            }
            SPAWN_EGGS.put(team, byRole);
            TUNICS.put(team, tunicsByRole);
        }
    }

    public static Item getEgg(Team team, Role role) {
        return SPAWN_EGGS.get(team).get(role).get();
    }

    public static Item getTunic(Team team, Role role) {
        return TUNICS.get(team).get(role).get();
    }
}

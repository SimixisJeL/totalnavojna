package sk.totalnavojna.blocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.items.ModItems;

import java.util.EnumMap;
import java.util.Map;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS;

    public static final Map<Team, RegistryObject<Block>> CORES;
    public static final Map<Team, RegistryObject<Block>> SPAWNERS;
    public static final Map<Team, RegistryObject<Block>> SUPPLIES;
    // one shared, team-neutral block: whoever holds it owns it, and the owner lives in its block state
    public static final RegistryObject<Block> CAPTURE_POINT;

    // War blocks are unbreakable in survival (NEXUS is taken down through its lives, not by mining); creative can still remove them.
    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(-1.0f, 3600000.0f)
                .sound(SoundType.NETHERITE_BLOCK);
    }

    private static BlockBehaviour.Properties supplyProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(-1.0f, 3600000.0f)
                .sound(SoundType.WOOD);
    }

    static {
        BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TotalnaVojna.MOD_ID);

        CAPTURE_POINT = BLOCKS.register("capture_point", () -> new CaptureBlock(props()));
        ModItems.ITEMS.register("capture_point", () -> new BlockItem(CAPTURE_POINT.get(), new Item.Properties()));

        CORES = new EnumMap<>(Team.class);
        SPAWNERS = new EnumMap<>(Team.class);
        SUPPLIES = new EnumMap<>(Team.class);

        for (Team team : Team.VALUES) {
            RegistryObject<Block> core = BLOCKS.register(team.getName() + "_core", () -> new CoreBlock(team, props()));
            RegistryObject<Block> spawner = BLOCKS.register(team.getName() + "_spawner", () -> new SpawnerBlock(team, props()));
            RegistryObject<Block> supply = BLOCKS.register(team.getName() + "_supply", () -> new SupplyBlock(team, supplyProps()));
            CORES.put(team, core);
            SPAWNERS.put(team, spawner);
            SUPPLIES.put(team, supply);

            ModItems.ITEMS.register(team.getName() + "_core", () -> new BlockItem(core.get(), new Item.Properties()));
            ModItems.ITEMS.register(team.getName() + "_spawner", () -> new BlockItem(spawner.get(), new Item.Properties()));
            ModItems.ITEMS.register(team.getName() + "_supply", () -> new BlockItem(supply.get(), new Item.Properties()));
        }
    }
}

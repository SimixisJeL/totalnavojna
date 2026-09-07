package sk.totalnavojna.items;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.jetbrains.annotations.NotNull;
import sk.totalnavojna.entities.ModEntities;
import sk.totalnavojna.entities.SwatEntity;

public class TeamSpawnEggItem extends ForgeSpawnEggItem {
    private final String teamName;
    private final String roleName;

    public TeamSpawnEggItem(String teamName, String roleName, int backgroundColor, int highlightColor, Properties props) {
        super(ModEntities.SWAT_ENTITY, backgroundColor, highlightColor, props);
        this.teamName = teamName;
        this.roleName = roleName;
    }

    @NotNull
    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        CompoundTag tag = stack.getOrCreateTagElement(EntityType.ENTITY_TAG);
        tag.putString(SwatEntity.NBT_KEY_TEAM, this.teamName);
        tag.putString(SwatEntity.NBT_KEY_ROLE, this.roleName);
        return stack;
    }
}

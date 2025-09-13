package su.uTa4u.specialforces;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import su.uTa4u.specialforces.entities.SwatEntity;
import su.uTa4u.specialforces.items.ModItems;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public enum Specialty {
    COMMANDER("commander", 0.3f),
    ASSAULTER("assaulter", 0.3f),
    GRENADIER("grenadier", 0.3f),
    BULLDOZER("bulldozer", 0.3f),
    ENGINEER("engineer", 0.3f),
    SNIPER("sniper", 1.0f),
    MEDIC("medic", 0.3f),
    SCOUT("scout", 0.3f),
    SPY("spy", 0.3f);

    private static final Random RNG = new Random();
    public static final Specialty[] VALUES = values();
    public static final int SIZE = VALUES.length;

    private static final Map<String, Specialty> SPECIALTY_BY_NAME = new HashMap<>();

    private final String name;
    private final ResourceLocation skin;
    private final ResourceLocation lootTable;
    private final Component typeName;
    // TODO: remove this, make headAitChance be dependant on difficulty
    //  Entities should not aim at head/body if view is not clear
    private final float headAimChance;

    Specialty(String name, float headAimChance) {
        this.name = name;
        this.skin = Util.getResource("textures/entity/" + name + ".png");
        this.headAimChance = headAimChance;
        this.lootTable = Util.getResource("spawn_inv/" + name);
        this.typeName = Component.translatable("entity." + SpecialForces.MOD_ID + "." + name);
    }

    public static Specialty getRandom() {
        return VALUES[RNG.nextInt(SIZE)];
    }

    public String getName() {
        return this.name;
    }

    public ResourceLocation getSkin() {
        return this.skin;
    }

    public float getHeadAimChance() {
        return this.headAimChance;
    }

    public ResourceLocation getLootTable() {
        return this.lootTable;
    }

    public Component getTypeName() {
        return this.typeName;
    }

    public ItemStack getSpawnEgg() {
        ItemStack egg = new ItemStack(ModItems.SWAT_SPAWN_EGG.get());
        CompoundTag displayTag = egg.getOrCreateTagElement(ItemStack.TAG_DISPLAY);
        if (displayTag.getTagType(ItemStack.TAG_LORE) == Tag.TAG_LIST) {
            ListTag loreTag = displayTag.getList(ItemStack.TAG_LORE, Tag.TAG_STRING);
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(this.typeName)));
        } else {
            ListTag loreTag = new ListTag();
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(this.typeName)));
            displayTag.put(ItemStack.TAG_LORE, loreTag);
        }
        CompoundTag entityTag = egg.getOrCreateTagElement("EntityTag");
        entityTag.putString(SwatEntity.NBT_KEY_SPECIALTY, this.name);
        return egg;
    }

    @Nullable
    public static Specialty byName(String name) {
        return SPECIALTY_BY_NAME.get(name);
    }

    static {
        for (Specialty spec : VALUES) {
            SPECIALTY_BY_NAME.put(spec.name, spec);
        }
    }
}

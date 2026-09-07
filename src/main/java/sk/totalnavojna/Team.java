package sk.totalnavojna;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public enum Team {
    OLIVA("oliva"),
    PIESOK("piesok");

    public static final Team[] VALUES = values();

    private final String name;
    private final ResourceLocation skin;
    private final ResourceLocation lootTable;

    Team(String name) {
        this.name = name;
        this.skin = Util.getResource("textures/entity/" + name + ".png");
        this.lootTable = Util.getResource("spawn_inv/" + name);
    }

    // The internal id. This is what lands in NBT and packets, so it must never change.
    public String getName() {
        return this.name;
    }

    // What a human sees. Defaults to Olive / Sand and can be renamed in the config.
    public String getDisplayName() {
        return TeamNames.get(this);
    }

    public ResourceLocation getSkin() {
        return this.skin;
    }

    public ResourceLocation getLootTable() {
        return this.lootTable;
    }

    public Component getTypeName() {
        return Component.translatable("entity." + TotalnaVojna.MOD_ID + ".soldier", TeamNames.get(this));
    }

    public float getHeadAimChance() {
        return 0.3f;
    }

    public Team enemy() {
        return this == OLIVA ? PIESOK : OLIVA;
    }

    @Nullable
    public static Team byName(String name) {
        return switch (name) {
            case "oliva" -> OLIVA;
            case "piesok" -> PIESOK;
            default -> null;
        };
    }
}

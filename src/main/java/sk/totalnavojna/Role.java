package sk.totalnavojna;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// APPEND-ONLY enum: ordinals are stored in entity data / packets.
public enum Role {
    ATTACKER("attacker", 1.0f),
    DEFENDER("defender", 1.0f),
    MEDIC("medic", 1.0f),
    SNIPER("sniper", 0.25f),
    PATHFINDER("pathfinder", 1.0f),
    DRONE_OPERATOR("drone_operator", 1.0f);

    public static final Role[] VALUES = values();

    private final String name;
    private final float aimErrorMult;
    private final Component typeName;

    Role(String name, float aimErrorMult) {
        this.name = name;
        this.aimErrorMult = aimErrorMult;
        this.typeName = Component.translatable("role." + TotalnaVojna.MOD_ID + "." + name);
    }

    public String getName() {
        return this.name;
    }

    public float getAimErrorMult() {
        return this.aimErrorMult;
    }

    public Component getTypeName() {
        return this.typeName;
    }

    // Support roles do not hunt enemies with their personal weapon on sight - they only shoot back.
    public boolean isSupport() {
        return this == PATHFINDER || this == DRONE_OPERATOR;
    }

    public int getDefaultMedkits() {
        return this == MEDIC ? 10 : 3;
    }

    public int getDefaultPotions() {
        return this == MEDIC ? 8 : 5;
    }

    // Bandages replaced healing potions in the kit; potions still work for anyone carrying old ones.
    public int getDefaultBandages() {
        return this == MEDIC ? 8 : 5;
    }

    @Nullable
    public static Role byName(String name) {
        for (Role r : VALUES) {
            if (r.name.equals(name)) return r;
        }
        return null;
    }
}

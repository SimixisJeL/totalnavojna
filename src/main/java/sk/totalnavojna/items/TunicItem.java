package sk.totalnavojna.items;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;

// Role tunic (chest armor): team colour on the sleeves/waist band, role colour + emblem on the chest so units are readable in combat.
public class TunicItem extends ArmorItem {
    private final Team team;
    private final Role role;

    public TunicItem(Team team, Role role, Properties props) {
        super(new TunicMaterial(team, role), Type.CHESTPLATE, props);
        this.team = team;
        this.role = role;
    }

    public Team getTeam() {
        return this.team;
    }

    public Role getRole() {
        return this.role;
    }

    public static class TunicMaterial implements ArmorMaterial {
        private final String name;

        public TunicMaterial(Team team, Role role) {
            // HumanoidArmorLayer resolves "<domain>:textures/models/armor/<name>_layer_1.png"
            this.name = TotalnaVojna.MOD_ID + ":" + team.getName() + "_" + role.getName() + "_tunic";
        }

        @Override
        public int getDurabilityForType(@NotNull Type type) {
            return 240;
        }

        @Override
        public int getDefenseForType(@NotNull Type type) {
            return 3;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @NotNull
        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @NotNull
        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.LEATHER);
        }

        @NotNull
        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public float getToughness() {
            return 0.0f;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0f;
        }
    }
}

package sk.totalnavojna.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.TotalnaVojna;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The dubbed Slovak lines. Every id here has a matching OGG in assets/totalnavojna/sounds/voice/
// and an entry in sounds.json - all three are generated together by dev/voice_gen.py.
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TotalnaVojna.MOD_ID);

    public static final List<String> VOICE_IDS = List.of(
            "radio_contact", "radio_under_fire", "radio_man_down", "radio_no_ammo", "radio_nexus_attack",
            "radio_point_taken", "radio_point_lost", "radio_drone_up", "radio_drone_hit", "radio_enemy_vehicle",
            "radio_flanking", "radio_falling_back", "radio_victory", "radio_defeat",
            "spawn_1", "spawn_2", "spawn_3", "spawn_4", "spawn_5",
            "death_1", "death_2", "death_3", "death_4", "death_5", "death_6",
            "kill_1", "kill_2", "kill_3", "revive_1", "revive_2", "revive_3"
    );

    private static final Map<String, RegistryObject<SoundEvent>> VOICE = new HashMap<>();

    static {
        for (String id : VOICE_IDS) {
            ResourceLocation rl = new ResourceLocation(TotalnaVojna.MOD_ID, "voice." + id);
            VOICE.put(id, SOUNDS.register("voice." + id, () -> SoundEvent.createVariableRangeEvent(rl)));
        }
    }

    @Nullable
    public static SoundEvent voice(String id) {
        RegistryObject<SoundEvent> obj = VOICE.get(id);
        return obj == null || !obj.isPresent() ? null : obj.get();
    }

    private ModSounds() {
    }
}

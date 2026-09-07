package sk.totalnavojna.client;

import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClientDownedState {
    private static final Set<UUID> DOWNED = new HashSet<>();

    public static void set(Collection<UUID> downed) {
        DOWNED.clear();
        DOWNED.addAll(downed);
    }

    public static boolean contains(UUID uuid) {
        return DOWNED.contains(uuid);
    }

    public static Set<UUID> all() {
        return DOWNED;
    }

    // is the LOCAL player downed?
    public static boolean isDowned() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && DOWNED.contains(mc.player.getUUID());
    }
}

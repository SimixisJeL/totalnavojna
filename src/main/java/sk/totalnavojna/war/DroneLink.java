package sk.totalnavojna.war;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Everything about "whose drone is this and does it still work out there".
//
// Minecraft only ticks entities inside a player's simulation distance (12 chunks = 192 blocks by default), so a drone
// sent past that simply froze in the air - that was the real range limit, not any Superb Warfare setting. While a drone
// is under AI control we hold a small ring of ENTITY_TICKING chunks around it, so it keeps flying however far the
// operator sends it. The tickets are released the moment the drone lands, detonates or is shot down.
//
// (Being *rendered* is a separate, client-side limit: the client only receives entities inside its own view distance.
// Far away the drone still works, you just watch it on the map instead of through the window.)
public final class DroneLink {

    public static final String TEAM_TAG = "tv_drone_team";
    public static final String OWNER_TAG = "tv_drone_owner";
    private static final int RING = 1; // 3x3 chunks around the drone

    private static final Map<UUID, ChunkPos> HELD = new HashMap<>();

    private DroneLink() {
    }

    public static void tagOwner(Entity drone, Team team, UUID operator) {
        drone.getPersistentData().putString(TEAM_TAG, team.getName());
        drone.getPersistentData().putUUID(OWNER_TAG, operator);
    }

    @Nullable
    public static Team teamOf(Entity drone) {
        String s = drone.getPersistentData().getString(TEAM_TAG);
        return s.isEmpty() ? null : Team.byName(s);
    }

    public static boolean isAiDrone(Entity drone) {
        return drone.getPersistentData().hasUUID(OWNER_TAG);
    }

    // Keep the chunks under this drone ticking; call every tick while it flies.
    public static void hold(ServerLevel level, Entity drone) {
        UUID id = drone.getUUID();
        ChunkPos now = new ChunkPos(drone.blockPosition());
        ChunkPos held = HELD.get(id);
        if (now.equals(held)) return;
        if (held != null) setForced(level, id, held, false);
        setForced(level, id, now, true);
        HELD.put(id, now);
    }

    public static void release(ServerLevel level, UUID droneId) {
        ChunkPos held = HELD.remove(droneId);
        if (held != null) setForced(level, droneId, held, false);
    }

    public static void releaseAll(ServerLevel level) {
        for (Map.Entry<UUID, ChunkPos> e : new HashMap<>(HELD).entrySet()) {
            setForced(level, e.getKey(), e.getValue(), false);
        }
        HELD.clear();
    }

    private static void setForced(ServerLevel level, UUID owner, ChunkPos center, boolean add) {
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                try {
                    ForgeChunkManager.forceChunk(level, TotalnaVojna.MOD_ID, owner, center.x + dx, center.z + dz, add, true);
                } catch (Exception e) {
                    TotalnaVojna.LOGGER.warn("[TV] could not {} chunk ticket for a drone", add ? "add" : "remove", e);
                    return;
                }
            }
        }
    }
}

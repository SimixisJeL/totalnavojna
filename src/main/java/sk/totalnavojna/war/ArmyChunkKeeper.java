package sk.totalnavojna.war;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.common.world.ForgeChunkManager;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.SwatEntity;

import java.util.*;

// Keeps the army's own chunks loaded and ticking.
//
// Minecraft only keeps chunks near a player loaded. Everything else stops existing as far as the server is concerned:
// soldiers out there do not tick, they vanish from `level.getEntities(...)` - so they disappear from the tactical map,
// map orders find nobody to give the order to, and the units simply stand still until a player walks back.
// That is the whole "they ignore orders when I am far away" problem, and it is not something a config in another mod
// can fix. We hold an ENTITY_TICKING ticket on every chunk our soldiers occupy.
//
// The occupied-chunk set is saved with the world, so after a restart the chunks come back before anyone walks over.
public final class ArmyChunkKeeper {

    private static final int SCAN_INTERVAL = 40;
    private static final int FORGET_SCANS = 3;

    // dim -> chunk -> how many scans in a row we found nobody there
    private static final Map<String, Map<Long, Integer>> EMPTY_SCANS = new HashMap<>();
    private static final Map<String, Set<Long>> FORCED = new HashMap<>();
    private static long lastScan = 0;
    private static boolean warnedCap = false;

    private ArmyChunkKeeper() {
    }

    public static void tick(MinecraftServer server) {
        if (!CommonConfig.KEEP_ARMY_LOADED.get()) {
            if (!FORCED.isEmpty()) releaseAll(server);
            return;
        }
        long now = server.overworld().getGameTime();
        if (now - lastScan < SCAN_INTERVAL) return;
        lastScan = now;

        WarGameData data = WarGameData.get(server.overworld());
        int cap = CommonConfig.MAX_ARMY_CHUNKS.get();

        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            Set<Long> wanted = new HashSet<>(data.getArmyChunks(dim));

            // everything we can currently see (which, once the tickets are in place, is everything)
            Set<Long> occupied = new HashSet<>();
            for (SwatEntity soldier : level.getEntities(EntityTypeTest.forClass(SwatEntity.class), (e) -> e.getState() != SwatEntity.STATE_DEAD)) {
                occupied.add(new ChunkPos(soldier.blockPosition()).toLong());
            }
            wanted.addAll(occupied);

            // a chunk that has been empty for a few scans in a row is released
            Map<Long, Integer> empty = EMPTY_SCANS.computeIfAbsent(dim, d -> new HashMap<>());
            Set<Long> loadedNow = FORCED.getOrDefault(dim, Set.of());
            Iterator<Long> it = wanted.iterator();
            while (it.hasNext()) {
                long c = it.next();
                if (occupied.contains(c)) {
                    empty.remove(c);
                    continue;
                }
                // only trust "empty" for chunks we were actually holding (so they really were loaded and checked)
                if (!loadedNow.contains(c)) continue;
                int n = empty.merge(c, 1, Integer::sum);
                if (n >= FORGET_SCANS) {
                    empty.remove(c);
                    it.remove();
                }
            }

            if (wanted.size() > cap) {
                if (!warnedCap) {
                    warnedCap = true;
                    TotalnaVojna.LOGGER.warn("[TV] army occupies {} chunks, holding only {} (config maxArmyChunks) - distant units may freeze", wanted.size(), cap);
                }
                List<Long> trimmed = new ArrayList<>(wanted);
                wanted = new HashSet<>(trimmed.subList(0, cap));
            } else {
                warnedCap = false;
            }

            apply(level, dim, wanted);
            data.setArmyChunks(dim, wanted);
        }
    }

    private static void apply(ServerLevel level, String dim, Set<Long> wanted) {
        Set<Long> current = FORCED.computeIfAbsent(dim, d -> new HashSet<>());
        for (long c : new HashSet<>(current)) {
            if (!wanted.contains(c)) {
                force(level, c, false);
                current.remove(c);
            }
        }
        for (long c : wanted) {
            if (current.add(c)) force(level, c, true);
        }
    }

    private static void force(ServerLevel level, long packed, boolean add) {
        ChunkPos pos = new ChunkPos(packed);
        try {
            ForgeChunkManager.forceChunk(level, TotalnaVojna.MOD_ID, ARMY_OWNER, pos.x, pos.z, add, true);
        } catch (Exception e) {
            TotalnaVojna.LOGGER.warn("[TV] could not {} an army chunk ticket at {}", add ? "add" : "remove", pos, e);
        }
    }

    // one shared ticket owner for the whole army, so a restart drops them all cleanly
    private static final UUID ARMY_OWNER = UUID.fromString("70741ab0-0000-4000-8000-000000000001");

    public static void releaseAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            Set<Long> current = FORCED.remove(dim);
            if (current == null) continue;
            for (long c : current) force(level, c, false);
        }
        EMPTY_SCANS.clear();
    }

    public static int heldChunks() {
        int n = 0;
        for (Set<Long> s : FORCED.values()) n += s.size();
        return n;
    }
}

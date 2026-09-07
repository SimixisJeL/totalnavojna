package sk.totalnavojna.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// One place for "can something stand / drive / fly here" questions. Every goal that used to roll its own
// block checks (vehicle steering, drop-downs, cover search, drone altitude) asks here instead, so the whole
// army agrees on what the terrain means.
public final class TerrainScan {

    public enum Ground { LAND, WATER, LAVA, VOID }

    private TerrainScan() {
    }

    public static boolean solid(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.getCollisionShape(level, p).isEmpty();
    }

    public static int surfaceY(ServerLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    // Highest terrain along a straight line - used for drone cruise altitude so it clears hills instead of flying into them.
    public static int maxSurfaceAlong(ServerLevel level, double x1, double z1, double x2, double z2, int samples) {
        int best = surfaceY(level, x1, z1);
        for (int i = 1; i <= samples; i++) {
            double f = (double) i / samples;
            best = Math.max(best, surfaceY(level, x1 + (x2 - x1) * f, z1 + (z2 - z1) * f));
        }
        return best;
    }

    // What is under this column, around the given height? Answers the "is that the sea?" question a driver needs.
    public static Ground groundAt(ServerLevel level, int x, int z, int aroundY) {
        for (int y = aroundY + 2; y > aroundY - 8; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getFluidState(p).isEmpty()) {
                return level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA) ? Ground.LAVA : Ground.WATER;
            }
            if (solid(level, p)) return Ground.LAND;
        }
        return Ground.VOID;
    }

    public static int groundHeight(ServerLevel level, int x, int z, int aroundY) {
        for (int y = aroundY + 2; y > aroundY - 12; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (solid(level, p) || !level.getFluidState(p).isEmpty()) return y + 1;
        }
        return aroundY - 12;
    }

    // Can an infantry-sized unit stand here? (2 free blocks on solid, dry ground)
    public static boolean standable(ServerLevel level, BlockPos feet) {
        return !solid(level, feet)
                && !solid(level, feet.above())
                && solid(level, feet.below())
                && level.getFluidState(feet).isEmpty();
    }

    // Can a ground/sea hull drive one step onto this column? Ships need water, everything else needs land.
    public static boolean drivable(ServerLevel level, int x, int z, int hullY, boolean ship, int maxClimb, int maxDrop) {
        Ground g = groundAt(level, x, z, hullY);
        if (g == Ground.LAVA || g == Ground.VOID) return false;
        if (ship != (g == Ground.WATER)) return false;
        int h = groundHeight(level, x, z, hullY);
        if (h > hullY + maxClimb) return false;   // wall
        if (h < hullY - maxDrop) return false;    // cliff / shoreline drop
        return !(solid(level, new BlockPos(x, h, z)) && solid(level, new BlockPos(x, h + 1, z)));
    }

    // Walk a bearing in 2-block steps and report whether the whole run is drivable.
    public static boolean bearingDrivable(ServerLevel level, Vec3 from, Vec3 dir, double lookahead, boolean ship, int maxClimb, int maxDrop) {
        int hullY = (int) Math.floor(from.y);
        for (double s = 2.0; s <= lookahead; s += 2.0) {
            int x = (int) Math.floor(from.x + dir.x * s);
            int z = (int) Math.floor(from.z + dir.z * s);
            if (!drivable(level, x, z, hullY, ship, maxClimb, maxDrop)) return false;
            // follow the terrain as we go, so a long gentle slope is fine but a staircase of walls is not
            hullY = groundHeight(level, x, z, hullY);
        }
        return true;
    }

    // Nearest position around 'wanted' that a hull could actually sit on; null when there is none nearby.
    @Nullable
    public static Vec3 nearestDrivable(ServerLevel level, Vec3 wanted, boolean ship, int radius) {
        int wy = surfaceY(level, wanted.x, wanted.z);
        int wx = (int) Math.floor(wanted.x);
        int wz = (int) Math.floor(wanted.z);
        if (drivable(level, wx, wz, wy, ship, 1, 3)) return wanted;
        for (int r = 2; r <= radius; r += 2) {
            for (int a = 0; a < 12; a++) {
                double ang = a * Math.PI / 6.0;
                int x = wx + (int) Math.round(Math.cos(ang) * r);
                int z = wz + (int) Math.round(Math.sin(ang) * r);
                int y = surfaceY(level, x, z);
                if (drivable(level, x, z, y, ship, 1, 3)) return new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }
}

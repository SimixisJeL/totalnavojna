package sk.totalnavojna.api;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;

// The seam between Totálna Vojna and whatever decides a team may field a soldier.
//
// Without a provider (no WarEconomy, or the economy switched off) recruiting is free and unlimited, exactly
// as it always was. With one, the Spawner stops being a spawn button and becomes a delivery point: the
// commander buys the men at the Communication Station, and the Spawner hands out what has already been paid for.
//
// This is deliberately tiny. WarEconomy depends on us, never the other way round, so everything the economy
// needs to say lives behind this one interface.
public final class RecruitmentGate {

    public interface Provider {
        // False = behave as if no economy existed at all.
        boolean isActive(ServerLevel level);

        // How many soldiers of this role the team has paid for and not yet spawned.
        int available(ServerLevel level, Team team, Role role);

        // Take that many out of the pool. Returns how many were actually taken, never more than asked.
        int take(ServerLevel level, Team team, Role role, int count);

        // Put men back when the spawn failed after they were already claimed.
        default void refund(ServerLevel level, Team team, Role role, int count) {
        }
    }

    private static Provider provider = null;

    private RecruitmentGate() {
    }

    public static void setProvider(@Nullable Provider newProvider) {
        provider = newProvider;
    }

    public static boolean isGated(ServerLevel level) {
        return provider != null && provider.isActive(level);
    }

    // -1 means "no limit", which is what the recruit menu shows when nothing is gating it.
    public static int available(ServerLevel level, Team team, Role role) {
        return isGated(level) ? Math.max(0, provider.available(level, team, role)) : -1;
    }

    // How many of `wanted` may actually be spawned right now.
    public static int claim(ServerLevel level, Team team, Role role, int wanted) {
        if (!isGated(level)) return wanted;
        return Math.max(0, provider.take(level, team, role, wanted));
    }

    // Claimed but never spawned - the ground was full, say. Nobody should pay for a soldier who never arrived.
    public static void refund(ServerLevel level, Team team, Role role, int count) {
        if (count > 0 && isGated(level)) provider.refund(level, team, role, count);
    }
}

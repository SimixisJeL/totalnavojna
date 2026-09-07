package sk.totalnavojna;

import sk.totalnavojna.config.CommonConfig;

import java.util.EnumMap;
import java.util.Map;

// What the teams are called on screen.
//
// The internal ids stay "oliva" and "piesok" forever - they are written into NBT and packets, and renaming
// them would break every existing save. This class is the cosmetic layer on top: the server reads the names
// from its config, and the client is told them in the war state sync, so a renamed team looks renamed
// on a dedicated server too.
public final class TeamNames {

    private static final Map<Team, String> OVERRIDE = new EnumMap<>(Team.class);

    private TeamNames() {
    }

    public static String get(Team team) {
        String override = OVERRIDE.get(team);
        if (override != null && !override.isBlank()) return override;
        try {
            String fromConfig = team == Team.OLIVA
                    ? CommonConfig.TEAM_NAME_OLIVA.get()
                    : CommonConfig.TEAM_NAME_PIESOK.get();
            if (fromConfig != null && !fromConfig.isBlank()) return fromConfig;
        } catch (IllegalStateException | NullPointerException ignored) {
            // config not loaded yet (very early startup, or a client that never loaded ours) - fall through
        }
        return fallback(team);
    }

    public static String fallback(Team team) {
        return team == Team.OLIVA ? "Olive" : "Sand";
    }

    // Set from the server sync. Blank clears the override and goes back to the local config.
    public static void setOverride(Team team, String name) {
        if (name == null || name.isBlank()) OVERRIDE.remove(team);
        else OVERRIDE.put(team, name);
    }
}

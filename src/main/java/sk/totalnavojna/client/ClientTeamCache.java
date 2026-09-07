package sk.totalnavojna.client;

import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;

public class ClientTeamCache {

    @Nullable
    private static Team myTeam = null;

    public static void set(@Nullable Team team) {
        myTeam = team;
    }

    @Nullable
    public static Team get() {
        return myTeam;
    }
}

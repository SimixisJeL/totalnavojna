package sk.totalnavojna.war;

import org.jetbrains.annotations.Nullable;

// How a team's soldiers treat vanilla/other-mod mobs (and vice versa: HOSTILE/ALL also makes monsters hunt the soldiers).
public enum MobStance {
    NEUTRAL("neutral", "§7NEUTRÁLNI"),   // ignore mobs, only shoot back at one that bit them
    HOSTILE("hostile", "§eLOVIA MONŠTRÁ"), // attack hostile mobs (zombies, skeletons, creepers, ...), monsters hunt soldiers
    ALL("all", "§cÚTOČIA NA VŠETKO");     // attack every mob incl. animals, monsters hunt soldiers

    private final String name;
    private final String label;

    MobStance(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public String getName() {
        return this.name;
    }

    public String getLabel() {
        return this.label;
    }

    @Nullable
    public static MobStance byName(String name) {
        for (MobStance s : values()) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }
}

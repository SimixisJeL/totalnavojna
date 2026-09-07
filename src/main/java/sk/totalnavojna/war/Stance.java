package sk.totalnavojna.war;

import org.jetbrains.annotations.Nullable;

// Diplomatic stance of one team towards the other team (append-only: names are stored in NBT, ordinals not).
public enum Stance {
    WAR("war", "§cVOJNA"),           // attack on sight, attack NEXUS
    TRUCE("truce", "§ePRÍMERIE"),    // do not attack unless the other team attacks us first (then 60 s retaliation)
    PASSIVE("passive", "§7PASIVITA"), // never attack, no matter what
    ALLIANCE("alliance", "§aSPOLUPRÁCA"); // friends: no attacks, medics revive/heal them, share targets

    private final String name;
    private final String label;

    Stance(String name, String label) {
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
    public static Stance byName(String name) {
        for (Stance s : values()) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }
}

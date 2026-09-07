package sk.totalnavojna.war;

import org.jetbrains.annotations.Nullable;

// Where a match is in its life. Stored by name in WarGameData, so this may be reordered freely.
public enum MatchPhase {
    IDLE("idle", "§7ŽIADNY ZÁPAS"),      // sandbox: build, spawn, mess about; nothing is scored
    PREP("prep", "§ePRÍPRAVA"),          // deployment window, weapons are cold
    COMBAT("combat", "§cBOJ"),
    ENDED("ended", "§6KONIEC");

    private final String name;
    private final String label;

    MatchPhase(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public String getName() {
        return this.name;
    }

    public String getLabel() {
        return this.label;
    }

    public boolean isFighting() {
        return this == COMBAT || this == IDLE;
    }

    @Nullable
    public static MatchPhase byName(String name) {
        for (MatchPhase p : values()) {
            if (p.name.equals(name)) return p;
        }
        return null;
    }
}

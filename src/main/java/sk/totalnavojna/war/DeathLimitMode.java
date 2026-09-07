package sk.totalnavojna.war;

// How losses end a war.
//   NONE    - nobody ever stops respawning (sandbox)
//   TEAM    - a shared pool: the whole team may lose N soldiers, then no more respawns
//   SOLDIER - every soldier has his own lives; after N deaths that man is gone for good and
//             shows up greyed out in the commander's roster
public enum DeathLimitMode {
    NONE,
    TEAM,
    SOLDIER
}

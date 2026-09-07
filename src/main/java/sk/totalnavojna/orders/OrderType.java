package sk.totalnavojna.orders;

// APPEND-ONLY: ordinals are stored in entity data and packets.
public enum OrderType {
    NONE,
    FOLLOW_COMMANDER,
    HOLD_POSITION,
    MOVE_TO_POSITION,
    FORM_WEDGE,
    FORM_COLUMN,
    CEASE_FIRE,
    FREE_FIRE,
    ATTACK_THAT_TARGET,
    FULL_HEAL,
    ATTACK_CORE,
    DEFEND_CORE,
    MOVE_ALONG_PATH,
    BOARD_VEHICLE,
    DISMOUNT_VEHICLE,
    RESUPPLY,            // go to the supply station now
    RETREAT_TO_NEXUS,    // converted server-side into MOVE_TO_POSITION at own NEXUS
    PATROL_PATH,         // walk/drive the last map route back and forth forever
    DRONE_STRIKE_TARGET, // drone operator: fly the drone into the designated entity
    DRONE_STRIKE_POSITION, // drone operator: fly the drone into the designated block position
    DRONE_SCOUT,         // drone operator: hover over a position and report/mark enemies
    DRONE_RECALL,        // drone operator: bring the drone back and land it
    MOVE_AND_UNLOAD      // vehicle driver: drive to position, passengers dismount there
}

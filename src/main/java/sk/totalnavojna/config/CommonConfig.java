package sk.totalnavojna.config;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;
import java.util.Map;

public final class CommonConfig {

    public static ForgeConfigSpec.IntValue GUN_ATTACK_COOLDOWN;

    public static ForgeConfigSpec.DoubleValue SWAT_ENTITY_EFFECTIVE_RANGE_MULT;
    public static ForgeConfigSpec.IntValue SWAT_ENTITY_DEAD_BODY_LIFESPAN;
    public static ForgeConfigSpec.IntValue SWAT_ENTITY_FAILED_GUN_POS_LIMIT;
    public static ForgeConfigSpec.IntValue SWAT_ENTITY_HOLD_POSITION_DURATION;
    public static ForgeConfigSpec.BooleanValue SWAT_ENTITY_NO_CORPSE;

    public static Map<Attribute, ForgeConfigSpec.DoubleValue> SOLDIER_ATTRIBUTES;
    public static ForgeConfigSpec.DoubleValue SOLDIER_AIM_ERROR;
    public static ForgeConfigSpec.BooleanValue ALLOW_DOWN;
    public static ForgeConfigSpec.IntValue DOWN_TIME;
    public static ForgeConfigSpec.BooleanValue DROP_ITEMS;
    public static ForgeConfigSpec.IntValue REVIVE_TIME;
    public static ForgeConfigSpec.IntValue MEDIC_REVIVE_TIME;
    public static ForgeConfigSpec.DoubleValue SOLDIER_HEAL_THRESHOLD;
    public static ForgeConfigSpec.DoubleValue BANDAGE_HEAL;
    public static ForgeConfigSpec.IntValue BANDAGE_USE_TICKS;
    public static ForgeConfigSpec.DoubleValue PATHFINDER_HEALTH;
    public static ForgeConfigSpec.BooleanValue SOLDIERS_BREACH_DOORS;
    public static ForgeConfigSpec.IntValue IRON_DOOR_BREAK_TICKS;
    public static ForgeConfigSpec.BooleanValue SOLDIERS_CLIMB;
    public static ForgeConfigSpec.BooleanValue SMART_COMBAT_MOVEMENT;
    public static ForgeConfigSpec.BooleanValue SMART_NAMETAGS;

    public static ForgeConfigSpec.BooleanValue ALLOW_RESPAWN;
    public static ForgeConfigSpec.IntValue RESPAWN_TIME;
    public static ForgeConfigSpec.IntValue MAX_DEATHS;
    public static ForgeConfigSpec.IntValue MAX_SOLDIERS_PER_TEAM;
    public static ForgeConfigSpec.ConfigValue<String> TEAM_NAME_OLIVA;
    public static ForgeConfigSpec.ConfigValue<String> TEAM_NAME_PIESOK;
    public static ForgeConfigSpec.BooleanValue SCOREBOARD_ENABLED;
    public static ForgeConfigSpec.IntValue CORE_LIVES;
    public static ForgeConfigSpec.IntValue CORE_HIT_TICKS;
    public static ForgeConfigSpec.IntValue CORE_HIT_TICKS_ATTACKER;
    public static ForgeConfigSpec.IntValue GRENADE_SAFE_RADIUS;
    public static ForgeConfigSpec.IntValue SUPPLY_RANGE;
    public static ForgeConfigSpec.IntValue VEHICLE_ENGAGE_RANGE;
    public static ForgeConfigSpec.IntValue DRONE_RANGE;
    public static ForgeConfigSpec.IntValue DRONE_ALTITUDE;
    public static ForgeConfigSpec.IntValue DRONE_COOLDOWN;
    public static ForgeConfigSpec.IntValue DRONE_MAX_DISTANCE;
    public static ForgeConfigSpec.BooleanValue DRONE_FORCE_CHUNKS;
    public static ForgeConfigSpec.IntValue DRONE_ENGAGE_RANGE;
    public static ForgeConfigSpec.BooleanValue VEHICLE_FORCE_CHUNKS;
    public static ForgeConfigSpec.BooleanValue KEEP_ARMY_LOADED;
    public static ForgeConfigSpec.IntValue MAX_ARMY_CHUNKS;
    public static ForgeConfigSpec.DoubleValue DRONE_EXPLOSION_DAMAGE;
    public static ForgeConfigSpec.DoubleValue DRONE_EXPLOSION_RADIUS;
    public static ForgeConfigSpec.BooleanValue DRONE_BREAKS_BLOCKS;

    // --- v0.15.0: shape of the war ---
    public static ForgeConfigSpec.EnumValue<sk.totalnavojna.war.DeathLimitMode> DEATH_LIMIT_MODE;
    public static ForgeConfigSpec.IntValue MAX_SOLDIER_DEATHS;
    public static ForgeConfigSpec.IntValue START_TICKETS;
    public static ForgeConfigSpec.IntValue TICKETS_PER_DEATH;
    public static ForgeConfigSpec.IntValue CAPTURE_RADIUS;
    public static ForgeConfigSpec.IntValue CAPTURE_TIME;
    public static ForgeConfigSpec.IntValue BLEED_INTERVAL;
    public static ForgeConfigSpec.IntValue PREP_SECONDS;
    public static ForgeConfigSpec.IntValue SPAWNER_LIVES;
    public static ForgeConfigSpec.BooleanValue NEUTRAL_ON_END;

    // --- v0.15.0: firefight dynamics ---
    public static ForgeConfigSpec.BooleanValue SUPPRESSION_ENABLED;
    public static ForgeConfigSpec.IntValue SUPPRESSION_TICKS;
    public static ForgeConfigSpec.DoubleValue SUPPRESSION_AIM_MULT;
    public static ForgeConfigSpec.BooleanValue FLANK_ENABLED;
    public static ForgeConfigSpec.DoubleValue FLANK_THRESHOLD;
    public static ForgeConfigSpec.IntValue FLANK_OFFSET;
    public static ForgeConfigSpec.IntValue PATH_TOLERANCE;
    public static ForgeConfigSpec.BooleanValue SQUAD_LEADERS;
    public static ForgeConfigSpec.IntValue SQUAD_COHESION;

    // --- v0.15.0: voice + log ---
    public static ForgeConfigSpec.BooleanValue VOICE_ENABLED;
    public static ForgeConfigSpec.IntValue VOICE_MIN_GAP;
    public static ForgeConfigSpec.DoubleValue VOICE_VOLUME;
    public static ForgeConfigSpec.BooleanValue BATTLE_LOG;

    public static final ForgeConfigSpec SPEC = init();

    private static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("GunAttack");

        GUN_ATTACK_COOLDOWN = builder
                .comment("Time between gun attacks,")
                .comment("in ticks (20 ticks = 1 second)")
                .comment("Default: 15")
                .defineInRange("cooldown", 15, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.push("SwatEntity");

        SWAT_ENTITY_EFFECTIVE_RANGE_MULT = builder
                .comment("Multiplier for entities weapon's effective range.")
                .comment("Default: 2.0")
                .defineInRange("effectiveRangeMult", 2.0, 0.01, 10.0);
        SWAT_ENTITY_DEAD_BODY_LIFESPAN = builder
                .comment("Time after which dead body will despawn,")
                .comment("in ticks (20 ticks = 1 second)")
                .comment("Default: 6000")
                .defineInRange("deadBodyLifespan", 6000, 1, Integer.MAX_VALUE);
        SWAT_ENTITY_FAILED_GUN_POS_LIMIT = builder
                .comment("Number of failed attempts entity has to do before it is considered as stuck.")
                .comment("Default: 1800")
                .defineInRange("failedGunPosLimit", 1800, 1, Integer.MAX_VALUE);
        SWAT_ENTITY_HOLD_POSITION_DURATION = builder
                .comment("Time duration for which the entity is gonna hold it's current position even if it's not optimal.")
                .comment("Higher values means less tps lag.")
                .comment("Default: 300")
                .defineInRange("holdPosDuration", 300, 1, Integer.MAX_VALUE);
        SWAT_ENTITY_NO_CORPSE = builder
                .comment("Should a corpse be left behind.")
                .comment("Default: true (soldier dies normally and drops inventory)")
                .define("noCorpse", true);

        builder.pop();

        builder.push("Soldier");

        SOLDIER_AIM_ERROR = builder
                .comment("Aim error in blocks. Lower = deadlier soldiers.")
                .comment("Default: 0.1")
                .defineInRange("aimError", 0.1, 0.0, 2.0);
        ALLOW_DOWN = builder
                .comment("If true, soldiers collapse into a revivable 'down' state at 0 HP instead of dying.")
                .comment("Default: true")
                .define("allowDown", true);
        DOWN_TIME = builder
                .comment("How long a downed soldier/player survives waiting for a medkit, in ticks.")
                .comment("Default: 500 (25s)")
                .defineInRange("downTime", 500, 20, Integer.MAX_VALUE);
        DROP_ITEMS = builder
                .comment("Should soldiers drop their inventory and equipment on death?")
                .comment("Default: false (prevents item flood in big battles)")
                .define("dropItems", false);
        REVIVE_TIME = builder
                .comment("How long a medkit revive takes for players and regular soldiers, in ticks.")
                .comment("Default: 120 (6s)")
                .defineInRange("reviveTime", 120, 1, Integer.MAX_VALUE);
        MEDIC_REVIVE_TIME = builder
                .comment("How long a medkit revive takes for MEDIC soldiers, in ticks.")
                .comment("Default: 80 (4s)")
                .defineInRange("medicReviveTime", 80, 1, Integer.MAX_VALUE);
        BANDAGE_HEAL = builder
                .comment("How much health one bandage restores. A bandage patches up a man who is still standing;")
                .comment("bringing back a downed one is the medkit's job.")
                .comment("Default: 8")
                .defineInRange("bandageHeal", 8.0, 1.0, 1024.0);
        BANDAGE_USE_TICKS = builder
                .comment("How long applying a bandage takes, in ticks. Long enough that doing it in the open is a decision.")
                .comment("Default: 40 (2s)")
                .defineInRange("bandageUseTicks", 40, 1, Integer.MAX_VALUE);
        SOLDIER_HEAL_THRESHOLD = builder
                .comment("Soldiers drink healing potions (out of combat) when below this HP.")
                .comment("Default: 20")
                .defineInRange("healThreshold", 20.0, 0.0, 1024.0);
        PATHFINDER_HEALTH = builder
                .comment("Max health of the PATHFINDER support role (digs paths, heals, only fights back).")
                .comment("Default: 20")
                .defineInRange("pathfinderHealth", 20.0, 1.0, 1024.0);
        SOLDIERS_BREACH_DOORS = builder
                .comment("Soldiers press levers/buttons on iron doors and slowly break iron doors/trapdoors.")
                .comment("Default: true")
                .define("breachDoors", true);
        IRON_DOOR_BREAK_TICKS = builder
                .comment("How long it takes a soldier to break an iron door / iron trapdoor, in ticks.")
                .comment("Default: 300 (15s)")
                .defineInRange("ironDoorBreakTicks", 300, 20, Integer.MAX_VALUE);
        SOLDIERS_CLIMB = builder
                .comment("Soldiers use ladders, vines and scaffolding to reach targets on other levels.")
                .comment("Default: true")
                .define("climbLadders", true);
        SMART_COMBAT_MOVEMENT = builder
                .comment("Tactical combat movement: strafing, side-stepping out of teammates' line of fire, snipers keeping distance, low-HP cover seeking.")
                .comment("Default: true")
                .define("smartCombatMovement", true);
        SMART_NAMETAGS = builder
                .comment("Smart nametags: soldier and player names are shown only when you look straight at them.")
                .comment("Default: true")
                .define("smartNametags", true);

        SOLDIER_ATTRIBUTES = new HashMap<>();
        defAttr(builder, Attributes.MAX_HEALTH, "maxHealth", 60.0, 1.0, 1024.0);
        defAttr(builder, Attributes.FOLLOW_RANGE, "followRange", 48.0, 0.0, 2048.0);
        defAttr(builder, Attributes.KNOCKBACK_RESISTANCE, "knockbackResistance", 0.15, 0.0, 1.0);
        defAttr(builder, Attributes.MOVEMENT_SPEED, "movementSpeed", 0.3, 0.0, 1024.0);
        defAttr(builder, Attributes.ATTACK_DAMAGE, "attackDamage", 2.0, 0.0, 2048.0);
        defAttr(builder, Attributes.ATTACK_KNOCKBACK, "attackKnockback", 0.0, 0.0, 5.0);
        defAttr(builder, Attributes.ATTACK_SPEED, "attackSpeed", 4.0, 0.0, 1024.0);
        defAttr(builder, Attributes.ARMOR, "armor", 2.0, 0.0, 30.0);
        defAttr(builder, Attributes.ARMOR_TOUGHNESS, "armorToughness", 0.0, 0.0, 20.0);

        builder.pop();

        builder.push("WarGame");

        ALLOW_RESPAWN = builder
                .comment("Dead soldiers respawn at their team's SPAWNER block.")
                .comment("Default: true")
                .define("allowRespawn", true);
        RESPAWN_TIME = builder
                .comment("Respawn delay in ticks.")
                .comment("Default: 200 (10s)")
                .defineInRange("respawnTime", 200, 20, Integer.MAX_VALUE);
        MAX_DEATHS = builder
                .comment("Max deaths per team before it is defeated. 0 = unlimited.")
                .comment("Default: 0")
                .defineInRange("maxDeaths", 0, 0, Integer.MAX_VALUE);
        MAX_SOLDIERS_PER_TEAM = builder
                .comment("Max living soldiers per team (spawn eggs stop working above this). 0 = unlimited.")
                .comment("Default: 0")
                .defineInRange("maxSoldiersPerTeam", 0, 0, 1000);
        TEAM_NAME_OLIVA = builder
                .comment("Display name of the first team. Only cosmetic - the internal id stays 'oliva', so renaming")
                .comment("a team never breaks an existing save.")
                .comment("Default: Olive")
                .define("teamNameOliva", "Olive");
        TEAM_NAME_PIESOK = builder
                .comment("Display name of the second team.")
                .comment("Default: Sand")
                .define("teamNamePiesok", "Sand");
        SCOREBOARD_ENABLED = builder
                .comment("Show the war scoreboard sidebar (deaths + NEXUS lives per team).")
                .comment("Default: true")
                .define("scoreboardEnabled", true);
        CORE_LIVES = builder
                .comment("Lives of a NEXUS (core) block. Each attacker (soldier or player) removes one life per hit cycle.")
                .comment("Default: 100")
                .defineInRange("coreLives", 100, 1, 100000);
        CORE_HIT_TICKS = builder
                .comment("Ticks one attacker (player or non-attacker soldier) needs to remove one NEXUS life.")
                .comment("Default: 40 (2s)")
                .defineInRange("coreHitTicks", 40, 1, Integer.MAX_VALUE);
        CORE_HIT_TICKS_ATTACKER = builder
                .comment("Ticks an ATTACKER-role soldier needs to remove one NEXUS life (role buff).")
                .comment("Default: 30 (1.5s)")
                .defineInRange("coreHitTicksAttacker", 30, 1, Integer.MAX_VALUE);
        GRENADE_SAFE_RADIUS = builder
                .comment("Attackers will not use the grenade launcher within this range of their own NEXUS.")
                .comment("Default: 80")
                .defineInRange("grenadeSafeRadius", 80, 0, 1000);
        SUPPLY_RANGE = builder
                .comment("Soldiers out of combat walk to a friendly SUPPLY STATION within this range to restock ammo/medkits/potions.")
                .comment("Default: 48")
                .defineInRange("supplyRange", 48, 4, 512);
        VEHICLE_ENGAGE_RANGE = builder
                .comment("Vehicle crews (Superb Warfare hulls driven by soldiers) engage enemies within this range; the driver stops to let the turret work.")
                .comment("Default: 48")
                .defineInRange("vehicleEngageRange", 48, 8, 256);
        DRONE_RANGE = builder
                .comment("Drone operator: how far (blocks) it looks for targets for its kamikaze drone.")
                .comment("Default: 96")
                .defineInRange("droneRange", 96, 16, 512);
        DRONE_ALTITUDE = builder
                .comment("Drone operator: cruise altitude above the terrain surface.")
                .comment("Default: 10")
                .defineInRange("droneAltitude", 10, 4, 64);
        DRONE_COOLDOWN = builder
                .comment("Drone operator: ticks between losing a drone and launching the next one.")
                .comment("Default: 200 (10s)")
                .defineInRange("droneCooldown", 200, 0, Integer.MAX_VALUE);
        DRONE_MAX_DISTANCE = builder
                .comment("Drone operator: control link range in blocks. Past this the drone turns around and flies home,")
                .comment("which is what gives the enemy something to play against (kill the operator, the drone goes home).")
                .comment("Default: 300")
                .defineInRange("droneMaxDistance", 300, 32, 4096);
        DRONE_FORCE_CHUNKS = builder
                .comment("Keep the chunks under an AI drone ticking while it flies.")
                .comment("Minecraft only ticks entities within a player's simulation distance (12 chunks by default), so without")
                .comment("this a drone freezes in mid air ~192 blocks from the nearest player. Costs a 3x3 chunk ticket per flying drone.")
                .comment("Default: true")
                .define("droneForceChunks", true);
        DRONE_ENGAGE_RANGE = builder
                .comment("Soldiers shoot at hostile drones within this range (a drone has 5 HP, so this is a real defence).")
                .comment("Set to 0 to disable air defence.")
                .comment("Default: 40")
                .defineInRange("droneEngageRange", 40, 0, 256);
        VEHICLE_FORCE_CHUNKS = builder
                .comment("Keep the chunks under a soldier-driven vehicle ticking, same reason as droneForceChunks.")
                .comment("Superb Warfare has its own vehicle_chunk_loading option, but it only applies to vehicles whose data")
                .comment("sets KeepChunkLoaded, which none of them do - so this is the one that actually matters.")
                .comment("Default: true")
                .define("vehicleForceChunks", true);
        KEEP_ARMY_LOADED = builder
                .comment("Keep the chunks your soldiers stand in loaded and ticking.")
                .comment("Without this, units further away than the simulation distance stop existing for the server:")
                .comment("they vanish from the tactical map, map orders cannot reach them and they stand still until you walk back.")
                .comment("Default: true")
                .define("keepArmyLoaded", true);
        MAX_ARMY_CHUNKS = builder
                .comment("Safety cap on how many chunks the army may hold loaded at once.")
                .comment("Roughly one chunk per group of soldiers standing together; raise it for a bigger battlefield.")
                .comment("Default: 256")
                .defineInRange("maxArmyChunks", 256, 16, 4096);
        DRONE_EXPLOSION_DAMAGE = builder
                .comment("Kamikaze drone: damage at the centre of the blast (falls off with distance).")
                .comment("Default: 160")
                .defineInRange("droneExplosionDamage", 160.0, 1.0, 10000.0);
        DRONE_EXPLOSION_RADIUS = builder
                .comment("Kamikaze drone: blast radius in blocks.")
                .comment("Default: 6.0")
                .defineInRange("droneExplosionRadius", 6.0, 1.0, 64.0);
        DRONE_BREAKS_BLOCKS = builder
                .comment("Kamikaze drone blast destroys terrain and buildings.")
                .comment("Default: true")
                .define("droneBreaksBlocks", true);

        builder.pop();

        builder.push("MatchShape");

        DEATH_LIMIT_MODE = builder
                .comment("How losses end the war:")
                .comment("  NONE    = nobody ever stops respawning (sandbox)")
                .comment("  TEAM    = shared pool: the team may lose maxDeaths soldiers in total, then no more respawns")
                .comment("  SOLDIER = every soldier has his own lives (maxSoldierDeaths); after that he is gone for good")
                .comment("            and shows up greyed out in the commander roster")
                .comment("Default: NONE")
                .defineEnum("deathLimitMode", sk.totalnavojna.war.DeathLimitMode.NONE);
        MAX_SOLDIER_DEATHS = builder
                .comment("SOLDIER mode: how many times one soldier may die before he is permanently dead.")
                .comment("Default: 2")
                .defineInRange("maxSoldierDeaths", 2, 1, 1000);
        START_TICKETS = builder
                .comment("Tickets each team starts a match with. Holding fewer capture points bleeds tickets; 0 tickets left = defeat.")
                .comment("0 = tickets off, so destroying the NEXUS is the only way to win.")
                .comment("Default: 0")
                .defineInRange("startTickets", 0, 0, 100000);
        TICKETS_PER_DEATH = builder
                .comment("Tickets a team loses for each soldier killed.")
                .comment("Default: 1")
                .defineInRange("ticketsPerDeath", 1, 0, 100);
        CAPTURE_RADIUS = builder
                .comment("Radius in blocks around a CAPTURE POINT block in which units count towards capturing it.")
                .comment("Default: 16")
                .defineInRange("captureRadius", 16, 4, 128);
        CAPTURE_TIME = builder
                .comment("Ticks one soldier needs to flip a capture point. More soldiers on the point make it faster, up to three times.")
                .comment("Default: 400 (20s)")
                .defineInRange("captureTime", 400, 20, Integer.MAX_VALUE);
        BLEED_INTERVAL = builder
                .comment("Ticks between ticket bleeds. Each bleed the losing side drops one ticket per capture point of difference.")
                .comment("Default: 200 (10s)")
                .defineInRange("bleedInterval", 200, 20, Integer.MAX_VALUE);
        PREP_SECONDS = builder
                .comment("Length of the PREP phase after /totalnavojna start. Weapons are cold, units may deploy.")
                .comment("Default: 60")
                .defineInRange("prepSeconds", 60, 0, 3600);
        SPAWNER_LIVES = builder
                .comment("Lives of a forward SPAWNER (FOB). Enemies knock it out the same way they chew through a NEXUS. 0 = indestructible.")
                .comment("Default: 20")
                .defineInRange("spawnerLives", 20, 0, 100000);
        NEUTRAL_ON_END = builder
                .comment("When a match ends (last NEXUS gone or tickets out) both teams are forced to PASSIVE so the world calms down.")
                .comment("Default: true")
                .define("neutralOnEnd", true);

        builder.pop();

        builder.push("Dynamics");

        SUPPRESSION_ENABLED = builder
                .comment("Incoming fire suppresses soldiers: they shoot worse and move more carefully for a moment. Deliberately mild.")
                .comment("Default: true")
                .define("suppressionEnabled", true);
        SUPPRESSION_TICKS = builder
                .comment("How long one burst of incoming fire keeps a soldier suppressed.")
                .comment("Default: 60 (3s)")
                .defineInRange("suppressionTicks", 60, 1, Integer.MAX_VALUE);
        SUPPRESSION_AIM_MULT = builder
                .comment("Aim error multiplier while suppressed. 1.0 = no effect at all.")
                .comment("Default: 1.6")
                .defineInRange("suppressionAimMult", 1.6, 1.0, 10.0);
        FLANK_ENABLED = builder
                .comment("A group that has lost too many men stops pushing head-on and tries to go around the enemy instead.")
                .comment("Default: true")
                .define("flankEnabled", true);
        FLANK_THRESHOLD = builder
                .comment("Fraction of the group that has to be lost before it attempts a flanking move. 0.5 = half the group.")
                .comment("Default: 0.5")
                .defineInRange("flankThreshold", 0.5, 0.05, 1.0);
        FLANK_OFFSET = builder
                .comment("How far to the side in blocks a flanking group swings before closing back in.")
                .comment("Default: 30")
                .defineInRange("flankOffset", 30, 8, 200);
        PATH_TOLERANCE = builder
                .comment("A commander-drawn route is a suggestion, not a rail: a waypoint counts as reached from this many blocks away,")
                .comment("and a waypoint the soldier cannot reach (up a tree, inside a wall, across a cliff) is skipped instead of being died for.")
                .comment("Default: 6")
                .defineInRange("pathTolerance", 6, 1, 64);
        SQUAD_LEADERS = builder
                .comment("Each control group gets a squad leader, marked with a star. Idle members keep near him instead of scattering.")
                .comment("Default: true")
                .define("squadLeaders", true);
        SQUAD_COHESION = builder
                .comment("How far an idle squad member may drift from his squad leader before walking back.")
                .comment("Default: 24")
                .defineInRange("squadCohesion", 24, 6, 200);

        builder.pop();

        builder.push("Voice");

        VOICE_ENABLED = builder
                .comment("Dubbed Slovak radio traffic and soldier one-liners.")
                .comment("Default: true")
                .define("voiceEnabled", true);
        VOICE_MIN_GAP = builder
                .comment("Minimum ticks between any two voice lines of one team. The same line waits four times as long")
                .comment("and one soldier waits three times as long, so a big battle never turns into a shouting match.")
                .comment("Default: 100 (5s)")
                .defineInRange("voiceMinGap", 100, 20, Integer.MAX_VALUE);
        VOICE_VOLUME = builder
                .comment("Volume of the voice lines.")
                .comment("Default: 1.0")
                .defineInRange("voiceVolume", 1.0, 0.0, 2.0);
        BATTLE_LOG = builder
                .comment("Record a battle log (kills, capture points, NEXUS hits) and print an after-action report when a match ends.")
                .comment("Default: true")
                .define("battleLog", true);

        builder.pop();

        return builder.build();
    }

    private static void defAttr(ForgeConfigSpec.Builder builder, Attribute attr, String key, double def, double min, double max) {
        SOLDIER_ATTRIBUTES.put(attr, builder
                .comment("Default: " + def)
                .defineInRange(key, def, min, max));
    }

    private CommonConfig() {
    }
}

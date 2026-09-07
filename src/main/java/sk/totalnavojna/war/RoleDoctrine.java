package sk.totalnavojna.war;

import sk.totalnavojna.Role;

// What each role is FOR, in numbers. Goals ask here instead of hard-coding thresholds, so a role's behaviour is
// described in one readable place and every goal agrees on it.
//
//  standoff        - the distance this role tries to keep from its target
//  leadDistance    - how far ahead of / behind the squad it moves (negative = stays behind)
//  retreatHealth   - fraction of max HP at which it breaks contact and looks for cover / falls back
//  huntsOnSight    - does it start fights by itself, or only return fire
//  holdsGround     - does it stand and shoot (defender/sniper) or push forward (attacker)
public final class RoleDoctrine {

    private RoleDoctrine() {
    }

    public static double standoff(Role role) {
        return switch (role) {
            case SNIPER -> 28.0;
            case ATTACKER -> 8.0;
            case DEFENDER -> 14.0;
            case MEDIC -> 12.0;
            case PATHFINDER -> 16.0;
            case DRONE_OPERATOR -> 40.0;
        };
    }

    public static double leadDistance(Role role) {
        return switch (role) {
            case ATTACKER -> 4.0;
            case DEFENDER -> 0.0;
            case MEDIC -> -6.0;
            case SNIPER -> -10.0;
            case PATHFINDER -> -8.0;
            case DRONE_OPERATOR -> -16.0;
        };
    }

    public static float retreatHealth(Role role) {
        return switch (role) {
            case ATTACKER -> 0.25f;
            case DEFENDER -> 0.2f;
            case MEDIC -> 0.4f;
            case SNIPER -> 0.5f;
            case PATHFINDER, DRONE_OPERATOR -> 0.6f;
        };
    }

    public static boolean huntsOnSight(Role role) {
        return !role.isSupport();
    }

    public static boolean holdsGround(Role role) {
        return role == Role.DEFENDER || role == Role.SNIPER;
    }

    // How much known threat this role tolerates at a position before it looks for another one.
    public static double threatTolerance(Role role) {
        return switch (role) {
            case ATTACKER -> 6.0;
            case DEFENDER -> 8.0;
            case MEDIC -> 3.0;
            case SNIPER -> 2.0;
            case PATHFINDER -> 2.0;
            case DRONE_OPERATOR -> 1.0;
        };
    }
}

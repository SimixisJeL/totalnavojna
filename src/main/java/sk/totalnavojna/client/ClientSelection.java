package sk.totalnavojna.client;

import java.util.LinkedHashSet;
import java.util.Set;

// The commander's current selection, shared by the commander screen, the tactical map and the HUD.
// One selection everywhere is the whole point: drag a box on the map, then hit an order in the menu,
// or the other way round, without the two screens disagreeing about who is selected.
public final class ClientSelection {

    public static final Set<Integer> IDS = new LinkedHashSet<>();

    private ClientSelection() {
    }

    public static boolean isEmpty() {
        return IDS.isEmpty();
    }

    public static int size() {
        return IDS.size();
    }

    public static void clear() {
        IDS.clear();
    }

    public static boolean contains(int id) {
        return IDS.contains(id);
    }

    // Drop anyone who is no longer in the synced army (dead, despawned, wrong dimension).
    public static void retainAlive() {
        Set<Integer> alive = new LinkedHashSet<>();
        for (ClientWarState.MapSoldier s : ClientWarState.SOLDIERS) alive.add(s.id);
        IDS.retainAll(alive);
    }
}

package sk.totalnavojna.entities.goals.utils;

import net.minecraft.world.phys.Vec3;
import sk.totalnavojna.orders.OrderType;

// Ported from Simple-Enemy-Mod-Public (GPL-3.0) by NekoYuni
public class FormationUtils {

    public static Vec3 getTargetPosition(Vec3 anchorPos, Vec3 anchorLook, OrderType formation, int index) {
        if (formation == OrderType.FORM_COLUMN) {
            return calculateColumnPosition(anchorPos, anchorLook, index);
        } else if (formation == OrderType.FORM_WEDGE) {
            return calculateWedgePosition(anchorPos, anchorLook, index);
        }
        return anchorPos;
    }

    private static Vec3 calculateColumnPosition(Vec3 leaderPos, Vec3 leaderLook, int index) {
        double spacing = 2.0;
        double behindDistance = 2.0 + (index * spacing);
        Vec3 behind = leaderLook.scale(-behindDistance);

        return leaderPos.add(behind);
    }

    private static Vec3 calculateWedgePosition(Vec3 leaderPos, Vec3 leaderLook, int index) {
        if (index == 0) {
            return leaderPos.add(leaderLook.scale(-2.0));
        }

        double spacing = 2.5;
        double row = (index - 1) / 2 + 1;
        double behindDistance = 2.0 + (row * 2.0);

        Vec3 right = new Vec3(-leaderLook.z, 0, leaderLook.x).normalize();

        double side = (index % 2 != 0) ? -1.0 : 1.0;
        double sideDistance = spacing * row;

        Vec3 behind = leaderLook.scale(-behindDistance);
        Vec3 sideways = right.scale(side * sideDistance);

        return leaderPos.add(behind).add(sideways);
    }
}

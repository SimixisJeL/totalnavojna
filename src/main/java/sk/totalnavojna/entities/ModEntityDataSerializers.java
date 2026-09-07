package sk.totalnavojna.entities;

import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import sk.totalnavojna.Team;

public class ModEntityDataSerializers {
    public static final EntityDataSerializer<Team> TEAM;

    static {
        TEAM = EntityDataSerializer.simpleEnum(Team.class);
        EntityDataSerializers.registerSerializer(TEAM);
    }
}

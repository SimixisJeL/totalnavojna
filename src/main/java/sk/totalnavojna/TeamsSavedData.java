package sk.totalnavojna;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamsSavedData extends SavedData {
    private static final String DATA_NAME = TotalnaVojna.MOD_ID + "_teams";
    private static final String NBT_KEY_PLAYERS = "Players";

    private final Map<UUID, Team> playerTeams = new HashMap<>();

    public static TeamsSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(TeamsSavedData::load, TeamsSavedData::new, DATA_NAME);
    }

    private static TeamsSavedData load(CompoundTag tag) {
        TeamsSavedData data = new TeamsSavedData();
        CompoundTag players = tag.getCompound(NBT_KEY_PLAYERS);
        for (String key : players.getAllKeys()) {
            Team team = Team.byName(players.getString(key));
            if (team == null) continue;
            try {
                data.playerTeams.put(UUID.fromString(key), team);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        this.playerTeams.forEach((uuid, team) -> players.putString(uuid.toString(), team.getName()));
        tag.put(NBT_KEY_PLAYERS, players);
        return tag;
    }

    public void setTeam(UUID player, @Nullable Team team) {
        if (team == null) {
            this.playerTeams.remove(player);
        } else {
            this.playerTeams.put(player, team);
        }
        this.setDirty();
    }

    @Nullable
    public Team getTeam(UUID player) {
        return this.playerTeams.get(player);
    }
}

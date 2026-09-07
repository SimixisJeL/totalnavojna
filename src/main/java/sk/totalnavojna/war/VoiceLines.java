package sk.totalnavojna.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.Team;
import sk.totalnavojna.TeamsSavedData;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.init.ModSounds;

import java.util.*;

// Voice: radio traffic to the commander and one-liners from the soldiers themselves.
//
// The whole point of the rate limits here is that a battle involves dozens of soldiers, and without them every
// contact would set off a dozen overlapping voices. There is one global gap between ANY two lines, a longer gap
// per category, and a per-soldier gap - so you get an occasional human voice, not a crowd.
public final class VoiceLines {

    public enum Line {
        CONTACT("radio_contact", true),
        UNDER_FIRE("radio_under_fire", true),
        MAN_DOWN("radio_man_down", true),
        NO_AMMO("radio_no_ammo", true),
        NEXUS_ATTACK("radio_nexus_attack", true),
        POINT_TAKEN("radio_point_taken", true),
        POINT_LOST("radio_point_lost", true),
        DRONE_UP("radio_drone_up", true),
        DRONE_HIT("radio_drone_hit", true),
        ENEMY_VEHICLE("radio_enemy_vehicle", true),
        FLANKING("radio_flanking", true),
        FALLING_BACK("radio_falling_back", true),
        VICTORY("radio_victory", true),
        DEFEAT("radio_defeat", true),
        SPAWN("spawn", false),
        DEATH("death", false),
        KILL("kill", false),
        REVIVE("revive", false);

        public final String key;
        public final boolean radio;

        Line(String key, boolean radio) {
            this.key = key;
            this.radio = radio;
        }
    }

    // how many numbered variants each soldier line has (spawn_1..spawn_4 etc.)
    private static final Map<Line, Integer> VARIANTS = Map.of(
            Line.SPAWN, 5,
            Line.DEATH, 6,
            Line.KILL, 3,
            Line.REVIVE, 3
    );

    private static final Map<UUID, Boolean> AUDIO_OFF = new HashMap<>();
    private static final Map<Team, Long> LAST_ANY = new EnumMap<>(Team.class);
    private static final Map<String, Long> LAST_LINE = new HashMap<>();
    private static final Map<UUID, Long> LAST_SPEAKER = new HashMap<>();
    private static final Random RNG = new Random();

    private VoiceLines() {
    }

    public static void clear() {
        LAST_ANY.clear();
        LAST_LINE.clear();
        LAST_SPEAKER.clear();
    }

    public static void setAudio(UUID player, boolean on) {
        if (on) AUDIO_OFF.remove(player);
        else AUDIO_OFF.put(player, true);
    }

    public static boolean audioOn(UUID player) {
        return !AUDIO_OFF.containsKey(player);
    }

    private static boolean allow(Team team, Line line, @Nullable UUID speaker, long now) {
        if (!CommonConfig.VOICE_ENABLED.get()) return false;
        int gap = CommonConfig.VOICE_MIN_GAP.get();
        Long any = LAST_ANY.get(team);
        if (any != null && now - any < gap) return false;
        String lineKey = team.getName() + "/" + line.key;
        Long last = LAST_LINE.get(lineKey);
        // the same line does not repeat for a good while, even if something keeps triggering it
        if (last != null && now - last < gap * 4L) return false;
        if (speaker != null) {
            Long s = LAST_SPEAKER.get(speaker);
            if (s != null && now - s < gap * 3L) return false;
        }
        LAST_ANY.put(team, now);
        LAST_LINE.put(lineKey, now);
        if (speaker != null) LAST_SPEAKER.put(speaker, now);
        return true;
    }

    private static String pick(Line line) {
        Integer n = VARIANTS.get(line);
        if (n == null) return line.key;
        return line.key + "_" + (1 + RNG.nextInt(n));
    }

    // Radio: only the commanders of that team hear it, wherever they are.
    public static void radio(ServerLevel level, Team team, Line line) {
        if (team == null || !line.radio) return;
        long now = level.getGameTime();
        if (!allow(team, line, null, now)) return;
        SoundEvent sound = ModSounds.voice(pick(line));
        if (sound == null) return;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (TeamsSavedData.get(p.serverLevel()).getTeam(p.getUUID()) != team) continue;
            if (!audioOn(p.getUUID())) continue;
            p.playNotifySound(sound, SoundSource.VOICE, (float) (double) CommonConfig.VOICE_VOLUME.get(), 1.0f);
        }
    }

    // A soldier speaking out loud: positional, anybody nearby hears it.
    public static void speak(ServerLevel level, Team team, Entity speaker, Line line) {
        if (team == null || line.radio) return;
        long now = level.getGameTime();
        if (!allow(team, line, speaker.getUUID(), now)) return;
        SoundEvent sound = ModSounds.voice(pick(line));
        if (sound == null) return;
        BlockPos at = speaker.blockPosition();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (!audioOn(p.getUUID())) continue;
            if (p.distanceToSqr(speaker) > 48.0 * 48.0) continue;
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.Holder.direct(sound), SoundSource.VOICE,
                    at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
                    (float) (double) CommonConfig.VOICE_VOLUME.get(), 1.0f, level.getRandom().nextLong()));
        }
    }
}

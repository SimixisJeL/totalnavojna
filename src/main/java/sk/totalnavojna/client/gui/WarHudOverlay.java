package sk.totalnavojna.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.client.ClientSelection;
import sk.totalnavojna.client.ClientTeamCache;
import sk.totalnavojna.client.ClientWarState;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketRequestWarState;

import java.util.ArrayList;
import java.util.List;

// A small always-on panel for the commander: where the match stands, how strong his groups are and what he
// currently has selected. Without it you have to open a screen to find out you are losing.
@Mod.EventBusSubscriber(modid = TotalnaVojna.MOD_ID, value = Dist.CLIENT)
public class WarHudOverlay {

    private static final int BG = 0xA00A0E08;
    private static int syncTimer = 0;

    // The HUD needs fresh data even when no screen is open, so it asks for a sync a couple of times a minute.
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (ClientTeamCache.get() == null) return;
        if (--syncTimer > 0) return;
        syncTimer = 40;
        ModNetworking.sendToServer(new PacketRequestWarState());
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        Team myTeam = ClientTeamCache.get();
        if (myTeam == null) return;

        List<String> lines = new ArrayList<>();
        lines.add(phaseLine(myTeam));
        String groups = groupLine();
        if (!groups.isEmpty()) lines.add(groups);
        lines.add("§7Vybraných: §b" + ClientSelection.size()
                + "  §7Živých: §a" + ClientWarState.SOLDIERS.size()
                + "  §7Straty: §c" + ClientWarState.DEATHS.getOrDefault(myTeam, 0));

        Font font = mc.font;
        GuiGraphics g = event.getGuiGraphics();
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        int x = 4;
        int y = 4;
        g.fill(x - 2, y - 2, x + width + 4, y + lines.size() * 10 + 2, BG);
        for (String line : lines) {
            g.drawString(font, line, x, y, 0xFFFFFFFF, false);
            y += 10;
        }
    }

    private static String phaseLine(Team myTeam) {
        String phase = switch (ClientWarState.phase) {
            case 1 -> "§ePRÍPRAVA" + (ClientWarState.phaseSecondsLeft > 0 ? " " + ClientWarState.phaseSecondsLeft + "s" : "");
            case 2 -> "§cBOJ";
            case 3 -> "§6KONIEC";
            default -> "§7VOĽNÝ REŽIM";
        };
        StringBuilder sb = new StringBuilder("§6⚔ ").append(phase);
        if (!ClientWarState.CAPTURES.isEmpty()) {
            sb.append("  §7⚑ §a").append(ClientWarState.points(Team.OLIVA))
                    .append("§7-§6").append(ClientWarState.points(Team.PIESOK));
        }
        int mine = ClientWarState.TICKETS.getOrDefault(myTeam, 0);
        int foe = ClientWarState.TICKETS.getOrDefault(myTeam.enemy(), 0);
        if (mine > 0 || foe > 0) {
            sb.append("  §7▣ §b").append(mine).append("§7/§c").append(foe);
        }
        return sb.toString();
    }

    // Strength of each non-empty control group, coloured by how healthy it is.
    private static String groupLine() {
        StringBuilder sb = new StringBuilder("§7Skupiny: ");
        boolean any = false;
        for (int grp = 1; grp <= ClientWarState.MAX_GROUP; grp++) {
            List<ClientWarState.MapSoldier> members = ClientWarState.group(grp);
            if (members.isEmpty()) continue;
            float sum = 0;
            int down = 0;
            for (ClientWarState.MapSoldier s : members) {
                sum += s.healthFraction();
                if (s.isDowned()) down++;
            }
            float avg = sum / members.size();
            String color = avg > 0.6f ? "§a" : (avg > 0.3f ? "§e" : "§c");
            sb.append("§f").append(grp).append(":").append(color).append(members.size());
            if (down > 0) sb.append("§e↓").append(down);
            sb.append(" ");
            any = true;
        }
        return any ? sb.toString() : "";
    }
}

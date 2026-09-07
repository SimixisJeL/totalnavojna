package sk.totalnavojna.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.client.ClientSelection;
import sk.totalnavojna.client.ClientTeamCache;
import sk.totalnavojna.client.ClientWarState;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketAssignGroup;
import sk.totalnavojna.network.PacketIssueOrder;
import sk.totalnavojna.network.PacketRequestWarState;
import sk.totalnavojna.orders.OrderType;

import java.util.*;

// Commander screen, laid out the way a strategy game does it, because that is the interface people already know:
//   left   - command card for the active branch (infantry / drones / vehicles), every order with a hotkey
//   middle - the roster: each unit with health, status and distance; click / ctrl+click / shift+click to select
//   right  - control groups 1-9 with strength and current order; click to select, ctrl+number to assign
//
// The roster is the WHOLE army, synced from the server, not just what happens to be near the player - so a unit a
// thousand blocks away can still be reviewed and commanded from here.
// Ported originally from Simple-Enemy-Mod-Public (GPL-3.0) by NekoYuni; little of that original remains.
public class CommanderMenuScreen extends Screen {

    private static final int PAD = 8;
    private static final int ROW_H = 12;
    // preferred widths; the real ones are computed in layout() so the window never runs off the monitor
    private static final int CARD_W = 158;
    private static final int ROSTER_W = 268;
    private static final int GROUPS_W = 128;
    private static final int WIN_H = 250;

    private int cardW = CARD_W;
    private int rosterW = ROSTER_W;
    private int groupsW = GROUPS_W;
    private int cardScroll = 0;
    // remembered from the last render so clicks land on the same pixels the user saw
    private int assignStripY = -1;
    private int assignCell = 12;
    private int clearButtonY = -1;

    private static final int BG = 0xD00A0E08;
    private static final int PANEL = 0xE0141A12;
    private static final int LINE = 0xFF2E3A26;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HOVER = 0xFFFFD24A;
    private static final int SEL = 0xFF6BE06B;
    private static final int HEAD = 0xFFFFA500;
    private static final int SECTION = 0xFF9AC0FF;

    // {label, action, hotkey}   action: section | order:X | pos:X | target:X | reset
    private static final String[][] INFANTRY = {
            {"— Pohyb —", "section", ""},
            {"Drž pozíciu", "order:HOLD_POSITION", "H"},
            {"Nasleduj ma", "order:FOLLOW_COMMANDER", "F"},
            {"Presun na…", "pos:MOVE_TO_POSITION", "M"},
            {"Formácia: Klin", "order:FORM_WEDGE", "K"},
            {"Formácia: Kolóna", "order:FORM_COLUMN", "L"},
            {"Hliadka po trase", "order:PATROL_PATH", "P"},
            {"— Boj —", "section", ""},
            {"Zaútoč na cieľ", "target:ATTACK_THAT_TARGET", "T"},
            {"Voľná paľba", "order:FREE_FIRE", "V"},
            {"Nestrieľať", "order:CEASE_FIRE", "C"},
            {"⚔ Útok na NEXUS", "order:ATTACK_CORE", "N"},
            {"⛨ Bráň NEXUS", "order:DEFEND_CORE", "B"},
            {"Ústup k NEXUSu", "order:RETREAT_TO_NEXUS", "R"},
            {"— Podpora —", "section", ""},
            {"Doliečiť sa", "order:FULL_HEAL", "G"},
            {"Doplň zásoby", "order:RESUPPLY", "Z"},
            {"Nastúp do vozidla", "target:BOARD_VEHICLE", "E"},
            {"Vystúp z vozidla", "order:DISMOUNT_VEHICLE", "X"},
            {"⟳ Reset rozkazov", "reset", "Q"},
    };

    private static final String[][] DRONES = {
            {"— Dron —", "section", ""},
            {"Útok na cieľ", "target:DRONE_STRIKE_TARGET", "T"},
            {"Útok na pozíciu", "pos:DRONE_STRIKE_POSITION", "M"},
            {"Prieskum pozície", "pos:DRONE_SCOUT", "S"},
            {"Návrat a pristátie", "order:DRONE_RECALL", "R"},
            {"Nálety ZAPNUTÉ", "order:FREE_FIRE", "V"},
            {"Nálety VYPNUTÉ", "order:CEASE_FIRE", "C"},
            {"— Operátor —", "section", ""},
            {"Drž pozíciu", "order:HOLD_POSITION", "H"},
            {"Nasleduj ma", "order:FOLLOW_COMMANDER", "F"},
            {"Presun na…", "pos:MOVE_TO_POSITION", "G"},
            {"Ústup k NEXUSu", "order:RETREAT_TO_NEXUS", "B"},
            {"Doplň drony", "order:RESUPPLY", "Z"},
            {"Nastúp do vozidla", "target:BOARD_VEHICLE", "E"},
            {"⟳ Reset rozkazov", "reset", "Q"},
    };

    private static final String[][] VEHICLES = {
            {"— Posádka —", "section", ""},
            {"Nastúp do vozidla", "target:BOARD_VEHICLE", "E"},
            {"Vystúp z vozidla", "order:DISMOUNT_VEHICLE", "X"},
            {"— Jazda —", "section", ""},
            {"Presun na…", "pos:MOVE_TO_POSITION", "M"},
            {"Presun + vysadiť", "pos:MOVE_AND_UNLOAD", "U"},
            {"Nasleduj ma", "order:FOLLOW_COMMANDER", "F"},
            {"Drž pozíciu", "order:HOLD_POSITION", "H"},
            {"Hliadka po trase", "order:PATROL_PATH", "P"},
            {"Ústup k NEXUSu", "order:RETREAT_TO_NEXUS", "R"},
            {"— Boj —", "section", ""},
            {"Zaútoč na cieľ", "target:ATTACK_THAT_TARGET", "T"},
            {"⚔ Útok na NEXUS", "order:ATTACK_CORE", "N"},
            {"⛨ Bráň NEXUS", "order:DEFEND_CORE", "B"},
            {"Vežička: voľná paľba", "order:FREE_FIRE", "V"},
            {"Vežička: nestrieľať", "order:CEASE_FIRE", "C"},
            {"⟳ Reset rozkazov", "reset", "Q"},
    };

    private static final String[] TAB_NAMES = {"PECHOTA", "DRONY", "VOZIDLÁ"};

    private static int tab = 0;
    // selection and group filter survive closing the screen, the way a control group does in an RTS
    private static final Set<Integer> SELECTED = ClientSelection.IDS;
    private static int groupFilter = -1;      // -1 = all groups
    private int scroll = 0;
    private int lastClickedRow = -1;
    private int syncTimer = 0;

    private final List<ClientWarState.MapSoldier> shown = new ArrayList<>();

    public CommanderMenuScreen() {
        super(Component.literal("Veliteľ"));
    }

    private static String[][] card() {
        return tab == 1 ? DRONES : (tab == 2 ? VEHICLES : INFANTRY);
    }

    @Override
    protected void init() {
        super.init();
        ModNetworking.sendToServer(new PacketRequestWarState());
    }

    @Override
    public void tick() {
        super.tick();
        if (--this.syncTimer <= 0) {
            this.syncTimer = 20;
            ModNetworking.sendToServer(new PacketRequestWarState());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== data =====

    private void rebuild() {
        this.shown.clear();
        for (ClientWarState.MapSoldier s : ClientWarState.SOLDIERS) {
            if (groupFilter >= 0 && s.group != groupFilter) continue;
            if (tab == 1 && s.role != Role.DRONE_OPERATOR.ordinal()) continue;
            if (tab == 2 && !s.inVehicle) continue;
            this.shown.add(s);
        }
        this.shown.sort(Comparator
                .comparingInt((ClientWarState.MapSoldier s) -> s.group == 0 ? 99 : s.group)
                .thenComparingInt(s -> s.role)
                .thenComparing(s -> s.name));
        // Men who used up their lives stay in the table, greyed out and unselectable - the commander should
        // see the cost of the war in the same list he gives orders from.
        if (tab == 0) {
            for (String name : ClientWarState.FALLEN) {
                ClientWarState.MapSoldier ghost = new ClientWarState.MapSoldier();
                ghost.id = -1;
                ghost.name = name;
                ghost.state = 2;
                ghost.hp = 0;
                ghost.maxHp = 1;
                this.shown.add(ghost);
            }
        }
        ClientSelection.retainAlive();
    }

    private static String roleLetter(int role) {
        Role r = role >= 0 && role < Role.VALUES.length ? Role.VALUES[role] : Role.ATTACKER;
        return switch (r) {
            case ATTACKER -> "§cÚ";
            case DEFENDER -> "§9O";
            case MEDIC -> "§fM";
            case SNIPER -> "§8S";
            case PATHFINDER -> "§6P";
            case DRONE_OPERATOR -> "§bD";
        };
    }

    private static String orderShort(int ordinal) {
        OrderType[] v = OrderType.values();
        OrderType o = ordinal >= 0 && ordinal < v.length ? v[ordinal] : OrderType.NONE;
        return switch (o) {
            case NONE -> "voľný";
            case HOLD_POSITION -> "drží";
            case FOLLOW_COMMANDER -> "nasleduje";
            case MOVE_TO_POSITION, MOVE_AND_UNLOAD -> "presun";
            case MOVE_ALONG_PATH -> "trasa";
            case PATROL_PATH -> "hliadka";
            case FORM_WEDGE -> "klin";
            case FORM_COLUMN -> "kolóna";
            case CEASE_FIRE -> "nestrieľa";
            case FREE_FIRE -> "voľná paľba";
            case ATTACK_THAT_TARGET -> "útok na cieľ";
            case FULL_HEAL -> "lieči sa";
            case ATTACK_CORE -> "útok NEXUS";
            case DEFEND_CORE -> "bráni NEXUS";
            case BOARD_VEHICLE -> "nastupuje";
            case DISMOUNT_VEHICLE -> "vystupuje";
            case RESUPPLY -> "zásoby";
            case RETREAT_TO_NEXUS -> "ústup";
            case DRONE_STRIKE_TARGET, DRONE_STRIKE_POSITION -> "nálet";
            case DRONE_SCOUT -> "prieskum";
            case DRONE_RECALL -> "návrat dronu";
        };
    }

    // Shrink the flexible panes until the window fits, roster first, then groups, then the order card.
    // Before this, on a wide-but-not-huge window the right pane simply ran off the edge of the screen.
    private void layout() {
        int available = Math.max(240, this.width - 8);
        int want = CARD_W + ROSTER_W + GROUPS_W + PAD * 4;
        if (want <= available) {
            this.cardW = CARD_W;
            this.rosterW = ROSTER_W;
            this.groupsW = GROUPS_W;
            return;
        }
        int overflow = want - available;
        this.rosterW = Math.max(130, ROSTER_W - overflow);
        overflow -= ROSTER_W - this.rosterW;
        this.groupsW = Math.max(104, GROUPS_W - Math.max(0, overflow));
        overflow -= GROUPS_W - this.groupsW;
        this.cardW = Math.max(112, CARD_W - Math.max(0, overflow));
    }

    private int totalW() {
        return this.cardW + this.rosterW + this.groupsW + PAD * 4;
    }

    private int winX() {
        return Math.max(4, (this.width - totalW()) / 2);
    }

    private int winY() {
        return Math.max(4, (this.height - WIN_H) / 2);
    }

    private int winH() {
        return Math.min(this.height - 8, WIN_H);
    }

    private int cardRows(int paneH) {
        return Math.max(1, (paneH - 15) / ROW_H);
    }

    // ===== render =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rebuild();
        layout();

        int totalW = totalW();
        int x0 = winX();
        int y0 = winY();
        int h = winH();

        g.fill(x0, y0, x0 + totalW, y0 + h, BG);
        g.fill(x0, y0, x0 + totalW, y0 + 1, LINE);
        g.fill(x0, y0 + h - 1, x0 + totalW, y0 + h, LINE);

        Team myTeam = ClientTeamCache.get();
        g.drawString(this.font, "§6⚔ VELITEĽSTVO", x0 + PAD, y0 + 5, HEAD, false);
        if (myTeam == null) {
            g.drawString(this.font, "§cNajprv /totalnavojna join oliva|piesok", x0 + PAD, y0 + 22, 0xFFFF5555, false);
            return;
        }
        String hdr = "§fTím §e" + myTeam.getDisplayName().toUpperCase()
                + "  §7|  §fŽivých §a" + ClientWarState.SOLDIERS.size()
                + "  §7|  §fStraty §c" + ClientWarState.DEATHS.getOrDefault(myTeam, 0)
                + "  §7|  §fVybraných §b" + SELECTED.size();
        g.drawString(this.font, hdr, x0 + 118, y0 + 5, TEXT, false);

        int tabY = y0 + 17;
        int tx = x0 + PAD;
        for (int t = 0; t < TAB_NAMES.length; t++) {
            boolean active = t == tab;
            boolean hov = in(mouseX, mouseY, tx, tabY, 62, 13);
            g.fill(tx, tabY, tx + 62, tabY + 13, active ? 0xFF244A24 : (hov ? 0xFF3A3A20 : 0xFF1E1E1E));
            g.drawCenteredString(this.font, TAB_NAMES[t], tx + 31, tabY + 3, active ? SEL : TEXT);
            tx += 65;
        }
        g.drawString(this.font, "§8Tab = ďalšia záložka", tx + 6, tabY + 3, DIM, false);

        int paneY = tabY + 17;
        int paneH = y0 + h - paneY - 16;

        renderCard(g, mouseX, mouseY, x0 + PAD, paneY, paneH);
        renderRoster(g, mouseX, mouseY, x0 + PAD * 2 + this.cardW, paneY, paneH);
        renderGroups(g, mouseX, mouseY, x0 + PAD * 3 + this.cardW + this.rosterW, paneY, paneH);

        g.drawString(this.font, matchLine(myTeam), x0 + PAD, y0 + h - 21, TEXT, false);
        g.drawString(this.font, "§8Klik = vyber · Ctrl/Shift klik = pridaj · A = všetci · 1-9 = skupina · koliesko = rolovanie",
                x0 + PAD, y0 + h - 11, DIM, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    // Where the match stands: phase, capture points, tickets, permanent losses.
    private static String matchLine(Team myTeam) {
        String match = switch (ClientWarState.phase) {
            case 1 -> "§ePRÍPRAVA" + (ClientWarState.phaseSecondsLeft > 0 ? " " + ClientWarState.phaseSecondsLeft + "s" : "");
            case 2 -> "§cBOJ";
            case 3 -> "§6KONIEC ZÁPASU";
            default -> "§7VOĽNÝ REŽIM §8(/totalnavojna start)";
        };
        if (!ClientWarState.CAPTURES.isEmpty()) {
            match += "  §7|  ⚑ §a" + ClientWarState.points(Team.OLIVA) + "§7-§6" + ClientWarState.points(Team.PIESOK);
        }
        int mine = ClientWarState.TICKETS.getOrDefault(myTeam, 0);
        int foe = ClientWarState.TICKETS.getOrDefault(myTeam.enemy(), 0);
        if (mine > 0 || foe > 0) {
            match += "  §7|  ▣ tikety §b" + mine + "§7/§c" + foe;
        }
        if (!ClientWarState.FALLEN.isEmpty()) {
            match += "  §7|  ✝ §8" + ClientWarState.FALLEN.size() + " nastálo padlých";
        }
        return match;
    }

    private void renderCard(GuiGraphics g, int mx, int my, int x, int y, int h) {
        g.fill(x, y, x + this.cardW, y + h, PANEL);
        String[][] card = card();
        int rows = cardRows(h);
        this.cardScroll = Math.max(0, Math.min(this.cardScroll, Math.max(0, card.length - rows)));

        g.drawString(this.font, "ROZKAZY", x + 4, y + 3, HEAD, false);
        if (card.length > rows) {
            g.drawString(this.font, "§8" + (this.cardScroll + 1) + "-"
                    + Math.min(card.length, this.cardScroll + rows) + "/" + card.length, x + this.cardW - 42, y + 3, DIM, false);
        }

        int ry = y + 15;
        boolean off = SELECTED.isEmpty();
        for (int i = 0; i < rows; i++) {
            int idx = i + this.cardScroll;
            if (idx >= card.length) break;
            String[] o = card[idx];
            if (o[1].equals("section")) {
                g.drawString(this.font, o[0], x + 4, ry + 2, SECTION, false);
                ry += ROW_H;
                continue;
            }
            boolean hov = in(mx, my, x + 2, ry, this.cardW - 4, ROW_H);
            if (hov) g.fill(x + 2, ry, x + this.cardW - 2, ry + ROW_H, 0x40FFD24A);
            boolean always = o[1].equals("reset");
            String label = o[0];
            int room = this.cardW - (o[2].isEmpty() ? 12 : 26);
            if (this.font.width(label) > room) label = this.font.plainSubstrByWidth(label, room - 4) + "…";
            g.drawString(this.font, label, x + 6, ry + 2, (off && !always) ? DIM : (hov ? HOVER : TEXT), false);
            if (!o[2].isEmpty()) g.drawString(this.font, "§8[" + o[2] + "]", x + this.cardW - 20, ry + 2, DIM, false);
            ry += ROW_H;
        }

        if (card.length > rows) {
            int trackY = y + 15;
            int trackH = h - 17;
            int barH = Math.max(10, trackH * rows / card.length);
            int barY = trackY + (trackH - barH) * this.cardScroll / Math.max(1, card.length - rows);
            g.fill(x + this.cardW - 3, trackY, x + this.cardW - 1, trackY + trackH, 0xFF202020);
            g.fill(x + this.cardW - 3, barY, x + this.cardW - 1, barY + barH, 0xFF6BE06B);
        }
    }

    private void renderRoster(GuiGraphics g, int mx, int my, int x, int y, int h) {
        g.fill(x, y, x + this.rosterW, y + h, PANEL);
        String title = "JEDNOTKY (" + this.shown.size() + ")"
                + (groupFilter >= 0 ? "  §7" + (groupFilter == 0 ? "bez skupiny" : "skupina " + groupFilter) : "");
        g.drawString(this.font, title, x + 4, y + 3, HEAD, false);

        int listY = y + 15;
        int rows = Math.max(1, (h - 18) / ROW_H);
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.shown.size() - rows)));

        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < rows; i++) {
            int idx = i + this.scroll;
            if (idx >= this.shown.size()) break;
            ClientWarState.MapSoldier s = this.shown.get(idx);
            int ry = listY + i * ROW_H;
            if (s.id < 0) {
                String gone = s.name.length() > 20 ? s.name.substring(0, 20) : s.name;
                g.drawString(this.font, "§8✝ " + gone + " §8— padol nastálo", x + 5, ry + 2, DIM, false);
                continue;
            }
            boolean sel = SELECTED.contains(s.id);
            boolean hov = in(mx, my, x + 2, ry, this.rosterW - 4, ROW_H);
            if (sel) g.fill(x + 2, ry, x + this.rosterW - 2, ry + ROW_H, 0x4033AA33);
            else if (hov) g.fill(x + 2, ry, x + this.rosterW - 2, ry + ROW_H, 0x30FFFFFF);

            String grp = s.group > 0 ? "§e" + s.group : "§8-";
            String name = s.name.length() > 15 ? s.name.substring(0, 15) : s.name;
            String star = s.leader ? "§6★" : " ";
            g.drawString(this.font, grp + star + roleLetter(s.role) + " §r" + name, x + 5, ry + 2, sel ? SEL : TEXT, false);

            int barX = x + 148;
            int barW = 38;
            float frac = s.healthFraction();
            int hpColor = s.isDowned() ? 0xFFFFC107 : (frac > 0.6f ? 0xFF4CAF50 : frac > 0.3f ? 0xFFFFC107 : 0xFFF44336);
            g.fill(barX, ry + 3, barX + barW, ry + 9, 0xFF202020);
            g.fill(barX, ry + 3, barX + (int) (barW * frac), ry + 9, hpColor);

            String status = s.isDowned() ? "§eDOWN" : (s.inVehicle ? "§bvozidlo" : (s.flyingDrone ? "§dlietá" : "§7" + orderShort(s.order)));
            g.drawString(this.font, status, barX + barW + 5, ry + 2, TEXT, false);

            int dist = mc.player == null ? 0 : (int) Math.sqrt(mc.player.distanceToSqr(s.x, s.y, s.z));
            g.drawString(this.font, "§8" + dist + "m", x + this.rosterW - 32, ry + 2, DIM, false);
        }

        if (this.shown.size() > rows) {
            int trackH = h - 18;
            int barH = Math.max(8, trackH * rows / this.shown.size());
            int barY = listY + (trackH - barH) * this.scroll / Math.max(1, this.shown.size() - rows);
            g.fill(x + this.rosterW - 3, listY, x + this.rosterW - 1, listY + trackH, 0xFF202020);
            g.fill(x + this.rosterW - 3, barY, x + this.rosterW - 1, barY + barH, 0xFF6BE06B);
        }
    }

    private void renderGroups(GuiGraphics g, int mx, int my, int x, int y, int h) {
        g.fill(x, y, x + this.groupsW, y + h, PANEL);
        g.drawString(this.font, "SKUPINY", x + 4, y + 3, HEAD, false);

        int ry = y + 15;
        for (int grp = 0; grp <= ClientWarState.MAX_GROUP; grp++) {
            List<ClientWarState.MapSoldier> members = ClientWarState.group(grp);
            boolean active = groupFilter == grp;
            boolean hov = in(mx, my, x + 2, ry, this.groupsW - 4, ROW_H);
            if (active) g.fill(x + 2, ry, x + this.groupsW - 2, ry + ROW_H, 0x4033AA33);
            else if (hov) g.fill(x + 2, ry, x + this.groupsW - 2, ry + ROW_H, 0x30FFFFFF);

            g.drawString(this.font, grp == 0 ? "§8—" : "§e" + grp, x + 6, ry + 2, TEXT, false);

            if (members.isEmpty()) {
                g.drawString(this.font, "§8prázdna", x + 20, ry + 2, DIM, false);
            } else {
                float sum = 0;
                Map<Integer, Integer> orders = new HashMap<>();
                int down = 0;
                for (ClientWarState.MapSoldier s : members) {
                    sum += s.healthFraction();
                    orders.merge(s.order, 1, Integer::sum);
                    if (s.isDowned()) down++;
                }
                float avg = sum / members.size();
                g.drawString(this.font, "§f" + members.size() + (down > 0 ? " §e↓" + down : ""), x + 20, ry + 2, TEXT, false);
                int barX = x + 48;
                int barW = 20;
                g.fill(barX, ry + 3, barX + barW, ry + 9, 0xFF202020);
                g.fill(barX, ry + 3, barX + (int) (barW * avg), ry + 9,
                        avg > 0.6f ? 0xFF4CAF50 : avg > 0.3f ? 0xFFFFC107 : 0xFFF44336);
                int top = orders.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
                String os = orderShort(top);
                if (os.length() > 9) os = os.substring(0, 9);
                g.drawString(this.font, "§7" + os, barX + barW + 4, ry + 2, DIM, false);
            }
            ry += ROW_H;
        }

        // Moving a man between groups used to be a hidden Ctrl+number. Now it is a row of buttons that
        // says what it does, and it works on whatever is selected - one soldier or the whole roster.
        ry += 5;
        boolean canAssign = !SELECTED.isEmpty();
        g.drawString(this.font, canAssign
                        ? "§ePRESUNÚŤ VÝBER (" + SELECTED.size() + ") DO:"
                        : "§8PRESUNÚŤ VÝBER DO:",
                x + 4, ry, canAssign ? HEAD : DIM, false);
        ry += 10;
        int cell = (this.groupsW - 8) / 10;
        for (int grp = 0; grp <= ClientWarState.MAX_GROUP; grp++) {
            int bx = x + 4 + grp * cell;
            boolean hovBtn = canAssign && in(mx, my, bx, ry, cell - 1, 13);
            g.fill(bx, ry, bx + cell - 1, ry + 13, hovBtn ? 0xFF3F7A2E : (canAssign ? 0xFF23281E : 0xFF1A1A1A));
            g.drawCenteredString(this.font, grp == 0 ? "—" : String.valueOf(grp),
                    bx + (cell - 1) / 2, ry + 3, canAssign ? TEXT : DIM);
        }
        this.assignStripY = ry;
        this.assignCell = cell;
        ry += 17;

        boolean hov = in(mx, my, x + 2, ry, this.groupsW - 4, ROW_H);
        g.fill(x + 2, ry, x + this.groupsW - 2, ry + ROW_H, hov ? 0xFF3A3A20 : 0xFF1E1E1E);
        g.drawCenteredString(this.font, "Zrušiť výber", x + this.groupsW / 2, ry + 2, SELECTED.isEmpty() ? DIM : TEXT);
        this.clearButtonY = ry;
        ry += ROW_H + 3;
        g.drawString(this.font, "§8— = bez skupiny", x + 4, ry, DIM, false);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ===== input =====

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        layout();
        int x0 = winX();
        int step = (int) Math.signum(delta);
        // the order card has its own scroll; anywhere else the wheel moves the roster
        if (mx < x0 + PAD + this.cardW) {
            this.cardScroll = Math.max(0, this.cardScroll - step * 2);
        } else {
            this.scroll = Math.max(0, this.scroll - step * 3);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int x0 = winX();
        int y0 = winY();
        int h = winH();
        int tabY = y0 + 17;

        int tx = x0 + PAD;
        for (int t = 0; t < TAB_NAMES.length; t++) {
            if (in(mx, my, tx, tabY, 62, 13)) {
                tab = t;
                this.scroll = 0;
                click();
                return true;
            }
            tx += 65;
        }

        int paneY = tabY + 17;
        int paneH = y0 + h - paneY - 16;

        int cardX = x0 + PAD;
        int ry = paneY + 15;
        String[][] card = card();
        int cardRows = cardRows(paneH);
        for (int i = 0; i < cardRows; i++) {
            int idx = i + this.cardScroll;
            if (idx >= card.length) break;
            String[] o = card[idx];
            if (!o[1].equals("section") && in(mx, my, cardX + 2, ry, this.cardW - 4, ROW_H)) {
                doAction(o[1]);
                return true;
            }
            ry += ROW_H;
        }

        int rosterX = x0 + PAD * 2 + this.cardW;
        int listY = paneY + 15;
        int rows = Math.max(1, (paneH - 18) / ROW_H);
        for (int i = 0; i < rows; i++) {
            int idx = i + this.scroll;
            if (idx >= this.shown.size()) break;
            if (in(mx, my, rosterX + 2, listY + i * ROW_H, this.rosterW - 4, ROW_H)) {
                clickRow(idx);
                return true;
            }
        }

        int gx = x0 + PAD * 3 + this.cardW + this.rosterW;
        int gy = paneY + 15;
        for (int grp = 0; grp <= ClientWarState.MAX_GROUP; grp++) {
            if (in(mx, my, gx + 2, gy, this.groupsW - 4, ROW_H)) {
                if (hasCtrl()) assignGroup(grp);
                else selectGroup(grp);
                return true;
            }
            gy += ROW_H;
        }
        if (this.assignStripY >= 0 && !SELECTED.isEmpty()) {
            for (int grp = 0; grp <= ClientWarState.MAX_GROUP; grp++) {
                int bx = gx + 4 + grp * this.assignCell;
                if (in(mx, my, bx, this.assignStripY, this.assignCell - 1, 13)) {
                    assignGroup(grp);
                    return true;
                }
            }
        }
        if (this.clearButtonY >= 0 && in(mx, my, gx + 2, this.clearButtonY, this.groupsW - 4, ROW_H)) {
            SELECTED.clear();
            groupFilter = -1;
            click();
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    private void clickRow(int idx) {
        int id = this.shown.get(idx).id;
        if (id < 0) return;   // a fallen name is a headstone, not a unit
        if (hasShift() && this.lastClickedRow >= 0) {
            int a = Math.min(this.lastClickedRow, idx);
            int b = Math.max(this.lastClickedRow, idx);
            for (int i = a; i <= b && i < this.shown.size(); i++) {
                int rowId = this.shown.get(i).id;
                if (rowId >= 0) SELECTED.add(rowId);
            }
        } else if (hasCtrl()) {
            if (!SELECTED.remove(id)) SELECTED.add(id);
        } else {
            SELECTED.clear();
            SELECTED.add(id);
        }
        this.lastClickedRow = idx;
        click();
    }

    private void selectGroup(int grp) {
        groupFilter = groupFilter == grp ? -1 : grp;
        SELECTED.clear();
        for (ClientWarState.MapSoldier s : ClientWarState.group(grp)) SELECTED.add(s.id);
        this.scroll = 0;
        click();
    }

    private void assignGroup(int grp) {
        if (SELECTED.isEmpty()) {
            msg("§cNajprv vyber jednotky (klik v zozname, alebo A).");
            return;
        }
        int moved = SELECTED.size();
        for (int id : SELECTED) ModNetworking.sendToServer(new PacketAssignGroup(id, grp));
        // the server will send a fresh roster; ask for it now so the change shows up straight away
        ModNetworking.sendToServer(new PacketRequestWarState());
        msg(grp == 0
                ? "§e" + moved + " jednotiek vyradených zo skupín."
                : "§e" + moved + " jednotiek presunutých do skupiny " + grp + ".");
        click();
    }

    private static boolean hasShift() {
        long w = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean hasCtrl() {
        long w = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) {
            tab = (tab + 1) % TAB_NAMES.length;
            this.scroll = 0;
            click();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            int grp = key - GLFW.GLFW_KEY_0;
            if (hasCtrl()) assignGroup(grp);
            else selectGroup(grp);
            return true;
        }
        if (key == GLFW.GLFW_KEY_A) {
            SELECTED.clear();
            for (ClientWarState.MapSoldier s : this.shown) {
                if (s.id >= 0) SELECTED.add(s.id);
            }
            click();
            return true;
        }
        String pressed = String.valueOf((char) Character.toUpperCase(key));
        for (String[] o : card()) {
            if (!o[2].isEmpty() && o[2].equals(pressed)) {
                doAction(o[1]);
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    // ===== actions =====

    private void doAction(String action) {
        if (action.equals("reset")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.connection.sendCommand("totalnavojna resetorders");
            msg("§aRozkazy zresetované.");
            this.onClose();
            return;
        }
        if (SELECTED.isEmpty()) {
            msg("§cNajprv vyber jednotky (klik v zozname, číslo skupiny alebo A).");
            return;
        }
        String[] parts = action.split(":");
        OrderType order = OrderType.valueOf(parts[1]);
        switch (parts[0]) {
            case "order" -> issue(order);
            case "pos" -> {
                CommanderOverlayRenderer.isSelectingPosition = true;
                CommanderOverlayRenderer.pendingPositionOrder = order;
                CommanderOverlayRenderer.selectedUnitsSnapshot = new HashSet<>(SELECTED);
                this.onClose();
                msg("§aKlikni na cieľovú pozíciu… (pravý klik = zrušiť)");
            }
            case "target" -> {
                CommanderOverlayRenderer.isSelectingTarget = true;
                CommanderOverlayRenderer.pendingTargetOrder = order;
                CommanderOverlayRenderer.selectedUnitsSnapshot = new HashSet<>(SELECTED);
                this.onClose();
                msg(order == OrderType.BOARD_VEHICLE
                        ? "§aKlikni na vozidlo… prvý nastupuje ako šofér (pravý klik = zrušiť)"
                        : "§aKlikni na cieľ — nepriateľ alebo vozidlo (pravý klik = zrušiť)");
            }
            default -> {
            }
        }
    }

    private void issue(OrderType order) {
        List<Integer> ids = new ArrayList<>(SELECTED);
        ids.sort(Comparator.reverseOrder());
        markOrderTime();
        for (int i = 0; i < ids.size(); i++) {
            ModNetworking.sendToServer(new PacketIssueOrder(ids.get(i), order, Vec3.ZERO, i, -1));
        }
        msg("§eRozkaz odoslaný " + ids.size() + " jednotkám.");
        this.onClose();
    }

    private static long lastOrderTime = 0;

    public static boolean shouldSuppressFire() {
        return System.currentTimeMillis() - lastOrderTime < 200;
    }

    public static void markOrderTime() {
        lastOrderTime = System.currentTimeMillis();
    }

    private void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}

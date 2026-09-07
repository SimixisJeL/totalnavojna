package sk.totalnavojna.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.client.ClientWarState;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketRecruit;
import sk.totalnavojna.network.PacketRequestWarState;

// Recruitment menu, opened by right-clicking your own SPAWNER block.
// Pick a role, pick a control group, pick how many, and they walk out of the base - to the group's
// rally point if the commander has set one.
//
// With WarEconomy running the Spawner stops being a free spawn button: it only hands out soldiers the
// commander has already bought at the Communication Station, and each role shows how many are waiting.
public class RecruitScreen extends Screen {

    private static final int PANEL = 0xE0141A12;
    private static final int BG = 0xD00A0E08;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEAD = 0xFFFFA500;
    private static final int SEL = 0xFF6BE06B;

    private static final int W = 300;
    private static final int H = 186;
    private static final int ROW = 14;
    private static final int[] COUNTS = {1, 3, 5, 10};

    private static final String[] ROLE_NOTE = {
            "útočí na NEXUS, granátomet",
            "drží vlastný NEXUS",
            "oživuje a lieči",
            "presná paľba z diaľky",
            "kope cestu, podpora",
            "vypúšťa kamikadze drony",
    };

    private final BlockPos pos;
    private final Team team;
    private final int[] available;      // per role, -1 = unlimited (no economy)
    private int role = 0;
    private int group = 1;
    private int count = 3;

    private RecruitScreen(BlockPos pos, Team team, int[] available) {
        super(Component.literal("Nábor"));
        this.pos = pos;
        this.team = team;
        this.available = available;
    }

    public static void open(BlockPos pos, Team team, int[] available) {
        Minecraft.getInstance().setScreen(new RecruitScreen(pos, team, available));
    }

    private int availableOf(int roleOrdinal) {
        return roleOrdinal >= 0 && roleOrdinal < this.available.length ? this.available[roleOrdinal] : -1;
    }

    private boolean gated() {
        for (int n : this.available) {
            if (n >= 0) return true;
        }
        return false;
    }

    // How many of the selected role may actually be ordered out right now.
    private int maxCount() {
        int limit = availableOf(this.role);
        return limit < 0 ? 64 : limit;
    }

    @Override
    protected void init() {
        super.init();
        ModNetworking.sendToServer(new PacketRequestWarState());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int x0() {
        return (this.width - W) / 2;
    }

    private int y0() {
        return (this.height - H) / 2;
    }

    private int aliveOfRole(int roleOrdinal) {
        int n = 0;
        for (ClientWarState.MapSoldier s : ClientWarState.SOLDIERS) {
            if (s.role == roleOrdinal) n++;
        }
        return n;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int x = x0();
        int y = y0();
        g.fill(x, y, x + W, y + H, BG);

        g.drawString(this.font, "§6⚑ NÁBOR — základňa §e" + this.team.getDisplayName().toUpperCase(), x + 8, y + 6, HEAD, false);
        g.drawString(this.font, "§7Živých v armáde: §f" + ClientWarState.SOLDIERS.size(), x + W - 118, y + 6, TEXT, false);

        // roles
        int rx = x + 8;
        int ry = y + 22;
        g.fill(rx, ry, rx + 168, ry + Role.VALUES.length * ROW + 4, PANEL);
        ry += 2;
        for (int i = 0; i < Role.VALUES.length; i++) {
            Role r = Role.VALUES[i];
            boolean active = this.role == i;
            boolean hover = in(mouseX, mouseY, rx + 2, ry, 164, ROW);
            if (active) g.fill(rx + 2, ry, rx + 166, ry + ROW, 0x4033AA33);
            else if (hover) g.fill(rx + 2, ry, rx + 166, ry + ROW, 0x30FFFFFF);
            int stock = availableOf(i);
            boolean sold_out = stock == 0;
            g.drawString(this.font, (active ? "§a▶ " : (sold_out ? "§8• " : "§7• ")) + r.getTypeName().getString(),
                    rx + 5, ry + 3, sold_out ? DIM : (active ? SEL : TEXT), false);
            String right = stock < 0 ? "§8×" + aliveOfRole(i) : (stock > 0 ? "§a" + stock + " ks" : "§8—");
            g.drawString(this.font, right, rx + 138, ry + 3, DIM, false);
            ry += ROW;
        }
        g.drawString(this.font, "§8" + ROLE_NOTE[Math.min(this.role, ROLE_NOTE.length - 1)], rx + 4, ry + 6, DIM, false);

        // groups
        int gx = x + 186;
        int gy = y + 22;
        g.drawString(this.font, "SKUPINA", gx, gy, HEAD, false);
        gy += 11;
        for (int i = 0; i <= ClientWarState.MAX_GROUP; i++) {
            int col = i % 5;
            int row = i / 5;
            int bx = gx + col * 21;
            int by = gy + row * 15;
            boolean active = this.group == i;
            boolean hover = in(mouseX, mouseY, bx, by, 19, 13);
            g.fill(bx, by, bx + 19, by + 13, active ? 0xC0006600 : (hover ? 0xC0555500 : 0xC0333333));
            g.drawCenteredString(this.font, i == 0 ? "—" : String.valueOf(i), bx + 9, by + 3, active ? SEL : TEXT);
        }
        gy += 15 * 2 + 8;

        g.drawString(this.font, "POČET", gx, gy, HEAD, false);
        gy += 11;
        for (int i = 0; i < COUNTS.length; i++) {
            int bx = gx + i * 26;
            boolean active = this.count == COUNTS[i];
            boolean hover = in(mouseX, mouseY, bx, gy, 24, 13);
            g.fill(bx, gy, bx + 24, gy + 13, active ? 0xC0006600 : (hover ? 0xC0555500 : 0xC0333333));
            g.drawCenteredString(this.font, String.valueOf(COUNTS[i]), bx + 12, gy + 3, active ? SEL : TEXT);
        }
        gy += 24;

        int can = Math.min(this.count, maxCount());
        boolean possible = can > 0;
        boolean hoverGo = possible && in(mouseX, mouseY, gx, gy, 105, 16);
        g.fill(gx, gy, gx + 105, gy + 16, possible ? (hoverGo ? 0xC0707000 : 0xC0404000) : 0xC0402020);
        g.drawCenteredString(this.font, possible ? "§eVYSLAŤ " + can + "×" : "§cNIE JE OBJEDNANÉ",
                gx + 52, gy + 4, 0xFFFFAA);

        g.drawString(this.font, gated()
                        ? "§8Vojakov objednáva veliteľ na Komunikačnej stanici, tu ich vysielaš do poľa."
                        : "§8Zhromaždisko skupiny nastavíš na taktickej mape (režim Zhromaždisko).",
                x + 8, y + H - 12, DIM, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int x = x0();
        int y = y0();

        int rx = x + 8;
        int ry = y + 24;
        for (int i = 0; i < Role.VALUES.length; i++) {
            if (in(mx, my, rx + 2, ry, 164, ROW)) {
                this.role = i;
                click();
                return true;
            }
            ry += ROW;
        }

        int gx = x + 186;
        int gy = y + 33;
        for (int i = 0; i <= ClientWarState.MAX_GROUP; i++) {
            int bx = gx + (i % 5) * 21;
            int by = gy + (i / 5) * 15;
            if (in(mx, my, bx, by, 19, 13)) {
                this.group = i;
                click();
                return true;
            }
        }
        gy += 15 * 2 + 8 + 11;
        for (int i = 0; i < COUNTS.length; i++) {
            if (in(mx, my, gx + i * 26, gy, 24, 13)) {
                this.count = COUNTS[i];
                click();
                return true;
            }
        }
        gy += 24;
        if (in(mx, my, gx, gy, 105, 16)) {
            int can = Math.min(this.count, maxCount());
            if (can <= 0) {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                return true;
            }
            ModNetworking.sendToServer(new PacketRecruit(this.pos, this.role, this.group, can));
            click();
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}

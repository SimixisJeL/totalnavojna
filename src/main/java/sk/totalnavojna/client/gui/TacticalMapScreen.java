package sk.totalnavojna.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.lwjgl.glfw.GLFW;
import sk.totalnavojna.Role;
import sk.totalnavojna.Team;
import sk.totalnavojna.TotalnaVojna;
import sk.totalnavojna.client.ClientSelection;
import sk.totalnavojna.client.ClientTeamCache;
import sk.totalnavojna.client.ClientWarState;
import sk.totalnavojna.entities.SwatEntity;
import sk.totalnavojna.network.ModNetworking;
import sk.totalnavojna.network.PacketMapOrder;
import sk.totalnavojna.network.PacketRequestWarState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Tactical chunk map: terrain render, unit icons with role and health, box selection, capture points,
// map orders, waypoint routes, rally points and chunk boundaries.
//
// Selection is shared with the commander screen through ClientSelection, so you can drag a box here and then
// hit an order over there. Any order issued from the map goes to the selection when there is one, and falls
// back to the group filter when there is not.
public class TacticalMapScreen extends Screen {

    private static final int TEX_BLOCKS = 512; // 32x32 chunks rendered
    private static final ResourceLocation MAP_TEXTURE = new ResourceLocation(TotalnaVojna.MOD_ID, "dynamic/tactical_map");

    private static final int MODE_VIEW = 0;
    private static final int MODE_SELECT = 1;
    private static final int MODE_MOVE = 2;
    private static final int MODE_PATH = 3;
    private static final int MODE_AREA = 4;
    private static final int MODE_RALLY = 5;
    private static final int MODE_DRONE_STRIKE = 6;
    private static final int MODE_DRONE_SCOUT = 7;
    private static final int MODE_UNLOAD = 8;

    private static final String[] MODES = {
            "Pohľad", "Výber (rám)", "Presun", "Trasa", "Mantinel", "Zhromaždisko",
            "Dron: nálet", "Dron: prieskum", "Vozidlo: vysadiť"
    };
    private static final String[] GROUP_BUTTONS = {"Vš", "—", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

    private static final int ROLE_COLOR_ATTACKER = 0xFFE05555;
    private static final int ROLE_COLOR_DEFENDER = 0xFF5599FF;
    private static final int ROLE_COLOR_MEDIC = 0xFFFFFFFF;
    private static final int ROLE_COLOR_SNIPER = 0xFFBBBBBB;
    private static final int ROLE_COLOR_PATHFINDER = 0xFFFFB040;
    private static final int ROLE_COLOR_DRONE = 0xFF40E0FF;

    private DynamicTexture texture;
    private int originX;
    private int originZ;

    private int mapX;
    private int mapY;
    private int mapPx;

    private int viewBlocks = 256;
    private double viewCenterX = TEX_BLOCKS / 2.0;
    private double viewCenterZ = TEX_BLOCKS / 2.0;

    private int mode = MODE_VIEW;
    private int group = -1; // -1 = all

    private final List<BlockPos> pathPoints = new ArrayList<>();
    private final Set<Long> areaChunks = new HashSet<>();

    // box selection
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;
    private double dragNowX;
    private double dragNowY;

    public TacticalMapScreen() {
        super(Component.literal("Taktická mapa"));
    }

    @Override
    protected void init() {
        super.init();
        this.mapPx = Math.min(this.height - 30, 230);
        this.mapX = this.width - this.mapPx - 12;
        this.mapY = 20;

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            this.originX = (((int) player.getX()) & ~15) - TEX_BLOCKS / 2;
            this.originZ = (((int) player.getZ()) & ~15) - TEX_BLOCKS / 2;
        }

        ModNetworking.sendToServer(new PacketRequestWarState());
        buildTerrain();
        loadAreaForGroup();
    }

    private void loadAreaForGroup() {
        this.areaChunks.clear();
        Set<Long> existing = ClientWarState.MY_AREAS.get(Math.max(0, this.group));
        if (existing != null) this.areaChunks.addAll(existing);
    }

    private void buildTerrain() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        if (this.texture == null) {
            this.texture = new DynamicTexture(TEX_BLOCKS, TEX_BLOCKS, true);
            mc.getTextureManager().register(MAP_TEXTURE, this.texture);
        }

        NativeImage img = this.texture.getPixels();
        if (img == null) return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < TEX_BLOCKS; dz++) {
            for (int dx = 0; dx < TEX_BLOCKS; dx++) {
                int wx = this.originX + dx;
                int wz = this.originZ + dz;
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    img.setPixelRGBA(dx, dz, 0xFF1a1a1a);
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                cursor.set(wx, y, wz);
                BlockState state = level.getBlockState(cursor);
                MapColor color = state.getMapColor(level, cursor);
                int rgb = color.col;
                if (rgb == 0) {
                    img.setPixelRGBA(dx, dz, 0xFF101010);
                    continue;
                }

                int yNorth = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) - 1;
                float shade = y > yNorth ? 1.0f : (y < yNorth ? 0.7f : 0.86f);

                int r = (int) (((rgb >> 16) & 0xFF) * shade);
                int g = (int) (((rgb >> 8) & 0xFF) * shade);
                int b = (int) ((rgb & 0xFF) * shade);
                img.setPixelRGBA(dx, dz, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
        this.texture.upload();
    }

    @Override
    public void removed() {
        if (this.texture != null) {
            Minecraft.getInstance().getTextureManager().release(MAP_TEXTURE);
            this.texture.close();
            this.texture = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== coordinate helpers =====

    private double viewOriginX() {
        return this.viewCenterX - this.viewBlocks / 2.0;
    }

    private double viewOriginZ() {
        return this.viewCenterZ - this.viewBlocks / 2.0;
    }

    private int worldToMapX(double wx) {
        return this.mapX + (int) (((wx - this.originX) - viewOriginX()) * this.mapPx / this.viewBlocks);
    }

    private int worldToMapZ(double wz) {
        return this.mapY + (int) (((wz - this.originZ) - viewOriginZ()) * this.mapPx / this.viewBlocks);
    }

    private int mapToWorldX(double mx) {
        return this.originX + (int) (viewOriginX() + (mx - this.mapX) * this.viewBlocks / this.mapPx);
    }

    private int mapToWorldZ(double my) {
        return this.originZ + (int) (viewOriginZ() + (my - this.mapY) * this.viewBlocks / this.mapPx);
    }

    private void clampView() {
        double half = this.viewBlocks / 2.0;
        this.viewCenterX = Math.max(half, Math.min(TEX_BLOCKS - half, this.viewCenterX));
        this.viewCenterZ = Math.max(half, Math.min(TEX_BLOCKS - half, this.viewCenterZ));
    }

    private boolean onMap(double mx, double my) {
        return mx >= this.mapX && mx < this.mapX + this.mapPx && my >= this.mapY && my < this.mapY + this.mapPx;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cz) << 32) | (((long) cx) & 0xFFFFFFFFL);
    }

    private static int roleColor(int role) {
        Role r = role >= 0 && role < Role.VALUES.length ? Role.VALUES[role] : Role.ATTACKER;
        return switch (r) {
            case ATTACKER -> ROLE_COLOR_ATTACKER;
            case DEFENDER -> ROLE_COLOR_DEFENDER;
            case MEDIC -> ROLE_COLOR_MEDIC;
            case SNIPER -> ROLE_COLOR_SNIPER;
            case PATHFINDER -> ROLE_COLOR_PATHFINDER;
            case DRONE_OPERATOR -> ROLE_COLOR_DRONE;
        };
    }

    private static boolean hasShift() {
        long w = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean hasCtrl() {
        long w = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    // ===== render =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        if (this.texture != null) {
            g.blit(MAP_TEXTURE, this.mapX, this.mapY, this.mapPx, this.mapPx, (float) viewOriginX(), (float) viewOriginZ(), this.viewBlocks, this.viewBlocks, TEX_BLOCKS, TEX_BLOCKS);
        }
        g.fill(this.mapX - 1, this.mapY - 1, this.mapX + this.mapPx + 1, this.mapY, 0xFFFFAA00);
        g.fill(this.mapX - 1, this.mapY + this.mapPx, this.mapX + this.mapPx + 1, this.mapY + this.mapPx + 1, 0xFFFFAA00);
        g.fill(this.mapX - 1, this.mapY, this.mapX, this.mapY + this.mapPx, 0xFFFFAA00);
        g.fill(this.mapX + this.mapPx, this.mapY, this.mapX + this.mapPx + 1, this.mapY + this.mapPx, 0xFFFFAA00);

        renderAreas(g);
        renderCaptures(g);

        // cores + forward bases
        for (ClientWarState.MapCore core : ClientWarState.CORES) {
            drawIcon(g, core.pos.getX(), core.pos.getZ(), core.team == Team.OLIVA ? 0xFF55FF55 : 0xFFFFCC55, 3);
        }
        for (ClientWarState.MapSpawner spawner : ClientWarState.SPAWNERS) {
            drawIcon(g, spawner.pos.getX(), spawner.pos.getZ(), spawner.team == Team.OLIVA ? 0xFF2E8B2E : 0xFFB8860B, 2);
        }

        Team myTeam = ClientTeamCache.get();
        renderOwnUnits(g);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && myTeam != null) {
            for (SwatEntity e : mc.level.getEntitiesOfClass(SwatEntity.class,
                    new net.minecraft.world.phys.AABB(this.originX, -256, this.originZ, this.originX + TEX_BLOCKS, 512, this.originZ + TEX_BLOCKS))) {
                if (e.getArmyTeam() != myTeam && e.getState() == SwatEntity.STATE_ALIVE) {
                    drawIcon(g, e.getX(), e.getZ(), 0xFFFF3030, 1);
                }
            }
        }

        if (mc.level != null) {
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(this.originX, -256, this.originZ, this.originX + TEX_BLOCKS, 512, this.originZ + TEX_BLOCKS);
            for (com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v : mc.level.getEntitiesOfClass(com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.class, box)) {
                if (v instanceof com.atsuishio.superbwarfare.entity.vehicle.DroneEntity) {
                    drawIcon(g, v.getX(), v.getZ(), 0xFFC040FF, 1);
                    continue;
                }
                boolean ours = false;
                boolean crewed = false;
                for (net.minecraft.world.entity.Entity p : v.getPassengers()) {
                    crewed = true;
                    if (p instanceof SwatEntity s && myTeam != null && s.getArmyTeam() == myTeam) ours = true;
                    if (p == mc.player) ours = true;
                }
                int color = ours ? 0xFF00B0C0 : (crewed ? 0xFFFF6060 : 0xFFA0A0A0);
                drawIcon(g, v.getX(), v.getZ(), color, 3);
            }
        }

        for (double[] s : ClientWarState.SPOTTED) {
            drawIcon(g, s[0], s[1], 0xFFFF8C00, 2);
        }

        if (mc.level != null) {
            for (Player p : mc.level.players()) {
                drawIcon(g, p.getX(), p.getZ(), 0xFFFFFFFF, 2);
            }
        }

        renderPathPreview(g);
        renderDragBox(g);
        renderToolbar(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderAreas(GuiGraphics g) {
        Set<Long> areaToShow = this.mode == MODE_AREA ? this.areaChunks : ClientWarState.MY_AREAS.getOrDefault(Math.max(0, this.group), Set.of());
        for (long key : areaToShow) {
            int cx = (int) (key & 0xFFFFFFFFL);
            int cz = (int) (key >>> 32);
            int x1 = worldToMapX(cx << 4);
            int z1 = worldToMapZ(cz << 4);
            int x2 = worldToMapX((cx << 4) + 16);
            int z2 = worldToMapZ((cz << 4) + 16);
            if (x2 < this.mapX || x1 > this.mapX + this.mapPx || z2 < this.mapY || z1 > this.mapY + this.mapPx) continue;
            g.fill(Math.max(x1, this.mapX), Math.max(z1, this.mapY), Math.min(x2, this.mapX + this.mapPx), Math.min(z2, this.mapY + this.mapPx), 0x4000FF40);
        }

        if (this.mode != MODE_AREA) return;
        int wStart = this.originX + (int) viewOriginX();
        for (int wx = (wStart & ~15); wx <= wStart + this.viewBlocks; wx += 16) {
            int px = worldToMapX(wx);
            if (px >= this.mapX && px <= this.mapX + this.mapPx) g.fill(px, this.mapY, px + 1, this.mapY + this.mapPx, 0x30FFFFFF);
        }
        int wStartZ = this.originZ + (int) viewOriginZ();
        for (int wz = (wStartZ & ~15); wz <= wStartZ + this.viewBlocks; wz += 16) {
            int pz = worldToMapZ(wz);
            if (pz >= this.mapY && pz <= this.mapY + this.mapPx) g.fill(this.mapX, pz, this.mapX + this.mapPx, pz + 1, 0x30FFFFFF);
        }
    }

    // Capture points: a diamond in the owner's colour, with a thin progress bar while somebody is taking it.
    private void renderCaptures(GuiGraphics g) {
        for (ClientWarState.MapCapture cap : ClientWarState.CAPTURES) {
            int px = worldToMapX(cap.pos.getX());
            int pz = worldToMapZ(cap.pos.getZ());
            if (px < this.mapX + 5 || px > this.mapX + this.mapPx - 5) continue;
            if (pz < this.mapY + 7 || pz > this.mapY + this.mapPx - 7) continue;

            int color = cap.owner == null ? 0xFFCCCCCC : (cap.owner == Team.OLIVA ? 0xFF55FF55 : 0xFFFFCC55);
            for (int i = 0; i <= 4; i++) {
                g.fill(px - i, pz - (4 - i), px + i + 1, pz - (4 - i) + 1, color);
                g.fill(px - i, pz + (4 - i), px + i + 1, pz + (4 - i) + 1, color);
            }
            if (cap.contested) {
                g.fill(px - 5, pz - 6, px + 6, pz - 5, 0xFFFF3030);
            }
            if (cap.progressPct > 0) {
                g.fill(px - 5, pz + 6, px + 6, pz + 8, 0xC0202020);
                g.fill(px - 5, pz + 6, px - 5 + (11 * cap.progressPct / 100), pz + 8, 0xFFFFD24A);
            }
        }
    }

    // Own soldiers: role colour, health tick above, selection frame, star for the squad leader.
    private void renderOwnUnits(GuiGraphics g) {
        for (ClientWarState.MapSoldier s : ClientWarState.SOLDIERS) {
            if (this.group >= 0 && s.group != this.group) continue;
            int px = worldToMapX(s.x);
            int pz = worldToMapZ(s.z);
            if (px < this.mapX + 4 || px > this.mapX + this.mapPx - 4) continue;
            if (pz < this.mapY + 5 || pz > this.mapY + this.mapPx - 5) continue;

            boolean selected = ClientSelection.contains(s.id);
            if (selected) {
                g.fill(px - 4, pz - 4, px + 5, pz - 3, 0xFF00FF00);
                g.fill(px - 4, pz + 4, px + 5, pz + 5, 0xFF00FF00);
                g.fill(px - 4, pz - 3, px - 3, pz + 4, 0xFF00FF00);
                g.fill(px + 4, pz - 3, px + 5, pz + 4, 0xFF00FF00);
            }
            int color = s.isDowned() ? 0xFFFFC107 : roleColor(s.role);
            g.fill(px - 2, pz - 2, px + 3, pz + 3, color);
            if (s.leader) {
                g.fill(px - 1, pz - 1, px + 2, pz + 2, 0xFF000000);
            }
            if (s.flyingDrone) {
                g.fill(px + 3, pz - 3, px + 5, pz - 1, 0xFFC040FF);
            }

            float frac = s.healthFraction();
            int hpColor = frac > 0.6f ? 0xFF4CAF50 : frac > 0.3f ? 0xFFFFC107 : 0xFFF44336;
            g.fill(px - 3, pz - 6, px + 4, pz - 5, 0xC0202020);
            g.fill(px - 3, pz - 6, px - 3 + (int) (7 * frac), pz - 5, hpColor);
        }
    }

    private void renderPathPreview(GuiGraphics g) {
        for (int i = 0; i < this.pathPoints.size(); i++) {
            BlockPos p = this.pathPoints.get(i);
            int px = worldToMapX(p.getX());
            int pz = worldToMapZ(p.getZ());
            if (i > 0) {
                BlockPos prev = this.pathPoints.get(i - 1);
                drawLine(g, worldToMapX(prev.getX()), worldToMapZ(prev.getZ()), px, pz, 0xFFFFFF00);
            }
            g.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFFFFFF00);
            g.drawString(this.font, String.valueOf(i + 1), px + 3, pz - 3, 0xFFFF00, false);
        }
    }

    private void renderDragBox(GuiGraphics g) {
        if (!this.dragging) return;
        int x1 = (int) Math.min(this.dragStartX, this.dragNowX);
        int x2 = (int) Math.max(this.dragStartX, this.dragNowX);
        int y1 = (int) Math.min(this.dragStartY, this.dragNowY);
        int y2 = (int) Math.max(this.dragStartY, this.dragNowY);
        g.fill(x1, y1, x2, y2, 0x2000FF00);
        g.fill(x1, y1, x2, y1 + 1, 0xFF00FF00);
        g.fill(x1, y2 - 1, x2, y2, 0xFF00FF00);
        g.fill(x1, y1, x1 + 1, y2, 0xFF00FF00);
        g.fill(x2 - 1, y1, x2, y2, 0xFF00FF00);
    }

    private void drawIcon(GuiGraphics g, double wx, double wz, int color, int radius) {
        int px = worldToMapX(wx);
        int pz = worldToMapZ(wz);
        if (px < this.mapX + radius || px > this.mapX + this.mapPx - radius) return;
        if (pz < this.mapY + radius || pz > this.mapY + this.mapPx - radius) return;
        g.fill(px - radius, pz - radius, px + radius, pz + radius, color);
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    // ===== toolbar =====

    private int toolbarModeY() {
        return 8 + 12 + 10 + 10 + 14 + 10;
    }

    private void renderToolbar(GuiGraphics g, int mx, int my) {
        int x = 8;
        int y = 8;
        g.fill(x - 3, y - 3, x + 104, y + 312, 0x90000000);

        g.drawString(this.font, "§6⚔ TAKTICKÁ MAPA", x, y, 0xFFAA00, false);
        y += 12;
        Team myTeam = ClientTeamCache.get();
        g.drawString(this.font, myTeam == null ? "§cBez tímu!" : "§fTím: §e" + myTeam.getDisplayName().toUpperCase(), x, y, 0xFFFFFF, false);
        y += 10;
        g.drawString(this.font, phaseLabel(), x, y, 0xFFFFFF, false);
        y += 10;
        String line = "☠ O:" + ClientWarState.DEATHS.getOrDefault(Team.OLIVA, 0)
                + " P:" + ClientWarState.DEATHS.getOrDefault(Team.PIESOK, 0);
        if (!ClientWarState.CAPTURES.isEmpty()) {
            line += "  ⚑ " + ClientWarState.points(Team.OLIVA) + "-" + ClientWarState.points(Team.PIESOK);
        }
        g.drawString(this.font, line, x, y, 0xCCCCCC, false);
        y += 14;

        g.drawString(this.font, "Režim:", x, y, 0xFFAA00, false);
        y += 10;
        for (int i = 0; i < MODES.length; i++) {
            boolean active = this.mode == i;
            boolean hover = mx >= x && mx < x + 96 && my >= y && my < y + 11;
            g.fill(x, y, x + 96, y + 11, active ? 0xC0006600 : (hover ? 0xC0555500 : 0xC0333333));
            g.drawString(this.font, MODES[i], x + 4, y + 2, active ? 0x00FF00 : 0xFFFFFF, false);
            y += 13;
        }

        y += 4;
        g.drawString(this.font, "Skupina:", x, y, 0xFFAA00, false);
        y += 10;
        for (int i = 0; i < GROUP_BUTTONS.length; i++) {
            int col = i % 6;
            int row = i / 6;
            int gx = x + col * 15;
            int gy = y + row * 13;
            boolean active = (i == 0 && this.group == -1) || (i > 0 && this.group == i - 1);
            boolean hover = mx >= gx && mx < gx + 13 && my >= gy && my < gy + 11;
            g.fill(gx, gy, gx + 13, gy + 11, active ? 0xC0006600 : (hover ? 0xC0555500 : 0xC0333333));
            g.drawCenteredString(this.font, GROUP_BUTTONS[i], gx + 6, gy + 2, active ? 0x00FF00 : 0xFFFFFF);
        }
        y += 13 * ((GROUP_BUTTONS.length + 5) / 6) + 4;

        g.drawString(this.font, "§bVybraných: §f" + ClientSelection.size(), x, y, 0xFFFFFF, false);
        y += 12;

        if (this.mode == MODE_PATH) {
            y = actionButton(g, mx, my, x, y, "Vyslať trasu (" + this.pathPoints.size() + ")");
            y = actionButton(g, mx, my, x, y, "Zmazať body");
        } else if (this.mode == MODE_AREA) {
            y = actionButton(g, mx, my, x, y, "Uložiť mantinel");
            y = actionButton(g, mx, my, x, y, "Zrušiť mantinel");
        } else if (this.mode == MODE_RALLY) {
            y = actionButton(g, mx, my, x, y, "Zrušiť zhromaždisko");
        } else if (this.mode == MODE_SELECT) {
            y = actionButton(g, mx, my, x, y, "Zrušiť výber");
        }
        y = actionButton(g, mx, my, x, y, "Obnoviť mapu");

        y += 4;
        String hint = switch (this.mode) {
            case MODE_SELECT -> "§7Ťahaj rám. Ctrl = pridať";
            case MODE_MOVE -> "§7Klik = presun, Shift = reťaz";
            case MODE_PATH -> "§7Klikaj body trasy";
            case MODE_AREA -> "§7Klikaj chunky oblasti";
            case MODE_RALLY -> "§7Klik = zhromaždisko skupiny";
            case MODE_DRONE_STRIKE -> "§7Klik = dron zaútočí na bod";
            case MODE_DRONE_SCOUT -> "§7Klik = dron sleduje bod";
            case MODE_UNLOAD -> "§7Klik = vozidlá tam vysadia";
            default -> "§7Ťahaj = posun mapy, koliesko = zoom";
        };
        g.drawString(this.font, hint, x, y, 0xAAAAAA, false);
        y += 10;
        g.drawString(this.font, "§8Rozkaz ide na výber,", x, y, 0x888888, false);
        g.drawString(this.font, "§8inak na skupinu.", x, y + 9, 0x888888, false);
    }

    private static String phaseLabel() {
        String label = switch (ClientWarState.phase) {
            case 1 -> "§ePRÍPRAVA" + (ClientWarState.phaseSecondsLeft > 0 ? " " + ClientWarState.phaseSecondsLeft + "s" : "");
            case 2 -> "§cBOJ";
            case 3 -> "§6KONIEC";
            default -> "§7VOĽNÝ REŽIM";
        };
        int oliva = ClientWarState.TICKETS.getOrDefault(Team.OLIVA, 0);
        int piesok = ClientWarState.TICKETS.getOrDefault(Team.PIESOK, 0);
        if (oliva > 0 || piesok > 0) label += " §7" + oliva + "/" + piesok;
        return label;
    }

    private int actionButton(GuiGraphics g, int mx, int my, int x, int y, String label) {
        boolean hover = mx >= x && mx < x + 96 && my >= y && my < y + 11;
        g.fill(x, y, x + 96, y + 11, hover ? 0xC0703800 : 0xC0402000);
        g.drawString(this.font, label, x + 4, y + 2, 0xFFDD99, false);
        return y + 13;
    }

    // ===== input =====

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (onMap(mx, my)) {
            if (delta > 0 && this.viewBlocks > 64) {
                this.viewBlocks /= 2;
            } else if (delta < 0 && this.viewBlocks < TEX_BLOCKS) {
                this.viewBlocks *= 2;
            }
            clampView();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (this.dragging && button == 0) {
            this.dragNowX = mx;
            this.dragNowY = my;
            return true;
        }
        if ((button == 2 || (button == 0 && this.mode == MODE_VIEW)) && onMap(mx, my)) {
            this.viewCenterX -= dragX * this.viewBlocks / this.mapPx;
            this.viewCenterZ -= dragY * this.viewBlocks / this.mapPx;
            clampView();
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.dragging && button == 0) {
            this.dragging = false;
            applyBoxSelection();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void applyBoxSelection() {
        double x1 = Math.min(this.dragStartX, this.dragNowX);
        double x2 = Math.max(this.dragStartX, this.dragNowX);
        double y1 = Math.min(this.dragStartY, this.dragNowY);
        double y2 = Math.max(this.dragStartY, this.dragNowY);
        if (!hasCtrl()) ClientSelection.clear();
        int added = 0;
        for (ClientWarState.MapSoldier s : ClientWarState.SOLDIERS) {
            if (this.group >= 0 && s.group != this.group) continue;
            int px = worldToMapX(s.x);
            int pz = worldToMapZ(s.z);
            if (px < x1 || px > x2 || pz < y1 || pz > y2) continue;
            ClientSelection.IDS.add(s.id);
            added++;
        }
        msg(added == 0 ? "§7V ráme nikto nie je." : "§aVybraných: §f" + ClientSelection.size());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && handleToolbarClick(mx, my)) return true;

        if (!onMap(mx, my)) return super.mouseClicked(mx, my, button);

        int wx = mapToWorldX(mx);
        int wz = mapToWorldZ(my);

        if (button == 1) {
            if (this.mode == MODE_PATH && !this.pathPoints.isEmpty()) {
                this.pathPoints.remove(this.pathPoints.size() - 1);
            } else if (this.mode == MODE_MOVE && !this.pathPoints.isEmpty()) {
                this.pathPoints.clear();
                msg("§7Reťaz rozkazov zrušená.");
            }
            return true;
        }

        if (button != 0) return super.mouseClicked(mx, my, button);

        switch (this.mode) {
            case MODE_SELECT -> {
                this.dragging = true;
                this.dragStartX = mx;
                this.dragStartY = my;
                this.dragNowX = mx;
                this.dragNowY = my;
            }
            case MODE_MOVE -> {
                // Shift chains waypoints: keep clicking to build a route, click without shift to send it.
                this.pathPoints.add(new BlockPos(wx, 0, wz));
                if (hasShift()) {
                    msg("§eBod " + this.pathPoints.size() + " pridaný do reťaze (klik bez Shiftu = odoslať).");
                    return true;
                }
                if (this.pathPoints.size() == 1) {
                    sendOrder(PacketMapOrder.MODE_MOVE, List.of(this.pathPoints.get(0)));
                } else {
                    sendOrder(PacketMapOrder.MODE_PATH, new ArrayList<>(this.pathPoints));
                }
                this.pathPoints.clear();
                this.onClose();
            }
            case MODE_DRONE_STRIKE, MODE_DRONE_SCOUT, MODE_UNLOAD -> {
                int pm = this.mode == MODE_DRONE_STRIKE ? PacketMapOrder.MODE_DRONE_STRIKE
                        : (this.mode == MODE_DRONE_SCOUT ? PacketMapOrder.MODE_DRONE_SCOUT : PacketMapOrder.MODE_UNLOAD);
                sendOrder(pm, List.of(new BlockPos(wx, 0, wz)));
                this.onClose();
            }
            case MODE_RALLY -> {
                ModNetworking.sendToServer(new PacketMapOrder(PacketMapOrder.MODE_RALLY_SET,
                        Math.max(0, this.group), List.of(new BlockPos(wx, 0, wz)), null));
            }
            case MODE_PATH -> this.pathPoints.add(new BlockPos(wx, 0, wz));
            case MODE_AREA -> {
                long key = chunkKey(wx >> 4, wz >> 4);
                if (!this.areaChunks.remove(key)) this.areaChunks.add(key);
            }
            default -> {
            }
        }
        return true;
    }

    // Orders go to the box selection when there is one, otherwise to the group filter.
    private void sendOrder(int packetMode, List<BlockPos> points) {
        ModNetworking.sendToServer(new PacketMapOrder(packetMode, this.group, points, null,
                ClientSelection.isEmpty() ? null : new ArrayList<>(ClientSelection.IDS)));
    }

    private boolean handleToolbarClick(double mx, double my) {
        int x = 8;
        int y = toolbarModeY();

        for (int i = 0; i < MODES.length; i++) {
            if (mx >= x && mx < x + 96 && my >= y && my < y + 11) {
                this.mode = i;
                this.pathPoints.clear();
                if (this.mode == MODE_AREA) loadAreaForGroup();
                return true;
            }
            y += 13;
        }

        y += 4 + 10;
        for (int i = 0; i < GROUP_BUTTONS.length; i++) {
            int col = i % 6;
            int row = i / 6;
            int gx = x + col * 15;
            int gy = y + row * 13;
            if (mx >= gx && mx < gx + 13 && my >= gy && my < gy + 11) {
                this.group = (i == 0) ? -1 : i - 1;
                if (this.mode == MODE_AREA) loadAreaForGroup();
                return true;
            }
        }
        y += 13 * ((GROUP_BUTTONS.length + 5) / 6) + 4;
        y += 12;

        if (this.mode == MODE_PATH) {
            if (hit(mx, my, x, y)) {
                if (!this.pathPoints.isEmpty()) {
                    sendOrder(PacketMapOrder.MODE_PATH, new ArrayList<>(this.pathPoints));
                    this.pathPoints.clear();
                    this.onClose();
                }
                return true;
            }
            y += 13;
            if (hit(mx, my, x, y)) {
                this.pathPoints.clear();
                return true;
            }
            y += 13;
        } else if (this.mode == MODE_AREA) {
            if (hit(mx, my, x, y)) {
                ModNetworking.sendToServer(new PacketMapOrder(PacketMapOrder.MODE_AREA_SET, Math.max(0, this.group), null, this.areaChunks));
                return true;
            }
            y += 13;
            if (hit(mx, my, x, y)) {
                this.areaChunks.clear();
                ModNetworking.sendToServer(new PacketMapOrder(PacketMapOrder.MODE_AREA_CLEAR, Math.max(0, this.group), null, null));
                return true;
            }
            y += 13;
        } else if (this.mode == MODE_RALLY) {
            if (hit(mx, my, x, y)) {
                ModNetworking.sendToServer(new PacketMapOrder(PacketMapOrder.MODE_RALLY_CLEAR, Math.max(0, this.group), null, null));
                return true;
            }
            y += 13;
        } else if (this.mode == MODE_SELECT) {
            if (hit(mx, my, x, y)) {
                ClientSelection.clear();
                msg("§7Výber zrušený.");
                return true;
            }
            y += 13;
        }

        if (hit(mx, my, x, y)) {
            ModNetworking.sendToServer(new PacketRequestWarState());
            buildTerrain();
            return true;
        }
        return false;
    }

    private static boolean hit(double mx, double my, int x, int y) {
        return mx >= x && mx < x + 96 && my >= y && my < y + 11;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            int n = key - GLFW.GLFW_KEY_0;
            this.group = n == 0 ? -1 : n - 1;
            if (this.mode == MODE_AREA) loadAreaForGroup();
            return true;
        }
        if (key == GLFW.GLFW_KEY_A) {
            ClientSelection.clear();
            for (ClientWarState.MapSoldier s : ClientWarState.SOLDIERS) {
                if (this.group < 0 || s.group == this.group) ClientSelection.IDS.add(s.id);
            }
            msg("§aVybraných: §f" + ClientSelection.size());
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            this.mode = (this.mode + 1) % MODES.length;
            this.pathPoints.clear();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }
}

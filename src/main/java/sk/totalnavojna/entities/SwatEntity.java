package sk.totalnavojna.entities;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.*;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.EffectiveRangeModifier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sk.totalnavojna.*;
import sk.totalnavojna.config.CommonConfig;
import sk.totalnavojna.entities.goals.GunAttackGoal;
import sk.totalnavojna.entities.goals.GunPosGoal;
import sk.totalnavojna.entities.goals.TeamHurtByTargetGoal;
import sk.totalnavojna.entities.goals.CommanderOrderGoal;
import sk.totalnavojna.orders.ICommandableMob;
import sk.totalnavojna.orders.OrderType;
import net.minecraft.server.level.ServerPlayer;
import sk.totalnavojna.items.ModItems;
import sk.totalnavojna.entities.goals.SelfHealGoal;
import sk.totalnavojna.entities.goals.MedicReviveGoal;
import sk.totalnavojna.entities.goals.WarObjectiveGoal;
import sk.totalnavojna.entities.goals.PathOrderGoal;
import sk.totalnavojna.entities.goals.ReturnToAreaGoal;
import sk.totalnavojna.entities.goals.BreachGoal;
import sk.totalnavojna.entities.goals.ClimbLadderGoal;
import sk.totalnavojna.entities.goals.PathfinderDigGoal;
import sk.totalnavojna.entities.goals.PathfinderSupportGoal;
import sk.totalnavojna.entities.goals.ResupplyGoal;
import sk.totalnavojna.entities.goals.CombatMovementGoal;
import sk.totalnavojna.entities.goals.DropDownGoal;
import sk.totalnavojna.entities.goals.FlankGoal;
import sk.totalnavojna.entities.goals.SquadCohesionGoal;
import sk.totalnavojna.entities.goals.BoardVehicleGoal;
import sk.totalnavojna.entities.goals.VehicleCrewGoal;
import sk.totalnavojna.entities.goals.VehicleDriveGoal;
import sk.totalnavojna.entities.goals.DroneOperatorGoal;
import sk.totalnavojna.entities.goals.ReportContactsGoal;
import sk.totalnavojna.entities.goals.AntiVehicleGoal;
import sk.totalnavojna.war.BattleIntel;
import sk.totalnavojna.war.RoleDoctrine;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import sk.totalnavojna.war.MobStance;
import net.minecraft.world.entity.monster.Enemy;
import sk.totalnavojna.war.ReviveHelper;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.AABB;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import sk.totalnavojna.war.WarEvents;
import sk.totalnavojna.war.WarGameData;
import net.minecraft.world.level.entity.EntityTypeTest;
import sk.totalnavojna.menus.SwatCorpseMenu;

import java.util.*;
import java.util.function.Supplier;

// TODO:
//  - only trigger siege mission if player is near the spawn point
//  - add structures

public class SwatEntity extends PathfinderMob implements IGunOperator, Container, MenuProvider, ICommandableMob, ReviveHelper.Channel {
    private static final String NBT_KEY_DEAD_BODY_AGE = "DeadBodyAge";
    private static final String NBT_KEY_STATE = "State";
    private static final String NBT_KEY_INVENTORY = "Inventory";
    private static final String NBT_KEY_SLOT = "Slot";
    private static final String NBT_KEY_SELECTED = "Selected";
    private static final String NBT_KEY_FAILED_GUN_POS_COUNTER = "GunPosCounter";
    public static final String NBT_KEY_TEAM = "Team";
    public static final String NBT_KEY_ROLE = "Role";
    public static final String NBT_KEY_GROUP = "Group";

    private static final EntityDimensions BOX_DIMENSIONS = EntityDimensions.scalable(0.6f, 0.6f);
    private static final EntityDataAccessor<Team> TEAM = SynchedEntityData.defineId(SwatEntity.class, ModEntityDataSerializers.TEAM);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SwatEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ORDER = SynchedEntityData.defineId(SwatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FORMATION_INDEX = SynchedEntityData.defineId(SwatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(SwatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GROUP = SynchedEntityData.defineId(SwatEntity.class, EntityDataSerializers.INT);
    private int suppressedUntil;
    private int lastNeighbourSuppress;
    // TODO: I remember that I didn't make this an enum because of some error, but idr what error. Try again?
    public static final byte STATE_ALIVE = 0;
    public static final byte STATE_DOWN = 1;
    public static final byte STATE_DEAD = 2;

    // The order by type in which guns should be used
    private static final List<String> GUN_TYPE_ORDER = List.of(Util.getGunTabTypeName(GunTabType.SNIPER), Util.getGunTabTypeName(GunTabType.RPG), Util.getGunTabTypeName(GunTabType.MG), Util.getGunTabTypeName(GunTabType.RIFLE), Util.getGunTabTypeName(GunTabType.SHOTGUN), Util.getGunTabTypeName(GunTabType.SMG), Util.getGunTabTypeName(GunTabType.PISTOL));

    

    private MeleeAttackGoal meleeAttackGoal;

    private short deadBodyAge = 0;
    private float currentGunAttackRadiusSqr;
    private int failedGunPosCounter = 0;

    private Vec3 moveToTarget = Vec3.ZERO;
    private int attackTargetId = -1;
    @Nullable
    private UUID commanderUUID = null;
    private CommanderOrderGoal commanderOrderGoal;
    private int downTimer = 0;
    @Nullable
    private UUID reviverUUID = null;
    private int reviveTicks = 0;
    private long reviveHeartbeat = 0;
    // movement intent + stuck detection (used by ladder / breach / pathfinder goals)
    @Nullable
    private Vec3 wantedDestination = null;
    private int wantedDestinationTick = 0;
    private int stuckTicks = 0;
    private Vec3 lastTickPos = Vec3.ZERO;
    private int idleReloadTimer = 0;
    private int gunSlotBeforeTool = -1;
    private int mountTargetId = -1;
    private boolean droneAloft = false;
    @Nullable
    private Entity lastDamager = null;
    private final List<BlockPos> pathWaypoints = new ArrayList<>();
    private int pathIndex = 0;
    private int orderFocusTicks = 0;

    protected SwatEntity(EntityType<SwatEntity> entityType, Level level) {
        super(entityType, level);
        // plan routes THROUGH doors: wooden ones get opened, iron ones get switched/breached by BreachGoal
        this.setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, 0.0f);
        this.setPathfindingMalus(BlockPathTypes.DOOR_IRON_CLOSED, 6.0f);
        this.setPathfindingMalus(BlockPathTypes.TRAPDOOR, 2.0f);
        this.setPathfindingMalus(BlockPathTypes.DOOR_OPEN, 0.0f);
    }

    public static SwatEntity withTeam(Level level, Team team) {
        SwatEntity entity = new SwatEntity(ModEntities.SWAT_ENTITY.get(), level);
        entity.setArmyTeam(team);
        return entity;
    }

    @Nullable
    @Override
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor levelAccessor, @NotNull DifficultyInstance difficulty, @NotNull MobSpawnType spawnType, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        if (spawnType == MobSpawnType.COMMAND) {
            if (dataTag != null) {
                this.load(dataTag);
            }
        } else if (spawnType == MobSpawnType.SPAWN_EGG) {
            if (dataTag != null && dataTag.contains(EntityType.ENTITY_TAG, Tag.TAG_COMPOUND)) {
                CompoundTag compoundtag = this.saveWithoutId(new CompoundTag());
                UUID uuid = this.getUUID();
                compoundtag.merge(dataTag.getCompound(EntityType.ENTITY_TAG));
                this.setUUID(uuid);
                this.load(compoundtag);
            }
        }

        if (!this.hasCustomName()) {
            this.setCustomName(Component.literal(VojenskeMena.nahodneMeno(this.random)));
            this.setCustomNameVisible(true);
        }

        this.generateInventory();

        this.copySpecialAttributes();

        if (spawnType == MobSpawnType.SPAWN_EGG && levelAccessor instanceof ServerLevel capCheckLevel) {
            int cap = CommonConfig.MAX_SOLDIERS_PER_TEAM.get();
            if (cap > 0) {
                int count = capCheckLevel.getEntities(EntityTypeTest.forClass(SwatEntity.class),
                        (e) -> e.getArmyTeam() == this.getArmyTeam() && e.getState() == STATE_ALIVE).size();
                if (count >= cap) {
                    this.discard();
                }
            }
        }

        return spawnData;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        this.setPersistenceRequired();
        this.takeNextGun();
    }

    // soldiers never despawn
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void checkDespawn() {
    }

    // plan paths over drops up to 6 blocks (a 60 HP soldier loses ~3 HP), DropDownGoal handles the rest
    @Override
    public int getMaxFallDistance() {
        return Math.max(6, super.getMaxFallDistance());
    }

    @NotNull
    @Override
    protected InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        Level level = player.level();

        // For debug purposes
        if (!level.isClientSide && player.isCreative() && this.isInvulnerable() && player.getMainHandItem().is(Items.WOODEN_AXE)) {
            this.remove(RemovalReason.KILLED);
            return InteractionResult.SUCCESS;
        }

        if (this.getState() == STATE_ALIVE && !level.isClientSide && player instanceof ServerPlayer serverPlayer && this.isCommandedBy(serverPlayer)) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SwatCorpseMenu(id, inv, this), this.getTypeName()));
            return InteractionResult.SUCCESS;
        }

        if (this.getState() == STATE_DEAD) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SwatCorpseMenu(id, inv, this), this.getTypeName()));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    protected boolean isImmobile() {
        // Moving dead/down agents is no more!
        return this.getState() != STATE_ALIVE;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.getEgg(this.getArmyTeam(), this.getRole()));
    }

    @Override
    public void heal(float healAmount) {
        if (this.getState() != STATE_ALIVE) return;
        super.heal(healAmount);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // Entities end up killing/heavily damaging each other otherwise (same team or mutual alliance)
        if (source.getEntity() instanceof SwatEntity other
                && (other.getArmyTeam() == this.getArmyTeam()
                    || (this.level() instanceof ServerLevel sl && WarGameData.get(sl).areAllied(other.getArmyTeam(), this.getArmyTeam())))) {
            return false;
        }
        // truce broken by the other team -> the whole team retaliates for a while
        if (this.level() instanceof ServerLevel sl && source.getEntity() instanceof LivingEntity attacker) {
            Team attackerTeam = ReviveHelper.teamOf(attacker);
            if (attackerTeam != null && attackerTeam != this.getArmyTeam()) {
                WarGameData data = WarGameData.get(sl);
                if (data.getStance(this.getArmyTeam()) == sk.totalnavojna.war.Stance.TRUCE) {
                    data.markTruceBroken(sl.getServer(), this.getArmyTeam(), sl.getGameTime());
                }
            }
        }
        // Downed soldiers can not be finished off - only the down timer (or void-type damage) kills them
        if (this.getState() == STATE_DOWN && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && this.getState() == STATE_ALIVE && source.getEntity() != null && this.level() instanceof ServerLevel sl2) {
            this.suppress();
            this.suppressNeighbours();
            if (this.random.nextInt(6) == 0) {
                sk.totalnavojna.war.VoiceLines.radio(sl2, this.getArmyTeam(), sk.totalnavojna.war.VoiceLines.Line.UNDER_FIRE);
            }
        }
        return hit;
    }

    @Override
    protected void actuallyHurt(@NotNull DamageSource damageSource, float damageAmount) {
        if (this.getState() == STATE_DOWN) {
            this.finishDown();
            return;
        }

        float dmg = damageAmount;
        dmg = this.getDamageAfterArmorAbsorb(damageSource, dmg);
        dmg = this.getDamageAfterMagicAbsorb(damageSource, dmg);
        dmg = Math.max(dmg - this.getAbsorptionAmount(), 0.0f);

        float hpAfterDmg = this.getHealth() - dmg;
        if (hpAfterDmg <= 0.0f) {
            this.lastDamager = damageSource.getEntity();
            if (CommonConfig.ALLOW_DOWN.get()) {
                this.setState(STATE_DOWN);
                this.setHealth(1.0f);
                this.downTimer = CommonConfig.DOWN_TIME.get();
                this.reviverUUID = null;
                this.reviveTicks = 0;
                this.reviveHeartbeat = 0;
                this.setTarget(null);
                this.restoreGun();
                this.stopRiding();
                this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
                this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MobEffectInstance.INFINITE_DURATION, Short.MAX_VALUE));
                return;
            }
            this.dieAndMaybeDropCorpse(damageSource);
            return;
        }

        super.actuallyHurt(damageSource, damageAmount);
    }

    private void dieAndMaybeDropCorpse(DamageSource damageSource) {
        if (this.level() instanceof ServerLevel voiceLevel) {
            sk.totalnavojna.war.VoiceLines.speak(voiceLevel, this.getArmyTeam(), this, sk.totalnavojna.war.VoiceLines.Line.DEATH);
        }
        WarEvents.onSoldierFinalDeath(this, this.lastDamager);
        if (CommonConfig.SWAT_ENTITY_NO_CORPSE.get()) {
            if (CommonConfig.DROP_ITEMS.get()) {
                this.compartments.forEach((comp) -> comp.forEach(this::spawnAtLocation));
            }
            this.die(damageSource);
            this.remove(RemovalReason.KILLED);
            return;
        }
        this.setState(STATE_DEAD);
        this.setInvulnerable(true);
        this.setHealth(1.0f);
        this.removeFreeWill();
        this.removeAllEffects();
        this.deadBodyAge = 0;
    }

    @Override
    protected void dropAllDeathLoot(@NotNull DamageSource damageSource) {
        if (CommonConfig.DROP_ITEMS.get()) {
            super.dropAllDeathLoot(damageSource);
        }
    }

    public void revive() {
        this.setState(STATE_ALIVE);
        this.setHealth(10.0f);
        if (this.level() instanceof ServerLevel voiceLevel) {
            sk.totalnavojna.war.VoiceLines.speak(voiceLevel, this.getArmyTeam(), this, sk.totalnavojna.war.VoiceLines.Line.REVIVE);
        }
        this.downTimer = 0;
        this.reviverUUID = null;
        this.reviveTicks = 0;
        this.reviveHeartbeat = 0;
        this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
        this.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    // ===== ReviveHelper.Channel (patient side of the revive protocol) =====

    @Override
    @Nullable
    public UUID getReviverUUID() {
        return this.reviverUUID;
    }

    @Override
    public void setReviverUUID(@Nullable UUID uuid) {
        this.reviverUUID = uuid;
    }

    @Override
    public int getReviveTicks() {
        return this.reviveTicks;
    }

    @Override
    public void setReviveTicks(int ticks) {
        this.reviveTicks = ticks;
    }

    @Override
    public long getReviveHeartbeat() {
        return this.reviveHeartbeat;
    }

    @Override
    public void setReviveHeartbeat(long gameTime) {
        this.reviveHeartbeat = gameTime;
    }

    @Override
    public void completeRevive() {
        this.revive();
    }

    @Override
    public String getPatientName() {
        return this.hasCustomName() ? this.getCustomName().getString() : "Vojak";
    }

    // ===== suppression =====
    // Being shot at is not just damage: it makes a man keep his head down and shoot worse for a moment.
    // Deliberately mild - it nudges the firefight, it does not decide it.

    public void suppress() {
        if (!CommonConfig.SUPPRESSION_ENABLED.get()) return;
        this.suppressedUntil = this.tickCount + CommonConfig.SUPPRESSION_TICKS.get();
    }

    public boolean isSuppressed() {
        return CommonConfig.SUPPRESSION_ENABLED.get() && this.tickCount < this.suppressedUntil;
    }

    // Aim error multiplier from role and suppression together - every shooting goal asks this, not the config.
    public double aimErrorMult() {
        double mult = this.getRole().getAimErrorMult();
        if (isSuppressed()) mult *= CommonConfig.SUPPRESSION_AIM_MULT.get();
        return mult;
    }

    // One burst near a squad pins the men around it too, not just whoever was hit.
    private void suppressNeighbours() {
        if (!CommonConfig.SUPPRESSION_ENABLED.get()) return;
        if (this.tickCount - this.lastNeighbourSuppress < 20) return;
        this.lastNeighbourSuppress = this.tickCount;
        for (SwatEntity mate : this.level().getEntitiesOfClass(SwatEntity.class, this.getBoundingBox().inflate(7.0),
                (e) -> e != this && e.getArmyTeam() == this.getArmyTeam() && e.getState() == STATE_ALIVE)) {
            mate.suppress();
        }
    }

    public boolean isBeingRevived() {
        return this.getState() == STATE_DOWN && ReviveHelper.isBeingRevived(this, this.level().getGameTime());
    }

    // ===== movement intent / stuck detection =====

    // Goals should use this instead of getNavigation().moveTo so ladder/breach/pathfinder helpers know where the unit wants to go.
    public boolean navigateTo(double x, double y, double z, double speed) {
        this.wantedDestination = new Vec3(x, y, z);
        this.wantedDestinationTick = this.tickCount;
        return this.getNavigation().moveTo(x, y, z, speed);
    }

    @Nullable
    public Vec3 getWantedDestination() {
        return this.wantedDestination;
    }

    public int getWantedDestinationAge() {
        return this.tickCount - this.wantedDestinationTick;
    }

    public int getStuckTicks() {
        return this.stuckTicks;
    }

    public boolean isStuck() {
        return this.stuckTicks > 40;
    }

    private void tickStuckDetection() {
        // "stuck" = we want to be somewhere (recent destination, still far away) but we are not moving - regardless of
        // whether the navigator still has a path (a wall in the way usually means NO path at all)
        boolean wantsToMove = this.wantedDestination != null && this.getWantedDestinationAge() < 300
                && this.wantedDestination.distanceToSqr(this.position()) > 2.0 * 2.0;
        if (wantsToMove && !this.isPassenger() && (this.onGround() || this.onClimbable())) {
            double moved = this.position().distanceToSqr(this.lastTickPos);
            if (moved < 0.0004) this.stuckTicks++;
            else this.stuckTicks = Math.max(0, this.stuckTicks - 2);
        } else {
            this.stuckTicks = 0;
        }
        this.lastTickPos = this.position();
    }

    // ===== targeting helpers =====

    public boolean isValidTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (!this.isEnemy(target)) return false;
        if (target instanceof SwatEntity swat) return swat.getState() == STATE_ALIVE;
        if (target instanceof ServerPlayer player) {
            if (player.isCreative() || player.isSpectator()) return false;
            return !WarEvents.isDowned(player);
        }
        return true;
    }

    // Is a living teammate standing between my eyes and my target?
    public boolean friendlyInLineOfFire() {
        LivingEntity target = this.getTarget();
        if (target == null) return false;
        Vec3 from = this.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        AABB search = this.getBoundingBox().minmax(target.getBoundingBox()).inflate(0.5);
        List<SwatEntity> mates = this.level().getEntitiesOfClass(SwatEntity.class, search,
                (e) -> e != this && e.getArmyTeam() == this.getArmyTeam() && e.getState() == STATE_ALIVE);
        for (SwatEntity mate : mates) {
            if (mate.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) return true;
        }
        return false;
    }

    // ===== tools (pathfinder) =====

    public int findToolSlot(BlockState state) {
        boolean pick = state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        boolean axe = state.is(BlockTags.MINEABLE_WITH_AXE);
        boolean shovel = state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        int fallback = -1;
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack stack = this.items.get(i);
            if (stack.isEmpty()) continue;
            if (pick && stack.is(ItemTags.PICKAXES)) return i;
            if (axe && stack.is(ItemTags.AXES)) return i;
            if (shovel && stack.is(ItemTags.SHOVELS)) return i;
            if (fallback < 0 && (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS))) fallback = i;
        }
        return fallback;
    }

    // Puts the right tool into the hand (visual + speed); returns true if one was found.
    public boolean holdTool(BlockState state) {
        int slot = findToolSlot(state);
        if (slot < 0) return false;
        if (slot > HOTBAR_INDEX_END) {
            int hotbar = this.getFreeHotbarIndex();
            if (hotbar == -1) hotbar = HOTBAR_INDEX_END;
            this.swapItems(slot, hotbar);
            slot = hotbar;
        }
        if (this.selected != slot) {
            if (this.gunSlotBeforeTool < 0) this.gunSlotBeforeTool = this.selected;
            this.selected = slot;
        }
        return true;
    }

    public void restoreGun() {
        if (this.gunSlotBeforeTool >= 0) {
            this.selected = this.gunSlotBeforeTool;
            this.gunSlotBeforeTool = -1;
        }
        if (!(this.getSelectedItem().getItem() instanceof IGun)) {
            this.takeNextGun();
        }
    }

    // ===== vehicles / drones =====

    public boolean hasDroneAloft() {
        return this.droneAloft;
    }

    public void setDroneAloft(boolean aloft) {
        this.droneAloft = aloft;
    }

    public int getMountTargetId() {
        return this.mountTargetId;
    }

    public void setMountTargetId(int id) {
        this.mountTargetId = id;
    }

    // Seated at a Superb Warfare weapon station? Then SBW fires the vehicle weapon for us and the personal gun stays holstered.
    public boolean isInVehicleWeaponSeat() {
        if (!(this.getVehicle() instanceof VehicleEntity v)) return false;
        if (v.getFirstPassenger() == this) return v.hasWeapon(0);
        int seat = v.getSeatIndex(this);
        return seat >= 0 && v.hasWeapon(seat);
    }

    // Put a specific item (monitor, tool) into the hand; restoreGun() undoes it.
    public boolean holdItem(Item item) {
        int slot = -1;
        for (int i = 0; i < this.items.size(); ++i) {
            if (this.items.get(i).is(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return false;
        if (slot > HOTBAR_INDEX_END) {
            int hotbar = this.getFreeHotbarIndex();
            if (hotbar == -1) hotbar = HOTBAR_INDEX_END;
            this.swapItems(slot, hotbar);
            slot = hotbar;
        }
        if (this.selected != slot) {
            if (this.gunSlotBeforeTool < 0) this.gunSlotBeforeTool = this.selected;
            this.selected = slot;
        }
        return true;
    }

    // ===== supplies =====

    public int countItem(Item item) {
        int n = 0;
        for (ItemStack stack : this.items) {
            if (stack.is(item)) n += stack.getCount();
        }
        return n;
    }

    public int countAmmo(ResourceLocation ammoId) {
        int n = 0;
        for (ItemStack stack : this.items) {
            if (stack.getItem() instanceof IAmmo ammo && ammoId.equals(ammo.getAmmoId(stack))) n += stack.getCount();
        }
        return n;
    }

    private boolean addToInventory(ItemStack stack) {
        // top up existing stacks first
        for (int i = 0; i < this.items.size() && !stack.isEmpty(); ++i) {
            ItemStack cur = this.items.get(i);
            if (!cur.isEmpty() && ItemStack.isSameItemSameTags(cur, stack) && cur.getCount() < cur.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
                cur.grow(move);
                stack.shrink(move);
            }
        }
        for (int i = INV_INDEX_START; i <= INV_INDEX_END && !stack.isEmpty(); ++i) {
            if (this.items.get(i).isEmpty()) {
                this.items.set(i, stack.copy());
                stack.setCount(0);
            }
        }
        for (int i = HOTBAR_INDEX_START; i <= HOTBAR_INDEX_END && !stack.isEmpty(); ++i) {
            if (this.items.get(i).isEmpty()) {
                this.items.set(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack.isEmpty();
    }

    private static int ammoTarget(ResourceLocation gunId) {
        return gunId.getPath().equals("m320") ? 3 : 90;
    }

    public boolean needsResupply() {
        if (this.countItem(ModItems.MEDKIT.get()) < Math.max(1, this.getRole().getDefaultMedkits() / 2)) return true;
        if (this.countItem(Items.POTION) == 0) return true;
        if (this.getRole() == Role.DRONE_OPERATOR) {
            Item drone = DroneOperatorGoal.droneItem();
            if (drone != null && this.countItem(drone) == 0) return true;
        }
        for (ItemStack stack : this.items) {
            IGun iGun = IGun.getIGunOrNull(stack);
            if (iGun == null) continue;
            ResourceLocation gunId = iGun.getGunId(stack);
            Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
            if (index.isEmpty()) continue;
            ResourceLocation ammoId = index.get().getGunData().getAmmoId();
            if (this.countAmmo(ammoId) < ammoTarget(gunId) / 3) return true;
        }
        return false;
    }

    public void resupply() {
        for (ItemStack stack : new ArrayList<>(this.items)) {
            IGun iGun = IGun.getIGunOrNull(stack);
            if (iGun == null) continue;
            ResourceLocation gunId = iGun.getGunId(stack);
            Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
            if (index.isEmpty()) continue;
            ResourceLocation ammoId = index.get().getGunData().getAmmoId();
            int missing = ammoTarget(gunId) - this.countAmmo(ammoId);
            while (missing > 0) {
                int batch = Math.min(missing, 64);
                if (!this.addToInventory(AmmoItemBuilder.create().setId(ammoId).setCount(batch).build())) break;
                missing -= batch;
            }
        }
        if (this.getRole() == Role.DRONE_OPERATOR) {
            Item drone = DroneOperatorGoal.droneItem();
            if (drone != null) {
                int missingDrones = 3 - this.countItem(drone);
                if (missingDrones > 0) this.addToInventory(new ItemStack(drone, missingDrones));
            }
        }
        int missingKits = this.getRole().getDefaultMedkits() - this.countItem(ModItems.MEDKIT.get());
        if (missingKits > 0) this.addToInventory(new ItemStack(ModItems.MEDKIT.get(), missingKits));
        int missingBandages = this.getRole().getDefaultBandages() - this.countItem(ModItems.BANDAGE.get());
        if (missingBandages > 0) this.addToInventory(new ItemStack(ModItems.BANDAGE.get(), missingBandages));
        this.takeNextGun();
    }

    // Top up the magazine while nothing is going on.
    private void tryIdleReload() {
        ItemStack gun = this.getSelectedItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            this.restoreGun();
            return;
        }
        if (this.tacz$data.reloadStateType != null && this.tacz$data.reloadStateType.isReloading()) return;
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun));
        if (index.isEmpty()) return;
        int max = index.get().getGunData().getAmmoAmount();
        if (iGun.getCurrentAmmoCount(gun) < max && this.hasAmmoForGun(gun)) {
            this.reload();
        }
    }

    private void finishDown() {
        this.dieAndMaybeDropCorpse(this.damageSources().generic());
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = this.getTarget();
        if (this.tacz$data.isCrawling && (target == null || target.isDeadOrDying() || this.getState() != STATE_ALIVE)) {
            this.crawl(false);
        }

        if (!this.level().isClientSide) {
            this.taczTick();

            ServerLevel serverLevel = (ServerLevel) this.level();

            if (this.orderFocusTicks > 0) {
                this.orderFocusTicks--;
            }

            if (this.attackTargetId >= 0 && this.tickCount % 10 == 0) {
                Entity designated = serverLevel.getEntity(this.attackTargetId);
                if (designated instanceof LivingEntity living && living.isAlive() && this.isEnemy(living)) {
                    this.setTarget(living);
                } else if (designated instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v && v.isAlive() && !v.isWreck()) {
                    // a designated vehicle is handled by AntiVehicleGoal / the vehicle crew, keep the designation
                    if (this.getTarget() != null && !this.isValidTarget(this.getTarget())) this.setTarget(null);
                } else {
                    this.attackTargetId = -1;
                }
            }

            if (this.getState() == STATE_ALIVE) {
                this.tickStuckDetection();
                if (this.getTarget() == null && ++this.idleReloadTimer >= 60) {
                    this.idleReloadTimer = 0;
                    this.tryIdleReload();
                }
            }

            if (this.getState() == STATE_DOWN) {
                ReviveHelper.tickTimeout(this, serverLevel.getGameTime());
                this.downTimer--;
                if (this.downTimer <= 0) {
                    this.finishDown();
                }
            }

            // Check if dead body should despawn
            if (this.getState() == STATE_DEAD) {
                this.deadBodyAge += 1;
                if (this.deadBodyAge >= CommonConfig.SWAT_ENTITY_DEAD_BODY_LIFESPAN.get()) {
                    this.discard();
                }
            }

        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);

        nbt.putString(NBT_KEY_TEAM, this.getArmyTeam().getName());

        nbt.putByte(NBT_KEY_STATE, this.getState());

        nbt.putInt(NBT_KEY_FAILED_GUN_POS_COUNTER, this.failedGunPosCounter);

        nbt.putShort(NBT_KEY_DEAD_BODY_AGE, this.deadBodyAge);

        nbt.putInt("Order", this.entityData.get(ORDER));
        nbt.putInt("FormationIndex", this.getFormationIndex());
        nbt.putDouble("MoveX", this.moveToTarget.x);
        nbt.putDouble("MoveY", this.moveToTarget.y);
        nbt.putDouble("MoveZ", this.moveToTarget.z);
        if (this.commanderUUID != null) {
            nbt.putUUID("CommanderUUID", this.commanderUUID);
        }

        nbt.putString(NBT_KEY_ROLE, this.getRole().getName());
        nbt.putInt(NBT_KEY_GROUP, this.getGroup());
        nbt.putInt("DownTimer", this.downTimer);

        ListTag pathTag = new ListTag();
        for (BlockPos p : this.pathWaypoints) {
            pathTag.add(net.minecraft.nbt.LongTag.valueOf(p.asLong()));
        }
        nbt.put("PathWaypoints", pathTag);
        nbt.putInt("PathIndex", this.pathIndex);




        nbt.put(NBT_KEY_INVENTORY, this.saveCompartments(new ListTag()));
        nbt.putInt(NBT_KEY_SELECTED, this.selected);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        if (nbt.contains(NBT_KEY_TEAM)) {
            Team spec = Team.byName(nbt.getString(NBT_KEY_TEAM));
            if (spec != null) this.setArmyTeam(spec);
        }

        if (nbt.contains(NBT_KEY_STATE)) {
            this.setState(nbt.getByte(NBT_KEY_STATE));
        }

        this.failedGunPosCounter = nbt.getInt(NBT_KEY_FAILED_GUN_POS_COUNTER);

        this.deadBodyAge = nbt.getShort(NBT_KEY_DEAD_BODY_AGE);

        if (nbt.contains("Order")) this.entityData.set(ORDER, nbt.getInt("Order"));
        if (nbt.contains("FormationIndex")) this.setFormationIndex(nbt.getInt("FormationIndex"));
        this.moveToTarget = new Vec3(nbt.getDouble("MoveX"), nbt.getDouble("MoveY"), nbt.getDouble("MoveZ"));
        if (nbt.hasUUID("CommanderUUID")) this.commanderUUID = nbt.getUUID("CommanderUUID");

        if (nbt.contains(NBT_KEY_ROLE)) {
            Role role = Role.byName(nbt.getString(NBT_KEY_ROLE));
            if (role != null) this.setRole(role);
        }
        if (nbt.contains(NBT_KEY_GROUP)) this.setGroup(nbt.getInt(NBT_KEY_GROUP));
        this.downTimer = nbt.getInt("DownTimer");

        this.pathWaypoints.clear();
        ListTag pathTag = nbt.getList("PathWaypoints", Tag.TAG_LONG);
        for (Tag t : pathTag) {
            if (t instanceof net.minecraft.nbt.LongTag longTag) {
                this.pathWaypoints.add(BlockPos.of(longTag.getAsLong()));
            }
        }
        this.pathIndex = nbt.getInt("PathIndex");



        this.loadCompartments(nbt.getList(NBT_KEY_INVENTORY, 10));
        this.selected = nbt.getInt(NBT_KEY_SELECTED);
    }

    // TODO: Need to register special and mission goals here, since the order in which they are registered matters
    //  however it is not actually possible since when this method is run
    //  specialty is unset (set to Specialty.Commander by default) so is the mission.
    @Override
    protected void registerGoals() {
        this.meleeAttackGoal = new MeleeAttackGoal(this, 1.0f, true);

        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BreachGoal(this));
        this.goalSelector.addGoal(1, new ClimbLadderGoal(this));
        this.goalSelector.addGoal(1, new DropDownGoal(this));
        this.goalSelector.addGoal(1, new BoardVehicleGoal(this));
        this.goalSelector.addGoal(1, new VehicleCrewGoal(this));
        this.goalSelector.addGoal(1, new VehicleDriveGoal(this));
        this.goalSelector.addGoal(2, new DroneOperatorGoal(this));
        this.goalSelector.addGoal(2, new MedicReviveGoal(this));
        this.goalSelector.addGoal(2, new PathfinderDigGoal(this));
        this.goalSelector.addGoal(3, new SelfHealGoal(this));
        this.goalSelector.addGoal(3, new ResupplyGoal(this));
        this.goalSelector.addGoal(3, new AntiVehicleGoal(this));
        this.goalSelector.addGoal(4, new GunAttackGoal(this));
        this.goalSelector.addGoal(5, new GunPosGoal(this));
        this.goalSelector.addGoal(5, new CombatMovementGoal(this));
        this.commanderOrderGoal = new CommanderOrderGoal(this, 1.15, 6.0f, 3.0f);
        this.goalSelector.addGoal(4, this.commanderOrderGoal);
        this.goalSelector.addGoal(6, new PathfinderSupportGoal(this));
        this.goalSelector.addGoal(7, new WarObjectiveGoal(this));
        this.goalSelector.addGoal(4, new PathOrderGoal(this));
        this.goalSelector.addGoal(2, new ReturnToAreaGoal(this));
        this.goalSelector.addGoal(5, new FlankGoal(this));
        this.goalSelector.addGoal(8, new SquadCohesionGoal(this));
        this.goalSelector.addGoal(0, new ReportContactsGoal(this));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        if (GoalUtils.hasGroundPathNavigation(this)) {
            ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
            ((GroundPathNavigation) this.getNavigation()).setCanPassDoors(true);
            this.goalSelector.addGoal(1, new OpenDoorGoal(this, false));
            this.goalSelector.addGoal(1, new BreakDoorGoal(this, 120, (d) -> true));
        }

        this.targetSelector.addGoal(1, new TeamHurtByTargetGoal(this));
        // support roles (pathfinder) never pick fights on their own - only TeamHurtByTargetGoal arms them
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, SwatEntity.class, 10, true, false, (le) -> !this.getRole().isSupport() && le instanceof SwatEntity s && this.isValidTarget(s) && !this.isOrderFocusSuppressed(s)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (le) -> !this.getRole().isSupport() && this.isValidTarget(le) && !this.isOrderFocusSuppressed(le)));
        // mobs, depending on the team's mob stance (NEUTRAL = never on sight)
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 20, true, false, (le) -> !this.getRole().isSupport() && !(le instanceof SwatEntity)
                && this.level() instanceof ServerLevel sl && WarGameData.get(sl).getMobStance(this.getArmyTeam()) != MobStance.NEUTRAL && this.isValidTarget(le)));
        // TODO: Use potions like how Witch does
    }

    public boolean hasMeleeAttackGoal() {
        return this.goalSelector.getAvailableGoals().stream()
                .anyMatch((wrap) -> wrap.getGoal() == this.meleeAttackGoal);
    }

    public static AttributeSupplier.Builder createDefaultAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK)
                .add(Attributes.ATTACK_SPEED);
    }

    @Override
    public int getHeadRotSpeed() {
        return 50;
    }

    @Override
    public int getMaxHeadXRot() {
        return 85;
    }

    private void copySpecialAttributes() {
        for (var e : CommonConfig.SOLDIER_ATTRIBUTES.entrySet()) {
            Attribute attr = e.getKey();
            AttributeInstance inst = this.getAttributes().getInstance(attr);
            if (inst == null) {
                TotalnaVojna.LOGGER.error("An error occurred when replacing attribute {}", attr);
                continue;
            }
            AttributeInstance newInst = new AttributeInstance(attr, (a) -> {
            });
            newInst.setBaseValue(e.getValue().get());
            inst.replaceFrom(newInst);
        }
        if (this.getRole() == Role.PATHFINDER) {
            AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(CommonConfig.PATHFINDER_HEALTH.get());
        }
        this.setHealth(this.getMaxHealth());
    }

    private void generateInventory() {
        Level level = this.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(Util.getResource("spawn_inv/" + this.getArmyTeam().getName() + "_" + this.getRole().getName()));
        LootParams lootParams = new LootParams.Builder(serverLevel).create(LootContextParamSets.EMPTY);

        ObjectArrayList<ItemStack> itemStacks = lootTable.getRandomItems(lootParams);
        List<Integer> indices = this.getAvailableInvSlotsShuffled();
        Util.shuffleAndSplitItems(itemStacks, indices.size(), this.random);

        for (int i = itemStacks.size() - 1; i >= 0; --i) {
            ItemStack itemStack = itemStacks.remove(i);
            // Place certain items in certain places
            if (itemStack.is(ModTags.Items.RULE_HOTBAR) && this.getFreeHotbarIndex() != -1) {
                this.items.set(this.getFreeHotbarIndex(), itemStack);
            } else if (itemStack.canEquip(EquipmentSlot.HEAD, this) && this.armor.get(0).isEmpty()) {
                this.armor.set(0, itemStack);
            } else if (itemStack.canEquip(EquipmentSlot.CHEST, this) && this.armor.get(1).isEmpty()) {
                this.armor.set(1, itemStack);
            } else if (itemStack.canEquip(EquipmentSlot.LEGS, this) && this.armor.get(2).isEmpty()) {
                this.armor.set(2, itemStack);
            } else if (itemStack.canEquip(EquipmentSlot.FEET, this) && this.armor.get(3).isEmpty()) {
                this.armor.set(3, itemStack);
            } else if (itemStack.is(ModTags.Items.RULE_OFFHAND) && this.offhand.get(0).isEmpty()) {
                this.offhand.set(0, itemStack);
            } else if (!itemStack.isEmpty()) {
                // The rest is placed in the random places
                this.items.set(indices.remove(indices.size() - 1), itemStack);
            }
        }
    }

    public void takeNextGun() {
        List<ItemStack> potentialNextGuns = new ArrayList<>();
        Map<ItemStack, CommonGunIndex> potentialNextGunIndexes = new HashMap<>();
        Map<ItemStack, Integer> potentialNextGunIndices = new HashMap<>();

        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack gunItemStack = this.items.get(i);
            CompoundTag nbt = gunItemStack.getOrCreateTag();
            if (!nbt.contains("GunId")) continue;

            ResourceLocation gunId = ResourceLocation.tryParse(nbt.getString("GunId"));
            if (gunId == null) continue;

            if (gunId.getPath().equals("m320") && this.level() instanceof ServerLevel coreCheckLevel
                    && WarGameData.get(coreCheckLevel).isNearOwnCore(coreCheckLevel, this.getArmyTeam(), this.blockPosition(), CommonConfig.GRENADE_SAFE_RADIUS.get())) {
                continue;
            }

            Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);
            if (gunIndexOpt.isEmpty()) continue;

            if (this.hasAmmoForGun(gunItemStack)) {
                CommonGunIndex gunIndex = gunIndexOpt.get();
                potentialNextGuns.add(gunItemStack);
                potentialNextGunIndexes.put(gunItemStack, gunIndex);
                potentialNextGunIndices.put(gunItemStack, i);
            }
        }

        // Out of guns... Let's go throw hands
        if (potentialNextGuns.isEmpty()) {
            // TODO: remove GunAttackGoal and add MeleeAttackGoal instead
            return;
        }

        potentialNextGuns.sort((itemStack1, itemStack2) -> {
            CommonGunIndex gunIndex1 = potentialNextGunIndexes.get(itemStack1);
            CommonGunIndex gunIndex2 = potentialNextGunIndexes.get(itemStack2);
            int index1 = GUN_TYPE_ORDER.indexOf(gunIndex1.getType());
            int index2 = GUN_TYPE_ORDER.indexOf(gunIndex2.getType());
            return index1 - index2;
        });

        ItemStack nextGun = potentialNextGuns.get(0);

        // If nextGun is not on hotbar, we swap it there
        int index = potentialNextGunIndices.get(nextGun);
        if (index > HOTBAR_INDEX_END) {
            int hotbarIndex = this.getFreeHotbarIndex();
            if (hotbarIndex == -1) hotbarIndex = this.random.nextInt(HOTBAR_INDEX_END + 1);
            this.swapItems(index, hotbarIndex);
            index = hotbarIndex;
        }
        this.selected = index;

        this.currentGunAttackRadiusSqr = 0.0f;
        AttachmentCacheProperty prop = new AttachmentCacheProperty();
        prop.eval(nextGun, potentialNextGunIndexes.get(nextGun).getGunData());
        this.updateCacheProperty(prop);
        // This is sus ngl
        this.tacz$data.currentGunItem = () -> nextGun;
        float effectiveRange = prop.getCache(EffectiveRangeModifier.ID);
        this.currentGunAttackRadiusSqr = (float) (effectiveRange * effectiveRange * CommonConfig.SWAT_ENTITY_EFFECTIVE_RANGE_MULT.get() * CommonConfig.SWAT_ENTITY_EFFECTIVE_RANGE_MULT.get());
    }

    @NotNull
    @Override
    public ItemStack getItemBySlot(@NotNull EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.getSelectedItem();
        } else if (slot == EquipmentSlot.OFFHAND) {
            return this.offhand.get(0);
        } else if (slot.isArmor()) {
            return this.armor.get(3 - slot.getIndex());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(@NotNull EquipmentSlot slot, @NotNull ItemStack itemStack) {
        this.verifyEquippedItem(itemStack);
        if (slot == EquipmentSlot.MAINHAND) {
            this.onEquipItem(slot, this.items.set(this.selected, itemStack), itemStack);
        } else if (slot == EquipmentSlot.OFFHAND) {
            this.onEquipItem(slot, this.offhand.set(0, itemStack), itemStack);
        } else if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            this.onEquipItem(slot, this.armor.set(3 - slot.getIndex(), itemStack), itemStack);
        }
    }

    @NotNull
    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armor;
    }

    @NotNull
    @Override
    public Iterable<ItemStack> getHandSlots() {
        return Lists.newArrayList(this.getMainHandItem(), this.getOffhandItem());
    }

    @Override
    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target.canBeSeenAsEnemy() && this.isValidTarget(target);
    }

    public boolean isEnemy(LivingEntity other) {
        if (other == null) return false;
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return other instanceof SwatEntity swat && swat.getArmyTeam() != this.getArmyTeam();
        }
        WarGameData data = WarGameData.get(serverLevel);
        long now = serverLevel.getGameTime();
        if (other instanceof SwatEntity swat) {
            if (swat.getArmyTeam() == this.getArmyTeam()) return false;
            return data.isHostile(this.getArmyTeam(), swat.getArmyTeam(), now) || recentlyHurtBy(other, data);
        }
        if (other instanceof Player player) {
            Team team = TeamsSavedData.get(serverLevel).getTeam(player.getUUID());
            if (team == null || team == this.getArmyTeam()) return false;
            return data.isHostile(this.getArmyTeam(), team, now) || recentlyHurtBy(other, data);
        }
        if (other instanceof Mob mob) {
            return switch (data.getMobStance(this.getArmyTeam())) {
                case ALL -> true;
                case HOSTILE -> mob instanceof Enemy || recentlyHurtBy(other, data);
                case NEUTRAL -> recentlyHurtBy(other, data);
            };
        }
        return recentlyHurtBy(other, data);
    }

    // shoot back at whoever just hit us - unless our stance is PASSIVE (or ALLIANCE with that team)
    private boolean recentlyHurtBy(LivingEntity other, WarGameData data) {
        if (this.getLastHurtByMob() != other) return false;
        if (this.tickCount - this.getLastHurtByMobTimestamp() > 200) return false;
        Team otherTeam = ReviveHelper.teamOf(other);
        if (otherTeam != null) {
            sk.totalnavojna.war.Stance st = data.getStance(this.getArmyTeam());
            return st == sk.totalnavojna.war.Stance.WAR || st == sk.totalnavojna.war.Stance.TRUCE;
        }
        return true;
    }

    @NotNull
    @Override
    protected Component getTypeName() {
        return this.getArmyTeam().getTypeName();
    }

    @NotNull
    @Override
    public EntityDimensions getDimensions(@NotNull Pose pose) {
        if (pose == Pose.SWIMMING || this.getState() != STATE_ALIVE) return BOX_DIMENSIONS;
        return super.getDimensions(pose);
    }

    @Override
    protected float getStandingEyeHeight(@NotNull Pose pose, @NotNull EntityDimensions dimensions) {
        if (pose == Pose.SWIMMING || this.getState() != STATE_ALIVE) return 0.4f;
        return 1.62f;
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TEAM, Team.OLIVA);
        this.entityData.define(STATE, STATE_ALIVE);
        this.entityData.define(ORDER, OrderType.NONE.ordinal());
        this.entityData.define(FORMATION_INDEX, 0);
        this.entityData.define(ROLE, Role.ATTACKER.ordinal());
        this.entityData.define(GROUP, 0);
    }

    public Team getArmyTeam() {
        return this.entityData.get(TEAM);
    }

    private void setArmyTeam(Team team) {
        this.entityData.set(TEAM, team);
    }

    public byte getState() {
        return this.entityData.get(STATE);
    }

    private void setState(byte state) {
        this.entityData.set(STATE, state);
    }

    public float getGunAttackRadiusSqr() {
        return this.currentGunAttackRadiusSqr;
    }





    public void incFailedGunPosCounter() {
        this.failedGunPosCounter += 1;
    }

    public void resetFailedGunPosCounter() {
        this.failedGunPosCounter = 0;
    }

    public int getFailedGunPosCounter() {
        return this.failedGunPosCounter;
    }

    ///////////////////////////////
    // Orders (ICommandableMob)  //
    ///////////////////////////////

    @Override
    public OrderType getOrder() {
        int i = this.entityData.get(ORDER);
        OrderType[] values = OrderType.values();
        return (i >= 0 && i < values.length) ? values[i] : OrderType.NONE;
    }

    @Override
    public void setOrder(OrderType order) {
        OrderType previous = this.getOrder();
        this.entityData.set(ORDER, order.ordinal());
        if (order == OrderType.RETREAT_TO_NEXUS && previous != order && this.level() instanceof ServerLevel voiceLevel) {
            sk.totalnavojna.war.VoiceLines.radio(voiceLevel, this.getArmyTeam(), sk.totalnavojna.war.VoiceLines.Line.FALLING_BACK);
        }
    }

    @Override
    public UUID getOwnerUUID() {
        return this.commanderUUID;
    }

    public void setCommanderUUID(UUID uuid) {
        this.commanderUUID = uuid;
    }

    @Override
    public Vec3 getMoveToTarget() {
        return this.moveToTarget;
    }

    public void setMoveToTarget(Vec3 pos) {
        this.moveToTarget = pos;
    }

    public int getFormationIndex() {
        return this.entityData.get(FORMATION_INDEX);
    }

    public void setFormationIndex(int index) {
        this.entityData.set(FORMATION_INDEX, index);
    }

    public void setAttackTargetId(int entityId) {
        this.attackTargetId = entityId;
    }

    public int getAttackTargetId() {
        return this.attackTargetId;
    }

    public void resetCommanderGoalCooldown() {
        if (this.commanderOrderGoal != null) {
            this.commanderOrderGoal.resetCombatCooldown();
        }
    }

    public Role getRole() {
        int i = this.entityData.get(ROLE);
        return (i >= 0 && i < Role.VALUES.length) ? Role.VALUES[i] : Role.ATTACKER;
    }

    public void setRole(Role role) {
        this.entityData.set(ROLE, role.ordinal());
    }

    public int getGroup() {
        return this.entityData.get(GROUP);
    }

    public void setGroup(int group) {
        this.entityData.set(GROUP, group);
    }

    public int findBandageSlot() {
        for (int i = 0; i < this.items.size(); ++i) {
            if (this.items.get(i).is(ModItems.BANDAGE.get())) return i;
        }
        return -1;
    }

    public int findPotionSlot() {
        for (int i = 0; i < this.items.size(); ++i) {
            if (this.items.get(i).is(Items.POTION)) return i;
        }
        return -1;
    }

    public int findMedkitSlot() {
        for (int i = 0; i < this.items.size(); ++i) {
            if (this.items.get(i).is(ModItems.MEDKIT.get())) return i;
        }
        return -1;
    }

    public List<BlockPos> getPathWaypoints() {
        return this.pathWaypoints;
    }

    public int getPathIndex() {
        return this.pathIndex;
    }

    public void setPathIndex(int index) {
        this.pathIndex = index;
    }

    public void markOrderFocus() {
        this.orderFocusTicks = 100;
    }

    // While freshly ordered, ignore distant enemies so the unit actually executes the command (still fights back up close).
    public boolean isOrderFocusSuppressed(LivingEntity target) {
        return this.orderFocusTicks > 0 && this.distanceToSqr(target) > 144.0;
    }

    // patrol: turn around at the end of the route and walk it back
    public void reversePathWaypoints() {
        java.util.Collections.reverse(this.pathWaypoints);
        this.pathIndex = Math.min(1, Math.max(0, this.pathWaypoints.size() - 1));
    }

    public void setPathWaypoints(List<BlockPos> waypoints) {
        this.pathWaypoints.clear();
        this.pathWaypoints.addAll(waypoints);
        this.pathIndex = 0;
        this.setOrder(OrderType.MOVE_ALONG_PATH);
    }

    // Panic button: forget every order, target and route and stand still. Used by "Reset rozkazov" when a unit
    // gets wedged in some state (a drone flight that never ended, a boarding order for a vehicle that is gone...).
    public void resetOrders() {
        this.setOrder(OrderType.NONE);
        this.setTarget(null);
        this.setLastHurtByMob(null);
        this.attackTargetId = -1;
        this.mountTargetId = -1;
        this.droneAloft = false;
        this.moveToTarget = Vec3.ZERO;
        this.pathWaypoints.clear();
        this.pathIndex = 0;
        this.orderFocusTicks = 0;
        this.stuckTicks = 0;
        this.wantedDestination = null;
        this.getNavigation().stop();
        this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0);
        this.setAggressive(false);
        this.aim(false);
        this.restoreGun();
        if (this.commanderOrderGoal != null) this.commanderOrderGoal.resetCombatCooldown();
        // re-run goal selection from scratch next tick
        this.goalSelector.getAvailableGoals().forEach(g -> {
            if (g.isRunning()) g.stop();
        });
    }

    public boolean isCommandedBy(ServerPlayer player) {
        Team team = TeamsSavedData.get(player.serverLevel()).getTeam(player.getUUID());
        return team == this.getArmyTeam();
    }

    ///////////////////////////////////////////////////////
    // IGunOperator interface implementation begins here //
    ///////////////////////////////////////////////////////

    private final LivingEntity tacz$shooter = this;
    private final ShooterDataHolder tacz$data = new ShooterDataHolder();
    private final LivingEntityDrawGun tacz$draw = new LivingEntityDrawGun(tacz$shooter, tacz$data);
    private final LivingEntityAim tacz$aim = new LivingEntityAim(tacz$shooter, this.tacz$data);
    private final LivingEntityCrawl tacz$crawl = new LivingEntityCrawl(tacz$shooter, this.tacz$data);
    private final LivingEntityAmmoCheck tacz$ammoCheck = new LivingEntityAmmoCheck(tacz$shooter);
    private final LivingEntityFireSelect tacz$fireSelect = new LivingEntityFireSelect(tacz$shooter, this.tacz$data);
    private final LivingEntityMelee tacz$melee = new LivingEntityMelee(tacz$shooter, this.tacz$data, this.tacz$draw);
    private final LivingEntityShoot tacz$shoot = new LivingEntityShoot(tacz$shooter, this.tacz$data, this.tacz$draw);
    private final LivingEntityBolt tacz$bolt = new LivingEntityBolt(this.tacz$data, this.tacz$shooter, this.tacz$draw, this.tacz$shoot);
    private final LivingEntityReload tacz$reload = new LivingEntityReload(tacz$shooter, this.tacz$data, this.tacz$draw, this.tacz$shoot);
    private final LivingEntitySpeedModifier tacz$speed = new LivingEntitySpeedModifier(tacz$shooter, tacz$data);
    private final LivingEntitySprint tacz$sprint = new LivingEntitySprint(tacz$shooter, this.tacz$data);

    @Override
    public long getSynShootCoolDown() {
        return ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.getValue(tacz$shooter);
    }

    @Override
    public long getSynMeleeCoolDown() {
        return ModSyncedEntityData.MELEE_COOL_DOWN_KEY.getValue(tacz$shooter);
    }

    @Override
    public long getSynDrawCoolDown() {
        return ModSyncedEntityData.DRAW_COOL_DOWN_KEY.getValue(tacz$shooter);
    }

    @Override
    public boolean getSynIsBolting() {
        return ModSyncedEntityData.IS_BOLTING_KEY.getValue(tacz$shooter);
    }

    @Override
    public ReloadState getSynReloadState() {
        return ModSyncedEntityData.RELOAD_STATE_KEY.getValue(tacz$shooter);
    }

    @Override
    public float getSynAimingProgress() {
        return ModSyncedEntityData.AIMING_PROGRESS_KEY.getValue(tacz$shooter);
    }

    @Override
    public boolean getSynIsAiming() {
        return ModSyncedEntityData.IS_AIMING_KEY.getValue(tacz$shooter);
    }

    @Override
    public float getSynSprintTime() {
        return ModSyncedEntityData.SPRINT_TIME_KEY.getValue(tacz$shooter);
    }

    @Override
    public void initialData() {
        this.tacz$data.initialData();
        this.tacz$data.currentGunItem = () -> {
            ItemStack itemStack = this.getMainHandItem();
            if (itemStack.getItem() instanceof IGun) {
                return itemStack;
            } else {
                return null;
            }
        };
    }

    @Override
    public void draw(Supplier<ItemStack> gunItemSupplier) {
        this.tacz$draw.draw(gunItemSupplier);
    }

    @Override
    public void bolt() {
        this.tacz$bolt.bolt();
    }

    @Override
    public void reload() {
        this.tacz$reload.reload();
    }

    @Override
    public void cancelReload() {
        this.tacz$reload.cancelReload();
    }

    @Override
    public void melee() {
        this.tacz$melee.melee();
    }

    @Override
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw) {
        return this.shoot(pitch, yaw, System.currentTimeMillis() - tacz$data.baseTimestamp);
    }

    @Override
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp) {
        return tacz$shoot.shoot(pitch, yaw, timestamp);
    }

    @Override
    public boolean needCheckAmmo() {
        return this.tacz$ammoCheck.needCheckAmmo();
    }

    @Override
    public boolean consumesAmmoOrNot() {
        return this.tacz$ammoCheck.consumesAmmoOrNot();
    }

    @Override
    public boolean getProcessedSprintStatus(boolean sprint) {
        return this.tacz$sprint.getProcessedSprintStatus(sprint);
    }

    @Override
    public void aim(boolean isAim) {
        this.tacz$aim.aim(isAim);
    }

    @Override
    public void crawl(boolean isCrawl) {
        this.tacz$crawl.crawl(isCrawl);
    }

    @Override
    public void updateCacheProperty(AttachmentCacheProperty cacheProperty) {
        this.tacz$data.cacheProperty = cacheProperty;
    }

    @Override
    @Nullable
    public AttachmentCacheProperty getCacheProperty() {
        return this.tacz$data.cacheProperty;
    }

    @Override
    public ShooterDataHolder getDataHolder() {
        return this.tacz$data;
    }

    @Override
    public boolean nextBulletIsTracer(int tracerCountInterval) {
        this.tacz$data.shootCount++;
        if (tracerCountInterval == -1) return false;
        return tacz$data.shootCount % (tracerCountInterval + 1) == 0;
    }

    @Override
    public void fireSelect() {
        this.tacz$fireSelect.fireSelect();
    }

    @Override
    public void zoom() {
        this.tacz$aim.zoom();
    }

    private void taczTick() {
        ReloadState reloadState = this.tacz$reload.tickReloadState();
        this.tacz$aim.tickAimingProgress();
        this.tacz$aim.tickSprint();
        this.tacz$crawl.tickCrawling();
        this.tacz$bolt.tickBolt();
        this.tacz$melee.scheduleTickMelee();
        this.tacz$speed.updateSpeedModifier();
        tacz$shooter.setSprinting(getProcessedSprintStatus(tacz$shooter.isSprinting()));

        ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(tacz$shooter, this.tacz$shoot.getShootCoolDown());
        ModSyncedEntityData.MELEE_COOL_DOWN_KEY.setValue(tacz$shooter, this.tacz$melee.getMeleeCoolDown());
        ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(tacz$shooter, this.tacz$draw.getDrawCoolDown());
        ModSyncedEntityData.IS_BOLTING_KEY.setValue(tacz$shooter, this.tacz$data.isBolting);
        ModSyncedEntityData.RELOAD_STATE_KEY.setValue(tacz$shooter, reloadState);
        ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(tacz$shooter, this.tacz$data.aimingProgress);
        ModSyncedEntityData.IS_AIMING_KEY.setValue(tacz$shooter, this.tacz$data.isAiming);
        ModSyncedEntityData.SPRINT_TIME_KEY.setValue(tacz$shooter, this.tacz$data.sprintTimeS);
    }

    //////////////////////////////////////////////////////////////////////
    // Container and MenuProvider interfaces implementation begins here //
    // ItemHandler capability implementation begins here                //
    //////////////////////////////////////////////////////////////////////

    // Adapted from AbstractMinecartContainer, ContainerEntity and Inventory

    public static final int SWAT_INVENTORY_SIZE = 36;
    public static final int SWAT_ARMOR_SIZE = 4;
    public static final int SWAT_OFFHAND_SIZE = 1;
    public static final int SWAT_CONTAINER_SIZE = SWAT_INVENTORY_SIZE + SWAT_ARMOR_SIZE + SWAT_OFFHAND_SIZE;
    private static final int HOTBAR_INDEX_START = 0;
    private static final int HOTBAR_INDEX_END = 8;
    private static final int INV_INDEX_START = 9;
    private static final int INV_INDEX_END = 35;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SWAT_INVENTORY_SIZE, ItemStack.EMPTY);
    private final NonNullList<ItemStack> armor = NonNullList.withSize(SWAT_ARMOR_SIZE, ItemStack.EMPTY);
    private final NonNullList<ItemStack> offhand = NonNullList.withSize(SWAT_OFFHAND_SIZE, ItemStack.EMPTY);
    private final List<NonNullList<ItemStack>> compartments = ImmutableList.of(this.items, this.armor, this.offhand);
    private LazyOptional<?> itemHandler = LazyOptional.of(() -> new InvWrapper(this));
    private int selected;

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction facing) {
        return capability == ForgeCapabilities.ITEM_HANDLER && this.isAlive() ? this.itemHandler.cast() : super.getCapability(capability, facing);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.itemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.itemHandler = LazyOptional.of(() -> new InvWrapper(this));
    }

    @Override
    public int getContainerSize() {
        return SWAT_CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (NonNullList<ItemStack> compartment : this.compartments) {
            for (ItemStack itemStack : compartment) {
                if (!itemStack.isEmpty()) return false;
            }
        }
        return true;
    }

    @NotNull
    @Override
    public ItemStack getItem(int index) {
        NonNullList<ItemStack> compartment = this.getCompartment(index);
        return compartment == null ? ItemStack.EMPTY : compartmentSafeGet(compartment, index);
    }

    @NotNull
    @Override
    public ItemStack removeItem(int index, int count) {
        if (count <= 0) return ItemStack.EMPTY;
        NonNullList<ItemStack> compartment = this.getCompartment(index);
        if (compartment == null) return ItemStack.EMPTY;
        ItemStack itemStack = compartmentSafeGet(compartment, index);
        if (itemStack.isEmpty()) return ItemStack.EMPTY;
        return itemStack.split(count);
    }

    @NotNull
    @Override
    public ItemStack removeItemNoUpdate(int index) {
        NonNullList<ItemStack> compartment = this.getCompartment(index);
        if (compartment == null) return ItemStack.EMPTY;
        ItemStack itemStack = compartmentSafeGet(compartment, index);
        if (itemStack.isEmpty()) return ItemStack.EMPTY;
        compartment.set(index, ItemStack.EMPTY);
        return itemStack;
    }

    @Override
    public void setItem(int index, @NotNull ItemStack itemStack) {
        NonNullList<ItemStack> compartment = this.getCompartment(index);
        if (compartment != null) {
            compartmentSafeSet(compartment, index, itemStack);
        }
    }

    private int getFreeHotbarIndex() {
        for (int i = HOTBAR_INDEX_START; i <= HOTBAR_INDEX_END; ++i) {
            if (this.items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public void setSelected(int index) {
        this.selected = index;
    }

    public ItemStack getSelectedItem() {
        if (HOTBAR_INDEX_START <= this.selected && this.selected <= HOTBAR_INDEX_END) {
            return this.items.get(this.selected);
        } else {
            return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings("unused")
    private int getFreeInvIndex() {
        for (int i = INV_INDEX_START; i <= INV_INDEX_END; ++i) {
            if (this.items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private ListTag saveCompartments(ListTag listTag) {
        int index;
        CompoundTag slot;
        ItemStack itemStack;
        for (index = 0; index < this.items.size(); ++index) {
            itemStack = this.items.get(index);
            if (!itemStack.isEmpty()) {
                slot = new CompoundTag();
                slot.putByte(NBT_KEY_SLOT, (byte) index);
                itemStack.save(slot);
                listTag.add(slot);
            }
        }
        for (index = 0; index < this.armor.size(); ++index) {
            itemStack = this.armor.get(index);
            if (!itemStack.isEmpty()) {
                slot = new CompoundTag();
                slot.putByte(NBT_KEY_SLOT, (byte) (index + 100));
                itemStack.save(slot);
                listTag.add(slot);
            }
        }
        for (index = 0; index < this.offhand.size(); ++index) {
            itemStack = this.offhand.get(index);
            if (!itemStack.isEmpty()) {
                slot = new CompoundTag();
                slot.putByte(NBT_KEY_SLOT, (byte) (index + 150));
                itemStack.save(slot);
                listTag.add(slot);
            }
        }

        return listTag;
    }

    private void loadCompartments(ListTag listTag) {
        this.items.clear();
        this.armor.clear();
        this.offhand.clear();
        for (int i = 0; i < listTag.size(); ++i) {
            CompoundTag slot = listTag.getCompound(i);
            int index = slot.getByte(NBT_KEY_SLOT) & 255;
            ItemStack itemStack = ItemStack.of(slot);
            if (!itemStack.isEmpty()) {
                // 0 <= index is always true
                if (index < this.items.size()) {
                    this.items.set(index, itemStack);
                } else if (100 <= index && index < this.armor.size() + 100) {
                    this.armor.set(index - 100, itemStack);
                } else if (150 <= index && index < this.offhand.size() + 150) {
                    this.offhand.set(index - 150, itemStack);
                }
            }
        }
    }

    private List<Integer> getAvailableInvSlotsShuffled() {
        ObjectArrayList<Integer> indices = new ObjectArrayList<>();
        for (int i = INV_INDEX_START; i <= INV_INDEX_END; ++i) {
            if (this.items.get(i).isEmpty()) {
                indices.add(i);
            }
        }
        net.minecraft.Util.shuffle(indices, this.random);
        return indices;
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack itemStacks) {
        return false;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return !this.isRemoved() && this.position().closerThan(player.position(), 8.0);
    }

    @Override
    public void clearContent() {
        for (List<ItemStack> list : this.compartments) {
            list.clear();
        }
    }

    public void swapItems(int index1, int index2) {
        if (index1 < 0 || index1 >= this.items.size()) return;
        if (index2 < 0 || index2 >= this.items.size()) return;
        ItemStack temp = this.items.get(index1);
        this.items.set(index1, this.items.get(index2));
        this.items.set(index2, temp);
    }

    // Adapted from AbstractGunItem#canReload
    public boolean hasAmmoForGun(ItemStack gunItemStack) {
        for (ItemStack ammoItemStack : this.items) {
            Item ammoItem = ammoItemStack.getItem();
            if (ammoItem instanceof IAmmo iAmmo) {
                if (iAmmo.isAmmoOfGun(gunItemStack, ammoItemStack)) {
                    return true;
                }
            }
            if (ammoItem instanceof IAmmoBox iAmmoBox) {
                if (iAmmoBox.isAllTypeCreative(ammoItemStack) || iAmmoBox.isCreative(ammoItemStack)) {
                    return true;
                }
                if (iAmmoBox.isAmmoBoxOfGun(gunItemStack, ammoItemStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Integer> getIndicesWithItem(Item item) {
        List<Integer> indices = new ArrayList<>();
        for (int i = HOTBAR_INDEX_START; i <= INV_INDEX_END; ++i) {
            if (this.items.get(i).is(item)) {
                indices.add(i);
            }
        }
        return indices;
    }

    // Common code for finding NonNullList (items / armor / offhand) by slot index
    @Nullable
    private NonNullList<ItemStack> getCompartment(int index) {
        NonNullList<ItemStack> ret = null;
        NonNullList<ItemStack> compartment;
        for (Iterator<NonNullList<ItemStack>> iterator = this.compartments.iterator(); iterator.hasNext(); index -= compartment.size()) {
            compartment = iterator.next();
            if (index < compartment.size()) {
                ret = compartment;
                break;
            }
        }
        return ret;
    }

    // Most of the container code in here was adapted from Inventory class used by player.
    // In Inventory methods somehow don't break even when they try to access index 36 of armor list (size 4)
    // I don't feel like finding out why and how, so we get this...
    private static ItemStack compartmentSafeGet(NonNullList<ItemStack> compartment, int index) {
        return compartment.get(index % compartment.size());
    }

    private static void compartmentSafeSet(NonNullList<ItemStack> compartment, int index, ItemStack itemStack) {
        compartment.set(index % compartment.size(), itemStack);
    }

    @Override
    public int getMaxStackSize() {
        return Container.super.getMaxStackSize();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new SwatCorpseMenu(containerId, playerInventory, this);
    }
}
